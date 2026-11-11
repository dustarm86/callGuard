package com.dustin.callguard.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AllowedNumberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(allowedNumber: AllowedNumber)

    @Query("SELECT * FROM allowed_numbers ORDER BY label COLLATE NOCASE, normalizedNumber")
    fun observeAll(): Flow<List<AllowedNumber>>

    @Query("SELECT EXISTS(SELECT 1 FROM allowed_numbers WHERE normalizedNumber = :normalizedNumber)")
    suspend fun isAllowedNumber(normalizedNumber: String): Boolean

    @Query("DELETE FROM allowed_numbers WHERE normalizedNumber = :normalizedNumber")
    suspend fun deleteByNumber(normalizedNumber: String)
}

@Dao
interface ScreenedCallDao {
    @Insert
    suspend fun insert(screenedCall: ScreenedCall)

    @Query("SELECT * FROM screened_calls ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ScreenedCall>>

    @Query(
        "SELECT COUNT(*) FROM screened_calls " +
            "WHERE normalizedNumber = :normalizedNumber " +
            "AND outcome = 'SILENCED' AND timestamp >= :sinceMillis"
    )
    suspend fun countSilencedSince(normalizedNumber: String, sinceMillis: Long): Int

    @Query("DELETE FROM screened_calls WHERE timestamp < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)

    @Query("DELETE FROM screened_calls")
    suspend fun clearAll()
}
