package com.example.droidautoconnection.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.example.droidautoconnection.data.BackendClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 音声セッションの状態。
 * IDLE: 停止中(相手未接続など)
 * REQUESTING_KEY: サーバーから一時キー取得中
 * LISTENING: 録音+Soniox認識中
 * ERROR: エラー(メッセージを添えて再試行待ち)
 */
enum class VoiceState { IDLE, REQUESTING_KEY, LISTENING, ERROR }

/**
 * 常時リスニング音声セッションの管理。
 * - サーバーからSoniox一時キーを取得してWebSocketセッションを張る
 * - 16kHz PCMモノラルでマイクを読み、Sonioxへ常時送信する
 * - 一時キー(実効1時間)とSonioxストリーム上限(300分)に備え、55分ごとに
 *   セッションを張り直す
 * - 相手との接続が切れたらpause()で止め、再接続時にstart()で再開する
 */
class VoiceSessionManager(
    private val context: Context,
    private val listener: Listener,
) : SonioxSocketClient.Listener {

    interface Listener {
        fun onVoicePartial(original: String, translation: String)
        fun onVoiceFinal(original: String, translation: String)
        fun onVoiceError(message: String)
        fun onVoiceStateChanged(state: VoiceState)
        fun onVoiceDebug(message: String)
    }

    companion object {
        private const val TAG = "PocChat"
        private const val SAMPLE_RATE = 16000
        private const val ROTATION_MINUTES = 55L
        private const val KEY_REFRESH_MARGIN_MS = 5 * 60 * 1000L
    }

    var state: VoiceState = VoiceState.IDLE
        private set(value) {
            field = value
            listener.onVoiceStateChanged(value)
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socketClient: SonioxSocketClient? = null
    private var audioRecord: AudioRecord? = null
    private var audioJob: Job? = null
    private var rotationJob: Job? = null
    private var sourceLang: String = "ja"
    private var targetLang: String = "en"
    private var deviceId: String = ""
    private var keyFetchedAt: Long = 0
    @Volatile private var running = false

    /** 相手と接続したら呼ぶ。既に動作中なら言語変更がなければ何もしない */
    fun start(source: String, target: String, deviceId: String) {
        this.deviceId = deviceId
        if (running && source == sourceLang && target == targetLang) return
        pause()
        sourceLang = source
        targetLang = target
        running = true
        scope.launch { startSession() }
    }

    /** 相手と切断したら呼ぶ。キーは温存し、マイクとソケットだけ止める */
    fun pause() {
        running = false
        rotationJob?.cancel()
        rotationJob = null
        audioJob?.cancel()
        audioJob = null
        runCatching { audioRecord?.stop() }
        audioRecord?.release()
        audioRecord = null
        socketClient?.stop()
        socketClient = null
        state = VoiceState.IDLE
    }

    fun shutdown() = pause()

    private suspend fun startSession() {
        if (!running) return

        val keyResult = BackendClient.fetchTempKey(deviceId)
        if (!running) return
        when (keyResult) {
            is BackendClient.KeyResult.Ok -> {
                keyFetchedAt = System.currentTimeMillis()
                startListening(keyResult.key, keyResult.model)
            }
            is BackendClient.KeyResult.Rejected -> {
                val reason = keyResult.errorCode ?: "REJECTED"
                state = VoiceState.ERROR
                listener.onVoiceError("キー取得拒否($reason): ${keyResult.message ?: ""}")
            }
            is BackendClient.KeyResult.Error -> {
                state = VoiceState.ERROR
                listener.onVoiceError("サーバーエラー: ${keyResult.message}")
            }
        }
    }

    @SuppressLint("MissingPermission") // 呼び出し前にPermissions.granted()で確認済み
    private fun startListening(apiKey: String, model: String) {
        if (!running) return
        state = VoiceState.LISTENING
        Log.d(TAG, "音声セッション開始: $sourceLang → $targetLang")

        socketClient = SonioxSocketClient(apiKey, model, sourceLang, targetLang, this).also { it.start() }

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuf * 2,
        )
        val buffer = ByteArray(minBuf * 2)
        audioRecord?.startRecording()

        audioJob = scope.launch {
            while (isActive && running) {
                val record = audioRecord ?: break
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    socketClient?.sendAudio(buffer.copyOf(read))
                }
            }
        }

        scheduleRotation()
    }

    /** 一時キー失効前にセッションを張り直す */
    private fun scheduleRotation() {
        rotationJob?.cancel()
        rotationJob = scope.launch {
            delay(ROTATION_MINUTES * 60 * 1000)
            if (!running) return@launch
            Log.d(TAG, "セッションローテーション: 張り直します")
            audioJob?.cancel()
            runCatching { audioRecord?.stop() }
            audioRecord?.release()
            audioRecord = null
            socketClient?.stop()
            socketClient = null
            startSession()
        }
    }

    private fun restartDueToKeyExpiry() {
        val elapsed = System.currentTimeMillis() - keyFetchedAt
        if (elapsed < 60 * 60 * 1000 - KEY_REFRESH_MARGIN_MS) {
            // キー失効以外の切断。バックオフ再接続はuiEvents経由で上位に任せる
            state = VoiceState.ERROR
            return
        }
        scope.launch {
            pause()
            running = true
            startSession()
        }
    }

    // --- SonioxSocketClient.Listener ---

    override fun onReady() {
        state = VoiceState.LISTENING
    }

    override fun onPartial(originalNew: String, translationNew: String) {
        listener.onVoicePartial(originalNew, translationNew)
    }

    override fun onFinal(originalFull: String, translationFull: String) {
        listener.onVoiceFinal(originalFull, translationFull)
    }

    override fun onError(message: String) {
        listener.onVoiceError(message)
    }

    override fun onDebug(message: String) {
        listener.onVoiceDebug(message)
    }

    override fun onClosed() {
        if (!running) return
        Log.d(TAG, "Soniox WS切断 → 再開を試みます")
        restartDueToKeyExpiry()
    }
}
