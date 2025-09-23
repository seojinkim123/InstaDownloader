package com.kimseojin.instadownloader.utils

import android.content.Context
import android.widget.Toast

object ToastUtils {
    fun showShort(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
    
    fun showLong(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    
    fun showDownloadComplete(context: Context, count: Int) {
        val message = if (count == 1) {
            "1 media saved to gallery"
        } else {
            "$count media saved to gallery"
        }
        showLong(context, message)
    }
    
    fun showDownloadError(context: Context, error: String) {
        showLong(context, "Download failed: $error")
    }
}