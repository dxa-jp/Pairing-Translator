package com.navypool.pairingtranslator.connection

/**
 * 会話ビューの1エントリ。音声1発話分。
 * - original: 話者の言語での原文
 * - translation: 相手の言語への訳文
 * - mine: 自分の発話ならtrue
 * - final: falseの間は部分結果として上書きされる
 */
data class SpeechEntry(
    val id: String,
    val original: String,
    val translation: String,
    val mine: Boolean,
    var final: Boolean = true,
    val ts: Long = System.currentTimeMillis(),
    val isSystem: Boolean = false,
) {
    companion object {
        fun system(text: String) = SpeechEntry(
            id = "system",
            original = text,
            translation = "",
            mine = false,
            isSystem = true,
        )
    }
}

data class LogLine(val time: String, val text: String)
