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

    @Query(
        "SELECT EXISTS(SELECT 1 FROM allowed_numbers WHERE normalizedNumber = :normalizedNumber " +
            "AND (expiresAt IS NULL OR expiresAt > :nowMillis))"
    )
    suspend fun isAllowedNumber(normalizedNumber: String, nowMillis: Long): Boolean

    @Query("DELETE FROM allowed_numbers WHERE expiresAt IS NOT NULL AND expiresAt <= :nowMillis")
    suspend fun deleteExpired(nowMillis: Long)

    @Query("DELETE FROM allowed_numbers WHERE normalizedNumber = :normalizedNumber")
    suspend fun deleteByNumber(normalizedNumber: String)
}

@Dao
interface AllowedPrefixDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(allowedPrefix: AllowedPrefix)

    @Query("SELECT * FROM allowed_prefixes ORDER BY prefix")
    fun observeAll(): Flow<List<AllowedPrefix>>

    // prefixes are digits only, so LIKE has no wildcard characters to worry about
    @Query("SELECT EXISTS(SELECT 1 FROM allowed_prefixes WHERE :normalizedNumber LIKE prefix || '%')")
    suspend fun matchesAnyPrefix(normalizedNumber: String): Boolean

    @Query("DELETE FROM allowed_prefixes WHERE prefix = :prefix")
    suspend fun deleteByPrefix(prefix: String)
}

@Dao
interface ScreenedCallDao {
    @Insert
    suspend fun insert(screenedCall: ScreenedCall)

    @Query("SELECT * FROM screened_calls ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ScreenedCall>>

    @Query("SELECT * FROM screened_calls ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<ScreenedCall>>

    @Query("SELECT * FROM screened_calls WHERE timestamp >= :sinceMillis ORDER BY timestamp DESC")
    suspend fun getSince(sinceMillis: Long): List<ScreenedCall>

    @Query(
        "SELECT COUNT(*) FROM screened_calls " +
            "WHERE normalizedNumber = :normalizedNumber " +
            "AND outcome IN ('SILENCED', 'REJECTED') AND timestamp >= :sinceMillis"
    )
    suspend fun countSilencedSince(normalizedNumber: String, sinceMillis: Long): Int

    @Query("DELETE FROM screened_calls WHERE timestamp < :beforeMillis")
    suspend fun deleteOlderThan(beforeMillis: Long)

    @Query("DELETE FROM screened_calls")
    suspend fun clearAll()
}

@Dao
interface NumberInfoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(numberInfo: NumberInfo)

    @Query("SELECT * FROM number_info")
    fun observeAll(): Flow<List<NumberInfo>>

    @Query("DELETE FROM number_info WHERE normalizedNumber = :normalizedNumber")
    suspend fun deleteByNumber(normalizedNumber: String)
}
