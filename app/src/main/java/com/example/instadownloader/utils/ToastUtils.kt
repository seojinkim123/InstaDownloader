package com.example.instadownloader.utils

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
            "미디어 1개가 갤러리에 저장되었습니다"
        } else {
            "미디어 ${count}개가 갤러리에 저장되었습니다"
        }
        showLong(context, message)
    }
    
    fun showDownloadError(context: Context, error: String) {
        showLong(context, "다운로드 실패: $error")
    }
}