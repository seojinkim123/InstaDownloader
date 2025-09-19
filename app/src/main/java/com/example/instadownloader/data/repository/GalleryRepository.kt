package com.example.instadownloader.data.repository

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.os.Build
import java.io.File
import com.example.instadownloader.data.database.AppDatabase
import com.example.instadownloader.data.database.DownloadedMediaEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class GalleryMediaItem(
    val id: String,
    val fileName: String,
    val uri: Uri,
    val originalUrl: String,
    val downloadDate: Long,
    val mediaType: String,
    val fileSize: Long,
    val thumbnailUri: Uri,
    val postId: String? = null,
    val ownerId: String? = null,
    val ownerUsername: String? = null,
    val ownerProfilePicUrl: String? = null
)

class GalleryRepository(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val mediaDao = database.downloadedMediaDao()
    private val repositoryScope = CoroutineScope(Dispatchers.IO)
    
    private val mediaContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            // MediaStore가 변경되면 동기화 수행
            repositoryScope.launch {
                syncWithMediaStore()
            }
        }
    }
    
    init {
        // MediaStore 변경 감지 시작
        startObservingMediaStore()
    }
    
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
        try {
            // 1. 실제 파일 삭제 (MediaStore를 통해)
            deleteFileFromMediaStore(mediaItem.uri)
            
            // 2. 데이터베이스에서 삭제
            mediaDao.deleteMediaById(mediaItem.id)
        } catch (e: Exception) {
            // 실패 시에도 데이터베이스에서는 삭제하여 앱 갤러리에서 제거
            mediaDao.deleteMediaById(mediaItem.id)
            throw e
        }
    }
    
    private fun deleteFileFromMediaStore(uri: Uri) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 이상: MediaStore를 통해 삭제
                val deleted = context.contentResolver.delete(uri, null, null)
                if (deleted == 0) {
                    // MediaStore 삭제 실패 시 직접 파일 삭제 시도
                    deleteFileDirectly(uri)
                }
            } else {
                // Android 9 이하: 직접 파일 삭제
                deleteFileDirectly(uri)
            }
        } catch (e: Exception) {
            // 최후의 수단: 직접 파일 삭제 시도
            deleteFileDirectly(uri)
        }
    }
    
    private fun deleteFileDirectly(uri: Uri) {
        try {
            val file = File(uri.path ?: return)
            if (file.exists()) {
                file.delete()
                
                // MediaStore에서도 제거 (갤러리 앱에서 보이지 않도록)
                context.contentResolver.delete(
                    MediaStore.Files.getContentUri("external"),
                    "${MediaStore.Files.FileColumns.DATA}=?",
                    arrayOf(file.absolutePath)
                )
            }
        } catch (e: Exception) {
            // 파일 삭제 실패는 로그만 남기고 진행
            e.printStackTrace()
        }
    }
    
    suspend fun getMediaCount(): Int {
        return mediaDao.getMediaCount()
    }
    
    suspend fun getMediaCountByType(type: String): Int {
        return mediaDao.getMediaCountByType(type)
    }
    
    private fun startObservingMediaStore() {
        // 이미지와 비디오 MediaStore 변경 감지
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaContentObserver
        )
        context.contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            mediaContentObserver
        )
    }
    
    fun stopObservingMediaStore() {
        context.contentResolver.unregisterContentObserver(mediaContentObserver)
    }
    
    private suspend fun syncWithMediaStore() {
        try {
            // 현재 데이터베이스의 모든 미디어 가져오기
            val dbMediaItems = mediaDao.getAllMedia()
            
            // 각 미디어 파일이 실제로 존재하는지 확인
            dbMediaItems.forEach { entity ->
                val uri = Uri.parse(entity.filePath)
                if (!fileExists(uri)) {
                    // 파일이 존재하지 않으면 데이터베이스에서 삭제
                    mediaDao.deleteMediaById(entity.id)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun fileExists(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
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
            thumbnailUri = Uri.parse(this.thumbnailPath ?: this.filePath),
            postId = this.postId,
            ownerId = this.ownerId,
            ownerUsername = this.ownerUsername,
            ownerProfilePicUrl = this.ownerProfilePicUrl
        )
    }
}