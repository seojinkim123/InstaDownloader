package com.example.instadownloader.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedMediaDao {
    @Query("SELECT * FROM downloaded_media ORDER BY downloadDate DESC")
    fun getAllMediaFlow(): Flow<List<DownloadedMediaEntity>>
    
    @Query("SELECT * FROM downloaded_media ORDER BY downloadDate DESC")
    suspend fun getAllMedia(): List<DownloadedMediaEntity>
    
    @Query("SELECT * FROM downloaded_media WHERE mediaType = :type ORDER BY downloadDate DESC")
    suspend fun getMediaByType(type: String): List<DownloadedMediaEntity>
    
    @Query("SELECT * FROM downloaded_media WHERE id = :id")
    suspend fun getMediaById(id: String): DownloadedMediaEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: DownloadedMediaEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaList(mediaList: List<DownloadedMediaEntity>)
    
    @Delete
    suspend fun deleteMedia(media: DownloadedMediaEntity)
    
    @Query("DELETE FROM downloaded_media WHERE id = :id")
    suspend fun deleteMediaById(id: String)
    
    @Query("DELETE FROM downloaded_media")
    suspend fun deleteAllMedia()
    
    @Query("SELECT COUNT(*) FROM downloaded_media")
    suspend fun getMediaCount(): Int
    
    @Query("SELECT COUNT(*) FROM downloaded_media WHERE mediaType = :type")
    suspend fun getMediaCountByType(type: String): Int
}