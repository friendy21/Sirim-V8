package com.sirim.scanner.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private const val DATA_STORE_NAME = "user_preferences"

private val Context.dataStore by preferencesDataStore(name = DATA_STORE_NAME)

class PreferencesManager(private val context: Context) : SkuSessionTracker {

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        prefs.toUserPreferences()
    }

    override val currentSkuIdFlow: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[Keys.CURRENT_SKU_ID]
    }

    suspend fun setStartupPage(page: StartupPage) {
        context.dataStore.edit { prefs ->
            if (page == StartupPage.AskEveryTime) {
                prefs.remove(Keys.STARTUP_PAGE)
            } else {
                prefs[Keys.STARTUP_PAGE] = page.storageKey
            }
        }
    }

    suspend fun setAuthentication() {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_AUTHENTICATED] = true
            prefs[Keys.AUTH_TIMESTAMP] = currentSessionMarker()
            prefs[Keys.AUTH_EXPIRY_DURATION] = DEFAULT_AUTH_EXPIRY_MILLIS
        }
    }

    suspend fun clearAuthentication() {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_AUTHENTICATED] = false
            prefs[Keys.AUTH_TIMESTAMP] = 0L
            prefs[Keys.AUTH_EXPIRY_DURATION] = DEFAULT_AUTH_EXPIRY_MILLIS
        }
    }

    suspend fun refreshAuthentication() {
        context.dataStore.edit { prefs ->
            if (prefs[Keys.IS_AUTHENTICATED] == true) {
                prefs[Keys.AUTH_TIMESTAMP] = currentSessionMarker()
            }
        }
    }

    override suspend fun setCurrentSku(recordId: Long?) {
        context.dataStore.edit { prefs ->
            if (recordId == null) {
                prefs.remove(Keys.CURRENT_SKU_ID)
            } else {
                prefs[Keys.CURRENT_SKU_ID] = recordId
            }
        }
    }

    override suspend fun getCurrentSkuId(): Long? {
        return context.dataStore.data.first()[Keys.CURRENT_SKU_ID]
    }

    private fun Preferences.toUserPreferences(): UserPreferences {
        val startupPage = this[Keys.STARTUP_PAGE]?.let(StartupPage::fromStorageKey)
            ?: StartupPage.AskEveryTime
        val authenticated = this[Keys.IS_AUTHENTICATED] ?: false
        val timestamp = this[Keys.AUTH_TIMESTAMP] ?: 0L
        val expiryDuration = this[Keys.AUTH_EXPIRY_DURATION]?.takeIf { it <= 0L }
            ?: DEFAULT_AUTH_EXPIRY_MILLIS
        return UserPreferences(
            startupPage = startupPage,
            isAuthenticated = authenticated,
            authTimestamp = timestamp,
            authExpiryDurationMillis = expiryDuration
        )
    }

    private object Keys {
        val STARTUP_PAGE = stringPreferencesKey("startup_page")
        val IS_AUTHENTICATED = booleanPreferencesKey("is_authenticated")
        val AUTH_TIMESTAMP = longPreferencesKey("auth_timestamp")
        val AUTH_EXPIRY_DURATION = longPreferencesKey("auth_expiry_duration")
        val CURRENT_SKU_ID = longPreferencesKey("current_sku_id")
    }

    companion object {
        const val DEFAULT_AUTH_EXPIRY_MILLIS: Long = 0L
    }

    private fun currentSessionMarker(): Long {
        return android.os.Process.myPid().toLong()
    }
}
