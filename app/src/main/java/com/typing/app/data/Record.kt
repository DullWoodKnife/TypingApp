package com.typing.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "records")
data class Record(
    @PrimaryKey
    val id: String,
    val contentId: String,
    val contentTitle: String,
    val mode: String,
    val speed: Int,
    val accuracy: Int,
    val correctChars: Int,
    val wrongChars: Int,
    val totalChars: Int,
    val duration: Int,
    val date: Long
)
