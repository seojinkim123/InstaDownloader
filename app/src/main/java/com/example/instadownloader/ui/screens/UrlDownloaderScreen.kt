package com.example.instadownloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.ui.res.painterResource
import com.example.instadownloader.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.instadownloader.InstagramScraper
import com.example.instadownloader.data.download.MediaDownloader
import com.example.instadownloader.data.model.MediaItem
import com.example.instadownloader.data.model.MediaType
import com.example.instadownloader.data.model.getMediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.instadownloader.utils.ToastUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlDownloaderScreen() {
    var urlText by remember { mutableStateOf("") }
    var mediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showMediaDialog by remember { mutableStateOf(false) }
    var selectedMediaItems by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }
    var showHelpDialog by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val downloader = remember { MediaDownloader(context) }
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 헤더
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = "StarSaver",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
            IconButton(
                onClick = { showHelpDialog = true },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.question_circle_svgrepo_com),
                    contentDescription = "Help",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = "Instagram Media Downloader",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            label = { Text("Enter Instagram URL") },
            placeholder = { Text("Paste post URL") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            trailingIcon = {
                IconButton(
                    onClick = {
                        val clipText = clipboardManager.getText()?.text
                        if (!clipText.isNullOrBlank()) {
                            urlText = clipText
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.paste_svgrepo_com),
                        contentDescription = "Paste"
                    )
                }
            }
        )
        
        Button(
            onClick = {
                if (urlText.isNotBlank()) {
                    coroutineScope.launch {
                        isLoading = true
                        errorMessage = null
                        mediaItems = emptyList()
                        
                        try {
                            // URL에서 포스트 ID 추출
                            val postId = extractPostIdFromUrl(urlText)
                            println("Extracted Post ID: $postId from URL: $urlText")
                            if (postId != null) {
                                val items = withContext(Dispatchers.IO) {
                                    InstagramScraper.scrapePostMedia(postId, "high")
                                }
                                
                                withContext(Dispatchers.Main) {
                                    mediaItems = items
                                    selectedMediaItems = items.indices.toSet() // 전체 선택을 디폴트로 설정
                                    isLoading = false
                                    if (items.isNotEmpty()) {
                                        showMediaDialog = true
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    errorMessage = "Please enter a valid Instagram URL"
                                    isLoading = false
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                errorMessage = e.message ?: "An error occurred while extracting media"
                                isLoading = false
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            enabled = urlText.isNotBlank() && !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isLoading) "Extracting..." else "Extract Media")
        }
        
        // 에러 메시지 표시
        errorMessage?.let { error ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = "Error: $error",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        
        // 빈 상태 표시
        if (!isLoading && urlText.isBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    InstructionSection()
                }
            }
        }
    }
    
    // 미디어 선택 다이얼로그
    if (showMediaDialog) {
        MediaSelectionDialog(
            mediaItems = mediaItems,
            selectedItems = selectedMediaItems,
            isDownloading = isDownloading,
            downloadProgress = downloadProgress,
            onSelectionChange = { index, isSelected ->
                selectedMediaItems = if (isSelected) {
                    selectedMediaItems + index
                } else {
                    selectedMediaItems - index
                }
            },
            onConfirm = {
                coroutineScope.launch {
                    isDownloading = true
                    downloadProgress = 0
                    
                    // WebView 메모리 보호 시작
                    WebViewManager.protectWebViewFromGC()
                    
                    val selectedItems = selectedMediaItems.map { index ->
                        mediaItems[index]
                    }
                    
                    val urls = selectedItems.map { it.url }
                    val isVideoList = selectedItems.map { it.type == MediaType.VIDEO }
                    val originalUrls = List(selectedItems.size) { urlText } // 모든 미디어의 원본 Instagram URL
                    val thumbnailUrls = selectedItems.map { if (it.type == MediaType.VIDEO) it.thumbnail else null }
                    
                    downloader.downloadMediaList(
                        urls,
                        isVideoList,
                        originalUrls,
                        thumbnailUrls,
                        onProgress = { current, _ ->
                            downloadProgress = current
                        }
                    ).fold(
                        onSuccess = { savedUris ->
                            isDownloading = false
                            showMediaDialog = false
                            // WebView 메모리 보호 해제
                            WebViewManager.releaseWebViewProtection()
                            ToastUtils.showDownloadComplete(context, savedUris.size)
                        },
                        onFailure = { error ->
                            isDownloading = false
                            // 실패 시에도 메모리 보호 해제
                            WebViewManager.releaseWebViewProtection()
                            ToastUtils.showDownloadError(context, error.message ?: "Unknown error")
                        }
                    )
                }
            },
            onDismiss = {
                showMediaDialog = false
            }
        )
    }
    
    // 도움말 다이얼로그
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("Important Information") },
            text = {
                Column {
                    Text("1. Please note that you can only log in with your Instagram account within the in-app browser (Facebook login is not supported).")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("2. You can download posts from private accounts or those with age restrictions after logging in within the in-app browser.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("3. If you make too many requests in a short period, your access may be temporarily limited.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showHelpDialog = false }
                ) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun MediaSelectionDialog(
    mediaItems: List<MediaItem>,
    selectedItems: Set<Int>,
    isDownloading: Boolean,
    downloadProgress: Int,
    onSelectionChange: (Int, Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select media to download (${selectedItems.size}/${mediaItems.size})",
                        modifier = Modifier.weight(1f)
                    )
                    
                    TextButton(
                        onClick = {
                            if (selectedItems.size == mediaItems.size) {
                                // 전체 해제
                                for (i in mediaItems.indices) {
                                    onSelectionChange(i, false)
                                }
                            } else {
                                // 전체 선택
                                for (i in mediaItems.indices) {
                                    if (!selectedItems.contains(i)) {
                                        onSelectionChange(i, true)
                                    }
                                }
                            }
                        },
                        enabled = !isDownloading
                    ) {
                        Text(
                            text = if (selectedItems.size == mediaItems.size) "Deselect All" else "Select All",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = downloadProgress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Downloading... $downloadProgress%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(400.dp)
            ) {
                itemsIndexed(mediaItems) { index, mediaItem ->
                    MediaSelectableCard(
                        mediaItem = mediaItem,
                        isSelected = selectedItems.contains(index),
                        enabled = !isDownloading,
                        onSelectionChange = { isSelected ->
                            if (!isDownloading) {
                                onSelectionChange(index, isSelected)
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selectedItems.isNotEmpty() && !isDownloading
            ) {
                if (isDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Download (${selectedItems.size})")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDownloading
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun MediaSelectableCard(
    mediaItem: MediaItem,
    isSelected: Boolean,
    enabled: Boolean = true,
    onSelectionChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .toggleable(
                value = isSelected,
                enabled = enabled,
                onValueChange = onSelectionChange
            ),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Box {
            AsyncImage(
                model = mediaItem.thumbnail ?: mediaItem.url,
                contentDescription = "Media",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // 체크박스
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChange,
                enabled = enabled,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurface
                )
            )
            
            // 미디어 타입 표시
            if (mediaItem.type == MediaType.VIDEO) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = "Video",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun InstructionSection() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            text = "Enter Instagram Post URL and press Extract Media",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Text(
            text = "How to get Instagram URL:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        // Step 1
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = "1. Click this share icon",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(id = R.drawable.share_1_svgrepo_com),
                contentDescription = "Share icon",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Step 2
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Text(
                text = "2. Click this link icon",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(id = R.drawable.link_minimalistic_2_svgrepo_com),
                contentDescription = "Link icon",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Step 3
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text(
                text = "3. Then URL is copied to clipboard",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        // Step 4
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "4. Click this paste icon or paste directly",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Icon(
                painter = painterResource(id = R.drawable.paste_svgrepo_com),
                contentDescription = "Paste icon",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun extractPostIdFromUrl(url: String): String? {
    // Instagram URL 패턴들:
    // https://www.instagram.com/p/POST_ID/
    // https://instagram.com/p/POST_ID/
    // https://www.instagram.com/reel/POST_ID/
    // https://instagram.com/reel/POST_ID/
    // URL에 ?img_index=1 같은 쿼리 파라미터가 있을 수 있음
    
    val patterns = listOf(
        Regex("instagram\\.com/p/([A-Za-z0-9_-]+)"),
        Regex("instagram\\.com/reel/([A-Za-z0-9_-]+)")
    )
    
    for (pattern in patterns) {
        val match = pattern.find(url)
        if (match != null) {
            return match.groupValues[1]
        }
    }
    
    return null
}