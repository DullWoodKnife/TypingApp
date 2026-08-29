package com.typing.app.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ContentDao {
    @Query("SELECT * FROM contents ORDER BY createdAt DESC")
    fun getAll(): LiveData<List<Content>>

    @Query("SELECT * FROM contents ORDER BY createdAt DESC")
    suspend fun getAllSync(): List<Content>

    @Query("SELECT * FROM contents WHERE id = :id")
    suspend fun getById(id: String): Content?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(content: Content)

    @Update
    suspend fun update(content: Content)

    @Delete
    suspend fun delete(content: Content)

    @Query("DELETE FROM contents WHERE id = :id")
    suspend fun deleteById(id: String)
}
