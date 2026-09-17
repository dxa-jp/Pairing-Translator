package com.example.droidautoconnection.voice

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SonioxTranscriptTest {
    private fun token(text: String, final: Boolean = true, status: String = "original",
                      lang: String = "ja", source: String = "") = JSONObject()
        .put("text", text).put("is_final", final).put("translation_status", status)
        .put("language", lang).put("source_language", source)
    private fun translated(text: String, final: Boolean = true) =
        token(text, final, "translation", "en", "ja")
    private fun end() = JSONObject().put("text", "<end>").put("is_final", true)
    private fun response(vararg tokens: JSONObject) =
        JSONObject().put("tokens", JSONArray(tokens.toList()))

    @Test fun foreignSpeechDoesNotPolluteJapaneseReply() {
        val buffer = SonioxTranscript("ja", "en")
        val foreign = buffer.accept(response(token("Where are you from?", status = "none", lang = "en")))
        assertEquals("", foreign.partial.original)
        val result = buffer.accept(response(token("日本から来ました。"),
            translated("I came from Japan."), end()))
        assertEquals(listOf(SonioxTranscript.Utterance("日本から来ました。", "I came from Japan.")), result.completed)
    }

    @Test fun rejectsWrongLanguageAndWrongTranslationSource() {
        val result = SonioxTranscript("ja", "en").accept(response(
            token("Hello", lang = "en"),
            token("Bonjour", lang = "fr"),
            token("Hello", status = "translation", lang = "en", source = "fr"),
            token("不明", lang = ""),
            token("訳", status = "translation", lang = "ja", source = "ja"),
            end()))
        assertTrue(result.completed.isEmpty())
        assertEquals("", result.partial.original)
        assertEquals(5, result.rejectedTokens)
    }

    @Test fun provisionalCorrectionReplacesPreviousText() {
        val buffer = SonioxTranscript("ja", "en")
        buffer.accept(response(token("私は今日", false), translated("Today", false)))
        val update = buffer.accept(response(token("私は明日", false), translated("Tomorrow", false)))
        assertEquals("私は明日", update.partial.original)
        assertEquals("Tomorrow", update.partial.translation)
        assertTrue(update.completed.isEmpty())
        assertEquals("", buffer.accept(response()).partial.original)
    }

    @Test fun finalPrefixSurvivesPartialReplacementAndTranslationDelay() {
        val buffer = SonioxTranscript("ja", "en")
        buffer.accept(response(token("私は"), token("今日", false)))
        val next = buffer.accept(response(token("明日", false)))
        assertEquals("私は明日", next.partial.original)
        buffer.accept(response(token("明日行きます。")))
        assertTrue(buffer.accept(response(translated("I will"))).completed.isEmpty())
        val result = buffer.accept(response(translated(" go tomorrow."), end()))
        assertEquals(listOf(SonioxTranscript.Utterance("私は明日行きます。", "I will go tomorrow.")), result.completed)
        assertTrue(buffer.accept(response(end())).completed.isEmpty())
    }

    @Test fun separatesMultipleEndpointsAndPreservesRepeatedWords() {
        val result = SonioxTranscript("ja", "en").accept(response(
            token("はい。"), translated("Yes."), end(),
            token("はい。"), translated("Yes."), end()))
        assertEquals(2, result.completed.size)
        assertEquals(result.completed[0], result.completed[1])
        assertEquals("", result.partial.original)
    }

    @Test fun incompleteUtteranceDoesNotLeakIntoNextOne() {
        val buffer = SonioxTranscript("ja", "en")
        assertTrue(buffer.accept(response(token("前の発話"), end())).completed.isEmpty())
        val next = buffer.accept(response(token("次です。"), translated("Next."), end()))
        assertEquals("次です。", next.completed.single().original)
    }

    @Test fun finishedFlushesOnlyConfirmedText() {
        val buffer = SonioxTranscript("ja", "en")
        buffer.accept(response(token("こんにちは。"), translated("Hello."), translated(" provisional", false)))
        val result = buffer.accept(response().put("finished", true))
        assertEquals("Hello.", result.completed.single().translation)
        assertTrue(buffer.accept(response().put("finished", true)).completed.isEmpty())
    }

    @Test fun englishDeviceAcceptsOnlyEnglishOriginalAndJapaneseTranslation() {
        val result = SonioxTranscript("en", "ja").accept(response(
            token("仕事しています。", status = "none"),
            token("Where are you from?", lang = "en"),
            token("どこから来たの？", status = "translation", lang = "ja", source = "en"),
            end()))
        assertEquals(listOf(SonioxTranscript.Utterance("Where are you from?", "どこから来たの？")), result.completed)
    }

    @Test fun retainsSourceWhenEndpointPrecedesTranslation() {
        val buffer = SonioxTranscript("ja", "en")
        assertTrue(buffer.accept(response(token("こんにちは。"), end())).completed.isEmpty())
        val result = buffer.accept(response(translated("Hello."), end()))
        assertEquals("こんにちは。", result.completed.single().original)
    }

    @Test fun manualFinalizationDoesNotSplitUtterance() {
        val buffer = SonioxTranscript("ja", "en")
        buffer.accept(response(token("こんにちは。"), translated("Hello.")))
        assertTrue(buffer.accept(response(JSONObject().put("text", "<fin>").put("is_final", true))).completed.isEmpty())
        assertEquals(1, buffer.accept(response(end())).completed.size)
    }

    @Test fun rejectsEnglishEvenWhenSonioxLabelsItJapanese() {
        val buffer = SonioxTranscript("ja", "en")
        val partial = buffer.accept(response(token("Hi there。", false), translated("Hi there.", false)))
        assertEquals("", partial.partial.original)
        assertEquals("", partial.partial.translation)
        val final = buffer.accept(response(token("Hi there。"), translated("Hi there."), end()))
        assertTrue(final.completed.isEmpty())
        assertEquals(1, final.rejectedUtterances)
        val question = buffer.accept(response(token("What are you doing?"), translated("What are you doing?"), end()))
        assertTrue(question.completed.isEmpty())
        val japanese = buffer.accept(response(token("プログラミングしてますよ。"), translated("I'm programming."), end()))
        assertEquals("プログラミングしてますよ。", japanese.completed.single().original)
    }

    @Test fun keepsProductNamesInsideJapaneseAndReleasesHeldPartial() {
        val buffer = SonioxTranscript("ja", "en")
        assertEquals("", buffer.accept(response(token("Android Studio", false))).partial.original)
        assertEquals("Android Studioを使っています。", buffer.accept(response(
            token("Android Studioを使っています。", false))).partial.original)
        val result = buffer.accept(response(token("Android Studioを使っています。"),
            translated("I use Android Studio."), end()))
        assertEquals("Android Studioを使っています。", result.completed.single().original)
    }

    @Test fun rejectedTextCannotLeakThroughEarlyEndpointOrFinished() {
        val buffer = SonioxTranscript("ja", "en")
        buffer.accept(response(token("Hi there。"), end()))
        assertTrue(buffer.accept(response(translated("Hi there."), end())).completed.isEmpty())
        buffer.accept(response(token("What are you doing?"), translated("What are you doing?")))
        assertTrue(buffer.accept(response().put("finished", true)).completed.isEmpty())
    }
}
