package com.sirim.scanner.data

import android.content.Context
import androidx.room.Room
import com.sirim.scanner.data.db.SirimDatabase
import com.sirim.scanner.data.export.ExportManager
import com.sirim.scanner.data.ocr.BarcodeAnalyzer
import com.sirim.scanner.data.ocr.QrCodeAnalyzer
import com.sirim.scanner.data.preferences.PreferencesManager
import com.sirim.scanner.data.repository.SirimRepository
import com.sirim.scanner.data.repository.SirimRepositoryImpl
import com.sirim.scanner.util.DefaultDispatcherProvider
import com.sirim.scanner.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

interface AppContainer {
    val repository: SirimRepository
    val exportManager: ExportManager
    val qrAnalyzer: QrCodeAnalyzer
    val barcodeAnalyzer: BarcodeAnalyzer
    val applicationScope: CoroutineScope
    val preferencesManager: PreferencesManager
    val dispatcherProvider: DispatcherProvider
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val applicationScopeImpl = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database: SirimDatabase = Room.databaseBuilder(
        context,
        SirimDatabase::class.java,
        "sirim_records.db"
    ).addMigrations(
        SirimDatabase.MIGRATION_1_2,
        SirimDatabase.MIGRATION_2_3,
        SirimDatabase.MIGRATION_3_4,
        SirimDatabase.MIGRATION_4_5,
        SirimDatabase.MIGRATION_5_6,
        SirimDatabase.MIGRATION_6_7,
        SirimDatabase.MIGRATION_7_8,
        SirimDatabase.MIGRATION_8_9,
        SirimDatabase.MIGRATION_9_10,
        SirimDatabase.MIGRATION_10_11,
        SirimDatabase.MIGRATION_11_12
    ).build()

    override val repository: SirimRepository by lazy {
        SirimRepositoryImpl(
            qrDao = database.qrRecordDao(),
            skuDao = database.skuRecordDao(),
            skuExportDao = database.skuExportDao(),
            productScanDao = database.productScanDao(),
            context = context.applicationContext
        )
    }

    override val exportManager: ExportManager by lazy {
        ExportManager(context.applicationContext)
    }

    override val qrAnalyzer: QrCodeAnalyzer by lazy { QrCodeAnalyzer() }

    override val barcodeAnalyzer: BarcodeAnalyzer by lazy { BarcodeAnalyzer() }

    override val applicationScope: CoroutineScope
        get() = applicationScopeImpl

    override val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(context.applicationContext)
    }

    override val dispatcherProvider: DispatcherProvider
        get() = DefaultDispatcherProvider
}
