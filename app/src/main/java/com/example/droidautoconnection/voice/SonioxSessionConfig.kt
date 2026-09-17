package com.example.droidautoconnection.voice

import org.json.JSONArray
import org.json.JSONObject

/** 両者の声がマイクへ入るため、片方の言語に強制せず識別してから選別する。 */
internal object SonioxSessionConfig {
    fun create(apiKey: String, model: String, sourceLang: String, targetLang: String) =
        JSONObject().apply {
            put("api_key", apiKey)
            put("model", model)
            put("language_hints", JSONArray(listOf(sourceLang, targetLang).distinct()))
            put("language_hints_strict", false)
            put("enable_language_identification", true)
            put("enable_endpoint_detection", true)
            put("audio_format", "pcm_s16le")
            put("sample_rate", 16000)
            put("num_channels", 1)
            put("translation", JSONObject().apply {
                put("type", "one_way")
                put("target_language", targetLang)
            })
        }
}
