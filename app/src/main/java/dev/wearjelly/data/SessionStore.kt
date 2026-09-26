package dev.wearjelly.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionStore(
    private val context: Context,
    private val json: Json
) {
    private val _session = MutableStateFlow<ServerSession?>(null)
    val session: StateFlow<ServerSession?> = _session.asStateFlow()

    private val _bitrate = MutableStateFlow(AudioBitrate.MEDIUM)
    val bitrate: StateFlow<AudioBitrate> = _bitrate.asStateFlow()

    val deviceId: String by lazy { getOrCreateDeviceId() }

    private val sessionFile: File
        get() = File(context.noBackupFilesDir, "jellyfin_session.enc")

    init {
        loadSession()
        loadBitrate()
    }

    fun setBitrate(newBitrate: AudioBitrate) {
        val sp = context.getSharedPreferences("wearjelly_settings", Context.MODE_PRIVATE)
        sp.edit().putString("audio_bitrate", newBitrate.name).apply()
        _bitrate.value = newBitrate
    }

    private fun loadBitrate() {
        val sp = context.getSharedPreferences("wearjelly_settings", Context.MODE_PRIVATE)
        val name = sp.getString("audio_bitrate", AudioBitrate.MEDIUM.name)
        val found = try {
            AudioBitrate.valueOf(name ?: AudioBitrate.MEDIUM.name)
        } catch (_: Exception) {
            AudioBitrate.MEDIUM
        }
        _bitrate.value = found
    }

    private fun getOrCreateDeviceId(): String {
        val sp = context.getSharedPreferences("wearjelly_device", Context.MODE_PRIVATE)
        var id = sp.getString("device_id", null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString().replace("-", "")
            sp.edit().putString("device_id", id).apply()
        }
        return id
    }

    @Synchronized
    fun save(newSession: ServerSession) {
        try {
            val jsonStr = json.encodeToString(newSession)
            val encrypted = encrypt(jsonStr)
            sessionFile.writeText(encrypted)
            _session.value = newSession
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果存储失败，至少内存中保存
            _session.value = newSession
        }
    }

    @Synchronized
    fun clear() {
        try {
            if (sessionFile.exists()) {
                sessionFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _session.value = null
    }

    @Synchronized
    private fun loadSession() {
        if (!sessionFile.exists()) {
            _session.value = null
            return
        }
        try {
            val encrypted = sessionFile.readText()
            val decrypted = decrypt(encrypted)
            val saved = json.decodeFromString<ServerSession>(decrypted)
            _session.value = saved
        } catch (e: Exception) {
            e.printStackTrace()
            // 解密或解析失败时清理异常文件
            sessionFile.delete()
            _session.value = null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "wearjelly_key"
        if (keyStore.containsAlias(alias)) {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            if (entry != null) {
                return entry.secretKey
            }
        }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun encrypt(plainText: String): String {
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        // 格式: Base64(iv):Base64(cipherBytes)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val dataBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        return "$ivBase64:$dataBase64"
    }

    private fun decrypt(cipherText: String): String {
        val parts = cipherText.split(":")
        if (parts.size != 2) throw IllegalArgumentException("Invalid encrypted format")
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)
        val secretKey = getOrCreateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val decryptedBytes = cipher.doFinal(encryptedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
