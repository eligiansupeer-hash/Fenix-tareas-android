package app.fenix.tareas.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts provider configuration, including API keys, with an Android Keystore AES-GCM key. */
class SecureProviderStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("fenix_ai_secure", Context.MODE_PRIVATE)
    private val alias = "fenix_provider_config_v1"

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    fun load(): List<ProviderConfig> {
        val encoded = prefs.getString("providers", null) ?: return emptyList()
        return try {
            val all = Base64.decode(encoded, Base64.NO_WRAP)
            if (all.isEmpty()) return emptyList()
            val ivSize = all[0].toInt() and 0xff
            if (ivSize <= 0 || all.size <= 1 + ivSize) return emptyList()
            val iv = all.copyOfRange(1, 1 + ivSize)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val json = JSONArray(String(cipher.doFinal(all.copyOfRange(1 + ivSize, all.size)), Charsets.UTF_8))
            (0 until json.length()).map { ProviderConfig.fromJson(json.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
    }

    /** Adds new catalog entries without overwriting user URLs, models, enabled flags or keys. */
    fun loadWithCatalog(): List<ProviderConfig> {
        val catalog = try {
            val raw = appContext.assets.open("provider-catalog.json").bufferedReader().use { it.readText() }
            val array = JSONObject(raw).getJSONArray("providers")
            (0 until array.length()).map { ProviderConfig.fromJson(array.getJSONObject(it)) }
        } catch (_: Exception) { emptyList() }
        val existing = load()
        if (catalog.isEmpty()) return existing
        val existingIds = existing.map { it.id }.toSet()
        val merged = if (existing.isEmpty()) catalog else existing + catalog.filter { it.id !in existingIds }
        if (merged != existing) save(merged)
        return merged
    }

    fun save(providers: List<ProviderConfig>) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(JSONArray().apply { providers.forEach { put(it.toJson()) } }.toString().toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val packed = byteArrayOf(iv.size.toByte()) + iv + encrypted
        prefs.edit().putString("providers", Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }
}
