package com.example.droidautoconnection.connection

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.droidautoconnection.data.BackendClient
import com.example.droidautoconnection.utils.Languages
import com.example.droidautoconnection.voice.VoiceSessionManager
import com.example.droidautoconnection.voice.VoiceState
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

/**
 * Nearby Connectionsによる自動接続 + 音声翻訳セッションの統合管理。
 *
 * - 両端末ともadvertise+discoverを同時実行し、onConnectionInitiatedで即accept(操作ゼロ)
 * - 接続確立後、HELLOで互いの入力言語を交換する
 * - 互いの言語が揃った時点でVoiceSessionManager(サーバーからキー取得→
 *   マイク常時リスニング→Soniox翻訳)を開始する
 * - 自分の発話の確定結果(原文+訳文)はspeechパケットとして相手へ送信する
 * - 切断時は音声を止めてadvertise/discoverを自動再開する
 */
class PeerLinkManager(private val context: Context) :
    VoiceSessionManager.Listener {

    enum class State { IDLE, SEARCHING, CONNECTING, CONNECTED }

    data class PeerInfo(
        val endpointId: String,
        val endpointName: String,
        val deviceId: String,
        val model: String,
        val lang: String,
    )

    companion object {
        const val SERVICE_ID = "com.example.droidautoconnection.SONIX_V1"
        val STRATEGY = Strategy.P2P_POINT_TO_POINT
        private const val PREFS = "poc_prefs"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_MY_LANG = "my_lang"
        private const val MAX_LOG_LINES = 300
        private const val MAX_ENTRIES = 500
        private const val MAX_REQUEST_ATTEMPTS = 3
        private const val MAX_STARTUP_RETRIES = 5
        private const val FALLBACK_REQUEST_DELAY_MS = 4000L
        private const val HEARTBEAT_INTERVAL_MS = 10_000L
        private const val HEARTBEAT_TIMEOUT_MS = 25_000L
        private const val TAG = "PocChat"
    }

    var state by mutableStateOf(State.IDLE)
        private set
    var peer by mutableStateOf<PeerInfo?>(null)
        private set
    var entries by mutableStateOf(listOf<SpeechEntry>())
        private set
    var logs by mutableStateOf(listOf<LogLine>())
        private set
    var serverState by mutableStateOf("未確認")
        private set
    var voiceState by mutableStateOf(VoiceState.IDLE)
        private set

    /** メイン画面で選択した自分の入力言語(次回接続時から反映) */
    var myLang: String
        get() = prefs.getString(KEY_MY_LANG, null)
            ?: Languages.getLanguageCode(Locale.getDefault().toLanguageTag())
        set(value) {
            prefs.edit().putString(KEY_MY_LANG, value).apply()
        }

    val myDeviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }

    val myEndpointName: String by lazy {
        try {
            Settings.Global.getString(
                context.contentResolver,
                Settings.Global.DEVICE_NAME,
            ) ?: Build.MODEL
        } catch (_: Exception) {
            Build.MODEL
        }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val voice = VoiceSessionManager(context, this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false
    private var connectedEndpointId: String? = null
    private val foundAt = HashMap<String, Long>()
    private val requestAttempts = HashMap<String, Int>()
    private var retryCount = 0
    private var heartbeatJob: Job? = null
    private var lastIncomingAt = 0L
    private var serverApproved = false

    // 自分の発話の途中表示。受信するスナップショットで置き換える。
    private var liveEntryId: String? = null

    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 音声リスナーはOkHttpスレッドからも呼ばれるため、UI状態の更新は必ずメインスレッドで行う */
    private fun onMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    private fun log(text: String) {
        android.util.Log.d(TAG, text)
        logs = (logs + LogLine(timeFmt.format(Date()), text)).takeLast(MAX_LOG_LINES)
    }

    private fun addEntry(entry: SpeechEntry) {
        entries = (entries + entry).takeLast(MAX_ENTRIES)
    }

    fun start() {
        if (started) return
        started = true
        retryCount = 0
        state = State.SEARCHING
        log("開始: advertise + discover (service=$SERVICE_ID)")
        registerIfNeeded()
        restartSessions()
    }

    /** サーバーに端末を登録する(管理UIでの承認を待つ) */
    private fun registerIfNeeded() {
        if (serverApproved) return
        serverState = "登録中…"
        scope.launch {
            when (val result = BackendClient.registerDevice(myDeviceId)) {
                is BackendClient.RegisterResult.Approved -> {
                    serverApproved = true
                    serverState = "承認済み"
                    log("サーバー: 端末は承認済みです")
                }
                is BackendClient.RegisterResult.Rejected -> {
                    serverState = "未承認(管理UIで承認してください)"
                    log("サーバー: 未承認(${result.message ?: "理由不明"})")
                }
                is BackendClient.RegisterResult.Error -> {
                    serverState = "サーバーエラー: ${result.message}"
                    log("サーバー: 登録エラー ${result.message}")
                }
            }
        }
    }

    fun stop() {
        started = false
        heartbeatJob?.cancel()
        voice.shutdown()
        runCatching { client.stopAdvertising() }
        runCatching { client.stopDiscovery() }
        runCatching { client.stopAllEndpoints() }
        connectedEndpointId = null
        peer = null
        state = State.IDLE
        log("停止")
    }

    /**
     * 画面ON・アプリ復帰時に呼ぶ。スリープ中にGMSがセッションを黙って停止
     * させることがあるため、能動的に張り直す。サーバー未承認なら再登録も試す。
     */
    fun resume() {
        if (!started) return
        if (!serverApproved) registerIfNeeded()
        if (state == State.CONNECTED) {
            val silentMs = System.currentTimeMillis() - lastIncomingAt
            if (silentMs > HEARTBEAT_TIMEOUT_MS) {
                log("復帰: 相手との通信が${silentMs / 1000}秒途絶えているため接続を破棄します")
                forceDisconnect()
            }
            return
        }
        if (state == State.CONNECTING) return
        log("復帰: advertise/discoverを張り直します")
        restartSessions()
    }

    /**
     * 前プロセスやスリープ前にGMSへ残ったセッション(幽霊)と新規開始が
     * 衝突してSTATUS_ALREADY_ADVERTISING(8001)が出るため、
     * stop→待ち→startの順で確実に張り直す。
     */
    private fun restartSessions() {
        scope.launch {
            runCatching { client.stopAdvertising() }
            runCatching { client.stopDiscovery() }
            delay(500)
            if (!started || state == State.CONNECTED || state == State.CONNECTING) return@launch
            retryCount = 0
            state = State.SEARCHING
            startAdvertising()
            startDiscovery()
        }
    }

    private fun startAdvertising() {
        client.startAdvertising(
            myEndpointName,
            SERVICE_ID,
            connectionLifecycleCallback,
            AdvertisingOptions.Builder().setStrategy(STRATEGY).build(),
        ).addOnSuccessListener {
            log("advertise開始: $myEndpointName")
        }.addOnFailureListener {
            onStartupFailure("advertise", it)
        }
    }

    private fun startDiscovery() {
        client.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            DiscoveryOptions.Builder().setStrategy(STRATEGY).build(),
        ).addOnSuccessListener {
            log("discover開始")
        }.addOnFailureListener {
            onStartupFailure("discover", it)
        }
    }

    private fun onStartupFailure(what: String, e: Exception) {
        val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
        // 「すでに実行中」は実質問題ない(セッションが生きている)ため再試行しない
        if (code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING ||
            code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING
        ) {
            log("$what はすでに実行中のため維持します")
            return
        }
        log("$what 失敗: ${e.message}")
        if (!started) return
        if (retryCount >= MAX_STARTUP_RETRIES) {
            log("$what の再試行上限に達しました。画面の再表示で再開します")
            return
        }
        val delayMs = (1000L shl retryCount.coerceAtMost(3)).coerceAtMost(8000L)
        retryCount++
        scope.launch {
            delay(delayMs)
            if (started && state != State.CONNECTED) {
                log("再試行($what)")
                if (what == "advertise") startAdvertising() else startDiscovery()
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            log("端末を検出: ${info.endpointName} ($endpointId)")
            foundAt[endpointId] = System.currentTimeMillis()
            requestAttempts.remove(endpointId)
            // 両側から同時にrequestConnectionするとSTATUS_ENDPOINT_IO_ERROR(8012)に
            // なるため、端末名の辞書順で小さい側だけが要求する(両側で同じ判定になる)。
            // 同一名の場合は両側がフォールバック側に回り、再試行が吸収する。
            if (myEndpointName < info.endpointName) {
                scheduleConnectionRequest(endpointId, Random.nextLong(100, 800))
            } else {
                scope.launch {
                    delay(FALLBACK_REQUEST_DELAY_MS)
                    if (!started || connectedEndpointId != null || state == State.CONNECTING) return@launch
                    if (requestAttempts.containsKey(endpointId)) return@launch
                    log("相手からの要求を待ちましたが来ないため要求します: $endpointId")
                    scheduleConnectionRequest(endpointId, 0)
                }
            }
        }

        override fun onEndpointLost(endpointId: String) {
            log("端末を見失う: $endpointId")
            foundAt.remove(endpointId)
            requestAttempts.remove(endpointId)
        }
    }

    /**
     * 接続要求。失敗時はバックオフ付きで再試行する。
     */
    private fun scheduleConnectionRequest(endpointId: String, delayMs: Long) {
        scope.launch {
            delay(delayMs)
            if (!started || connectedEndpointId != null) return@launch
            val attempts = requestAttempts.getOrDefault(endpointId, 0)
            if (attempts >= MAX_REQUEST_ATTEMPTS) return@launch
            client.requestConnection(myEndpointName, endpointId, connectionLifecycleCallback)
                .addOnSuccessListener { log("接続要求を送信: $endpointId") }
                .addOnFailureListener { e ->
                    val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
                    if (code == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) {
                        log("すでに接続中/接続済みのため維持します: $endpointId")
                        return@addOnFailureListener
                    }
                    val next = attempts + 1
                    requestAttempts[endpointId] = next
                    log("接続要求失敗(${next}/${MAX_REQUEST_ATTEMPTS}回目): ${e.message}")
                    if (next < MAX_REQUEST_ATTEMPTS) {
                        scheduleConnectionRequest(endpointId, delayMs = 1000L * next)
                    } else {
                        log("接続要求を断念: $endpointId (再検出を待ちます)")
                    }
                }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            log("接続開始 → 自動accept: ${connectionInfo.endpointName} ($endpointId)")
            state = State.CONNECTING
            if (peer == null) {
                peer = PeerInfo(endpointId, connectionInfo.endpointName, "?", "?", "?")
            }
            client.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { log("accept失敗: ${it.message}") }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    connectedEndpointId = endpointId
                    state = State.CONNECTED
                    retryCount = 0
                    runCatching { client.stopAdvertising() }
                    runCatching { client.stopDiscovery() }
                    val elapsed = foundAt[endpointId]?.let { System.currentTimeMillis() - it }
                    if (elapsed != null) {
                        log("接続確立 (検出から${elapsed}ms): $endpointId")
                    } else {
                        log("接続確立: $endpointId")
                    }
                    lastIncomingAt = System.currentTimeMillis()
                    startHeartbeat()
                    sendHello()
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED ->
                    log("接続が拒否されました: $endpointId")
                else ->
                    log("接続結果エラー(code=${result.status.statusCode}): $endpointId")
            }
            if (result.status.statusCode != ConnectionsStatusCodes.STATUS_OK && started) {
                if (peer?.endpointId == endpointId) peer = null
                state = State.SEARCHING
                startAdvertising()
                startDiscovery()
            }
        }

        override fun onDisconnected(endpointId: String) {
            log("切断: $endpointId")
            if (endpointId == connectedEndpointId) {
                heartbeatJob?.cancel()
                connectedEndpointId = null
                peer = null
                voice.pause()
                if (started) {
                    state = State.SEARCHING
                    addEntry(SpeechEntry.system("接続が切れました。再接続を試みています…"))
                    startAdvertising()
                    startDiscovery()
                } else {
                    state = State.IDLE
                }
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type != Payload.Type.BYTES) return
            val bytes = payload.asBytes() ?: return
            lastIncomingAt = System.currentTimeMillis()
            when (val packet = Protocol.parse(bytes)) {
                is Protocol.Packet.Hello -> {
                    peer = PeerInfo(
                        endpointId = endpointId,
                        endpointName = packet.name,
                        deviceId = packet.deviceId,
                        model = packet.model,
                        lang = packet.lang,
                    )
                    log("HELLO受信: ${packet.name} (${packet.model}, lang=${packet.lang})")
                    addEntry(
                        SpeechEntry.system(
                            "${packet.name}(${Languages.codeToDisplayMap[packet.lang] ?: packet.lang}) と接続しました",
                        ),
                    )
                    // 互いの言語が揃ったので音声セッションを開始する
                    voice.start(myLang, packet.lang, myDeviceId)
                }
                is Protocol.Packet.Speech ->
                    addEntry(
                        SpeechEntry(
                            id = UUID.randomUUID().toString(),
                            original = packet.original,
                            translation = packet.translation,
                            mine = false,
                            final = true,
                            ts = packet.ts,
                        ),
                    )
                is Protocol.Packet.Ping ->
                    runCatching {
                        client.sendPayload(
                            endpointId,
                            Payload.fromBytes(Protocol.pong(System.currentTimeMillis())),
                        )
                    }
                is Protocol.Packet.Pong -> Unit
                null -> log("解析できないパケットを受信 (${bytes.size} bytes)")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // BYTESペイロードはonPayloadReceived時点で完了しているため何もしない
        }
    }

    /**
     * 10秒ごとにPINGを送り、相手からいかなるパケットも25秒間受信しなけれ
     * ば接続が死んだと判断して張り直す。相手アプリの中断・強制終了時に
     * onDisconnectedがこちらへ届かないケースの保険。
     */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && started && state == State.CONNECTED) {
                delay(HEARTBEAT_INTERVAL_MS)
                val silentMs = System.currentTimeMillis() - lastIncomingAt
                if (silentMs > HEARTBEAT_TIMEOUT_MS) {
                    log("相手からの応答が${silentMs / 1000}秒ないため切断と判断します")
                    forceDisconnect()
                    break
                }
                val id = connectedEndpointId ?: break
                runCatching {
                    client.sendPayload(id, Payload.fromBytes(Protocol.ping(System.currentTimeMillis())))
                }
            }
        }
    }

    private fun forceDisconnect() {
        heartbeatJob?.cancel()
        val id = connectedEndpointId
        connectedEndpointId = null
        peer = null
        voice.pause()
        if (id != null) runCatching { client.disconnectFromEndpoint(id) }
        addEntry(SpeechEntry.system("応答がないため接続を切断しました。再接続を試みます…"))
        if (started) {
            state = State.SEARCHING
            restartSessions()
        } else {
            state = State.IDLE
        }
    }

    private fun sendHello() {
        val endpointId = connectedEndpointId ?: return
        val bytes = Protocol.hello(
            deviceId = myDeviceId,
            name = myEndpointName,
            model = Build.MODEL,
            lang = myLang,
        )
        runCatching { client.sendPayload(endpointId, Payload.fromBytes(bytes)) }
            .onFailure { log("HELLO送信失敗: ${it.message}") }
    }

    private fun sendSpeech(original: String, translation: String) {
        val endpointId = connectedEndpointId ?: return
        runCatching {
            client.sendPayload(
                endpointId,
                Payload.fromBytes(
                    Protocol.speech(myDeviceId, original, translation, myLang, System.currentTimeMillis()),
                ),
            )
        }.onFailure { log("speech送信失敗: ${it.message}") }
    }

    private fun updateLiveEntry(update: (SpeechEntry) -> SpeechEntry) {
        val id = liveEntryId ?: return
        entries = entries.map { if (it.id == id) update(it) else it }
    }

    // --- VoiceSessionManager.Listener ---

    override fun onVoicePartial(original: String, translation: String) {
        onMainThread {
            if (original.isBlank() && translation.isBlank()) {
                val id = liveEntryId
                entries = entries.filterNot { it.id == id }
                liveEntryId = null
                return@onMainThread
            }
            if (liveEntryId == null) {
                liveEntryId = UUID.randomUUID().toString()
                addEntry(
                    SpeechEntry(
                        id = liveEntryId!!,
                        original = "",
                        translation = "",
                        mine = true,
                        final = false,
                    ),
                )
            }
            updateLiveEntry {
                it.copy(original = original, translation = translation)
            }
        }
    }

    override fun onVoiceFinal(original: String, translation: String) {
        onMainThread {
            val id = liveEntryId
            liveEntryId = null
            if (id != null) {
                entries = entries.map {
                    if (it.id == id) it.copy(original = original, translation = translation, final = true)
                    else it
                }
            } else if (original.isNotBlank()) {
                addEntry(
                    SpeechEntry(
                        id = UUID.randomUUID().toString(),
                        original = original,
                        translation = translation,
                        mine = true,
                        final = true,
                    ),
                )
            }
            if (original.isNotBlank() || translation.isNotBlank()) {
                sendSpeech(original, translation)
            }
        }
    }

    override fun onVoiceError(message: String) {
        onMainThread {
            log("音声エラー: $message")
            addEntry(SpeechEntry.system(message))
        }
    }

    override fun onVoiceDebug(message: String) {
        onMainThread { log(message) }
    }

    override fun onVoiceStateChanged(state: VoiceState) {
        onMainThread {
            if (state != VoiceState.LISTENING) onVoicePartial("", "")
            voiceState = state
        }
    }
}
