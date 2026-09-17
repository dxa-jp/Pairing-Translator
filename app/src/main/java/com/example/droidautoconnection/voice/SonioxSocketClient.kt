package com.example.droidautoconnection.voice

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString
import org.json.JSONObject

/** 確定トークンを蓄積し、発話の終端で原文・訳文を送信する。 */
class SonioxSocketClient(
    private val apiKey: String,
    private val model: String,
    private val sourceLang: String,
    private val targetLang: String,
    private val listener: Listener,
) : WebSocketListener() {
    interface Listener {
        fun onReady()
        /** 現在の発話全体。追記せず置き換えて表示する。 */
        fun onPartial(originalNew: String, translationNew: String)
        fun onFinal(originalFull: String, translationFull: String)
        fun onError(message: String)
        fun onClosed()
        fun onDebug(message: String)
    }

    companion object {
        private const val TAG = "PocChat"
        private const val URL = "wss://stt-rt.soniox.com/transcribe-websocket"
    }

    private val client = OkHttpClient()
    private val transcript = SonioxTranscript(sourceLang, targetLang)
    private var webSocket: WebSocket? = null
    @Volatile private var ready = false
    @Volatile private var stopped = false

    fun start() {
        stopped = false
        webSocket = client.newWebSocket(Request.Builder().url(URL).build(), this)
    }

    fun stop() {
        stopped = true
        ready = false
        runCatching { webSocket?.close(1000, "client stop") }
        webSocket = null
    }

    fun sendAudio(data: ByteArray) {
        if (ready) webSocket?.send(data.toByteString())
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        if (stopped) {
            webSocket.close(1000, "client stop")
            return
        }
        val config = SonioxSessionConfig.create(apiKey, model, sourceLang, targetLang)
        // 一時キーを含む設定JSONをログに出さない。
        listener.onDebug("Soniox設定: model=$model, input=$sourceLang, target=$targetLang, strict=false, endpoint=true")
        webSocket.send(config.toString())
        ready = true
        listener.onReady()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        if (stopped) return
        try {
            val json = JSONObject(text)
            if (json.has("error_message") || json.has("error_type")) {
                listener.onError("Soniox: ${json.optString("error_message", "認識エラー")}")
                return
            }
            if (!json.has("tokens") && !json.optBoolean("finished")) return
            // キーを含まない応答の確定トークンだけを診断可能にする。
            val tokens = json.optJSONArray("tokens")
            val tokenSummary = (0 until (tokens?.length() ?: 0)).mapNotNull { index ->
                val token = tokens?.optJSONObject(index) ?: return@mapNotNull null
                if (!token.optBoolean("is_final")) return@mapNotNull null
                "${token.optString("translation_status")}/${token.optString("language")}/${token.optString("source_language")}: ${token.optString("text")}"
            }
            if (tokenSummary.isNotEmpty()) {
                listener.onDebug("Soniox確定トークン(status/lang/source): " + tokenSummary.joinToString(" | ").take(1200))
            }
            val update = transcript.accept(json)
            for (utterance in update.completed) {
                listener.onDebug("Soniox確定: input=$sourceLang, original=${utterance.original}, translation=${utterance.translation}")
                listener.onFinal(utterance.original, utterance.translation)
            }
            listener.onPartial(update.partial.original, update.partial.translation)
            if (update.rejectedTokens > 0) {
                listener.onDebug("Soniox対象外トークン除外: ${update.rejectedTokens}")
            }
            if (update.rejectedUtterances > 0) {
                listener.onDebug("Soniox原文の文字種不一致を除外: ${update.rejectedUtterances}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Soniox parse error: ${e.message}")
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        if (stopped) return
        ready = false
        listener.onPartial("", "")
        listener.onError("Soniox接続エラー: ${t.message}")
        listener.onClosed()
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        if (stopped) return
        ready = false
        // 切断を翻訳完了とみなさず、未完了の内容は送らない。
        listener.onPartial("", "")
        listener.onClosed()
    }
}
