package com.shisan.campuspro.core.datastore

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.shisan.campuspro.core.model.Credentials
import com.shisan.campuspro.core.model.LoginMode

class EncryptedCredentialsStore(private val context: Context) {
    private val prefs by lazy {
        val ctx = context.applicationContext
        EncryptedSharedPreferences.create(
            ctx,
            "jwxt_credentials",
            MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun loadCredentials(): Credentials? {
        val username = prefs.getString(KEY_USERNAME, null)
        val password = prefs.getString(KEY_PASSWORD, null)
        val loginMode = prefs.getString(KEY_LOGIN_MODE, null)
            ?.let { runCatching { LoginMode.valueOf(it) }.getOrNull() }
            ?: LoginMode.JWXT_DIRECT
        return if (username.isNullOrBlank() || password.isNullOrBlank()) null else Credentials(username, password, loginMode)
    }

    fun saveCredentials(credentials: Credentials) {
        prefs.edit()
            .putString(KEY_USERNAME, credentials.username)
            .putString(KEY_PASSWORD, credentials.password)
            .putString(KEY_LOGIN_MODE, credentials.loginMode.name)
            .apply()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_USERNAME)
            .remove(KEY_PASSWORD)
            .remove(KEY_LOGIN_MODE)
            .apply()
    }

    private companion object {
        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_LOGIN_MODE = "login_mode"
    }
}
