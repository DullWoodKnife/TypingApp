package com.typing.app.data

import androidx.lifecycle.LiveData

class DataRepository(private val contentDao: ContentDao, private val recordDao: RecordDao) {
    val allContents: LiveData<List<Content>> = contentDao.getAll()
    val allRecords: LiveData<List<Record>> = recordDao.getAll()

    suspend fun getContent(id: String): Content? = contentDao.getById(id)
    suspend fun getAllContentsSync(): List<Content> = contentDao.getAllSync()
    suspend fun insertContent(content: Content) = contentDao.insert(content)
    suspend fun updateContent(content: Content) = contentDao.update(content)
    suspend fun deleteContent(content: Content) = contentDao.delete(content)
    suspend fun deleteContentById(id: String) = contentDao.deleteById(id)

    suspend fun getAllRecordsSync(): List<Record> = recordDao.getAllSync()
    suspend fun insertRecord(record: Record) = recordDao.insert(record)
    suspend fun clearAllRecords() = recordDao.deleteAll()
    suspend fun getRecordCount(): Int = recordDao.getCount()
    suspend fun getBestSpeed(): Int = recordDao.getBestSpeed() ?: 0
    suspend fun getBestAccuracy(): Int = recordDao.getBestAccuracy() ?: 0
}
