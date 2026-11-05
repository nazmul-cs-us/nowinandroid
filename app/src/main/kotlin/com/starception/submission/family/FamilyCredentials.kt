package com.starception.submission.family

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Per-install credentials protected by Android Keystore; excluded from backups. */
internal class FamilyCredentials(context: Context) {
    private val prefs = context.getSharedPreferences("family_call_private", Context.MODE_PRIVATE)
    val deviceId: String = prefs.getString("device", null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString("device", it).commit()
    }
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("family-call-login", null) as? SecretKey) ?: KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("family-call-login", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun save(jid: String, password: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.doFinal(JSONObject().put("jid", jid).put("password", password).toString().toByteArray())
        prefs.edit().putString("login", Base64.encodeToString(cipher.iv + data, Base64.NO_WRAP)).commit()
    }
    fun load(): Pair<String, String>? = runCatching {
        val saved = prefs.getString("login", null) ?: return null
        val data = Base64.decode(saved, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        }
        val value = JSONObject(String(cipher.doFinal(data.copyOfRange(12, data.size))))
        value.getString("jid") to value.getString("password")
    }.getOrNull()
    fun clear() { prefs.edit().remove("login").commit() }
}
