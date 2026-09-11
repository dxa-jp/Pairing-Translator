package com.example.droidautoconnection.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * MultiTranslatorサーバーのAPIクライアント。
 * - POST /api/device_auth.php : 端末登録(管理UIでの承認が必要)
 * - POST /api/temp_key.php    : Soniox一時APIキー発行
 *
 * 注意: このクライアントは既存サーバーを利用するだけで、サーバー側に変更を加えない。
 */
object BackendClient {

    const val BASE_URL = "https://multitranslator.dev.x-tools.biz/"

    /** 端末登録の結果 */
    sealed class RegisterResult {
        data object Approved : RegisterResult()
        data class Rejected(val errorCode: String?, val message: String?) : RegisterResult()
        data class Error(val message: String) : RegisterResult()
    }

    /** 一時キー取得の結果 */
    sealed class KeyResult {
        data class Ok(val key: String, val model: String) : KeyResult()
        data class Rejected(val errorCode: String?, val message: String?) : KeyResult()
        data class Error(val message: String) : KeyResult()
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 端末をサーバーに登録する。管理UIで承認済みならApproved */
    suspend fun registerDevice(deviceId: String): RegisterResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder().add("device_id", deviceId).build()
        val request = Request.Builder()
            .url(BASE_URL + "api/device_auth.php")
            .post(body)
            .build()
        val json = runCatching { executeAndParse(request) }.getOrElse {
            return@withContext RegisterResult.Error(it.message ?: "network error")
        }
        when {
            json.optBoolean("success") && json.optJSONObject("data")?.optBoolean("status") == true ->
                RegisterResult.Approved
            json.optBoolean("success") ->
                RegisterResult.Rejected(null, "デバイスは登録されましたが未承認です")
            else -> RegisterResult.Rejected(
                json.optNullableString("error_code"),
                json.optNullableString("message"),
            )
        }
    }

    /** Soniox一時APIキーを取得する(実効1時間・サーバー側固定) */
    suspend fun fetchTempKey(deviceId: String): KeyResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("device_id", deviceId)
            .add("duration", "60")
            .build()
        val request = Request.Builder()
            .url(BASE_URL + "api/temp_key.php")
            .post(body)
            .build()
        val json = runCatching { executeAndParse(request) }.getOrElse {
            return@withContext KeyResult.Error(it.message ?: "network error")
        }
        val key = json.optNullableString("temp_api_key")
        val model = (json.opt("model") as? String)?.trim()
        when {
            json.optBoolean("success") && !key.isNullOrEmpty() -> {
                if (model.isNullOrEmpty()) {
                    KeyResult.Error("一時キーAPIの応答に有効なモデル名がありません")
                } else {
                    KeyResult.Ok(key, model)
                }
            }
            else -> KeyResult.Rejected(
                errorCode = json.optNullableString("error_code"),
                message = json.optNullableString("message") ?: "キーを取得できませんでした",
            )
        }
    }

    /** HTTPエラー時もレスポンスボディからエラーコードを読み取る */
    private fun executeAndParse(request: Request): JSONObject =
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (text.isNotEmpty()) JSONObject(text) else JSONObject()
        }

    private fun JSONObject.optNullableString(name: String): String? =
        if (has(name) && !isNull(name)) getString(name) else null
}
