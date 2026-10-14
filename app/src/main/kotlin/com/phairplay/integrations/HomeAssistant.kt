package com.phairplay.integrations

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Small dependency-free Home Assistant REST client and Android Keystore token storage. */
object HomeAssistant {
    private const val KEY_ALIAS = "phairplay_home_assistant_token"
    private const val PREFS = "home_assistant_secret"
    private const val TOKEN = "token"

    fun saveToken(context: Context, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(TOKEN, android.util.Base64.encodeToString(cipher.iv + encrypted, android.util.Base64.NO_WRAP))
            .apply()
    }

    fun token(context: Context): String? = runCatching {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN, null) ?: return null
        val bytes = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }.getOrNull()

    fun clearToken(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(TOKEN).apply()
    }

    private fun getKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    fun request(baseUrl: String, token: String, path: String, body: JSONObject? = null): String {
        val normalized = baseUrl.trim().trimEnd('/')
        require(normalized.startsWith("https://", true) || normalized.startsWith("http://", true))
        val conn = URL(normalized + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 7000
        conn.readTimeout = 7000
        conn.requestMethod = if (body == null) "GET" else "POST"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Accept", "application/json")
        if (body != null) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Home Assistant HTTP $code")
            return response
        } finally { conn.disconnect() }
    }

    fun state(baseUrl: String, token: String, entityId: String): JSONObject =
        JSONObject(request(baseUrl, token, "/api/states/${entityId.trim()}"))

    fun states(baseUrl: String, token: String): List<JSONObject> =
        org.json.JSONArray(request(baseUrl, token, "/api/states")).let { values ->
            (0 until values.length()).mapNotNull { values.optJSONObject(it) }
        }

    fun callService(baseUrl: String, token: String, domain: String, service: String, entityId: String, data: JSONObject = JSONObject()) {
        require(domain.matches(Regex("[a-z_]+")) && service.matches(Regex("[a-z_]+")))
        data.put("entity_id", entityId)
        request(baseUrl, token, "/api/services/$domain/$service", data)
    }

    fun calendars(baseUrl: String, token: String, entityId: String, start: String, end: String): JSONArray =
        JSONArray(request(baseUrl, token, "/api/calendars/${entityId.trim()}?start=${java.net.URLEncoder.encode(start, "UTF-8")}&end=${java.net.URLEncoder.encode(end, "UTF-8")}"))
}
