package com.example.instadownloader.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Message
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebBrowserScreen() {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Instagram WebView with popup and multi-window support
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        @Suppress("DEPRECATION")
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT

                        // ✅ 팝업/새창 허용 (회색 오버레이 원인 해소)
                        setSupportMultipleWindows(true)
                        javaScriptCanOpenWindowsAutomatically = true

                        // ✅ Instagram 웹뷰 차단 우회 (wv 토큰 완전 제거)
                        val defaultUA = WebSettings.getDefaultUserAgent(context)
                        userAgentString = defaultUA
                            .replace("; wv", "")              // WebView 토큰 제거
                            .replace("Version/4.0", "Version/4.0 Chrome/131.0.0.0") // 브라우저 시그니처 강화

                        // ✅ 파일 및 콘텐츠 접근 (Android 13+ 쿠키 처리에 중요)
                        allowFileAccess = false
                        allowContentAccess = true
                        @Suppress("DEPRECATION")
                        allowFileAccessFromFileURLs = false
                        @Suppress("DEPRECATION")
                        allowUniversalAccessFromFileURLs = false

                        // 미디어 자동재생 허용
                        mediaPlaybackRequiresUserGesture = false

                        // 모바일 반응형 뷰포트 설정
                        loadWithOverviewMode = false
                        useWideViewPort = true
                        setSupportZoom(true)
                        builtInZoomControls = false
                        displayZoomControls = false

                        // 보안 설정 (권장: 혼합콘텐츠 금지)
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                        // 모바일 최적화
                        layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                        textZoom = 100
                    }

                    // ✅ 팝업 창을 현재 WebView로 라우팅
                    webChromeClient = object : WebChromeClient() {
                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?
                        ): Boolean {
                            val transport = resultMsg?.obj as? WebView.WebViewTransport
                            // 새 창 대신 현재 WebView 재사용
                            transport?.webView = this@apply
                            resultMsg?.sendToTarget()
                            return true
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Instagram 페이지가 로드된 후 스크립트 주입
                            if (url?.contains("instagram.com") == true) {
                                injectOverlayDetectionScript(view)
                                injectDownloadButtonScript(view)
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url ?: return false
                            val scheme = url.scheme ?: return false

                            if (scheme == "http" || scheme == "https") {
                                val host = url.host.orEmpty()

                                // ✅ 확장된 인스타/메타 도메인 화이트리스트 (로그인/SSO 과정 포함)
                                val allowedDomains = listOf(
                                    // Instagram 코어 도메인
                                    "instagram.com", "www.instagram.com", "m.instagram.com",
                                    "i.instagram.com", "help.instagram.com",

                                    // Instagram CDN 및 미디어 도메인
                                    "cdninstagram.com", "static.cdninstagram.com",
                                    "scontent-ssn1-1.cdninstagram.com", "scontent.cdninstagram.com",

                                    // Instagram 링크 및 리다이렉션
                                    "l.instagram.com", "link.instagram.com",

                                    // Facebook/Meta 계열 (SSO, Accounts Center)
                                    "facebook.com", "www.facebook.com", "m.facebook.com",
                                    "accountscenter.instagram.com", "accountscenter.facebook.com",
                                    "meta.com", "www.meta.com",

                                    // Facebook CDN
                                    "fbcdn.net", "static.xx.fbcdn.net", "static.cdninstagram.com",
                                    "scontent.xx.fbcdn.net", "external.xx.fbcdn.net"
                                )

                                // 인스타/메타 계열은 WebView 내에서 처리 (가급적 제한 없이)
                                if (allowedDomains.any { host == it || host.endsWith(".$it") }) {
                                    return false // WebView에서 처리
                                }

                                // ✅ http/https는 가급적 가로채지 않고 WebView에서 처리 (안전한 접근)
                                // 단, 명백히 외부 사이트인 경우만 외부 브라우저로
                                val blockedDomains = listOf(
                                    "google.com", "youtube.com", "twitter.com", "tiktok.com",
                                    "amazon.com", "apple.com", "microsoft.com", "github.com"
                                )

                                if (blockedDomains.any { host == it || host.endsWith(".$it") }) {
                                    return try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, url))
                                        true
                                    } catch (_: Exception) {
                                        false // 실패 시 WebView에서 처리
                                    }
                                }

                                // ✅ 기타 http/https는 WebView에서 처리 (도메인 필터 과도 제한 금지)
                                return false
                            }

                            // mailto:, tel:, intent: 등은 외부로
                            return try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, url))
                                true
                            } catch (_: Exception) {
                                true
                            }
                        }
                    }

                    // ✅ 쿠키 매니저 설정 (Android 13+ 중요 - 제3자 쿠키 허용)
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    // Android 13에서 쿠키 동의/SSO에 필수
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    // 쿠키 동기화 (세션 유지에 중요)
                    cookieManager.flush()

                    loadUrl("https://www.instagram.com/accounts/login/")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// 회색 오버레이 감지 및 자동 처리 스크립트
private fun injectOverlayDetectionScript(webView: WebView?) {
    webView?.evaluateJavascript(
        """
        (function() {
            console.log('Instagram overlay detection script started...');
            
            // 회색 오버레이/모달 감지 및 처리 함수
            function detectAndHandleOverlays() {
                // Instagram에서 자주 사용하는 오버레이/모달 셀렉터들
                const overlaySelectors = [
                    // 일반적인 오버레이/모달 패턴
                    'div[role="dialog"]',
                    'div[role="presentation"]', 
                    'div[aria-modal="true"]',
                    
                    // 회색 배경 오버레이 패턴
                    'div[style*="position: fixed"][style*="background"]',
                    'div[style*="position: absolute"][style*="background"]',
                    'div[class*="modal"]',
                    'div[class*="overlay"]',
                    'div[class*="backdrop"]',
                    
                    // Instagram 특정 패턴들
                    'div[style*="z-index"][style*="rgba"]',
                    'div[style*="position: fixed"][style*="top: 0"][style*="left: 0"]'
                ];
                
                let handledOverlays = 0;
                
                overlaySelectors.forEach(selector => {
                    const overlays = document.querySelectorAll(selector);
                    
                    overlays.forEach(overlay => {
                        // 이미 처리된 오버레이는 스킵
                        if (overlay.dataset.overlayHandled) return;
                        
                        const style = window.getComputedStyle(overlay);
                        const rect = overlay.getBoundingClientRect();
                        
                        // 오버레이 감지 조건들
                        const isOverlay = (
                            // 전체 화면을 덮는 요소
                            (rect.width >= window.innerWidth * 0.8 && rect.height >= window.innerHeight * 0.8) ||
                            // fixed/absolute positioning with high z-index
                            ((style.position === 'fixed' || style.position === 'absolute') && 
                             parseInt(style.zIndex) > 1000) ||
                            // 배경이 있는 투명/반투명 요소
                            (style.backgroundColor && style.backgroundColor.includes('rgba'))
                        );
                        
                        if (isOverlay) {
                            console.log('Detected overlay:', selector, overlay);
                            handleOverlay(overlay);
                            overlay.dataset.overlayHandled = 'true';
                            handledOverlays++;
                        }
                    });
                });
                
                if (handledOverlays > 0) {
                    console.log('Handled', handledOverlays, 'overlays');
                    showOverlayNotification('감지된 오버레이 ' + handledOverlays + '개를 처리했습니다');
                }
            }
            
            // 오버레이 처리 함수
            function handleOverlay(overlay) {
                // 1. 클릭 가능한 버튼 찾기
                const clickableElements = overlay.querySelectorAll('button, [role="button"], a, [onclick]');
                
                // 2. 닫기 버튼 패턴 찾기
                const closeButtons = Array.from(clickableElements).filter(el => {
                    const text = el.textContent?.toLowerCase() || '';
                    const ariaLabel = el.getAttribute('aria-label')?.toLowerCase() || '';
                    return text.includes('close') || text.includes('닫기') || text.includes('확인') ||
                           ariaLabel.includes('close') || ariaLabel.includes('dismiss') ||
                           el.querySelector('svg') || text.includes('×') || text.includes('✕');
                });
                
                if (closeButtons.length > 0) {
                    // 닫기 버튼 자동 클릭
                    console.log('Auto-clicking close button:', closeButtons[0]);
                    closeButtons[0].click();
                    return;
                }
                
                // 3. 확인/동의 버튼 찾기
                const confirmButtons = Array.from(clickableElements).filter(el => {
                    const text = el.textContent?.toLowerCase() || '';
                    return text.includes('확인') || text.includes('동의') || text.includes('accept') || 
                           text.includes('continue') || text.includes('ok') || text.includes('allow');
                });
                
                if (confirmButtons.length > 0) {
                    console.log('Auto-clicking confirm button:', confirmButtons[0]);
                    confirmButtons[0].click();
                    return;
                }
                
                // 4. 오버레이 외부 클릭으로 닫기
                const overlayBackground = overlay.querySelector('div[style*="position: fixed"]');
                if (overlayBackground && !overlayBackground.querySelector('button, input, textarea')) {
                    console.log('Clicking overlay background to dismiss');
                    overlayBackground.click();
                    return;
                }
                
                // 5. 마지막 수단: 오버레이 숨기기
                console.log('Hiding problematic overlay');
                overlay.style.display = 'none';
                overlay.style.visibility = 'hidden';
                overlay.style.opacity = '0';
                overlay.style.pointerEvents = 'none';
            }
            
            // 오버레이 처리 알림
            function showOverlayNotification(message) {
                const notification = document.createElement('div');
                notification.innerHTML = message;
                notification.style.cssText = 
                    'position: fixed;' +
                    'top: 70px;' +
                    'left: 50%;' +
                    'transform: translateX(-50%);' +
                    'background: rgba(33, 150, 243, 0.9);' +
                    'color: white;' +
                    'padding: 12px 20px;' +
                    'border-radius: 20px;' +
                    'z-index: 99999;' +
                    'font-size: 14px;' +
                    'font-weight: 500;' +
                    'box-shadow: 0 4px 12px rgba(0,0,0,0.3);' +
                    'max-width: 90vw;' +
                    'text-align: center;';
                
                document.body.appendChild(notification);
                
                setTimeout(() => {
                    if (notification.parentNode) {
                        notification.style.opacity = '0';
                        setTimeout(() => {
                            notification.parentNode.removeChild(notification);
                        }, 300);
                    }
                }, 2000);
            }
            
            // 초기 오버레이 검사
            detectAndHandleOverlays();
            
            // 주기적 오버레이 검사 (로그인 후 나타나는 오버레이 대응)
            let overlayCheckInterval = setInterval(detectAndHandleOverlays, 2000);
            
            // DOM 변화 감지로 새로운 오버레이 실시간 처리
            const overlayObserver = new MutationObserver(function(mutations) {
                let hasNewOverlay = false;
                
                mutations.forEach(function(mutation) {
                    if (mutation.addedNodes.length > 0) {
                        mutation.addedNodes.forEach(function(node) {
                            if (node.nodeType === 1) {
                                // 새로 추가된 요소가 오버레이일 가능성 체크
                                const style = window.getComputedStyle(node);
                                if (style.position === 'fixed' || style.position === 'absolute' ||
                                    node.getAttribute('role') === 'dialog' ||
                                    node.getAttribute('aria-modal') === 'true') {
                                    hasNewOverlay = true;
                                }
                            }
                        });
                    }
                });
                
                if (hasNewOverlay) {
                    console.log('New overlay detected via DOM mutation');
                    setTimeout(detectAndHandleOverlays, 500);
                }
            });
            
            overlayObserver.observe(document.body, {
                childList: true,
                subtree: true
            });
            
            // 페이지 전환 시 감지 중지
            window.addEventListener('beforeunload', function() {
                clearInterval(overlayCheckInterval);
                overlayObserver.disconnect();
            });
            
            console.log('Instagram overlay detection and auto-handling initialized');
        })();
        """.trimIndent(),
        null
    )
}

private fun injectDownloadButtonScript(webView: WebView?) {
    webView?.evaluateJavascript(
        """
        (function() {
            console.log('Starting Instagram download button injection...');
            
            function createDownloadButton() {
                const button = document.createElement('div');
                button.innerHTML = '⬇';
                button.style.cssText = `
                    position: absolute;
                    top: 12px;
                    left: 12px;
                    width: 44px;
                    height: 44px;
                    background: rgba(0, 0, 0, 0.8);
                    color: white;
                    border-radius: 50%;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    cursor: pointer;
                    z-index: 9999;
                    font-size: 20px;
                    font-weight: bold;
                    box-shadow: 0 4px 12px rgba(0,0,0,0.4);
                    transition: all 0.2s ease;
                    user-select: none;
                    touch-action: manipulation;
                    -webkit-tap-highlight-color: transparent;
                `;
                
                // 터치 이벤트 최적화 (모바일)
                button.ontouchstart = function(e) {
                    e.preventDefault();
                    this.style.transform = 'scale(1.1)';
                    this.style.background = 'rgba(25, 118, 210, 0.9)';
                };
                
                button.ontouchend = function(e) {
                    e.preventDefault();
                    this.style.transform = 'scale(1)';
                    this.style.background = 'rgba(0, 0, 0, 0.8)';
                };
                
                // 마우스 이벤트 (데스크톱 호환)
                button.onmouseenter = function() {
                    this.style.transform = 'scale(1.1)';
                    this.style.background = 'rgba(25, 118, 210, 0.9)';
                };
                
                button.onmouseleave = function() {
                    this.style.transform = 'scale(1)';
                    this.style.background = 'rgba(0, 0, 0, 0.8)';
                };
                
                return button;
            }
            
            function extractMediaFromPost(article) {
                console.log('Extracting media from post...');
                const mediaUrls = new Set();
                
                // 단일 이미지/비디오 추출
                const images = article.querySelectorAll('img');
                images.forEach(img => {
                    const src = img.src || img.currentSrc;
                    if (src && (src.includes('cdninstagram') || src.includes('fbcdn'))) {
                        // 고해상도 이미지 URL로 변환
                        const highResUrl = src.replace(/s\d{3,4}x\d{3,4}/, 's1080x1080');
                        mediaUrls.add(highResUrl);
                        console.log('Found image:', highResUrl);
                    }
                });
                
                // 비디오 추출
                const videos = article.querySelectorAll('video');
                videos.forEach(video => {
                    const src = video.src || video.currentSrc;
                    if (src && (src.includes('cdninstagram') || src.includes('fbcdn'))) {
                        mediaUrls.add(src);
                        console.log('Found video:', src);
                    }
                });
                
                return Array.from(mediaUrls);
            }
            
            // 모바일 최적화된 알림 함수
            function showMobileNotification(message, type) {
                const notification = document.createElement('div');
                notification.innerHTML = message;
                
                const bgColor = type === 'error' ? 'rgba(244, 67, 54, 0.9)' : 'rgba(76, 175, 80, 0.9)';
                
                notification.style.cssText = 
                    'position: fixed;' +
                    'top: 20px;' +
                    'left: 50%;' +
                    'transform: translateX(-50%);' +
                    'background: ' + bgColor + ';' +
                    'color: white;' +
                    'padding: 16px 24px;' +
                    'border-radius: 28px;' +
                    'z-index: 10000;' +
                    'font-size: 16px;' +
                    'font-weight: 500;' +
                    'box-shadow: 0 4px 12px rgba(0,0,0,0.3);' +
                    'max-width: 90vw;' +
                    'text-align: center;' +
                    'animation: slideDown 0.3s ease-out;';
                
                // CSS 애니메이션 정의
                if (!document.getElementById('mobile-notification-styles')) {
                    const style = document.createElement('style');
                    style.id = 'mobile-notification-styles';
                    style.textContent = 
                        '@keyframes slideDown {' +
                        'from { opacity: 0; transform: translateX(-50%) translateY(-20px); }' +
                        'to { opacity: 1; transform: translateX(-50%) translateY(0); }' +
                        '}';
                    document.head.appendChild(style);
                }
                
                document.body.appendChild(notification);
                
                setTimeout(() => {
                    if (notification.parentNode) {
                        notification.style.opacity = '0';
                        notification.style.transform = 'translateX(-50%) translateY(-20px)';
                        setTimeout(() => {
                            notification.parentNode.removeChild(notification);
                        }, 300);
                    }
                }, type === 'error' ? 2000 : 3000);
            }
            
            function simulateDownload(mediaUrls) {
                console.log('Simulating download for', mediaUrls.length, 'media items');
                
                // 각 미디어 URL에 대해 다운로드 시뮬레이션
                mediaUrls.forEach((url, index) => {
                    setTimeout(() => {
                        // Create a temporary link to trigger download
                        const link = document.createElement('a');
                        link.href = url;
                        link.download = 'instagram_media_' + Date.now() + '_' + index;
                        link.style.display = 'none';
                        document.body.appendChild(link);
                        
                        // Trigger download (note: this may not work due to CORS)
                        try {
                            link.click();
                        } catch (e) {
                            console.log('Direct download failed, copying URL to clipboard instead');
                            
                            // 다운로드 실패 시 URL을 콘솔에 출력
                            console.log('Media URL ' + (index + 1) + ':', url);
                        }
                        
                        document.body.removeChild(link);
                    }, index * 500); // Stagger downloads
                });
                
                // Show mobile-optimized notification
                showMobileNotification('찾은 미디어 ' + mediaUrls.length + '개를 다운로드 중...', 'success');
            }
            
            function addDownloadButtons() {
                const articles = document.querySelectorAll('article');
                console.log('Found', articles.length, 'articles');
                
                articles.forEach((article, index) => {
                    // 이미 버튼이 있으면 스킵
                    if (article.querySelector('.insta-download-btn')) {
                        return;
                    }
                    
                    // 미디어 컨테이너 찾기 (Instagram의 구조에 따라)
                    const mediaContainer = article.querySelector('div > div:nth-child(2)') || 
                                         article.querySelector('div[role="button"]')?.closest('div') ||
                                         article.querySelector('img, video')?.closest('div');
                    
                    if (!mediaContainer) {
                        console.log('Media container not found for article', index);
                        return;
                    }
                    
                    const downloadBtn = createDownloadButton();
                    downloadBtn.classList.add('insta-download-btn');
                    
                    // 터치와 클릭 이벤트 모두 지원 (모바일 최적화)
                    function handleDownloadClick(e) {
                        e.preventDefault();
                        e.stopPropagation();
                        
                        // 터치 피드백
                        downloadBtn.style.transform = 'scale(0.95)';
                        setTimeout(() => {
                            downloadBtn.style.transform = 'scale(1)';
                        }, 100);
                        
                        console.log('Download button clicked for article', index);
                        const mediaUrls = extractMediaFromPost(article);
                        
                        if (mediaUrls.length > 0) {
                            simulateDownload(mediaUrls);
                        } else {
                            console.log('No media found in this post');
                            showMobileNotification('이 포스트에서 미디어를 찾을 수 없습니다', 'error');
                        }
                    }
                    
                    // 터치 및 클릭 이벤트 등록
                    downloadBtn.addEventListener('touchend', handleDownloadClick, { passive: false });
                    downloadBtn.addEventListener('click', handleDownloadClick);
                    
                    // Position the button
                    const parentStyle = window.getComputedStyle(mediaContainer);
                    if (parentStyle.position === 'static') {
                        mediaContainer.style.position = 'relative';
                    }
                    
                    mediaContainer.appendChild(downloadBtn);
                    console.log('Download button added to article', index);
                });
            }
            
            // 초기 버튼 추가
            addDownloadButtons();
            
            // 스크롤 및 페이지 변경 감지
            let buttonUpdateTimeout;
            function scheduleButtonUpdate() {
                clearTimeout(buttonUpdateTimeout);
                buttonUpdateTimeout = setTimeout(addDownloadButtons, 1000);
            }
            
            // 스크롤 이벤트
            window.addEventListener('scroll', scheduleButtonUpdate);
            
            // DOM 변화 감지
            const observer = new MutationObserver(function(mutations) {
                let shouldUpdate = false;
                mutations.forEach(function(mutation) {
                    if (mutation.addedNodes.length > 0) {
                        for (let node of mutation.addedNodes) {
                            if (node.nodeType === 1 && (node.tagName === 'ARTICLE' || node.querySelector('article'))) {
                                shouldUpdate = true;
                                break;
                            }
                        }
                    }
                });
                
                if (shouldUpdate) {
                    console.log('New articles detected, updating buttons...');
                    scheduleButtonUpdate();
                }
            });
            
            observer.observe(document.body, {
                childList: true,
                subtree: true
            });
            
            console.log('Instagram download button script injection completed');
        })();
        """.trimIndent(),
        null
    )
}