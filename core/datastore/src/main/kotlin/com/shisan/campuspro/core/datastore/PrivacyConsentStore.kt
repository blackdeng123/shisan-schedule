package com.shisan.campuspro.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shisan.campuspro.core.model.CurrentPrivacyPolicyVersion
import com.shisan.campuspro.core.model.PrivacyConsentState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.privacyDataStore by preferencesDataStore(name = "privacy_consent")

class PrivacyConsentStore(private val context: Context) {
    val state: Flow<PrivacyConsentState> = context.privacyDataStore.data.map { preferences ->
        PrivacyConsentState(
            acceptedPolicyVersion = preferences[ACCEPTED_POLICY_VERSION],
            acceptedAtMillis = preferences[ACCEPTED_AT_MILLIS],
        )
    }

    suspend fun accept(
        policyVersion: String = CurrentPrivacyPolicyVersion,
        acceptedAtMillis: Long = System.currentTimeMillis(),
    ) {
        context.privacyDataStore.edit { preferences ->
            preferences[ACCEPTED_POLICY_VERSION] = policyVersion
            preferences[ACCEPTED_AT_MILLIS] = acceptedAtMillis
        }
    }

    suspend fun revoke() {
        context.privacyDataStore.edit { preferences ->
            preferences.remove(ACCEPTED_POLICY_VERSION)
            preferences.remove(ACCEPTED_AT_MILLIS)
        }
    }

    suspend fun migrateLegacyCredentials(clearCredentialsAndSession: suspend () -> Unit) {
        val alreadyMigrated = context.privacyDataStore.data.first()[LEGACY_CREDENTIALS_MIGRATED] ?: false
        if (alreadyMigrated) return
        clearCredentialsAndSession()
        context.privacyDataStore.edit { preferences ->
            preferences[LEGACY_CREDENTIALS_MIGRATED] = true
        }
    }

    private companion object {
        val ACCEPTED_POLICY_VERSION = stringPreferencesKey("accepted_policy_version")
        val ACCEPTED_AT_MILLIS = longPreferencesKey("accepted_at_millis")
        val LEGACY_CREDENTIALS_MIGRATED = booleanPreferencesKey("legacy_credentials_migrated_v1")
    }
}
