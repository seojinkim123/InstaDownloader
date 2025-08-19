package com.example.instadownloader.data.model

data class MediaItem(
    val url: String,
    val type: MediaType,
    val thumbnail: String? = null
)

enum class MediaType {
    IMAGE, VIDEO
}

fun String.getMediaType(): MediaType {
    return when {
        this.contains(".mp4") || this.contains("video") -> MediaType.VIDEO
        else -> MediaType.IMAGE
    }
}