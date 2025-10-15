package com.sirim.scanner.data.preferences

import kotlinx.coroutines.flow.Flow

interface SkuSessionTracker {
    val currentSkuIdFlow: Flow<Long?>

    suspend fun setCurrentSku(recordId: Long?)

    suspend fun getCurrentSkuId(): Long?
}
