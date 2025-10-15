package com.sirim.scanner.data.preferences

data class UserPreferences(
    val startupPage: StartupPage = StartupPage.AskEveryTime,
    val isAuthenticated: Boolean = false,
    val authTimestamp: Long = 0L,
    val authExpiryDurationMillis: Long = PreferencesManager.DEFAULT_AUTH_EXPIRY_MILLIS
) {
    fun isSessionValid(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (!isAuthenticated) return false
        if (authExpiryDurationMillis <= 0L) {
            return authTimestamp == android.os.Process.myPid().toLong()
        }
        val expiryAt = authTimestamp + authExpiryDurationMillis
        return nowMillis < expiryAt
    }

    fun remainingSessionTimeMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        if (authExpiryDurationMillis <= 0L) {
            return 0L
        }
        val expiryAt = authTimestamp + authExpiryDurationMillis
        return (expiryAt - nowMillis).coerceAtLeast(0L)
    }
}

enum class StartupPage(val storageKey: String) {
    AskEveryTime("ask"),
    SirimScannerV2("qr_scanner"),
    SkuScanner("sku_scanner"),
    Storage("storage");

    companion object {
        fun fromStorageKey(key: String): StartupPage = entries.firstOrNull { it.storageKey == key }
            ?: AskEveryTime
    }
}
