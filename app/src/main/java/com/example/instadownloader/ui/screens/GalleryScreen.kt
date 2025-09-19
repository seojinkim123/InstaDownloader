package com.example.instadownloader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.instadownloader.data.repository.GalleryRepository
import com.example.instadownloader.data.repository.GalleryMediaItem
import com.example.instadownloader.ui.components.ZoomableImageViewer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen() {
    var showFilter by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    var mediaItems by remember { mutableStateOf<List<GalleryMediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedMedia by remember { mutableStateOf<GalleryMediaItem?>(null) }
    var viewerMediaItems by remember { mutableStateOf<List<GalleryMediaItem>>(emptyList()) }
    var viewerInitialPage by remember { mutableStateOf(0) }
    var showViewer by remember { mutableStateOf(false) }
    var isPostGroupView by remember { mutableStateOf(true) }
    
    val context = LocalContext.current
    val repository = remember { GalleryRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // 데이터 로드
    LaunchedEffect(selectedFilter) {
        isLoading = true
        mediaItems = when (selectedFilter) {
            "Images" -> repository.getMediaByType("image")
            "Videos" -> repository.getMediaByType("video")
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
                text = "Download Gallery",
                style = MaterialTheme.typography.headlineMedium
            )
            
            IconButton(
                onClick = { showFilter = true }
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Filter")
            }
        }
        
        // 미디어 개수 표시 및 토글
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Total ${mediaItems.size} items",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Switch(
                checked = isPostGroupView,
                onCheckedChange = { isPostGroupView = it }
            )
        }
        
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
                        text = "No downloaded media",
                        style = MaterialTheme.typography.bodyLarge
                    )

                }
            }
        } else {
            if (isPostGroupView) {
                // 포스트별 그룹화된 뷰
                val groupedMedia = mediaItems.groupBy { it.postId ?: it.id }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groupedMedia.toList()) { (postId, mediaList) ->
                        PostGroupCard(
                            mediaItems = mediaList.sortedBy { it.downloadDate },
                            onClick = {
                                viewerMediaItems = mediaList.sortedBy { it.downloadDate }
                                viewerInitialPage = 0
                                showViewer = true
                            }
                        )
                    }
                }
            } else {
                // 개별 미디어 그리드 (기존 방식)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mediaItems) { media ->
                        val index = mediaItems.indexOf(media)
                        GalleryMediaCard(
                            media = media,
                            onClick = {
                                viewerMediaItems = mediaItems
                                viewerInitialPage = index
                                showViewer = true
                            },
                            onDelete = {
                                coroutineScope.launch {
                                    repository.deleteMedia(media)
                                    // 삭제 후 목록 새로고침
                                    mediaItems = when (selectedFilter) {
                                        "Images" -> repository.getMediaByType("image")
                                        "Videos" -> repository.getMediaByType("video")
                                        else -> repository.getAllMedia()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    
    // 필터 다이얼로그
    if (showFilter) {
        AlertDialog(
            onDismissRequest = { showFilter = false },
            title = { Text("Select Filter") },
            text = {
                Column {
                    listOf("All", "Images", "Videos").forEach { filter ->
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
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFilter = false }
                ) {
                    Text("Cancel")
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
                        model = if (media.mediaType == "video") media.thumbnailUri else media.uri,
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
                        text = if (media.mediaType == "video") "Video" else "Image",
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
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
    
    // 이미지 뷰어
    if (showViewer) {
        ZoomableImageViewer(
            mediaItems = viewerMediaItems,
            initialPage = viewerInitialPage,
            onDismiss = { showViewer = false }
        )
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
                        text = "Video",
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
                    .padding(2.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete",
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
            title = { Text("Delete Media") },
            text = { Text("Are you sure you want to delete this media?\nIt will also be removed from your gallery.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PostGroupCard(
    mediaItems: List<GalleryMediaItem>,
    onClick: () -> Unit
) {
    val firstMedia = mediaItems.first()
    val mediaCount = mediaItems.size
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxWidth()
    ) {
        Box {
            // 대표 이미지 (첫 번째 미디어)
            AsyncImage(
                model = firstMedia.thumbnailUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // 미디어 개수 표시 (우상단) - owner 스타일과 유사하게
            if (mediaCount > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            CircleShape
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$mediaCount",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Owner 프로필 (우하단)
            if (firstMedia.ownerUsername != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            shape = MaterialTheme.shapes.small
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 프로필 이미지
                    if (firstMedia.ownerProfilePicUrl != null) {
                        AsyncImage(
                            model = firstMedia.ownerProfilePicUrl,
                            contentDescription = "Profile",
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .border(1.dp, Color.White, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    // 사용자명
                    Text(
                        text = firstMedia.ownerUsername,
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}