package com.example.instadownloader.data.repository

import android.content.Context
import android.net.Uri
import com.example.instadownloader.data.database.AppDatabase
import com.example.instadownloader.data.database.DownloadedMediaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class GalleryMediaItem(
    val id: String,
    val fileName: String,
    val uri: Uri,
    val originalUrl: String,
    val downloadDate: Long,
    val mediaType: String,
    val fileSize: Long,
    val thumbnailUri: Uri
)

class GalleryRepository(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val mediaDao = database.downloadedMediaDao()
    
    fun getAllMediaFlow(): Flow<List<GalleryMediaItem>> {
        return mediaDao.getAllMediaFlow().map { entities ->
            entities.map { entity ->
                entity.toGalleryMediaItem()
            }
        }
    }
    
    suspend fun getAllMedia(): List<GalleryMediaItem> {
        return mediaDao.getAllMedia().map { entity ->
            entity.toGalleryMediaItem()
        }
    }
    
    suspend fun getMediaByType(type: String): List<GalleryMediaItem> {
        return mediaDao.getMediaByType(type).map { entity ->
            entity.toGalleryMediaItem()
        }
    }
    
    suspend fun deleteMedia(mediaItem: GalleryMediaItem) {
        mediaDao.deleteMediaById(mediaItem.id)
    }
    
    suspend fun getMediaCount(): Int {
        return mediaDao.getMediaCount()
    }
    
    suspend fun getMediaCountByType(type: String): Int {
        return mediaDao.getMediaCountByType(type)
    }
    
    private fun DownloadedMediaEntity.toGalleryMediaItem(): GalleryMediaItem {
        return GalleryMediaItem(
            id = this.id,
            fileName = this.fileName,
            uri = Uri.parse(this.filePath),
            originalUrl = this.originalUrl,
            downloadDate = this.downloadDate,
            mediaType = this.mediaType,
            fileSize = this.fileSize,
            thumbnailUri = Uri.parse(this.thumbnailPath ?: this.filePath)
        )
    }
}