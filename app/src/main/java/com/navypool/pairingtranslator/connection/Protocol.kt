package com.navypool.pairingtranslator.connection

import org.json.JSONObject

/**
 * Nearby ConnectionsのBYTESペイロード(上限32KB)でやり取りするJSONプロトコル。
 * - hello  : 接続時に自分の情報と言語を交換
 * - speech : 音声認識・翻訳結果(原文+訳文)を相手へ送る
 * - ping/pong : 接続生存確認(ハートビート)
 */
object Protocol {

    sealed interface Packet {
        data class Hello(
            val deviceId: String,
            val name: String,
            val model: String,
            val lang: String,
        ) : Packet

        data class Speech(
            val deviceId: String,
            val original: String,
            val translation: String,
            val lang: String,
            val ts: Long,
        ) : Packet

        data class Ping(val ts: Long) : Packet
        data class Pong(val ts: Long) : Packet
    }

    fun hello(deviceId: String, name: String, model: String, lang: String): ByteArray =
        JSONObject().apply {
            put("type", "hello")
            put("id", deviceId)
            put("name", name)
            put("model", model)
            put("lang", lang)
        }.toString().toByteArray(Charsets.UTF_8)

    fun speech(deviceId: String, original: String, translation: String, lang: String, ts: Long): ByteArray =
        JSONObject().apply {
            put("type", "speech")
            put("id", deviceId)
            put("orig", original)
            put("trans", translation)
            put("lang", lang)
            put("ts", ts)
        }.toString().toByteArray(Charsets.UTF_8)

    fun ping(ts: Long): ByteArray =
        JSONObject().apply {
            put("type", "ping")
            put("ts", ts)
        }.toString().toByteArray(Charsets.UTF_8)

    fun pong(ts: Long): ByteArray =
        JSONObject().apply {
            put("type", "pong")
            put("ts", ts)
        }.toString().toByteArray(Charsets.UTF_8)

    fun parse(bytes: ByteArray): Packet? = try {
        val obj = JSONObject(String(bytes, Charsets.UTF_8))
        when (obj.optString("type")) {
            "hello" -> Packet.Hello(
                deviceId = obj.optString("id"),
                name = obj.optString("name"),
                model = obj.optString("model"),
                lang = obj.optString("lang"),
            )
            "speech" -> Packet.Speech(
                deviceId = obj.optString("id"),
                original = obj.optString("orig"),
                translation = obj.optString("trans"),
                lang = obj.optString("lang"),
                ts = obj.optLong("ts"),
            )
            "ping" -> Packet.Ping(obj.optLong("ts"))
            "pong" -> Packet.Pong(obj.optLong("ts"))
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}
