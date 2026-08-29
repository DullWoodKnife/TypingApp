package com.typing.app.data

data class Record(
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
