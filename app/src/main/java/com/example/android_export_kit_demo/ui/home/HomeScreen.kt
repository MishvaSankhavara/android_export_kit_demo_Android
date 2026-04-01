package com.example.android_export_kit_demo.uime

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import com.example.android_export_kit_demo.AppColors
import com.example.android_export_kit_demo.model.FrameModel
import com.example.android_export_kit_demo.model.LayerType
import com.example.android_export_kit_demo.viewmodel.FrameViewModel
import java.io.File
import kotlin.collections.isNotEmpty

// ─────────────────────────────────────────────
// Home Screen (equivalent to Flutter HomeScreen)
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: FrameViewModel,
    onFrameSelected: (FrameModel) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var isGridView by remember { mutableStateOf(true) }
    var showClearDialog by remember { mutableStateOf(false) }

    // File picker launcher for ZIP files
    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val bytes = stream.readBytes()
                val name = it.lastPathSegment ?: "frame.zip"
                viewModel.loadFramesFromZip(bytes, name)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Frame Editor",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1C1C)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                actions = {
                    IconButton(onClick = { isGridView = !isGridView }) {
                        Icon(
                            if (isGridView) Icons.Default.List else Icons.Default.GridView,
                            contentDescription = "Toggle view"
                        )
                    }
                    if (uiState.frames.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear all")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { zipPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                icon = { Icon(Icons.Outlined.FolderZip, contentDescription = null) },
                text = { Text("Load Zip") },
                containerColor = AppColors.Primary,
                contentColor = Color.White
            )
        },
        containerColor = AppColors.Surface
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = AppColors.Primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Extracting frames...")
                    }
                }
                uiState.frames.isEmpty() -> {
                    EmptyState(onPickZip = {
                        zipPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed"))
                    })
                }
                isGridView -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.frames) { frame ->
                            FrameCard(
                                frame = frame,
                                onTap = {
                                    viewModel.selectFrame(frame)
                                    onFrameSelected(frame)
                                }
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.frames) { frame ->
                            FrameListTile(
                                frame = frame,
                                onTap = {
                                    viewModel.selectFrame(frame)
                                    onFrameSelected(frame)
                                }
                            )
                        }
                    }
                }
            }

            // Error Snackbar
            uiState.errorMessage?.let { error ->
                LaunchedEffect(error) {
                    viewModel.clearError()
                }
            }
        }
    }

    // Clear All Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all frames?") },
            text = { Text("All loaded frames will be removed.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.clearFrames(); showClearDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ─────────────────────────────────────────────
// Frame Grid Card
// ─────────────────────────────────────────────

@Composable
private fun FrameCard(frame: FrameModel, onTap: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.9f)
            ) {
                FrameThumbnail(frame = frame)
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = frame.displayTitle,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${frame.layers.size} layers",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Frame List Tile
// ─────────────────────────────────────────────

@Composable
private fun FrameListTile(frame: FrameModel, onTap: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                FrameThumbnail(frame = frame)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = frame.displayTitle,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${frame.layers.size} layers · ${frame.canvasWidth.toInt()}×${frame.canvasHeight.toInt()}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Icon(
                Icons.Default.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color.Gray
            )
        }
    }
}

// ─────────────────────────────────────────────
// Frame Thumbnail
// ─────────────────────────────────────────────

@Composable
private fun FrameThumbnail(frame: FrameModel) {
    val bgLayer = frame.layers.firstOrNull {
        it.type == LayerType.IMAGE && it.src != null
    }

    val srcFile = bgLayer?.src?.let { File(it) }
    if (srcFile != null && srcFile.exists()) {
        AsyncImage(
            model = srcFile,
            contentDescription = frame.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.Primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = AppColors.Primary
            )
        }
    }
}

// ─────────────────────────────────────────────
// Empty State
// ─────────────────────────────────────────────

@Composable
private fun EmptyState(onPickZip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(AppColors.Primary.copy(alpha = 0.08f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.FolderZip,
                contentDescription = null,
                modifier = Modifier.size(50.dp),
                tint = AppColors.Primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "No Frames Loaded",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Import a zip file containing:\n" +
                   "  • json/ — frame definitions\n" +
                   "  • skins/ — images & icons\n" +
                   "  • fonts/ — custom fonts",
            fontSize = 14.sp,
            color = Color.Gray,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onPickZip,
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
        ) {
            Icon(Icons.Default.UploadFile, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import Zip File")
        }
    }
}
