package com.example.droidautoconnection.voice

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

/** 合成音声を実Sonioxへ送った応答を、本番と同じ受信処理に再生する。 */
class SonioxLiveResponseTest {
    private fun replay(source: String, target: String): List<SonioxTranscript.Utterance> {
        val frames = javaClass.getResourceAsStream("/soniox-detected-$source.json")!!.use {
            JSONArray(it.bufferedReader(Charsets.UTF_8).readText())
        }
        val buffer = SonioxTranscript(source, target)
        val completed = mutableListOf<SonioxTranscript.Utterance>()
        for (i in 0 until frames.length()) {
            val update = buffer.accept(frames.getJSONObject(i))
            if (source == "ja") {
                assertFalse(update.partial.original.contains("Hi there"))
                assertFalse(update.partial.original.contains("What are you doing"))
            }
            completed.addAll(update.completed)
        }
        return completed
    }

    @Test fun japaneseDeviceIgnoresEnglishSpeechInRealResponses() {
        val result = replay("ja", "en")
        assertEquals(2, result.size)
        assertEquals("こんにちは。", result[0].original)
        assertEquals("プログラミングしてますよ。", result[1].original)
        assertTrue(result.all { it.translation.isNotBlank() })
    }

    @Test fun englishDeviceIgnoresJapaneseSpeechInRealResponses() {
        val result = replay("en", "ja")
        assertFalse(result.isEmpty())
        val original = result.joinToString("") { it.original }
        assertEquals("Hi there! What are you doing?", original)
        assertTrue(result.all { it.translation.isNotBlank() })
    }
}
