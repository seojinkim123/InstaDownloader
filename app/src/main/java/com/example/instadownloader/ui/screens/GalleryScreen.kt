package com.example.instadownloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.instadownloader.data.repository.GalleryRepository
import com.example.instadownloader.data.repository.GalleryMediaItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen() {
    var showFilter by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("전체") }
    var mediaItems by remember { mutableStateOf<List<GalleryMediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedMedia by remember { mutableStateOf<GalleryMediaItem?>(null) }
    
    val context = LocalContext.current
    val repository = remember { GalleryRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // 데이터 로드
    LaunchedEffect(selectedFilter) {
        isLoading = true
        mediaItems = when (selectedFilter) {
            "이미지" -> repository.getMediaByType("image")
            "동영상" -> repository.getMediaByType("video")
            else -> repository.getAllMedia()
        }
        isLoading = false
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 상단 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "다운로드 갤러리",
                style = MaterialTheme.typography.headlineMedium
            )
            
            IconButton(
                onClick = { showFilter = true }
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "필터")
            }
        }
        
        // 미디어 개수 표시
        Text(
            text = "총 ${mediaItems.size}개 항목",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (isLoading) {
            // 로딩 상태
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (mediaItems.isEmpty()) {
            // 빈 상태
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "다운로드한 미디어가 없습니다",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "URL 다운로더나 브라우저에서 미디어를 다운로드해보세요",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        } else {
            // 미디어 그리드
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(mediaItems) { media ->
                    GalleryMediaCard(
                        media = media,
                        onClick = {
                            selectedMedia = media
                        },
                        onDelete = {
                            coroutineScope.launch {
                                repository.deleteMedia(media)
                                // 삭제 후 목록 새로고침
                                mediaItems = when (selectedFilter) {
                                    "이미지" -> repository.getMediaByType("image")
                                    "동영상" -> repository.getMediaByType("video")
                                    else -> repository.getAllMedia()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    
    // 필터 다이얼로그
    if (showFilter) {
        AlertDialog(
            onDismissRequest = { showFilter = false },
            title = { Text("필터 선택") },
            text = {
                Column {
                    listOf("전체", "이미지", "동영상").forEach { filter ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter }
                            )
                            Text(
                                text = filter,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showFilter = false }
                ) {
                    Text("적용")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFilter = false }
                ) {
                    Text("취소")
                }
            }
        )
    }
    
    // 미디어 상세 보기 다이얼로그
    selectedMedia?.let { media ->
        Dialog(
            onDismissRequest = { selectedMedia = null }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    AsyncImage(
                        model = media.uri,
                        contentDescription = media.fileName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        contentScale = ContentScale.Fit
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = media.fileName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Text(
                        text = if (media.mediaType == "video") "동영상" else "이미지",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(
                            onClick = { selectedMedia = null }
                        ) {
                            Text("닫기")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryMediaCard(
    media: GalleryMediaItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxWidth()
    ) {
        Box {
            AsyncImage(
                model = media.thumbnailUri,
                contentDescription = media.fileName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // 비디오 표시 오버레이
            if (media.mediaType == "video") {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
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
            
            // 삭제 버튼
            IconButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            MaterialTheme.shapes.small
                        )
                        .padding(2.dp)
                )
            }
        }
    }
    
    // 삭제 확인 다이얼로그
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("미디어 삭제") },
            text = { Text("이 미디어를 삭제하시겠습니까?\n갤러리에서도 함께 삭제됩니다.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("취소")
                }
            }
        )
    }
}