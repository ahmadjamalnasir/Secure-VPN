package com.example.service

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.wireguard.crypto.KeyPair
import com.wireguard.crypto.Key
import android.util.Log

object WireGuardManager {
    private const val PREFS_NAME = "secure_wg_prefs"
    private const val KEY_PRIVATE_KEY = "wg_client_private_key"

    fun getOrGenerateClientKeyPair(context: Context): KeyPair {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val sharedPreferences = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val storedPrivateKeyBase64 = sharedPreferences.getString(KEY_PRIVATE_KEY, null)
            if (!storedPrivateKeyBase64.isNullOrEmpty()) {
                try {
                    val privateKey = Key.fromBase64(storedPrivateKeyBase64)
                    return KeyPair(privateKey)
                } catch (e: Exception) {
                    // Try parsing error recovery
                }
            }

            // Generate new KeyPair using KeyPair class from the SDK
            val newKeyPair = KeyPair()
            val privateKeyBase64 = newKeyPair.privateKey.toBase64()

            sharedPreferences.edit()
                .putString(KEY_PRIVATE_KEY, privateKeyBase64)
                .apply()

            return newKeyPair
        } catch (e: Exception) {
            // General keystore/crypto fallback
            // To ensure 100% reliability, if master key generation fails on older/unsupported devices
            val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val storedPrivateKeyBase64 = sharedPreferences.getString(KEY_PRIVATE_KEY, null)
            if (!storedPrivateKeyBase64.isNullOrEmpty()) {
                try {
                    val privateKey = Key.fromBase64(storedPrivateKeyBase64)
                    return KeyPair(privateKey)
                } catch (ex: Exception) {
                    // ignore
                }
            }
            val newKeyPair = KeyPair()
            sharedPreferences.edit()
                .putString(KEY_PRIVATE_KEY, newKeyPair.privateKey.toBase64())
                .apply()
            return newKeyPair
        }
    }

    fun getClientPrivateKey(context: Context): Key {
        return getOrGenerateClientKeyPair(context).privateKey
    }
}
