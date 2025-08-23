package com.example.instadownloader.ui.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun WebBrowserScreen() {
    val context = LocalContext.current
    
    // 웹뷰
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                
                settings.apply {
                    javaScriptEnabled = true
                }
                
                loadUrl("https://www.instagram.com")
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}