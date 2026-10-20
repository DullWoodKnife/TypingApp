package com.typing.app.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface RecordDao {
    @Query("SELECT * FROM records ORDER BY date DESC")
    fun getAll(): LiveData<List<Record>>

    @Query("SELECT * FROM records ORDER BY date DESC")
    suspend fun getAllSync(): List<Record>

    @Insert
    suspend fun insert(record: Record)

    @Query("DELETE FROM records")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM records")
    suspend fun getCount(): Int

    @Query("SELECT MAX(speed) FROM records")
    suspend fun getBestSpeed(): Int?

    @Query("SELECT MAX(accuracy) FROM records")
    suspend fun getBestAccuracy(): Int?
}
