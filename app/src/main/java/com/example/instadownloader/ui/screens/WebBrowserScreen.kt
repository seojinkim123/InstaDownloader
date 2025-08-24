package com.example.instadownloader.ui.screens

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.util.Log
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.example.instadownloader.data.download.MediaDownloader

data class InstagramMediaItem(
    val url: String,
    val type: String, // "image" or "video"
    val isSelected: Boolean = true
)

class WebViewInterface(
    private val onMediaFound: (List<InstagramMediaItem>) -> Unit,
    private val onMediaUpdate: (List<InstagramMediaItem>) -> Unit,
    private val onBlobProcessing: (String, String) -> Unit,
    private val onBlobDownload: (String, String) -> Unit
) {
    @JavascriptInterface
    fun showDownloadDialog(mediaJson: String) {
        try {
            val mediaList = parseMediaJson(mediaJson)
            Handler(Looper.getMainLooper()).post {
                onMediaFound(mediaList)
            }
        } catch (e: Exception) {
            Log.e("WebView", "Error parsing media: ${e.message}")
        }
    }

    @JavascriptInterface
    fun updateMediaList(mediaJson: String) {
        try {
            val mediaList = parseMediaJson(mediaJson)
            Handler(Looper.getMainLooper()).post {
                onMediaUpdate(mediaList)
            }
        } catch (e: Exception) {
            Log.e("WebView", "Error updating media: ${e.message}")
        }
    }

    @JavascriptInterface
    fun notifyBlobProcessing(filename: String, status: String) {
        Handler(Looper.getMainLooper()).post {
            onBlobProcessing(filename, status)
        }
    }

    @JavascriptInterface
    fun downloadBlobVideo(filename: String, base64Data: String) {
        Handler(Looper.getMainLooper()).post {
            onBlobDownload(filename, base64Data)
        }
    }

    private fun parseMediaJson(json: String): List<InstagramMediaItem> {
        return json.split("||").mapNotNull { item ->
            val parts = item.split("::", limit = 2)
            if (parts.size == 2) {
                InstagramMediaItem(
                    url = parts[1],
                    type = parts[0],
                    isSelected = true
                )
            } else null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebBrowserScreen() {
    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf("") }
    var showBottomSheet by remember { mutableStateOf(false) }
    var mediaItems by remember { mutableStateOf<List<InstagramMediaItem>>(emptyList()) }
    var selectedItems by remember { mutableStateOf<List<InstagramMediaItem>>(emptyList()) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 상단 네비게이션 바
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (canGoBack) {
                        webView?.goBack()
                    }
                },
                enabled = canGoBack
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
            }

            IconButton(
                onClick = {
                    webView?.reload()
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "새로고침")
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // 현재 URL 표시
            Text(
                text = currentUrl.take(30) + if (currentUrl.length > 30) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        // 웹뷰
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            currentUrl = url ?: ""
                            Log.d("WebView", "페이지 시작: $url")
                        }

                        // ✨ CHANGED: onPageFinished에 X 버튼 클릭 스크립트 주입 로직 추가
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            canGoBack = view?.canGoBack() ?: false
                            currentUrl = url ?: ""
                            Log.d("WebView", "페이지 완료: $url")

                            // "X" 닫기 버튼 자동 클릭 스크립트 주입 (모든 페이지에서 실행)
                            view?.evaluateJavascript(getCloseButtonClickScript(), null)

                            // 다운로드 버튼 스크립트는 로그인 페이지가 아닐 때만 주입
                            if (url?.contains("accounts/login") == false) {
                                view?.evaluateJavascript(getInstagramScript(), null)
                            }
                        }

                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                            handler?.proceed()
                            Log.w("WebView", "SSL 에러 무시: ${error?.toString()}")
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString()
                            val headers = request?.requestHeaders
                            Log.d("WebView", "🌐 URL 로딩: $url")
                            Log.d("WebView", "📨 요청 헤더: $headers")
                            return super.shouldOverrideUrlLoading(view, request)
                        }

                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            Log.e("WebView", "에러 발생: $description ($errorCode) - $failingUrl")
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            Log.d("WebView", "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()}")
                            return true
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            Log.d("WebView", "📊 로딩 진행률: $newProgress%")

                            if (newProgress == 50) {
                                val cookieManager = CookieManager.getInstance()
                                val hasCookie = cookieManager.hasCookies()
                                Log.d("WebView", "🍪 쿠키 상태: $hasCookie")

                                val url = view?.url
                                if (url != null) {
                                    val cookies = cookieManager.getCookie(url)
                                    Log.d("WebView", "🍪 현재 쿠키: $cookies")
                                }
                            }
                        }
                    }

                    addJavascriptInterface(
                        WebViewInterface(
                            onMediaFound = { foundMedia ->
                                mediaItems = foundMedia
                                selectedItems = foundMedia
                                showBottomSheet = true
                            },
                            onMediaUpdate = { updatedMedia ->
                                mediaItems = updatedMedia
                                selectedItems = updatedMedia.map { media ->
                                    selectedItems.find { it.url == media.url } ?: media
                                }
                            },
                            onBlobProcessing = { filename, status ->
                                when {
                                    status == "start" -> {
                                        Toast.makeText(context, "비디오 처리 중: $filename", Toast.LENGTH_SHORT).show()
                                    }
                                    status.startsWith("error::") -> {
                                        val errorMsg = status.removePrefix("error::")
                                        Toast.makeText(context, "처리 실패: $errorMsg", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onBlobDownload = { filename, base64Data ->
                                scope.launch {
                                    try {
                                        val mediaDownloader = MediaDownloader(context)
                                        val result = mediaDownloader.downloadBase64Video(filename, base64Data)

                                        result.fold(
                                            onSuccess = { savedUri ->
                                                Toast.makeText(context, "비디오 다운로드 완료: $filename", Toast.LENGTH_SHORT).show()
                                            },
                                            onFailure = { error ->
                                                Toast.makeText(context, "다운로드 실패: ${error.message}", Toast.LENGTH_LONG).show()
                                            }
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "다운로드 오류: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ),
                        "Android"
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        javaScriptCanOpenWindowsAutomatically = true
                        domStorageEnabled = true
                        @Suppress("DEPRECATION")
                        databaseEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        allowFileAccess = false
                        allowContentAccess = true
                        @Suppress("DEPRECATION")
                        allowUniversalAccessFromFileURLs = false
                        @Suppress("DEPRECATION")
                        allowFileAccessFromFileURLs = false
                        setGeolocationEnabled(true)
                        mediaPlaybackRequiresUserGesture = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = false
                        }
                    }

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    Log.d("WebView", "✅ 쿠키 설정 완료")
                    cookieManager.flush()
                    Log.d("WebView", "🔄 쿠키 동기화 완료")
                    @Suppress("DEPRECATION")
                    CookieManager.setAcceptFileSchemeCookies(true)
                    Log.d("WebView", "🍪 쿠키 매니저 설정 완료")

                    setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
                    loadUrl("https://www.instagram.com")
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showBottomSheet = false
            },
            sheetState = bottomSheetState,
            modifier = Modifier.fillMaxHeight()
        ) {
            InstagramMediaBottomSheet(
                mediaItems = mediaItems,
                selectedItems = selectedItems,
                onSelectionChange = { selectedItems = it },
                onDownload = { selectedUrls ->
                    scope.launch {
                        try {
                            val mediaDownloader = MediaDownloader(context)
                            val urls = selectedUrls.map { it.url }
                            val isVideoList = selectedUrls.map { it.type == "video" }

                            val result = mediaDownloader.downloadMediaList(
                                mediaUrls = urls,
                                isVideoList = isVideoList,
                                onProgress = { _, _ -> },
                                onItemComplete = { _, _ -> }
                            )

                            result.fold(
                                onSuccess = { savedUris ->
                                    Toast.makeText(context, "다운로드 완료: ${savedUris.size}개 파일", Toast.LENGTH_SHORT).show()
                                },
                                onFailure = { error ->
                                    Toast.makeText(context, "다운로드 실패: ${error.message}", Toast.LENGTH_SHORT).show()
                                }
                            )

                            showBottomSheet = false
                        } catch (e: Exception) {
                            Toast.makeText(context, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

// ✨ NEW: 'X' 닫기 버튼 자동 클릭 스크립트 함수 추가
private fun getCloseButtonClickScript(): String {
    return """
        (function() {
            if (window.closeModalObserver) {
                return;
            }
            console.log('모달 닫기 버튼 감지 스크립트 시작');

            const clickCloseButton = (modal) => {
                const selectors = [
                    'button[aria-label="Close"]',
                    'button[aria-label="닫기"]',
                    'div[role="button"][aria-label="Close"]',
                    'div[role="button"][aria-label="닫기"]',
                    'svg[aria-label="닫기"]',
                    'svg[aria-label="Close"]'
                ];
                for (const selector of selectors) {
                    const closeButton = modal.querySelector(selector);
                    if (closeButton) {
                        console.log('닫기 버튼 발견!', closeButton);
                        const clickableElement = closeButton.closest('div[role="button"]') || closeButton;
                        clickableElement.click();
                        return true;
                    }
                }
                return false;
            };

            const observerCallback = (mutationsList, observer) => {
                for(const mutation of mutationsList) {
                    if (mutation.type === 'childList') {
                        mutation.addedNodes.forEach(node => {
                            if (node.nodeType !== 1) return;
                            const dialog = node.querySelector ? (node.matches('div[role="dialog"]') ? node : node.querySelector('div[role="dialog"]')) : null;
                            if (dialog) {
                                console.log('role="dialog" 모달 발견');
                                if (clickCloseButton(dialog)) {
                                    console.log('닫기 버튼 클릭 성공. 관찰을 중단합니다.');
                                    observer.disconnect();
                                    window.closeModalObserver = null;
                                }
                            }
                        });
                    }
                }
            };
            
            const observer = new MutationObserver(observerCallback);
            observer.observe(document.body, { childList: true, subtree: true });
            window.closeModalObserver = observer;

            console.log('이제부터 모달 및 닫기 버튼을 감시합니다.');
            
            setTimeout(() => {
                if (window.closeModalObserver) {
                    console.log('시간 초과. 모달 감시를 중지합니다.');
                    window.closeModalObserver.disconnect();
                    window.closeModalObserver = null;
                }
            }, 30000); // 30초 후 자동 중지
        })();
    """.trimIndent()
}


// Instagram 스크립트를 별도 함수로 분리
private fun getInstagramScript(): String {
    return """
        (function() {
            console.log('Instagram Download Script Started');
            
            function addDownloadButtons() {
                const articles = document.querySelectorAll('article');
                articles.forEach((article, index) => {
                    if (article.querySelector('.download-btn-custom')) return;
                    const hasMedia = article.querySelector('img[src*="scontent"]') || article.querySelector('video');
                    if (!hasMedia) return;
                    const rect = article.getBoundingClientRect();
                    const isVisible = rect.top < window.innerHeight && rect.bottom > 0;
                    if (!isVisible) return;

                    console.log('Adding download button to post ' + index);
                    article.style.position = 'relative';
                    
                    const downloadBtn = document.createElement('div');
                    downloadBtn.className = 'download-btn-custom';
                    downloadBtn.innerHTML = '🔥';
                    downloadBtn.style.cssText = 'position: absolute !important; top: 60px !important; left: 12px !important; width: 40px !important; height: 40px !important; border-radius: 50% !important; background: rgba(0,0,0,0.8) !important; color: white !important; cursor: pointer !important; z-index: 9999 !important; font-size: 20px !important; display: flex !important; align-items: center !important; justify-content: center !important; box-shadow: 0 2px 8px rgba(0,0,0,0.5) !important; transition: all 0.2s ease !important;';
                    
                    downloadBtn.addEventListener('click', async function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        console.log('Download button clicked');
                        this.innerHTML = '⏳';
                        this.style.pointerEvents = 'none';
                        try {
                            Android.showDownloadDialog('image::loading');
                            const mediaUrls = await extractMediaFromPost(article);
                            if (mediaUrls.length > 0) {
                                Android.showDownloadDialog(mediaUrls.join('||'));
                            }
                        } catch (error) {
                            console.log('Error extracting media: ' + error);
                        } finally {
                            this.innerHTML = '🔥';
                            this.style.pointerEvents = 'auto';
                        }
                    });
                    
                    article.appendChild(downloadBtn);
                });
            }
            
            async function extractMediaFromPost(article) {
                const mediaItems = new Set();
                const mainContainer = article.querySelector('div[role="presentation"]') || article;
                const isCarousel = mainContainer.querySelector('ul') && mainContainer.querySelector('button[aria-label*="Next"], button[aria-label*="다음"]');
                
                if (isCarousel) {
                    await extractCarouselMediaWithUpdates(mainContainer, mediaItems);
                } else {
                    extractSingleMedia(mainContainer, mediaItems);
                }
                
                return Array.from(mediaItems);
            }
            
            function extractSingleMedia(container, mediaItems) {
                container.querySelectorAll('img[src*="scontent"]').forEach(img => {
                    if (!img.src.includes('profile')) mediaItems.add('image::' + img.src);
                });
                container.querySelectorAll('video').forEach(video => {
                    if (video.src) mediaItems.add('video::' + video.src);
                });
            }
            
            async function extractCarouselMediaWithUpdates(container, mediaItems) {
                let maxAttempts = 20;
                let lastCollectedCount = -1;
                while (maxAttempts-- > 0 && lastCollectedCount !== mediaItems.size) {
                    lastCollectedCount = mediaItems.size;
                    const ul = container.querySelector('ul');
                    if (ul) {
                        ul.querySelectorAll('li').forEach(li => {
                            const img = li.querySelector('img[src*="scontent"]');
                            if (img && !img.src.includes('profile')) mediaItems.add('image::' + img.src);
                            const video = li.querySelector('video');
                            if (video && video.src) mediaItems.add('video::' + video.src);
                        });
                    }
                    if (mediaItems.size > 0) {
                        try { Android.updateMediaList(Array.from(mediaItems).join('||')); } catch(e){}
                    }
                    const nextButton = container.querySelector('button[aria-label*="Next"], button[aria-label*="다음"]');
                    if (nextButton) {
                        nextButton.click();
                        await new Promise(resolve => setTimeout(resolve, 150));
                    } else {
                        break;
                    }
                }
            }
            
            const run = () => setTimeout(addDownloadButtons, 500);
            window.addEventListener('scroll', run);
            const observer = new MutationObserver((mutations) => {
                for (const mutation of mutations) {
                    if (mutation.addedNodes.length > 0) {
                        run();
                        return;
                    }
                }
            });
            observer.observe(document.body, { childList: true, subtree: true });
            run();
            console.log('Download script setup complete');
        })();
    """
}

@Composable
fun InstagramMediaBottomSheet(
    mediaItems: List<InstagramMediaItem>,
    selectedItems: List<InstagramMediaItem>,
    onSelectionChange: (List<InstagramMediaItem>) -> Unit,
    onDownload: (List<InstagramMediaItem>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "미디어 선택",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { onSelectionChange(emptyList()) }) {
                Text("전체 해제")
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(mediaItems.size) { index ->
                val item = mediaItems[index]
                val isSelected = selectedItems.any { it.url == item.url }

                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            val currentSelection = selectedItems.toMutableList()
                            if (isSelected) {
                                currentSelection.removeAll { it.url == item.url }
                            } else {
                                currentSelection.add(item)
                            }
                            onSelectionChange(currentSelection)
                        }
                ) {
                    if (item.url == "loading") {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        AsyncImage(
                            model = item.url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                    }

                    if (item.type.contains("video")) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "비디오", color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onDownload(selectedItems) },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedItems.isNotEmpty()
        ) {
            Text("다운로드 (${selectedItems.size}개)")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}