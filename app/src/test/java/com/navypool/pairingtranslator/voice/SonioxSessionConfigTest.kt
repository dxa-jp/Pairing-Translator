package com.navypool.pairingtranslator.voice

import org.junit.Assert.*
import org.junit.Test

class SonioxSessionConfigTest {
    @Test fun identifiesBothLanguagesWithoutForcingSourceLanguage() {
        for ((source, target) in listOf("ja" to "en", "en" to "ja")) {
            val config = SonioxSessionConfig.create("test-key", "server-selected-model", source, target)
            assertEquals("server-selected-model", config.getString("model"))
            assertEquals(listOf(source, target), config.getJSONArray("language_hints").let {
                (0 until it.length()).map(it::getString)
            })
            assertFalse(config.getBoolean("language_hints_strict"))
            assertTrue(config.getBoolean("enable_language_identification"))
            assertTrue(config.getBoolean("enable_endpoint_detection"))
            assertFalse(config.has("language"))
            assertEquals("one_way", config.getJSONObject("translation").getString("type"))
            assertEquals(target, config.getJSONObject("translation").getString("target_language"))
        }
    }
}
