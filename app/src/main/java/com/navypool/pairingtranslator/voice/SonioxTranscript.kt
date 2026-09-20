package com.navypool.pairingtranslator.voice

import org.json.JSONObject

/** 確定分は一度だけ追記、未確定分は毎応答で置換する。 */
internal class SonioxTranscript(private val sourceLang: String, private val targetLang: String) {
    data class Utterance(val original: String, val translation: String)
    data class Update(val completed: List<Utterance>, val partial: Utterance,
                      val rejectedTokens: Int, val rejectedUtterances: Int)

    private val original = StringBuilder()
    private val translation = StringBuilder()
    private var awaitingTranslation = false

    fun accept(response: JSONObject): Update {
        val completed = mutableListOf<Utterance>()
        val partialOriginal = StringBuilder()
        val partialTranslation = StringBuilder()
        var rejected = 0
        var rejectedUtterances = 0

        fun finish(streamFinished: Boolean = false) {
            val utterance = Utterance(original.toString().trim(), translation.toString().trim())
            if (utterance.original.isNotEmpty() && !matchesSourceScript(utterance.original)) {
                rejectedUtterances++
                original.clear()
                translation.clear()
                partialOriginal.clear()
                partialTranslation.clear()
                awaitingTranslation = false
                return
            }
            if (utterance.original.isNotEmpty() && utterance.translation.isNotEmpty()) {
                completed.add(utterance)
            }
            // 原文の終端が先に届いても、遅れて届く訳文まで原文を保持する。
            if (!streamFinished && utterance.original.isNotEmpty() && utterance.translation.isEmpty()) {
                awaitingTranslation = true
                partialOriginal.clear()
                partialTranslation.clear()
                return
            }
            awaitingTranslation = false
            original.clear()
            translation.clear()
            partialOriginal.clear()
            partialTranslation.clear()
        }

        val tokens = response.optJSONArray("tokens")
        for (i in 0 until (tokens?.length() ?: 0)) {
            val token = tokens?.optJSONObject(i) ?: continue
            val text = token.optString("text")
            val isFinal = token.optBoolean("is_final")
            if (text == "<fin>") continue // 手動確定の通知は発話終端ではない。
            if (text == "<end>") {
                if (isFinal) finish()
                continue
            }
            if (text.isEmpty()) continue
            val status = token.optString("translation_status")
            val language = token.optString("language")
            val isOriginal = status == "original" && language == sourceLang
            // source_language を含まない応答形式でも訳文を落とさない。
            val translationSource = token.optString("source_language")
            val isTranslation = status == "translation" && language == targetLang &&
                (translationSource.isEmpty() || translationSource == sourceLang)
            if (isOriginal && awaitingTranslation) {
                // 訳文が来ないまま新しい原文が始まった場合、前の発話を混ぜない。
                original.clear()
                translation.clear()
                awaitingTranslation = false
            }
            when {
                isOriginal && isFinal -> original.append(text)
                isOriginal -> partialOriginal.append(text)
                isTranslation && isFinal -> translation.append(text)
                isTranslation -> partialTranslation.append(text)
                else -> rejected++ // none・他言語・言語不明を自分の発話に混ぜない。
            }
        }
        if (response.optBoolean("finished")) finish(streamFinished = true)
        val displayOriginal = original.toString() + partialOriginal
        val displayTranslation = translation.toString() + partialTranslation
        return Update(
            completed,
            if (matchesSourceScript(displayOriginal)) Utterance(displayOriginal, displayTranslation)
            else Utterance("", ""),
            rejected,
            rejectedUtterances,
        )
    }

    private fun matchesSourceScript(text: String): Boolean {
        if (text.isBlank()) return false
        // 日本語ラベルの英語だけの発話を通さない。日本語文中の英字・製品名は残す。
        return sourceLang != "ja" || text.any {
            it in '\u3040'..'\u30ff' || it in '\u3400'..'\u9fff' || it in '\uff66'..'\uff9d'
        }
    }
}
