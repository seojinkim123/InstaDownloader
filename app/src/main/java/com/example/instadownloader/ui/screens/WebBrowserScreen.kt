package com.example.instadownloader.ui.screens

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.util.Log
import android.widget.Toast
import android.os.Handler
import android.os.Looper
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
        }
        
        HorizontalDivider()
        
        // 웹뷰
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            isLoading = true
                        }
                        
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            canGoBack = view?.canGoBack() ?: false
                            
                            // Instagram 포스트 감지 및 다운로드 버튼 추가 JavaScript 주입
                            view?.evaluateJavascript("""
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
                                            downloadBtn.innerHTML = '📥';
                                            downloadBtn.style.cssText = 
                                                'position: absolute !important;' +
                                                'top: 12px !important;' +
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
                                                    this.innerHTML = '📥';
                                                    this.style.pointerEvents = 'auto';
                                                }
                                            });
                                            
                                            article.appendChild(downloadBtn);
                                        });
                                    }
                                    
                                    async function extractMediaFromPost(article) {
                                        console.log('Extracting media from post');
                                        
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
                                        return mediaItems;
                                    }
                                    
                                    function extractSingleMedia(container, mediaItems) {
                                        const images = container.querySelectorAll('img[src*="scontent"]');
                                        images.forEach(img => {
                                            if (!img.src.includes('profile')) {
                                                mediaItems.push('image::' + img.src);
                                            }
                                        });
                                        
                                        const videos = container.querySelectorAll('video');
                                        videos.forEach(video => {
                                            if (video.src) {
                                                mediaItems.push('video::' + video.src);
                                            }
                                        });
                                    }
                                    
                                    async function extractCarouselMediaWithUpdates(container, mediaItems) {
                                        console.log('Processing carousel with real-time updates');
                                        
                                        let currentIndex = 0;
                                        let maxAttempts = 25;
                                        let consecutiveFailures = 0;
                                        const maxConsecutiveFailures = 8;
                                        let hasCollectedInThisIteration = false;
                                        
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
                                                        
                                                        // 비디오 확인
                                                        const video = li.querySelector('video');
                                                        if (video) {
                                                            let videoUrl = video.src;
                                                            if (!videoUrl) {
                                                                const source = video.querySelector('source');
                                                                if (source) videoUrl = source.src;
                                                            }
                                                            
                                                            if (videoUrl && !mediaItems.some(item => item.includes(videoUrl))) {
                                                                const videoType = videoUrl.startsWith('blob:') ? 'video-blob' : 'video';
                                                                mediaItems.push(videoType + '::' + videoUrl);
                                                                hasCollectedInThisIteration = true;
                                                            }
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
                                                
                                                // 다음 버튼 찾기
                                                const nextButtons = container.querySelectorAll('button[aria-label]');
                                                let nextButton = null;
                                                
                                                for (let btn of nextButtons) {
                                                    const ariaLabel = btn.getAttribute('aria-label');
                                                    const rect = btn.getBoundingClientRect();
                                                    const containerRect = container.getBoundingClientRect();
                                                    
                                                    if (rect.left > containerRect.left + containerRect.width / 2 && 
                                                        rect.width > 0 && rect.height > 0 &&
                                                        (ariaLabel && (ariaLabel.includes('Next') || ariaLabel.includes('다음') || ariaLabel.includes('넘기')))) {
                                                        nextButton = btn;
                                                        break;
                                                    }
                                                }
                                                
                                                if (!nextButton) {
                                                    console.log('🏁 No more next button found - final collection');
                                                    
                                                    // 마지막 수집
                                                    const finalUl = container.querySelector('ul');
                                                    if (finalUl) {
                                                        const finalLis = finalUl.querySelectorAll('li');
                                                        let finalCollected = false;
                                                        
                                                        finalLis.forEach((li, liIndex) => {
                                                            if (liIndex === 0) return;
                                                            
                                                            const rect = li.getBoundingClientRect();
                                                            if (rect.width > 100 && rect.height > 100) {
                                                                const img = li.querySelector('img[src*="scontent"]');
                                                                if (img && !img.src.includes('profile')) {
                                                                    const isDuplicate = mediaItems.some(item => item.includes(img.src));
                                                                    if (!isDuplicate) {
                                                                        mediaItems.push('image::' + img.src);
                                                                        finalCollected = true;
                                                                    }
                                                                }
                                                                
                                                                const video = li.querySelector('video');
                                                                if (video) {
                                                                    let videoUrl = video.src;
                                                                    if (!videoUrl) {
                                                                        const source = video.querySelector('source');
                                                                        if (source) videoUrl = source.src;
                                                                    }
                                                                    
                                                                    if (videoUrl && !mediaItems.some(item => item.includes(videoUrl))) {
                                                                        const videoType = videoUrl.startsWith('blob:') ? 'video-blob' : 'video';
                                                                        mediaItems.push(videoType + '::' + videoUrl);
                                                                        finalCollected = true;
                                                                    }
                                                                }
                                                            }
                                                        });
                                                        
                                                        if (finalCollected) {
                                                            try {
                                                                Android.updateMediaList(mediaItems.join('||'));
                                                            } catch (e) {}
                                                        }
                                                    }
                                                    
                                                    break;
                                                }
                                                
                                                nextButton.click();
                                                await new Promise(resolve => setTimeout(resolve, 100)); // 초고속!
                                                currentIndex++;
                                                
                                            } catch (e) {
                                                console.log('❌ Error in carousel step ' + currentIndex + ': ' + e);
                                                consecutiveFailures++;
                                                currentIndex++;
                                            }
                                        }
                                        
                                        console.log('🎯 Carousel complete. Total: ' + mediaItems.length + ' media');
                                    }
                                    
                                    async function extractCarouselMedia(container, mediaItems) {
                                        console.log('Processing carousel');
                                        
                                        let currentIndex = 0;
                                        let maxAttempts = 25;
                                        let consecutiveFailures = 0;
                                        const maxConsecutiveFailures = 8;
                                        let hasCollectedInThisIteration = false;
                                        
                                        while (currentIndex < maxAttempts && consecutiveFailures < maxConsecutiveFailures) {
                                            try {
                                                console.log('Carousel step ' + (currentIndex + 1));
                                                
                                                const ul = container.querySelector('ul');
                                                if (!ul) {
                                                    console.log('No ul found');
                                                    break;
                                                }
                                                
                                                const lis = ul.querySelectorAll('li');
                                                console.log('Found ' + lis.length + ' li elements');
                                                
                                                // 현재 페이지에서 실제로 보이는 미디어 수집
                                                hasCollectedInThisIteration = false;
                                                
                                                // 모든 li 요소를 확인하여 현재 보이는 미디어 찾기
                                                lis.forEach((li, liIndex) => {
                                                    if (liIndex === 0) return; // 첫 번째 li는 무시
                                                    
                                                    const rect = li.getBoundingClientRect();
                                                    if (rect.width > 100 && rect.height > 100) {
                                                        console.log('Checking visible li at index ' + liIndex);
                                                        
                                                        // 이미지 확인
                                                        const img = li.querySelector('img[src*="scontent"]');
                                                        if (img && !img.src.includes('profile')) {
                                                            const isDuplicate = mediaItems.some(item => item.includes(img.src));
                                                            if (!isDuplicate) {
                                                                mediaItems.push('image::' + img.src);
                                                                console.log('✅ Added image: ' + img.src.substring(img.src.lastIndexOf('/') + 1, img.src.lastIndexOf('?') !== -1 ? img.src.lastIndexOf('?') : img.src.length));
                                                                hasCollectedInThisIteration = true;
                                                            }
                                                        }
                                                        
                                                        // 비디오 확인
                                                        const video = li.querySelector('video');
                                                        if (video) {
                                                            let videoUrl = video.src;
                                                            if (!videoUrl) {
                                                                const source = video.querySelector('source');
                                                                if (source) videoUrl = source.src;
                                                            }
                                                            
                                                            if (videoUrl && !mediaItems.some(item => item.includes(videoUrl))) {
                                                                const videoType = videoUrl.startsWith('blob:') ? 'video-blob' : 'video';
                                                                mediaItems.push(videoType + '::' + videoUrl);
                                                                console.log('✅ Added video: ' + (videoUrl.length > 50 ? videoUrl.substring(0, 50) + '...' : videoUrl));
                                                                hasCollectedInThisIteration = true;
                                                            }
                                                        }
                                                    }
                                                });
                                                
                                                if (hasCollectedInThisIteration) {
                                                    consecutiveFailures = 0;
                                                } else {
                                                    consecutiveFailures++;
                                                }
                                                
                                                // 다음 버튼 찾기
                                                const nextButtons = container.querySelectorAll('button[aria-label]');
                                                let nextButton = null;
                                                
                                                for (let btn of nextButtons) {
                                                    const ariaLabel = btn.getAttribute('aria-label');
                                                    const rect = btn.getBoundingClientRect();
                                                    const containerRect = container.getBoundingClientRect();
                                                    
                                                    // 오른쪽에 있고, 보이는 상태이며, Next 관련 레이블을 가진 버튼
                                                    if (rect.left > containerRect.left + containerRect.width / 2 && 
                                                        rect.width > 0 && rect.height > 0 &&
                                                        (ariaLabel && (ariaLabel.includes('Next') || ariaLabel.includes('다음') || ariaLabel.includes('넘기')))) {
                                                        nextButton = btn;
                                                        break;
                                                    }
                                                }
                                                
                                                if (!nextButton) {
                                                    console.log('🏁 No more next button found - this should be the last slide');
                                                    
                                                    // 마지막 슬라이드에서 한 번 더 미디어 수집 시도
                                                    console.log('Final collection attempt...');
                                                    const finalUl = container.querySelector('ul');
                                                    if (finalUl) {
                                                        const finalLis = finalUl.querySelectorAll('li');
                                                        finalLis.forEach((li, liIndex) => {
                                                            if (liIndex === 0) return;
                                                            
                                                            const rect = li.getBoundingClientRect();
                                                            if (rect.width > 100 && rect.height > 100) {
                                                                const img = li.querySelector('img[src*="scontent"]');
                                                                if (img && !img.src.includes('profile')) {
                                                                    const isDuplicate = mediaItems.some(item => item.includes(img.src));
                                                                    if (!isDuplicate) {
                                                                        mediaItems.push('image::' + img.src);
                                                                        console.log('✅ Final image added: ' + img.src.substring(img.src.lastIndexOf('/') + 1, img.src.lastIndexOf('?') !== -1 ? img.src.lastIndexOf('?') : img.src.length));
                                                                    }
                                                                }
                                                                
                                                                const video = li.querySelector('video');
                                                                if (video) {
                                                                    let videoUrl = video.src;
                                                                    if (!videoUrl) {
                                                                        const source = video.querySelector('source');
                                                                        if (source) videoUrl = source.src;
                                                                    }
                                                                    
                                                                    if (videoUrl && !mediaItems.some(item => item.includes(videoUrl))) {
                                                                        const videoType = videoUrl.startsWith('blob:') ? 'video-blob' : 'video';
                                                                        mediaItems.push(videoType + '::' + videoUrl);
                                                                        console.log('✅ Final video added');
                                                                    }
                                                                }
                                                            }
                                                        });
                                                    }
                                                    
                                                    break;
                                                }
                                                
                                                console.log('➡️ Clicking next button...');
                                                nextButton.click();
                                                await new Promise(resolve => setTimeout(resolve, 600));
                                                currentIndex++;
                                                
                                            } catch (e) {
                                                console.log('❌ Error in carousel step ' + currentIndex + ': ' + e);
                                                consecutiveFailures++;
                                                currentIndex++;
                                            }
                                        }
                                        
                                        console.log('🎯 Carousel processing complete. Total media: ' + mediaItems.length);
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
                                    
                                    // Blob URL을 Base64로 변환하는 함수
                                    async function blobToBase64(blobUrl) {
                                        try {
                                            console.log('Converting blob URL to base64: ' + blobUrl.substring(0, 50) + '...');
                                            
                                            const response = await fetch(blobUrl);
                                            if (!response.ok) {
                                                throw new Error('Failed to fetch blob: ' + response.status);
                                            }
                                            
                                            const blob = await response.blob();
                                            console.log('Blob size: ' + Math.round(blob.size / 1024) + ' KB');
                                            
                                            // 파일 크기 체크 (50MB 제한)
                                            if (blob.size > 50 * 1024 * 1024) {
                                                throw new Error('File too large: ' + Math.round(blob.size / 1024 / 1024) + 'MB (max 50MB)');
                                            }
                                            
                                            return new Promise((resolve, reject) => {
                                                const reader = new FileReader();
                                                reader.onloadend = () => {
                                                    console.log('Base64 conversion completed');
                                                    resolve(reader.result);
                                                };
                                                reader.onerror = () => {
                                                    reject(new Error('Failed to read blob as base64'));
                                                };
                                                reader.readAsDataURL(blob);
                                            });
                                        } catch (error) {
                                            console.log('Error converting blob to base64: ' + error.message);
                                            throw error;
                                        }
                                    }
                                    
                                    // Blob 비디오 처리 함수
                                    async function processBlobVideo(blobUrl, filename) {
                                        try {
                                            console.log('Processing blob video: ' + filename);
                                            
                                            // 사용자에게 처리 중임을 알림
                                            Android.notifyBlobProcessing(filename, 'start');
                                            
                                            const base64Data = await blobToBase64(blobUrl);
                                            
                                            // Base64 데이터를 Android로 전달
                                            Android.downloadBlobVideo(filename, base64Data);
                                            
                                        } catch (error) {
                                            console.log('Failed to process blob video: ' + error.message);
                                            Android.notifyBlobProcessing(filename, 'error::' + error.message);
                                        }
                                    }
                                    
                                    // Blob URL 처리를 위한 전역 함수로 노출
                                    window.processBlobVideo = processBlobVideo;
                                    
                                    console.log('Download script setup complete');
                                })();
                            """, null)
                        }
                    }
                    
                    // Console 로그 처리
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                            Log.d("WebView", "${consoleMessage.message()} -- From line ${consoleMessage.lineNumber()}")
                            return true
                        }
                    }
                    
                    // JavaScript 인터페이스 추가
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
                    
                    // 웹뷰 기본 설정
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                    
                    
                    loadUrl("https://www.instagram.com")
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
    
    // 바텀 시트
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