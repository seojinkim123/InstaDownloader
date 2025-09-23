package com.kimseojin.instadownloader.data.database

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
    val thumbnailPath: String? = null,
    val postId: String? = null, // 포스트별 그룹화를 위한 ID
    val ownerId: String? = null, // 계정 ID (불변)
    val ownerUsername: String? = null, // 계정 사용자명 (변경 가능)
    val ownerProfilePicUrl: String? = null // 프로필 사진 URL
)