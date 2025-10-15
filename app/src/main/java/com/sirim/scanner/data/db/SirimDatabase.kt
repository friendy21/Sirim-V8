package com.sirim.scanner.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [QrRecord::class, SkuRecord::class, SkuExportRecord::class, ProductScan::class],
    version = 12,
    exportSchema = true
)
abstract class SirimDatabase : RoomDatabase() {
    abstract fun qrRecordDao(): QrRecordDao
    abstract fun skuRecordDao(): SkuRecordDao
    abstract fun skuExportDao(): SkuExportDao
    abstract fun productScanDao(): ProductScanDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("BEGIN TRANSACTION")
                try {
                    database.execSQL(
                        "CREATE TABLE IF NOT EXISTS sku_records (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "barcode TEXT NOT NULL, " +
                            "batch_no TEXT, " +
                            "brand_trademark TEXT, " +
                            "model TEXT, " +
                            "type TEXT, " +
                            "rating TEXT, " +
                            "size TEXT, " +
                            "image_path TEXT, " +
                            "created_at INTEGER NOT NULL, " +
                            "is_verified INTEGER NOT NULL, " +
                            "needs_sync INTEGER NOT NULL, " +
                            "server_id TEXT, " +
                            "last_synced INTEGER)"
                    )
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sku_records_barcode ON sku_records(barcode)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_sku_records_created_at ON sku_records(created_at)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_sku_records_brand_trademark ON sku_records(brand_trademark)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_sku_records_is_verified ON sku_records(is_verified)")
                    database.execSQL("COMMIT")
                } catch (error: Exception) {
                    database.execSQL("ROLLBACK")
                    throw error
                }
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_sirim_records_brand_verified " +
                        "ON sirim_records(brand_trademark, is_verified)"
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sirim_records ADD COLUMN custom_fields TEXT")
                database.execSQL("ALTER TABLE sirim_records ADD COLUMN capture_confidence REAL")
            }
        }

        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sku_records ADD COLUMN gallery_paths TEXT")
            }
        }

        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS sku_exports (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "uri TEXT NOT NULL, " +
                        "file_name TEXT NOT NULL, " +
                        "record_count INTEGER NOT NULL, " +
                        "updated_at INTEGER NOT NULL" +
                        ")"
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sku_exports_updated_at ON sku_exports(updated_at)")
            }
        }

        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sirim_records ADD COLUMN is_duplicate INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8: Migration = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sku_records ADD COLUMN linked_serial TEXT")
            }
        }

        val MIGRATION_8_9: Migration = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sirim_records ADD COLUMN qr_payload TEXT")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sirim_records_qr_payload ON sirim_records(qr_payload)")
            }
        }

        val MIGRATION_9_10: Migration = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS index_sirim_records_brand_verified")
                database.execSQL("DROP INDEX IF EXISTS index_sirim_records_qr_payload")
                database.execSQL("DROP TABLE IF EXISTS sirim_records")
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS qr_records (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "payload TEXT NOT NULL, " +
                        "label TEXT, " +
                        "captured_at INTEGER NOT NULL" +
                        ")"
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_qr_records_payload ON qr_records(payload)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_qr_records_captured_at ON qr_records(captured_at)")
            }
        }

        val MIGRATION_10_11: Migration = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE qr_records ADD COLUMN field_source TEXT")
                database.execSQL("ALTER TABLE qr_records ADD COLUMN field_note TEXT")
            }
        }

        val MIGRATION_11_12: Migration = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS product_scans (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "sku_report TEXT NOT NULL, " +
                        "sku_name TEXT NOT NULL, " +
                        "sirim_data TEXT, " +
                        "timestamp INTEGER NOT NULL, " +
                        "product_image_path TEXT, " +
                        "total_captured INTEGER NOT NULL" +
                        ")"
                )
                database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_product_scans_sku_report ON product_scans(sku_report)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_product_scans_timestamp ON product_scans(timestamp)"
                )
            }
        }
    }
}
