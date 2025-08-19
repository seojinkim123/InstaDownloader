package com.example.instadownloader.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_media")
data class DownloadedMediaEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val filePath: String, // MediaStore Uri
    val originalUrl: String,
    val downloadDate: Long,
    val mediaType: String, // "image" or "video"
    val fileSize: Long,
    val thumbnailPath: String? = null
)