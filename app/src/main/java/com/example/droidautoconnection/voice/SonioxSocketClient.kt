package com.example.droidautoconnection.voice

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

/**
 * SonioxのリアルタイムSTT+翻訳WebSocketクライアント。
 * MultiTranslator/HonyakuSapoの実装を移植したもの。
 *
 * プロトコル:
 * - 接続後、最初にJSON設定メッセージを送る(api_key/model/language/translation)
 * - 以降、音声はバイナリフレーム(16kHz PCM s16le)で送る
 * - 結果はテキストフレームで tokens[] が返る
 *   - text, is_final, translation_status("original"/"translation"), language
 *   - 各メッセージは現在発話の累積トークンを含む
 *
 * 確定タイミング(HonyakuSapo方式):
 * - 原文と訳文は独立してストリーミングされ、訳文は原文より遅れて届く
 * - 原文のis_finalでは確定しない(この時点では訳文が未完成)
 * - **訳文のis_finalが立った時点で**、そこまでの原文ファイナル+訳文ファイナルの
 *   ペアをonFinalで返す。これにより喋り途中の文章が送信されない
 */
class SonioxSocketClient(
    private val apiKey: String,
    private val model: String,
    private val sourceLang: String,
    private val targetLang: String,
    private val listener: Listener,
) : WebSocketListener() {

    interface Listener {
        /** 設定送信済みで音声送信可能になった */
        fun onReady()
        /** 部分結果(差分)。発話中表示用 */
        fun onPartial(originalNew: String, translationNew: String)
        /** 確定結果。訳文のis_finalを待ってから発火する */
        fun onFinal(originalFull: String, translationFull: String)
        fun onError(message: String)
        fun onClosed()
        /** デバッグ情報(生応答ダンプなど) */
        fun onDebug(message: String)
    }

    companion object {
        private const val TAG = "PocChat"
        private const val URL = "wss://stt-rt.soniox.com/transcribe-websocket"

        private const val DUMP_LEN = 350
    }

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    @Volatile private var ready = false

    // 表示用: 各メッセージの累積トークンから既出分を差し引くためのプレフィックス
    private var confirmedOriginal = ""
    private var confirmedTranslation = ""

    // 送信用: 最後の訳文確定以降のファイナル原文/訳文の蓄積
    private val finalOriginalAccum = StringBuilder()
    private val finalTranslationAccum = StringBuilder()
    private var loggedConfigDump = false

    fun start() {
        val request = Request.Builder().url(URL).build()
        webSocket = client.newWebSocket(request, this)
    }

    fun stop() {
        ready = false
        runCatching { webSocket?.close(1000, "client stop") }
        webSocket = null
    }

    fun sendAudio(data: ByteArray) {
        if (!ready) return
        webSocket?.send(data.toByteString())
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.d(TAG, "Soniox WS open")
        resetUtteranceState()
        val config = JSONObject().apply {
            put("api_key", apiKey)
            put("model", model)
            // 入力言語を単数形の"language"で固定指定する(HonyakuSapoの実績方式)。
            // ※"languages"(複数形配列)を渡すとone_way翻訳が効かなくなる
            put("language", sourceLang)
            put("audio_format", "pcm_s16le")
            put("sample_rate", 16000)
            put("num_channels", 1)
            put("enable_streaming_translation", true)
            put("include_non_final", true)
            put("translation", JSONObject().apply {
                put("type", "one_way")
                put("target_language", targetLang)
            })
        }
        listener.onDebug("Soniox設定: ${config.dump(600)}")
        webSocket.send(config.toString())
        ready = true
        listener.onReady()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            val json = JSONObject(text)
            if (json.has("error_message") || json.has("error_type")) {
                val type = json.optString("error_type", "unknown")
                val msg = json.optString("error_message", "Soniox error")
                Log.e(TAG, "Soniox error: $type $msg")
                listener.onError("Soniox: $msg")
                return
            }
            if (!json.has("tokens")) return
            val tokens = json.optJSONArray("tokens") ?: return

            val currentOriginal = StringBuilder()
            val currentTranslation = StringBuilder()
            var lastOriginalFinal = false
            var lastTranslationFinal = false
            var sawTranslationToken = false

            for (i in 0 until tokens.length()) {
                val token = tokens.optJSONObject(i) ?: continue
                val tokenText = token.optString("text", "")
                if (tokenText.isEmpty()) continue
                val isFinal = token.optBoolean("is_final", false)
                val status = token.optString("translation_status", "original")

                if (status == "translation") {
                    sawTranslationToken = true
                    currentTranslation.append(tokenText)
                    if (isFinal) lastTranslationFinal = true
                } else {
                    currentOriginal.append(tokenText)
                    if (isFinal) lastOriginalFinal = true
                }
            }

            // デバッグ: 訳文トークンが来ているかの確認用(全メッセージは量が多すぎるため
            // 訳文トークンあり/原文確定/訳文確定のいずれかの時だけダンプ)
            if (!loggedConfigDump || sawTranslationToken || lastOriginalFinal || lastTranslationFinal) {
                loggedConfigDump = true
                val rootLang = json.optString("language", "")
                listener.onDebug(
                    "Soniox応答(lang=$rootLang, origFin=$lastOriginalFinal, trFin=$lastTranslationFinal): " +
                        buildString {
                            append("orig=[")
                            append(currentOriginal.dump(120))
                            append("] tr=[")
                            append(currentTranslation.dump(120))
                            append("]")
                        },
                )
            }

            // 差分計算(累積→既出プレフィックスを除去)
            val newOriginal = stripPrefix(currentOriginal.toString(), confirmedOriginal)
            val newTranslation = stripPrefix(currentTranslation.toString(), confirmedTranslation)

            // 送信用蓄積には is_final の立ったトークンのみ使う
            // (未確定トークンは書き換わり続けるため差分蓄積すると壊れる)
            if (lastOriginalFinal) {
                confirmedOriginal = "" // 次のメッセージから新しい発話の累積が始まる
            } else if (newOriginal.isNotEmpty()) {
                confirmedOriginal = currentOriginal.toString()
            }

            if (lastTranslationFinal) {
                confirmedTranslation = ""
            } else if (newTranslation.isNotEmpty()) {
                confirmedTranslation = currentTranslation.toString()
            }

            // ファイナル原文/訳文トークンだけを蓄積してペアを作る
            for (i in 0 until tokens.length()) {
                val token = tokens.optJSONObject(i) ?: continue
                val tokenText = token.optString("text", "")
                if (tokenText.isEmpty()) continue
                if (!token.optBoolean("is_final", false)) continue
                when (token.optString("translation_status", "original")) {
                    "translation" -> finalTranslationAccum.append(tokenText)
                    else -> finalOriginalAccum.append(tokenText)
                }
            }

            when {
                // 訳文のis_final → 原文+訳文のペアを確定して返す
                lastTranslationFinal -> {
                    val orig = finalOriginalAccum.toString().trim()
                    val tr = finalTranslationAccum.toString().trim()
                    finalOriginalAccum.clear()
                    finalTranslationAccum.clear()
                    if (orig.isNotEmpty() || tr.isNotEmpty()) {
                        listener.onFinal(orig, tr)
                    }
                }
                // 途中経過(訳文確定のメッセージでは部分結果を出さない)
                !lastOriginalFinal -> {
                    if (newOriginal.isNotEmpty() || newTranslation.isNotEmpty()) {
                        listener.onPartial(newOriginal, newTranslation)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Soniox parse error: ${e.message}")
        }
    }

    /** 切断時に未確定の蓄積があれば確定として吐き出す */
    private fun flushPending() {
        val orig = finalOriginalAccum.toString().trim()
        val tr = finalTranslationAccum.toString().trim()
        finalOriginalAccum.clear()
        finalTranslationAccum.clear()
        if (orig.isNotEmpty() || tr.isNotEmpty()) {
            listener.onFinal(orig, tr)
        }
    }

    private fun resetUtteranceState() {
        confirmedOriginal = ""
        confirmedTranslation = ""
        finalOriginalAccum.clear()
        finalTranslationAccum.clear()
        loggedConfigDump = false
    }

    /**
     * 累積テキストから既に表示済みのプレフィックスを取り除く。
     * Sonioxは累積の仕方が微妙に変わることがあるため、末尾一致での
     * オーバーラップ除去も行う(HonyakuSapoと同じ方針)。
     */
    private fun stripPrefix(fullText: String, prefix: String): String {
        if (prefix.isEmpty()) return fullText
        if (fullText.length >= prefix.length && fullText.startsWith(prefix)) {
            return fullText.substring(prefix.length)
        }
        if (fullText.length > prefix.length && prefix.startsWith(fullText)) {
            return "" // 累積が縮んだ(確定済み分が削られた)場合は新規分なし
        }
        // 末尾50文字でオーバーラップを探す
        val search = if (prefix.length > 50) prefix.substring(prefix.length - 50) else prefix
        val index = fullText.lastIndexOf(search)
        if (index != -1) return fullText.substring(index + search.length)
        return fullText
    }

    private fun StringBuilder.dump(max: Int): String {
        val s = toString()
        return if (s.length > max) s.substring(0, max) + "…" else s
    }

    private fun JSONObject.dump(max: Int): String {
        val s = toString()
        return if (s.length > max) s.substring(0, max) + "…" else s
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        if (!ready) return
        ready = false
        Log.e(TAG, "Soniox WS failure: ${t.message}")
        flushPending()
        listener.onError("Soniox接続エラー: ${t.message}")
        listener.onClosed()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        if (!ready) return
        ready = false
        flushPending()
        listener.onClosed()
    }
}
