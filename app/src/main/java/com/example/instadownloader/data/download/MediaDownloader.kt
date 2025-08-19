package com.example.instadownloader.data.download

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.instadownloader.data.database.AppDatabase
import com.example.instadownloader.data.database.DownloadedMediaEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class MediaDownloader(private val context: Context) {
    private val client = OkHttpClient()
    private val database = AppDatabase.getDatabase(context)
    private val mediaDao = database.downloadedMediaDao()
    
    suspend fun downloadMedia(
        url: String,
        filename: String,
        isVideo: Boolean = false,
        originalUrl: String = url,
        onProgress: (Int) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Download failed: ${response.code}"))
            }
            
            val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
            val contentLength = body.contentLength()
            
            // MediaStore를 사용하여 갤러리에 저장
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, 
                        if (isVideo) Environment.DIRECTORY_MOVIES + "/InstaDownloader"
                        else Environment.DIRECTORY_PICTURES + "/InstaDownloader")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            
            val contentResolver = context.contentResolver
            val collection = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            
            val uri = contentResolver.insert(collection, contentValues)
                ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))
            
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                body.byteStream().use { inputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        if (contentLength > 0) {
                            val progress = (totalBytesRead * 100 / contentLength).toInt()
                            onProgress(progress)
                        }
                    }
                }
            }
            
            // Android Q 이상에서 IS_PENDING 플래그 제거
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }
            
            // 데이터베이스에 메타데이터 저장
            val mediaEntity = DownloadedMediaEntity(
                id = UUID.randomUUID().toString(),
                fileName = filename,
                filePath = uri.toString(),
                originalUrl = originalUrl,
                downloadDate = System.currentTimeMillis(),
                mediaType = if (isVideo) "video" else "image",
                fileSize = contentLength,
                thumbnailPath = uri.toString() // 이미지의 경우 자기 자신이 썸네일
            )
            
            mediaDao.insertMedia(mediaEntity)
            
            Result.success(uri.toString())
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun generateFilename(url: String, isVideo: Boolean): String {
        val timestamp = System.currentTimeMillis()
        val extension = if (isVideo) "mp4" else "jpg"
        val urlHash = url.hashCode().toString().takeLast(6)
        return "insta_${timestamp}_${urlHash}.$extension"
    }
    
    suspend fun downloadMediaList(
        mediaUrls: List<String>,
        isVideoList: List<Boolean>,
        originalUrls: List<String> = mediaUrls,
        onProgress: (Int, Int) -> Unit = { _, _ -> }, // current, total
        onItemComplete: (Int, String) -> Unit = { _, _ -> }
    ): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val results = mutableListOf<String>()
            
            mediaUrls.forEachIndexed { index, url ->
                val isVideo = isVideoList.getOrElse(index) { false }
                val originalUrl = originalUrls.getOrElse(index) { url }
                val filename = generateFilename(url, isVideo)
                
                val result = downloadMedia(url, filename, isVideo, originalUrl) { itemProgress ->
                    // 개별 파일 진행률을 전체 진행률로 변환
                    val totalProgress = (index * 100 + itemProgress) / mediaUrls.size
                    onProgress(totalProgress, 100)
                }
                
                result.fold(
                    onSuccess = { savedUri ->
                        results.add(savedUri)
                        onItemComplete(index, savedUri)
                    },
                    onFailure = { error ->
                        return@withContext Result.failure(error)
                    }
                )
            }
            
            Result.success(results)
            
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}