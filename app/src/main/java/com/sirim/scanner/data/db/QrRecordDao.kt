package com.sirim.scanner.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QrRecordDao {
    @Query("SELECT * FROM qr_records ORDER BY captured_at DESC")
    fun getAllRecords(): Flow<List<QrRecord>>

    @Query("SELECT * FROM qr_records ORDER BY captured_at DESC")
    suspend fun getAllRecordsOnce(): List<QrRecord>

    @Query("SELECT * FROM qr_records WHERE id = :id")
    suspend fun getRecordById(id: Long): QrRecord?

    @Query("SELECT * FROM qr_records WHERE payload = :payload LIMIT 1")
    suspend fun findByPayload(payload: String): QrRecord?

    @Query(
        "SELECT * FROM qr_records WHERE payload LIKE :query " +
            "OR label LIKE :query " +
            "OR field_source LIKE :query " +
            "OR field_note LIKE :query ORDER BY captured_at DESC"
    )
    fun searchRecords(query: String): Flow<List<QrRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: QrRecord): Long

    @Update
    suspend fun update(record: QrRecord)

    @Delete
    suspend fun delete(record: QrRecord)

    @Query("DELETE FROM qr_records")
    suspend fun clearAll()
}
