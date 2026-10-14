package com.xike.app

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

internal data class StoredMediaResult(val value: Any?, val ownedReadUris: List<Uri>)

/** Durable URI metadata only. Media bytes and backup passwords are never stored here. */
internal class PendingMediaResultStore(context: Context) {
    private val preferences =
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "xike-pending-media-results",
            MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun save(key: String, value: Any?, ownedReadUris: List<Uri>) {
        val json = JSONObject().put("version", 1)
        when (value) {
            null -> json.put("kind", "null")
            is Uri -> json.put("kind", "uri").put("value", value.toString())
            is List<*> -> {
                require(value.all { it is Uri })
                json.put("kind", "uris").put("value", JSONArray(value.map { it.toString() }))
            }
            else -> error("Unsupported media result")
        }
        json.put("ownedReadUris", JSONArray(ownedReadUris.map(Uri::toString)))
        check(preferences.edit().putString(key, json.toString()).commit()) {
            "Could not preserve pending media selection"
        }
    }

    fun load(key: String): StoredMediaResult? {
        val raw = preferences.getString(key, null) ?: return null
        val json = JSONObject(raw)
        require(json.getInt("version") == 1)
        val value =
            when (json.getString("kind")) {
                "null" -> null
                "uri" -> Uri.parse(json.getString("value"))
                "uris" ->
                    json.getJSONArray("value").let { array ->
                        List(array.length()) { Uri.parse(array.getString(it)) }
                    }
                else -> error("Unsupported media result")
            }
        val grants = json.getJSONArray("ownedReadUris")
        return StoredMediaResult(value, List(grants.length()) { Uri.parse(grants.getString(it)) })
    }

    fun remove(key: String) {
        check(preferences.edit().remove(key).commit()) {
            "Could not acknowledge pending media selection"
        }
    }
}
