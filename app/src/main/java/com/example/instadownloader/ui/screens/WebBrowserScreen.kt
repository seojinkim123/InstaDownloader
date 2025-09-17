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
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.DisposableEffect
import java.lang.ref.WeakReference

// 웹뷰 상태를 전역으로 관리하는 싱글톤 (Compose State 사용)
object WebViewManager {
    private var cachedWebView: WeakReference<WebView>? = null
    private var protectedWebView: WebView? = null // 강한 참조로 보호
    
    // 바텀시트 상태를 전역으로 관리 (Compose State 사용)
    private val _showBottomSheet = mutableStateOf(false)
    private val _mediaItems = mutableStateOf<List<InstagramMediaItem>>(emptyList())
    private val _selectedItems = mutableStateOf<List<InstagramMediaItem>>(emptyList())
    
    // 임시 메모리 캐시 (앱 종료 시 자동 삭제)
    private val postMediaCache = mutableMapOf<String, List<InstagramMediaItem>>()
    
    // Compose에서 관찰 가능한 State 노출
    val showBottomSheet: State<Boolean> = _showBottomSheet
    val mediaItems: State<List<InstagramMediaItem>> = _mediaItems
    val selectedItems: State<List<InstagramMediaItem>> = _selectedItems
    
    fun getCachedWebView(): WebView? = protectedWebView ?: cachedWebView?.get()
    
    fun setCachedWebView(webView: WebView) {
        cachedWebView = WeakReference(webView)
        if (protectedWebView == null) {
            protectedWebView = webView
        }
    }
    
    // 다운로드 중 WebView를 강한 참조로 보호
    fun protectWebViewFromGC() {
        val webView = cachedWebView?.get()
        if (webView != null) {
            protectedWebView = webView
            Log.d("WebView", "WebView 메모리 보호 활성화")
        }
    }
    
    // 다운로드 완료 후 보호 해제
    fun releaseWebViewProtection() {
        protectedWebView = null
        Log.d("WebView", "WebView 메모리 보호 해제")
    }
    
    fun clearCache() {
        cachedWebView?.clear()
        cachedWebView = null
        protectedWebView = null
        // 상태도 초기화
        _showBottomSheet.value = false
        _mediaItems.value = emptyList()
        _selectedItems.value = emptyList()
        // 미디어 캐시도 초기화
        postMediaCache.clear()
    }
    
    // 포스트 미디어 캐시 관련 메소드들
    fun getCachedMedia(postKey: String): List<InstagramMediaItem>? {
        return postMediaCache[postKey]
    }
    
    fun setCachedMedia(postKey: String, mediaItems: List<InstagramMediaItem>) {
        postMediaCache[postKey] = mediaItems
        Log.d("WebView", "포스트 미디어 캐시 저장: $postKey, ${mediaItems.size}개")
    }
    
    fun getCacheSize(): Int {
        return postMediaCache.size
    }
    
    // 바텀시트 상태 업데이트 메서드들
    fun updateBottomSheetState(show: Boolean, media: List<InstagramMediaItem> = emptyList()) {
        _showBottomSheet.value = show
        if (media.isNotEmpty()) {
            _mediaItems.value = media
            _selectedItems.value = media
        }
        Log.d("WebView", "전역 상태 업데이트: showBottomSheet=$show, 미디어=${media.size}개")
    }
    
    fun updateMediaSelection(selected: List<InstagramMediaItem>) {
        _selectedItems.value = selected
    }
    
    fun updateMediaItems(items: List<InstagramMediaItem>) {
        _mediaItems.value = items
        _selectedItems.value = items.map { media ->
            _selectedItems.value.find { it.url == media.url } ?: media
        }
    }
    
    fun hideBottomSheet() {
        _showBottomSheet.value = false
    }
}

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
        Log.d("WebView", "JavaScript showDownloadDialog 호출됨: $mediaJson")
        try {
            val mediaList = parseMediaJson(mediaJson)
            Log.d("WebView", "파싱된 미디어 개수: ${mediaList.size}")
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

    @JavascriptInterface
    fun getCachedMedia(postKey: String): String? {
        val cached = WebViewManager.getCachedMedia(postKey)
        return if (cached != null) {
            cached.joinToString("||") { "${it.type}::${it.url}" }
        } else null
    }

    @JavascriptInterface
    fun setCachedMedia(postKey: String, mediaData: String) {
        val mediaList = parseMediaJson(mediaData)
        WebViewManager.setCachedMedia(postKey, mediaList)
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
    val cachedWebView = WebViewManager.getCachedWebView()
    var webView: WebView? by remember { mutableStateOf(cachedWebView) }
    var canGoBack by remember { mutableStateOf(cachedWebView?.canGoBack() ?: false) }
    var isLoading by remember { mutableStateOf(false) }
    
    // 전역 상태를 직접 관찰 (by 델리게이트 사용)
    val showBottomSheetState by WebViewManager.showBottomSheet
    val mediaItemsState by WebViewManager.mediaItems  
    val selectedItemsState by WebViewManager.selectedItems

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // 웹뷰 상태 관리
    DisposableEffect(Unit) {
        Log.d("WebView", "WebBrowserScreen 시작 - 캐시된 웹뷰 로드 시도")
        onDispose {
            Log.d("WebView", "WebBrowserScreen 종료 - 웹뷰 상태 유지")
            // 웹뷰는 캐시에서 계속 유지됨
        }
    }

    // 뒤로가기 처리: 웹뷰에서 뒤로갈 수 있으면 웹뷰 뒤로가기 실행, 없으면 기본 동작
    BackHandler(enabled = true) {
        Log.d("WebView", "BackHandler 실행됨 - canGoBack: $canGoBack")
        webView?.let { webViewInstance ->
            if (webViewInstance.canGoBack()) {
                Log.d("WebView", "웹뷰 뒤로가기 실행")
                webViewInstance.goBack()
                // 뒤로가기 실행 후 상태 업데이트
                canGoBack = webViewInstance.canGoBack()
                Log.d("WebView", "웹뷰 뒤로가기 실행 후 - canGoBack: $canGoBack")
            } else {
                Log.d("WebView", "웹뷰에서 뒤로갈 페이지가 없음 - 기본 뒤로가기 실행하지 않음")
                // 여기서는 아무것도 하지 않으므로, 시스템 기본 뒤로가기가 실행되지 않음
                // 탭 전환을 원한다면 명시적으로 처리해야 함
            }
        } ?: run {
            Log.d("WebView", "웹뷰가 null - 기본 뒤로가기 실행하지 않음")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 간소화된 상단바 (새로고침 버튼만)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            contentAlignment = Alignment.CenterEnd
        ) {
            IconButton(
                onClick = {
                    webView?.reload()
                }
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "새로고침")
            }
            
            // 로딩 인디케이터
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        // 웹뷰
        AndroidView(
            factory = { context ->
                // 캐시된 웹뷰가 있으면 사용, 없으면 새로 생성
                val cachedInstance = WebViewManager.getCachedWebView()
                val webViewInstance = cachedInstance ?: WebView(context)
                
                webViewInstance.apply {
                    // 🔧 핵심 수정사항들
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            canGoBack = view?.canGoBack() ?: false
                            Log.d("WebView", "페이지 시작: $url, canGoBack: $canGoBack")
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            canGoBack = view?.canGoBack() ?: false
                            Log.d("WebView", "페이지 완료: $url, canGoBack: $canGoBack")

                            // "X" 닫기 버튼 자동 클릭 스크립트 주입 (모든 페이지에서 실행)
                            view?.evaluateJavascript(getCloseButtonClickScript(), null)

                            // 로그인 페이지인 경우 스크립트 주입하지 않음 (기존 로직 유지)
                            if (url?.contains("accounts/login") == false) {
                                Log.d("WebView", "Instagram 스크립트 주입 시작 - URL: $url")
                                // Instagram 포스트 감지 및 다운로드 버튼 추가 JavaScript 주입
                                view?.evaluateJavascript(getInstagramScript()) { result ->
                                    Log.d("WebView", "Instagram 스크립트 주입 완료 - 결과: $result")
                                }
                            } else {
                                Log.d("WebView", "로그인 페이지이므로 스크립트 주입 생략 - URL: $url")
                            }
                        }

                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                            // SSL 에러 무시 (개발용, 실제 배포시에는 주의)
                            handler?.proceed()
                            Log.w("WebView", "SSL 에러 무시: ${error?.toString()}")
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString()
                            val headers = request?.requestHeaders
                            Log.d("WebView", "🌐 URL 로딩: $url")
                            Log.d("WebView", "📨 요청 헤더: $headers")

                            Log.w("WebView", "❌ 차단된 도메인: $url")
                            return super.shouldOverrideUrlLoading(view, request)
                        }

                        @Suppress("DEPRECATION")
                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            Log.e("WebView", "에러 발생: $description ($errorCode) - $failingUrl")
                        }
                    }

                    // Console 로그 처리
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            Log.d("WebView", "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()}")
                            return true
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            Log.d("WebView", "📊 로딩 진행률: $newProgress%")

                            // 쿠키 상태 로깅 (50% 진행 시점에서)
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

                    // JavaScript 인터페이스 추가 (캐시된 웹뷰라도 항상 재설정)
                    // 기존 인터페이스 제거 후 새로 설정
                    try {
                        removeJavascriptInterface("Android")
                    } catch (e: Exception) {
                        Log.d("WebView", "기존 JavaScript Interface 제거 실패 (정상): ${e.message}")
                    }
                    
                    addJavascriptInterface(
                        WebViewInterface(
                            onMediaFound = { foundMedia ->
                                Log.d("WebView", "JavaScript onMediaFound 호출됨: ${foundMedia.size}개 미디어")
                                // 전역 상태만 업데이트 (로컬 상태는 자동 동기화)
                                WebViewManager.updateBottomSheetState(true, foundMedia)
                                Log.d("WebView", "onMediaFound - 전역 상태 업데이트 완료")
                            },
                            onMediaUpdate = { updatedMedia ->
                                // 전역 상태만 업데이트
                                WebViewManager.updateMediaItems(updatedMedia)
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
                    Log.d("WebView", "JavaScript Interface 'Android' 설정 완료")

                    // 🔧 핵심 웹뷰 설정 (인스타그램 로그인 문제 해결)
                    settings.apply {
                        // JavaScript 활성화
                        javaScriptEnabled = true
                        javaScriptCanOpenWindowsAutomatically = true

                        // DOM Storage 활성화
                        domStorageEnabled = true

                        // 데이터베이스 활성화 (deprecated but still works)
                        @Suppress("DEPRECATION")
                        databaseEnabled = true

                        // 뷰포트 설정
                        loadWithOverviewMode = true
                        useWideViewPort = true

                        // 줌 설정
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false

                        // Mixed Content 허용 (HTTPS + HTTP)
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                        // ✅ Instagram 웹뷰 차단 우회 (wv 토큰 완전 제거)
                        val defaultUA = WebSettings.getDefaultUserAgent(context)
                        userAgentString = defaultUA
                            .replace("; wv", "") // WebView 토큰 제거
                            .replace("Version/4.0", "Version/4.0 Chrome/131.0.0.0") // 브라우저 시그니처 강화

                        // 🔧 최적화된 캐시 모드 설정 (인스타그램용)
                        cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK

                        // 🔧 보안 강화된 파일 접근 설정
                        allowFileAccess = false  // 보안상 false로 변경
                        allowContentAccess = true

                        // Deprecated 보안 설정 (여전히 필요)
                        @Suppress("DEPRECATION")
                        allowUniversalAccessFromFileURLs = false  // XSS 공격 방지
                        @Suppress("DEPRECATION")
                        allowFileAccessFromFileURLs = false  // 파일 접근 제한

                        // Geolocation 허용
                        setGeolocationEnabled(true)

                        // 미디어 재생 설정
                        mediaPlaybackRequiresUserGesture = false

                        // 안전하지 않은 콘텐츠 허용
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            safeBrowsingEnabled = false
                        }
                    }

                    // 🔧 강화된 쿠키 관리자 설정 (로그인 세션 유지)
                    val cookieManager = CookieManager.getInstance()

                    // 🔧 쿠키 설정 (현재 minSdk가 24이므로 항상 최신 API 사용)
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    Log.d("WebView", "✅ 쿠키 설정 완료")

                    // 쿠키 즉시 동기화
                    cookieManager.flush()
                    Log.d("WebView", "🔄 쿠키 동기화 완료")

                    // 파일 스키마 쿠키 허용 (deprecated but still needed)
                    @Suppress("DEPRECATION")
                    CookieManager.setAcceptFileSchemeCookies(true)

                    Log.d("WebView", "🍪 쿠키 매니저 설정 완료")

                    // 🔧 하드웨어 가속 활성화
                    setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

                    // 첫 생성시에만 Instagram 로드
                    if (cachedInstance == null) {
                        loadUrl("https://www.instagram.com")
                    } else {
                        Log.d("WebView", "캐시된 웹뷰 재사용 - 현재 URL: ${this.url}")
                    }
                    
                    // 웹뷰 인스턴스 저장
                    webView = this
                    WebViewManager.setCachedWebView(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }

    // 바텀 시트 (전역 상태 기반)
    if (showBottomSheetState) {
        ModalBottomSheet(
            onDismissRequest = {
                WebViewManager.hideBottomSheet()
                Log.d("WebView", "바텀시트 닫기")
            },
            sheetState = bottomSheetState,
            modifier = Modifier.fillMaxHeight()
        ) {
            InstagramMediaBottomSheet(
                mediaItems = mediaItemsState,
                selectedItems = selectedItemsState,
                onSelectionChange = { 
                    WebViewManager.updateMediaSelection(it)
                },
                onDownload = { selectedUrls ->
                    scope.launch {
                        try {
                            val mediaDownloader = MediaDownloader(context)
                            val urls = selectedUrls.map { it.url }
                            val isVideoList = selectedUrls.map { it.type == "video" }

                            val result = mediaDownloader.downloadMediaList(
                                mediaUrls = urls,
                                isVideoList = isVideoList,
                                onProgress = { current, total ->
                                    // 진행률 업데이트는 Toast로 간단히 처리
                                },
                                onItemComplete = { index, savedUri ->
                                    // 개별 완료 처리
                                }
                            )

                            result.fold(
                                onSuccess = { savedUris ->
                                    Toast.makeText(context, "다운로드 완료: ${savedUris.size}개 파일", Toast.LENGTH_SHORT).show()
                                },
                                onFailure = { error ->
                                    Toast.makeText(context, "다운로드 실패: ${error.message}", Toast.LENGTH_SHORT).show()
                                }
                            )

                            WebViewManager.hideBottomSheet()
                            Log.d("WebView", "다운로드 완료 후 바텀시트 닫기")
                        } catch (e: Exception) {
                            Toast.makeText(context, "오류: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

// 'X' 닫기 버튼 자동 클릭 스크립트
private fun getCloseButtonClickScript(): String {
    return """
        (function() {
            // 스크립트가 여러 번 실행되는 것을 방지
            if (window.closeModalObserver) {
                return;
            }
            console.log('모달 닫기 버튼 감지 스크립트 시작');

            const clickCloseButton = (modal) => {
                // 닫기 버튼을 찾기 위한 다양한 CSS 선택자
                const selectors = [
                    'button[aria-label="Close"]',    // 영어 "Close"
                    'button[aria-label="닫기"]',      // 한국어 "닫기"
                    'div[role="button"][aria-label="Close"]',
                    'div[role="button"][aria-label="닫기"]',
                    'svg[aria-label="닫기"]',
                    'svg[aria-label="Close"]'
                ];

                for (const selector of selectors) {
                    const closeButton = modal.querySelector(selector);
                    if (closeButton) {
                        console.log('닫기 버튼 발견!', closeButton);
                        // 클릭 가능한 가장 가까운 부모 요소를 찾아 클릭 (더 안정적)
                        const clickableElement = closeButton.closest('div[role="button"], button');
                        if (clickableElement) {
                            clickableElement.click();
                        } else {
                            closeButton.click();
                        }
                        return true;
                    }
                }
                return false;
            };

            const observerCallback = (mutationsList, observer) => {
                for(const mutation of mutationsList) {
                    if (mutation.type === 'childList') {
                        // 페이지에 새로 추가된 노드들을 확인
                        mutation.addedNodes.forEach(node => {
                            // 추가된 노드가 Element가 아니면 무시
                            if (node.nodeType !== 1) return;

                            // role="dialog"를 가진 모달을 직접 찾거나 자식 중에 있는지 확인
                            const dialog = node.querySelector ? (node.matches('div[role="dialog"]') ? node : node.querySelector('div[role="dialog"]')) : null;
                            
                            if (dialog) {
                                console.log('role="dialog" 모달 발견');
                                if (clickCloseButton(dialog)) {
                                     console.log('닫기 버튼 클릭 성공. 관찰을 중단합니다.');
                                     observer.disconnect(); // 목표 달성 후 observer 중지
                                     window.closeModalObserver = null;
                                }
                            }
                        });
                    }
                }
            };
            
            // DOM의 변화를 감지하는 MutationObserver 생성
            const observer = new MutationObserver(observerCallback);

            // body 전체의 자식 요소 추가/삭제를 감시
            observer.observe(document.body, { childList: true, subtree: true });

            // window 객체에 observer를 저장하여 중복 실행 방지
            window.closeModalObserver = observer;

            console.log('이제부터 모달 및 닫기 버튼을 감시합니다.');
            
            // 안전장치: 30초 후에 자동으로 감시 중지
            setTimeout(() => {
                if (window.closeModalObserver) {
                    console.log('시간 초과. 모달 감시를 중지합니다.');
                    window.closeModalObserver.disconnect();
                    window.closeModalObserver = null;
                }
            }, 30000); // 30초
        })();
    """.trimIndent()
}

// Instagram 스크립트를 별도 함수로 분리 (기존 코드 원본 유지)
private fun getInstagramScript(): String {
    return """
        (function() {
            console.log('Instagram Download Script Started');
            
            function addDownloadButtons() {
                const articles = document.querySelectorAll('article');
                console.log('Found articles: ' + articles.length);
                
                articles.forEach((article, index) => {
                    // 이미 버튼이 있으면 스킵
                    if (article.querySelector('.download-btn-custom')) {
                        return;
                    }
                    
                    // 포스트에 미디어가 있는지 확인
                    const hasMedia = article.querySelector('img[src*="scontent"]') || article.querySelector('video');
                    if (!hasMedia) {
                        return;
                    }
                    
                    // 포스트가 화면에 보이는지 확인 (성능 최적화)
                    const rect = article.getBoundingClientRect();
                    const isVisible = rect.top < window.innerHeight && rect.bottom > 0;
                    if (!isVisible) {
                        return;
                    }
                    
                    console.log('Adding download button to post ' + index);
                    
                    article.style.position = 'relative';
                    
                    const downloadBtn = document.createElement('div');
                    downloadBtn.className = 'download-btn-custom';
                    downloadBtn.innerHTML = '🔥';
                    downloadBtn.style.cssText = 
                        'position: absolute !important;' +
                        'top: 60px !important;' +
                        'left: 12px !important;' +
                        'width: 40px !important;' +
                        'height: 40px !important;' +
                        'border-radius: 50% !important;' +
                        'background: rgba(0,0,0,0.8) !important;' +
                        'color: white !important;' +
                        'cursor: pointer !important;' +
                        'z-index: 9999 !important;' +
                        'font-size: 20px !important;' +
                        'display: flex !important;' +
                        'align-items: center !important;' +
                        'justify-content: center !important;' +
                        'box-shadow: 0 2px 8px rgba(0,0,0,0.5) !important;' +
                        'transition: all 0.2s ease !important;';
                    
                    downloadBtn.addEventListener('mouseenter', function() {
                        this.style.transform = 'scale(1.1)';
                        this.style.background = 'rgba(0,0,0,0.9)';
                    });
                    
                    downloadBtn.addEventListener('mouseleave', function() {
                        this.style.transform = 'scale(1)';
                        this.style.background = 'rgba(0,0,0,0.8)';
                    });
                    
                    downloadBtn.addEventListener('click', async function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        
                        console.log('Download button clicked');
                        
                        this.innerHTML = '⏳';
                        this.style.pointerEvents = 'none';
                        
                        try {
                            // 즉시 바텀 시트 띄우기 (로딩 상태로)
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
            
            // 포스트 고유 키 생성 (URL 기반)
            function generatePostKey(article) {
                // 포스트 URL을 찾는 여러 방법 시도
                let postUrl = '';
                
                // 1. 포스트 링크 찾기 (a[href*="/p/"])
                const postLink = article.querySelector('a[href*="/p/"]');
                if (postLink) {
                    postUrl = postLink.href;
                } else {
                    // 2. 현재 페이지 URL 사용 (포스트 상세 페이지인 경우)
                    if (window.location.href.includes('/p/')) {
                        postUrl = window.location.href;
                    } else {
                        // 3. article의 위치 기반 고유값 생성
                        const rect = article.getBoundingClientRect();
                        postUrl = 'post_' + Math.round(rect.top) + '_' + Math.round(rect.left) + '_' + Date.now();
                    }
                }
                
                // URL에서 shortcode 추출 (더 안정적인 키)
                const shortcodeMatch = postUrl.match(/\/p\/([A-Za-z0-9_-]+)/);
                const postKey = shortcodeMatch ? shortcodeMatch[1] : postUrl;
                
                console.log('Generated post key:', postKey, 'from URL:', postUrl);
                return postKey;
            }

            async function extractMediaFromPost(article) {
                console.log('Extracting media from post');
                
                // 포스트 고유 키 생성
                const postKey = generatePostKey(article);
                
                // 캐시에서 확인
                try {
                    const cachedData = Android.getCachedMedia(postKey);
                    if (cachedData && cachedData !== 'null') {
                        console.log('🎯 Found cached media for post:', postKey);
                        return cachedData.split('||').map(item => {
                            const parts = item.split('::', 2);
                            return parts.length === 2 ? parts[0] + '::' + parts[1] : item;
                        });
                    }
                } catch (e) {
                    console.log('Cache check failed:', e);
                }
                
                const mediaItems = [];
                
                // 메인 컨테이너 찾기 (article > div > div[1])
                const divs = article.querySelectorAll(':scope > div');
                let mainContainer = null;
                
                if (divs.length >= 1) {
                    const childDivs = divs[0].querySelectorAll(':scope > div');
                    if (childDivs.length >= 2) {
                        mainContainer = childDivs[1]; // 두 번째 div가 메인 컨테이너
                    }
                }
                
                if (!mainContainer) {
                    mainContainer = article;
                }
                
                console.log('Main container found');
                
                // 캐러셀 확인 (button[aria-label] 존재 여부)
                const carouselButtons = mainContainer.querySelectorAll('button[aria-label]');
                const isCarousel = carouselButtons.length > 0;
                
                console.log('Is carousel: ' + isCarousel);
                
                if (isCarousel) {
                    // 캐러셀 처리 (실시간 업데이트)
                    await extractCarouselMediaWithUpdates(mainContainer, mediaItems);
                } else {
                    // 단일 이미지/영상 처리
                    extractSingleMedia(mainContainer, mediaItems);
                }
                
                console.log('Total media found: ' + mediaItems.length);
                
                // 수집 완료 후 캐시에 저장
                if (mediaItems.length > 0) {
                    try {
                        Android.setCachedMedia(postKey, mediaItems.join('||'));
                        console.log('💾 Media cached for post:', postKey);
                    } catch (e) {
                        console.log('Failed to cache media:', e);
                    }
                }
                
                return mediaItems;
            }
            
            function extractSingleMedia(container, mediaItems) {
                const images = container.querySelectorAll('img[src*="scontent"]');
                images.forEach(img => {
                    if (!img.src.includes('profile')) {
                        mediaItems.push('image::' + img.src);
                    }
                });
                
                // 동영상은 건너뛰기 (더 이상 처리하지 않음)
                console.log('동영상 감지됨 - 건너뛰기');
            }
            
            async function extractCarouselMediaWithUpdates(container, mediaItems) {
                console.log('Processing carousel with real-time updates');
                
                let currentIndex = 0;
                let maxAttempts = 25;
                let consecutiveFailures = 0;
                const maxConsecutiveFailures = 8;
                let hasCollectedInThisIteration = false;
                
                // 처음 한번만 다음 버튼을 찾기 (aria-label 속성 존재 + 오른쪽 위치)
                let nextButton = null;
                const buttons = container.querySelectorAll('button[aria-label]');
                
                for (let btn of buttons) {
                    const rect = btn.getBoundingClientRect();
                    const containerRect = container.getBoundingClientRect();
                    
                    // 오른쪽 절반에 있고 보이는 버튼 = 다음 버튼
                    if (rect.left > containerRect.left + containerRect.width / 2 && 
                        rect.width > 0 && rect.height > 0) {
                        nextButton = btn;
                        console.log('🎯 Found next button:', nextButton);
                        break;
                    }
                }
                
                if (!nextButton) {
                    console.log('❌ No next button found - single media');
                    return mediaItems;
                }
                
                while (currentIndex < maxAttempts && consecutiveFailures < maxConsecutiveFailures) {
                    try {
                        console.log('Carousel step ' + (currentIndex + 1));
                        
                        const ul = container.querySelector('ul');
                        if (!ul) {
                            console.log('No ul found');
                            break;
                        }
                        
                        const lis = ul.querySelectorAll('li');
                        hasCollectedInThisIteration = false;
                        
                        // 모든 li 요소를 확인하여 현재 보이는 미디어 찾기
                        lis.forEach((li, liIndex) => {
                            if (liIndex === 0) return; // 첫 번째 li는 무시
                            
                            const rect = li.getBoundingClientRect();
                            if (rect.width > 100 && rect.height > 100) {
                                // 이미지 확인
                                const img = li.querySelector('img[src*="scontent"]');
                                if (img && !img.src.includes('profile')) {
                                    const isDuplicate = mediaItems.some(item => item.includes(img.src));
                                    if (!isDuplicate) {
                                        mediaItems.push('image::' + img.src);
                                        hasCollectedInThisIteration = true;
                                    }
                                }
                                
                                // 비디오는 건너뛰기 (수집하지 않지만 계속 진행)
                                const video = li.querySelector('video');
                                if (video) {
                                    console.log('캐러셀에서 동영상 감지됨 - 건너뛰고 계속 진행');
                                    hasCollectedInThisIteration = true; // 진행했다고 표시하여 루프 지속
                                }
                            }
                        });
                        
                        // 새 미디어를 찾았으면 바로 UI 업데이트
                        if (hasCollectedInThisIteration) {
                            try {
                                Android.updateMediaList(mediaItems.join('||'));
                                console.log('📱 Updated UI with ' + mediaItems.length + ' media items');
                            } catch (e) {
                                console.log('Failed to update UI: ' + e);
                            }
                            consecutiveFailures = 0;
                        } else {
                            consecutiveFailures++;
                        }
                        
                        // 기존에 찾아둔 다음 버튼이 여전히 유효한지 체크
                        if (!nextButton || !nextButton.offsetParent || nextButton.disabled) {
                            console.log('🏁 Next button no longer available - final collection');
                            break;
                        }
                        
                        nextButton.click();
                        await new Promise(resolve => setTimeout(resolve, 100));
                        currentIndex++;
                        
                    } catch (e) {
                        console.log('❌ Error in carousel step ' + currentIndex + ': ' + e);
                        consecutiveFailures++;
                        currentIndex++;
                    }
                }
                
                console.log('🎯 Carousel complete. Total: ' + mediaItems.length + ' media');
            }
            
            // 즉시 실행
            setTimeout(addDownloadButtons, 1000);
            
            // 주기적으로 체크 (더 자주)
            setInterval(addDownloadButtons, 2000);
            
            // 스크롤 이벤트 리스너
            let scrollTimeout;
            window.addEventListener('scroll', function() {
                clearTimeout(scrollTimeout);
                scrollTimeout = setTimeout(addDownloadButtons, 300);
            });
            
            // DOM 변경 감지 (Instagram이 동적으로 콘텐츠를 로드하므로)
            const observer = new MutationObserver(function(mutations) {
                let shouldCheck = false;
                mutations.forEach(function(mutation) {
                    if (mutation.addedNodes && mutation.addedNodes.length > 0) {
                        for (let i = 0; i < mutation.addedNodes.length; i++) {
                            const node = mutation.addedNodes[i];
                            if (node.nodeType === 1 && (node.tagName === 'ARTICLE' || node.querySelector('article'))) {
                                shouldCheck = true;
                                break;
                            }
                        }
                    }
                });
                
                if (shouldCheck) {
                    setTimeout(addDownloadButtons, 500);
                }
            });
            
            observer.observe(document.body, {
                childList: true,
                subtree: true
            });
            
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

        // Unselect All 버튼
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = {
                    onSelectionChange(mediaItems.map { it.copy(isSelected = false) })
                }
            ) {
                Text("전체 해제")
            }
        }

        // 미디어 그리드
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(mediaItems.size) { index ->
                val item = mediaItems[index]
                val isSelected = selectedItems.any { it.url == item.url && it.isSelected }

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
                            val updatedItems = selectedItems.toMutableList()
                            val existingIndex = updatedItems.indexOfFirst { it.url == item.url }
                            if (existingIndex >= 0) {
                                updatedItems[existingIndex] = updatedItems[existingIndex].copy(isSelected = !isSelected)
                            } else {
                                updatedItems.add(item.copy(isSelected = true))
                            }
                            onSelectionChange(updatedItems)
                        }
                ) {
                    if (item.url == "loading") {
                        // 로딩 상태 표시
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    strokeWidth = 3.dp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "수집 중...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        AsyncImage(
                            model = item.url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // 선택 체크박스 (우상단)
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }

                    // 비디오 표시 (좌하단)
                    if (item.type == "video") {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.7f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "비디오",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 다운로드 버튼
        Button(
            onClick = {
                val selected = selectedItems.filter { it.isSelected }
                if (selected.isNotEmpty()) {
                    onDownload(selected)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedItems.any { it.isSelected }
        ) {
            Text("다운로드 (${selectedItems.count { it.isSelected }}개)")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}