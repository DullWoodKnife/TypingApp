package com.typing.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contents")
data class Content(
    @PrimaryKey
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long
)
