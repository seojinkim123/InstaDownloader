package com.example.instadownloader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
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
        Text(
            text = "Instagram URL 다운로더",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        OutlinedTextField(
            value = urlText,
            onValueChange = { urlText = it },
            label = { Text("Instagram URL을 입력하세요") },
            placeholder = { Text("https://www.instagram.com/p/...") },
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
                    Icon(Icons.Default.Add, contentDescription = "붙여넣기")
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
                                val urls = withContext(Dispatchers.IO) {
                                    InstagramScraper.scrapePostMedia(postId, "high")
                                }
                                
                                // URL 리스트를 MediaItem 리스트로 변환
                                val items: List<MediaItem> = urls.mapIndexed { index: Int, url: String ->
                                    MediaItem(
                                        url = url,
                                        type = url.getMediaType(),
                                        thumbnail = url
                                    )
                                }
                                
                                withContext(Dispatchers.Main) {
                                    mediaItems = items
                                    selectedMediaItems = emptySet()
                                    isLoading = false
                                    if (items.isNotEmpty()) {
                                        showMediaDialog = true
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    errorMessage = "올바른 Instagram URL을 입력해주세요"
                                    isLoading = false
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                errorMessage = e.message ?: "미디어 추출 중 오류가 발생했습니다"
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
            Text(if (isLoading) "추출 중..." else "미디어 추출")
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
                    text = "오류: $error",
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
                    .height(200.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Instagram URL을 입력하고 미디어 추출을 눌러주세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    
                    val selectedItems = selectedMediaItems.map { index ->
                        mediaItems[index]
                    }
                    
                    val urls = selectedItems.map { it.url }
                    val isVideoList = selectedItems.map { it.type == MediaType.VIDEO }
                    val originalUrls = List(selectedItems.size) { urlText } // 모든 미디어의 원본 Instagram URL
                    
                    downloader.downloadMediaList(
                        urls,
                        isVideoList,
                        originalUrls,
                        onProgress = { current, _ ->
                            downloadProgress = current
                        }
                    ).fold(
                        onSuccess = { savedUris ->
                            isDownloading = false
                            showMediaDialog = false
                            ToastUtils.showDownloadComplete(context, savedUris.size)
                        },
                        onFailure = { error ->
                            isDownloading = false
                            ToastUtils.showDownloadError(context, error.message ?: "알 수 없는 오류")
                        }
                    )
                }
            },
            onDismiss = {
                showMediaDialog = false
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
                Text("다운로드할 미디어를 선택하세요 (${selectedItems.size}/${mediaItems.size})")
                
                if (isDownloading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = downloadProgress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "다운로드 중... $downloadProgress%",
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
                    Text("다운로드 (${selectedItems.size})")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDownloading
            ) {
                Text("취소")
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
                contentDescription = "미디어",
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
                        text = "동영상",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
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