package com.example.android_export_kit_demo.uime

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import coil.compose.AsyncImage
import com.example.android_export_kit_demo.model.FrameLayer
import com.example.android_export_kit_demo.model.FrameModel
import com.example.android_export_kit_demo.model.LayerType
import com.example.android_export_kit_demo.AppColors
import com.example.android_export_kit_demo.viewmodel.FrameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ─────────────────────────────────────────────
// Select Photo Screen (equivalent to Flutter SelectPhotoFromGallery)
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectPhotoScreen(
    frame: FrameModel,
    viewModel: FrameViewModel,
    onProceed: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imageCount = 4

    var mediaList by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val selectedMedia = remember { mutableStateListOf<Uri>() }
    var isProcessing by remember { mutableStateOf(false) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scope.launch {
                mediaList = loadGalleryImages(context)
                isLoading = false
            }
        } else {
            isLoading = false
        }
    }

    // Request gallery permission on first launch
    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }

    fun toggleSelection(uri: Uri) {
        if (selectedMedia.contains(uri)) {
            selectedMedia.remove(uri)
        } else {
            if (selectedMedia.size < imageCount) {
                selectedMedia.add(uri)
            }
        }
    }

    fun proceedToEditor() {
        isProcessing = true
        scope.launch {
            try {
                val photoSlots = frame.layers.filter { it.isPhotoSlot }
                selectedMedia.forEachIndexed { i, uri ->
                    val file = uriToCacheFile(context, uri) ?: return@forEachIndexed
                    when {
                        i < photoSlots.size -> photoSlots[i].customImage = file
                        photoSlots.isEmpty() && i == 0 -> {
                            val bg = frame.layers.firstOrNull {
                                it.type == LayerType.IMAGE && !it.isBackground
                            } ?: frame.layers.firstOrNull { it.type == LayerType.IMAGE }
                            bg?.customImage = file
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    viewModel.selectFrame(frame)
                    onProceed()
                }
            } catch (e: Exception) {
                // handle error
            } finally {
                isProcessing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${selectedMedia.size} / $imageCount Selected") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (selectedMedia.isNotEmpty()) {
                SelectionBottomBar(
                    selectedMedia = selectedMedia.toList(),
                    imageCount = imageCount,
                    onRemove = { uri -> toggleSelection(uri) },
                    onConfirm = { proceedToEditor() }
                )
            }
        },
        containerColor = AppColors.Surface
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                mediaList.isEmpty() -> Text(
                    "No images found",
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(mediaList) { _, uri ->
                        val isSelected = selectedMedia.contains(uri)
                        val selectionIndex = selectedMedia.indexOf(uri)
                        val isDisabled = !isSelected && selectedMedia.size >= imageCount

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { toggleSelection(uri) }
                        ) {
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Selected overlay with number badge
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.4f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(AppColors.Primary, shape = CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${selectionIndex + 1}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            // Dim unselectable items
                            if (isDisabled) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.White.copy(alpha = 0.5f))
                                )
                            }
                        }
                    }
                }
            }

            // Processing overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Selection Bottom Bar
// ─────────────────────────────────────────────

@Composable
private fun SelectionBottomBar(
    selectedMedia: List<Uri>,
    imageCount: Int,
    onRemove: (Uri) -> Unit,
    onConfirm: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            // Selected images preview strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(70.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                selectedMedia.forEach { uri ->
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                    ) {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                        // Remove button
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .background(Color.Red, shape = CircleShape)
                                .clickable { onRemove(uri) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Confirm button
            Button(
                onClick = onConfirm,
                enabled = selectedMedia.size == imageCount,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Primary,
                    disabledContainerColor = Color.LightGray
                )
            ) {
                Text(
                    text = if (selectedMedia.size == imageCount) {
                        "Confirm"
                    } else {
                        "Select ${imageCount - selectedMedia.size} more"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────

private suspend fun loadGalleryImages(context: Context): List<Uri> =
    withContext(Dispatchers.IO) {
        val images = mutableListOf<Uri>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext() && images.size < 100) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(collection, id)
                images.add(contentUri)
            }
        }
        images
    }

private suspend fun uriToCacheFile(context: Context, uri: Uri): File? =
    withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val tempFile = File.createTempFile("gallery_", ".jpg", context.cacheDir)
            tempFile.outputStream().use { output -> inputStream.copyTo(output) }
            tempFile
        } catch (_: Exception) {
            null
        }
    }
