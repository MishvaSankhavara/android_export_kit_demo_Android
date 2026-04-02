package com.example.android_export_kit_demo.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.example.android_export_kit_demo.model.DrawingStroke
import com.example.android_export_kit_demo.model.FrameLayer
import kotlin.math.abs
import kotlin.math.roundToInt
import com.frameeditor.R
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.android_export_kit_demo.model.FrameModel
import com.example.android_export_kit_demo.model.LayerType
import com.example.android_export_kit_demo.AppColors
import coil.compose.AsyncImage
import com.example.android_export_kit_demo.view.FrameCanvasView
import com.example.android_export_kit_demo.view.HeartShape
import com.example.android_export_kit_demo.view.CloudShape
import com.example.android_export_kit_demo.view.BurstShape
import com.example.android_export_kit_demo.view.SpeechBubbleShape
import com.example.android_export_kit_demo.viewmodel.FrameUiState
import com.example.android_export_kit_demo.viewmodel.FrameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.example.android_export_kit_demo.ui.editor.EmojiData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrameEditorScreen(
    viewModel: FrameViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val frame = uiState.selectedFrame ?: run {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No frame selected")
        }
        return
    }

    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    
    val imeBottom = WindowInsets.ime.getBottom(density)
    var baseMaxHeight by remember { mutableStateOf<Dp?>(null) }
    var baseBottomPadding by remember { mutableStateOf<Dp?>(null) }
    
    var activeEditorTab by remember { mutableStateOf("Edit") }
    var activeSubEditor by remember { mutableStateOf<String?>(null) }
    var textInput by remember { mutableStateOf("") }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showImageSheet by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val layer = uiState.selectedLayer ?: return@let
            val file = uriToFile(context, it) ?: return@let
            viewModel.setLayerCustomImage(layer, file)
        }
    }

    // Sync textInput with selected layer text
    LaunchedEffect(uiState.selectedLayer?.id, uiState.selectedLayer?.text) {
        val sel = uiState.selectedLayer
        if (sel != null && sel.type == LayerType.TEXT) {
            textInput = sel.text ?: ""
            // When selecting a text layer, close the Filter panel to switch to text editing
            if (activeSubEditor == "Filters") {
                activeSubEditor = null
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBackIosNew, contentDescription = "Back", tint = Color.Black, modifier = Modifier.size(20.dp))
                    }
                },
                title = {
                    Text("Edit", fontWeight = FontWeight.Bold, color = Color.Black)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                actions = {
                    // All other actions removed as per user request
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
                Divider(thickness = 1.dp, color = Color.LightGray)
                if (uiState.isDrawingMode) {
                    DrawPanel(
                        viewModel = viewModel,
                        uiState = uiState,
                        onClose = {
                            viewModel.clearDrawing()
                            viewModel.setDrawingMode(false)
                        }
                    )
                } else {
                    val sel = uiState.selectedLayer
                    val isText = sel != null && sel.type == LayerType.TEXT
                    val isImage = sel != null && sel.type == LayerType.IMAGE

                    if (activeSubEditor != null && (activeSubEditor == "Stickers" || !isText)) {
                        IntegratedPanel(
                            title = activeSubEditor ?: "",
                            onClose = { activeSubEditor = null },
                            content = {
                                SubEditorContent(activeSubEditor!!, sel, viewModel, frame, uiState, onClose = { activeSubEditor = null })
                            }
                        )
                    } else {
                        Surface(
                            color = Color.White,
                            modifier = Modifier.navigationBarsPadding()
                        ) {
                            if (sel == null) {
                                GenActions(
                                    viewModel = viewModel,
                                    onOpenSubEditor = { activeSubEditor = it; activeEditorTab = "Edit" }
                                )
                            } else if (isText) {
                                TextEditorBar(
                                    layer = sel,
                                    textInput = textInput,
                                    onTextInputChange = { textInput = it; viewModel.updateLayerText(sel, it) },
                                    activeSubEditor = activeSubEditor,
                                    onOpenSubEditor = { activeSubEditor = if (activeSubEditor == it) null else it },
                                    onDone = { activeSubEditor = null; viewModel.deselectAll() },
                                    viewModel = viewModel,
                                    frame = frame
                                )
                            } else if (isImage) {
                                ImageEditorBar(
                                    layer = sel,
                                    activeEditorTab = activeEditorTab,
                                    onChangeTab = { activeEditorTab = it },
                                    onOpenSubEditor = { activeSubEditor = if (activeSubEditor == it) null else it },
                                    onShowImageSheet = { showImageSheet = true },
                                    viewModel = viewModel
                                )
                            } else {
                                LayerActionsBar(layer = sel, onOpenSubEditor = { activeSubEditor = it }, viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color.White
    ) { padding ->
        val currentBottomPadding = padding.calculateBottomPadding()
        if (baseBottomPadding == null && imeBottom == 0) {
            baseBottomPadding = currentBottomPadding
        }
        val safeBaseBottom = baseBottomPadding ?: currentBottomPadding
        val keyboardHeightDp = (currentBottomPadding - safeBaseBottom).coerceAtLeast(0.dp)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 0.dp)
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.TopCenter
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Update baseMaxHeight only when keyboard is hidden
                if (imeBottom == 0) {
                    if (baseMaxHeight == null || (maxHeight > 0.dp && (baseMaxHeight == null || maxHeight > baseMaxHeight!!))) {
                        baseMaxHeight = maxHeight
                    }
                }
                
                val stableHeight = baseMaxHeight ?: maxHeight
                
                // Calculate precise shift only if selected layer is covered by keyboard
                val sel = uiState.selectedLayer
                val shiftNeeded = if (sel != null && keyboardHeightDp > 2.dp) {
                    val canvasAspect = frame.canvasWidth / frame.canvasHeight
                    val (canvasW, canvasH) = if (maxWidth.value / maxHeight.value > canvasAspect) {
                        Pair(maxHeight * canvasAspect, maxHeight)
                    } else {
                        Pair(maxWidth, maxWidth / canvasAspect)
                    }
                    val scale = if (frame.canvasWidth > 0f) canvasW.value / frame.canvasWidth else 1f
                    
                    val canvasTop = padding.calculateTopPadding() + 24.dp
                    val layerBottom = canvasTop + (sel.y + sel.height).dp * scale
                    val keyboardTop = stableHeight - currentBottomPadding
                    
                    if (layerBottom > keyboardTop - 10.dp) {
                        (layerBottom - (keyboardTop - 10.dp)).coerceAtMost(keyboardHeightDp)
                    } else 0.dp
                } else 0.dp

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stableHeight)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding() + 24.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                FrameCanvasView(
                frame = frame,
                selectedLayer = uiState.selectedLayer,
                isDrawingMode = uiState.isDrawingMode,
                currentDrawingStrokes = uiState.currentDrawingStrokes,
                activeStroke = uiState.activeStroke,
                updateCount = uiState.updateCount,
                keyboardShiftPx = with(density) { -shiftNeeded.toPx() },
                onBitmapCaptured = { capturedBitmap = it },
                onLayerTap = { layer ->
                    activeEditorTab = "Edit"
                    if (layer.id == "__deselect__") {
                        viewModel.deselectAll()
                        activeSubEditor = null
                    } else {
                        val isEmoji = layer.type == LayerType.TEXT && isOnlyEmoji(layer.text)
                        
                        if (uiState.selectedLayer === layer) {
                            if (isEmoji) {
                                activeSubEditor = "Stickers"
                            } else if (layer.type == LayerType.TEXT) {
                                activeSubEditor = "Edit Text"
                            } else if (layer.type == LayerType.IMAGE && layer.isPhotoSlot) {
                                showImageSheet = true
                            }
                        } else {
                            viewModel.selectLayer(layer)
                            if (layer.type == LayerType.TEXT) {
                                textInput = layer.text ?: ""
                                // Set to Stickers if emoji, else clear to avoid persistent panel
                                if (isEmoji) {
                                    activeSubEditor = "Stickers"
                                } else {
                                    activeSubEditor = null
                                }
                            } else {
                                // Clear for non-text layers (images, slots)
                                activeSubEditor = null
                            }
                        }
                    }
                },
                onLayerTransform = { layer, x, y, width, height, rotation, fontSize ->
                    if (x != null || y != null) {
                        viewModel.setLayerPosition(layer, x ?: layer.x, y ?: layer.y, skipUndo = true)
                    }
                    if (width != null || height != null) {
                        viewModel.setLayerSize(layer, width ?: layer.width, height ?: layer.height, skipUndo = true)
                    }
                    rotation?.let { viewModel.setLayerRotation(layer, it, skipUndo = true) }
                    fontSize?.let { viewModel.updateLayerFontSize(layer, it, skipUndo = true) }
                },
                onPhotoTransform = { layer, scale, panX, panY ->
                    viewModel.updatePhotoTransform(layer, scale, panX, panY)
                },
                onRatioDetected = { layer, ratio ->
                    viewModel.updateLayerRatio(layer, ratio)
                },
                onStickerTextChange = { layer, text ->
                    viewModel.updateLayerText(layer, text)
                },
                onDeleteLayer = { layer ->
                    if (layer.isPhotoSlot && !layer.isSticker) {
                        viewModel.setLayerCustomImage(layer, null)
                        viewModel.deselectAll()
                        activeSubEditor = null
                    } else {
                        viewModel.deleteLayer(layer)
                        viewModel.deselectAll()
                        activeSubEditor = null
                    }
                },
                onToggleFlip = { layer ->
                    viewModel.toggleLayerFlip(layer)
                },
                onDrawingStart = { x, y -> viewModel.startDrawing(x, y) },
                onDrawingUpdate = { x, y -> viewModel.updateDrawing(x, y) },
                onDrawingEnd = { viewModel.endDrawing() }
            )
                    }
                }
            }
        }

        if (showImageSheet) {
            ModalBottomSheet(onDismissRequest = { showImageSheet = false }) {
                Column(modifier = Modifier.padding(bottom = 32.dp)) {
                    ListItem(
                        headlineContent = { Text("Gallery") },
                        leadingContent = { Icon(Icons.Outlined.PhotoLibrary, null) },
                        modifier = Modifier.clickable {
                            showImageSheet = false
                            imagePickerLauncher.launch("image/*")
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Camera") },
                        leadingContent = { Icon(Icons.Outlined.CameraAlt, null) },
                        modifier = Modifier.clickable {
                            showImageSheet = false
                            Toast.makeText(context, "Camera picker requires file authority setup.", Toast.LENGTH_SHORT).show()
                        }
                    )
                    if (uiState.selectedLayer?.customImage != null) {
                        ListItem(
                            headlineContent = { Text("Remove", color = Color.Red) },
                            leadingContent = { Icon(Icons.Outlined.RemoveCircleOutline, null, tint = Color.Red) },
                            modifier = Modifier.clickable {
                                showImageSheet = false
                                viewModel.setLayerCustomImage(uiState.selectedLayer!!, null)
                            }
                        )
                    }
                }
            }
        }
    }
}



// ─────────────────────────────────────────────
// Draw Panel
// ─────────────────────────────────────────────
@Composable
private fun DrawPanel(viewModel: FrameViewModel, uiState: FrameUiState, onClose: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.98f),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 4.dp)) {
            // Header: X | Draw | Undo | Check (Super thin)
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) { 
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) 
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Draw", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { viewModel.undoDrawingStroke() }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Undo, null, modifier = Modifier.size(18.dp))
                    }
                }

                IconButton(onClick = { viewModel.addDrawingLayer() }, modifier = Modifier.size(32.dp)) { 
                    Icon(Icons.Default.Check, null, tint = Color(0xFFA259FF), modifier = Modifier.size(22.dp)) 
                }
            }
            Divider(color = Color.LightGray.copy(alpha = 0.3f))

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                // Row 1: Compact Toggle | Slider | Value
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pen/Eraser Small Toggle
                    Row(
                        modifier = Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFF0F0F0)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                .background(if (!uiState.isEraser) Color(0xFFA259FF) else Color.Transparent)
                                .clickable { viewModel.setDrawingEraser(false) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, null, tint = if (!uiState.isEraser) Color.White else Color.Black, modifier = Modifier.size(14.dp))
                        }
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape)
                                .background(if (uiState.isEraser) Color(0xFFA259FF) else Color.Transparent)
                                .clickable { viewModel.setDrawingEraser(true) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Brush, null, tint = if (uiState.isEraser) Color.White else Color.Black, modifier = Modifier.size(14.dp))
                        }
                    }

                    Slider(
                        value = uiState.drawingWidth,
                        onValueChange = { viewModel.setDrawingWidth(it) },
                        valueRange = 1f..100f,
                        modifier = Modifier.weight(1f).height(32.dp),
                        colors = SliderDefaults.colors(thumbColor = Color(0xFFA259FF), activeTrackColor = Color(0xFFA259FF))
                    )
                    Text("${uiState.drawingWidth.toInt()}", fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(24.dp))
                }

                Spacer(Modifier.height(4.dp))

                // Row 2: Colors & Styles Combined or squashed
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Styles (Icons only)
                    val styles = listOf(
                        Triple("normal", Icons.Default.Gesture, "Basic"),
                        Triple("neon", Icons.Default.AutoFixHigh, "Neon"),
                        Triple("dotted", Icons.Default.MoreHoriz, "Dots"),
                        Triple("rainbow", Icons.Default.Loop, "Rain")
                    )
                    LazyRow(modifier = Modifier.weight(0.4f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(styles) { (mode, icon, _) ->
                            val isSel = uiState.drawingMode == mode
                            Box(
                                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) Color(0xFFF8F2FF) else Color(0xFFF5F5F5))
                                    .border(if (isSel) 1.dp else 0.dp, Color(0xFFA259FF), RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setDrawingMode(mode) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(icon, null, tint = if (isSel) Color(0xFFA259FF) else Color.Black.copy(alpha=0.6f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    // Small Color Row
                    LazyRow(modifier = Modifier.weight(0.6f).height(32.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        val colors = listOf(Color.Black, Color.White, Color.Red, Color.DarkGray, Color.Yellow, Color.Green, Color.Blue, Color.Magenta)
                        items(colors) { c ->
                            val isSel = c.toArgb() == uiState.drawingColor
                            Box(
                                modifier = Modifier.size(24.dp).background(c, CircleShape)
                                    .border(if (isSel) 2.dp else 1.dp, if (isSel) Color(0xFFA259FF) else Color.LightGray.copy(alpha=0.3f), CircleShape)
                                    .clickable { viewModel.setDrawingColor(c.toArgb()) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorRowLarge(selColorInt: Int, onCh: (Color) -> Unit) {
    val colors = listOf(Color.Black, Color.White, Color.Red, Color.DarkGray, Color.Yellow, Color.Green, Color.Blue, Color.Cyan, Color.Magenta)
    LazyRow(modifier = Modifier.height(44.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(colors) { c ->
            val isSel = c.toArgb() == selColorInt
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(c, CircleShape)
                    .border(if (isSel) 3.dp else 1.dp, if (isSel) Color(0xFFA259FF) else Color.LightGray.copy(alpha=0.4f), CircleShape)
                    .clickable { onCh(c) }
            )
        }
    }
}

@Composable
private fun ColorRowSlim(selColorInt: Int, onCh: (Color) -> Unit) {
    val colors = listOf(Color.Black, Color.White, Color.Red, Color.Magenta, Color(0xFFA259FF), Color.Blue, Color.Cyan, Color.Green, Color.Yellow, Color.Gray)
    LazyRow(modifier = Modifier.height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        items(colors) { c ->
            val isSel = c.toArgb() == selColorInt
            Box(
                modifier = Modifier.size(32.dp).padding(end = 6.dp)
                    .background(c, CircleShape)
                    .border(if (isSel) 2.dp else 1.dp, if (isSel) AppColors.Primary else Color.LightGray.copy(alpha=0.5f), CircleShape)
                    .clickable { onCh(c) },
                contentAlignment = Alignment.Center
            ) {
                if (isSel) Icon(Icons.Default.Check, null, tint = if (c == Color.White) Color.Black else Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────
// Main Editor ToolBars
// ─────────────────────────────────────────────
@Composable
private fun GenActions(viewModel: FrameViewModel, onOpenSubEditor: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        GenActionItem(Icons.Outlined.FilterVintage, "Filters") { onOpenSubEditor("Filters") }
        GenActionItem(Icons.Outlined.AddReaction, "Stickers") { onOpenSubEditor("Stickers") }
        GenActionItem(Icons.Default.TextFields, "Text") {
            viewModel.addTextLayer("Tap to edit")
            onOpenSubEditor("Edit Text")
        }
        GenActionItem(Icons.Outlined.Brush, "Draw") {
            onOpenSubEditor("")
            viewModel.setDrawingMode(true)
        }
    }
}

@Composable
private fun GenActionItem(icon: ImageVector, label: String, onTap: () -> Unit) {
    Column(
        modifier = Modifier.clickable(onClick = onTap).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(28.dp), tint = Color.Black.copy(alpha=0.87f))
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

enum class TextEditorTab {
    Keyboard, Font, Style, Preset, Curve, Size
}

@Composable
private fun TextEditorBar(
    layer: FrameLayer,
    textInput: String,
    onTextInputChange: (String) -> Unit,
    activeSubEditor: String?,
    onOpenSubEditor: (String) -> Unit,
    onDone: () -> Unit,
    viewModel: FrameViewModel,
    frame: FrameModel
) {
    if (layer.isLocked) return

    var currentTab by remember(layer.id) { 
        mutableStateOf(if (layer.presetId != null) TextEditorTab.Preset else TextEditorTab.Font) 
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .animateContentSize(tween(300))
    ) {
        // 1. Top Action Icons Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp), 
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp) // Subtle spacing between weight items
        ) {
            TextEditorTabIcon(Icons.Default.Keyboard, "Keyboard", currentTab == TextEditorTab.Keyboard, Modifier.weight(1f)) { currentTab = TextEditorTab.Keyboard }
            TextEditorTabIcon(Icons.Default.TextFields, "Font", currentTab == TextEditorTab.Font, Modifier.weight(1f)) { currentTab = TextEditorTab.Font }
            TextEditorTabIcon(Icons.Default.Palette, "Style", currentTab == TextEditorTab.Style, Modifier.weight(1f)) { currentTab = TextEditorTab.Style }
            TextEditorTabIcon(Icons.Default.Star, "Preset", currentTab == TextEditorTab.Preset, Modifier.weight(1f)) { currentTab = TextEditorTab.Preset }
            TextEditorTabIcon(Icons.Default.Abc, "Curve", currentTab == TextEditorTab.Curve, Modifier.weight(1f)) { currentTab = TextEditorTab.Curve }
            
            // Done Checkmark styled same as tabs for uniformity
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onDone)
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Check, "Done", tint = Color.Black, modifier = Modifier.size(28.dp)) // Slightly larger checkmark as in original
            }
        }

        Divider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))

        // 2. Tab Content Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp) // Professional fixed height
        ) {
            when (currentTab) {
                TextEditorTab.Keyboard -> KeyboardPanelContent(layer, textInput, onTextInputChange)
                TextEditorTab.Font -> AdvancedFontPanelContent(viewModel, layer)
                TextEditorTab.Style -> StylePanelContent(viewModel, layer)
                TextEditorTab.Preset -> PresetPanelContent(viewModel, layer)
                TextEditorTab.Curve -> CurvePanelContent(viewModel, layer)
                TextEditorTab.Size -> ResizePanelContent(viewModel, layer)
            }
        }
    }
}

@Composable
private fun TextEditorTabIcon(icon: ImageVector, label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFFF0F4FF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color(0xFF3F51B5) else Color.Gray,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            fontSize = 9.sp,
            color = if (isSelected) Color(0xFF3F51B5) else Color.Gray,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun KeyboardPanelContent(layer: FrameLayer, textInput: String, onTextInputChange: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
        OutlinedTextField(
            value = textInput,
            onValueChange = onTextInputChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Medium),
            placeholder = { Text("Enter text...", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Color.Gray) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF3F51B5),
                unfocusedBorderColor = Color(0xFFEEEEEE),
                cursorColor = Color(0xFF3F51B5)
            )
        )
    }
}

@Composable
private fun TextToolIcon(icon: ImageVector, label: String, active: Boolean, enabled: Boolean = true, color: Color? = null, onTap: () -> Unit) {
    Column(
        modifier = Modifier
            .then(if (enabled) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val baseTint = if (enabled) (color ?: Color.Black.copy(alpha=0.45f)) else Color.Black.copy(alpha=0.15f)
        val tint = if (active) Color(0xFFA259FF) else baseTint
        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp), tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = tint)
    }
}

@Composable
private fun ImageEditorBar(
    layer: FrameLayer,
    activeEditorTab: String,
    onChangeTab: (String) -> Unit,
    onOpenSubEditor: (String) -> Unit,
    onShowImageSheet: () -> Unit,
    viewModel: FrameViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(90.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (layer.isPhotoSlot) {
            ActionIcon(Icons.Outlined.Image, "Replace", enabled = !layer.isLocked) { onShowImageSheet() }
        }
        ActionIcon(Icons.Outlined.FilterVintage, "Filter", enabled = !layer.isLocked) { onOpenSubEditor("Filters") }
        ActionIcon(Icons.Outlined.AddReaction, "Sticker", enabled = !layer.isLocked) { onOpenSubEditor("Stickers") }
        ActionIcon(Icons.Default.Tune, "Opacity", enabled = !layer.isLocked) { onOpenSubEditor("Opacity") }
        ActionIcon(Icons.Default.Flip, "Mirror H", enabled = !layer.isLocked) { viewModel.toggleLayerFlip(layer) }
        ActionIcon(Icons.Default.Flip, "Mirror V", enabled = !layer.isLocked) { viewModel.toggleLayerFlipV(layer) }
        ActionIcon(Icons.Default.FlipToFront, "Front", enabled = !layer.isLocked) { viewModel.bringLayerToFront(layer) }
        ActionIcon(Icons.Default.ControlCamera, "Move", enabled = !layer.isLocked) { onOpenSubEditor("Move") }
        ActionIcon(Icons.Default.RotateRight, "Rotate", enabled = !layer.isLocked) { onOpenSubEditor("Rotate") }
        ActionIcon(Icons.Default.ZoomIn, "Zoom In", enabled = !layer.isLocked) { viewModel.scaleLayer(layer, 1.1f) }
        ActionIcon(Icons.Default.ZoomOut, "Zoom Out", enabled = !layer.isLocked) { viewModel.scaleLayer(layer, 0.9f) }
        ActionIcon(if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, "Lock", enabled = true) { viewModel.toggleLayerLock(layer) }
        ActionIcon(Icons.Default.Refresh, "Reset", enabled = !layer.isLocked) { viewModel.resetLayerToOriginal(layer) }
        ActionIcon(Icons.Outlined.Delete, "Delete", color = Color.Red) {
            if (layer.isPhotoSlot && !layer.isSticker) {
                viewModel.setLayerCustomImage(layer, null)
                viewModel.deselectAll()
            } else {
                viewModel.deleteLayer(layer)
                viewModel.deselectAll()
            }
        }
    }
}

@Composable
private fun LayerActionsBar(layer: FrameLayer, onOpenSubEditor: (String) -> Unit, viewModel: FrameViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().height(90.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionIcon(Icons.Default.Tune, "Opacity", enabled = !layer.isLocked) { onOpenSubEditor("Opacity") }
        ActionIcon(Icons.Outlined.AddReaction, "Sticker", enabled = !layer.isLocked) { onOpenSubEditor("Stickers") }
        ActionIcon(Icons.Default.FlipToFront, "Front", enabled = !layer.isLocked) { viewModel.bringLayerToFront(layer) }
        ActionIcon(Icons.Default.ControlCamera, "Move", enabled = !layer.isLocked) { onOpenSubEditor("Move") }
        ActionIcon(Icons.Default.AspectRatio, "Resize", enabled = !layer.isLocked) { onOpenSubEditor("Resize") }
        ActionIcon(if (layer.visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, if (layer.visible) "Hide" else "Show", enabled = !layer.isLocked) {
            viewModel.toggleLayerVisibility(layer)
        }
        ActionIcon(Icons.Outlined.FileCopy, "Duplicate", enabled = !layer.isLocked) { viewModel.duplicateLayer(layer) }
        ActionIcon(Icons.Default.Refresh, "Reset", color = Color(0xffffb74d), enabled = !layer.isLocked) { viewModel.resetLayerToOriginal(layer) }
        if (!(layer.isPhotoSlot && !layer.isSticker && layer.customImage == null)) {
            ActionIcon(Icons.Outlined.Delete, "Delete", color = Color.Red, enabled = !layer.isLocked) { viewModel.deleteLayer(layer); viewModel.deselectAll() }
        }
        ActionIcon(if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, "Lock", enabled = true) { viewModel.toggleLayerLock(layer) }
        ActionIcon(Icons.Outlined.CheckCircle, "Done", color = Color(0xff00e676)) { viewModel.deselectAll() }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, label: String, color: Color? = null, enabled: Boolean = true, onTap: () -> Unit) {
    Column(
        modifier = Modifier.then(if (enabled) Modifier.clickable(onClick = onTap) else Modifier).padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val tint = if (enabled) (color ?: Color.Black.copy(alpha=0.87f)) else Color.Black.copy(alpha=0.15f)
        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp), tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (enabled) Color.Unspecified else Color.Black.copy(alpha=0.15f))
    }
}

// ─────────────────────────────────────────────
// Integrated Panel Container & Content
// ─────────────────────────────────────────────
@Composable
private fun IntegratedPanel(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.95f),
        tonalElevation = 4.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            if (title != "Filters") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
                    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onClose) { Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp), tint = AppColors.Primary) }
                }
                Divider(color = Color.LightGray.copy(alpha = 0.5f))
            }
            content()
        }
    }
}

@Composable
private fun SubEditorContent(
    activeSubEditor: String,
    layer: FrameLayer?,
    viewModel: FrameViewModel,
    frame: FrameModel,
    uiState: FrameUiState,
    onClose: () -> Unit
) {
    when (activeSubEditor) {
        "Filters" -> FilterAdjustPanelContent(viewModel, layer, onClose)
        "Stickers" -> StickersPanelContent(viewModel, uiState, onClose)
        "Opacity" -> if (layer != null) OpacityPanelContent(viewModel, layer)
        "Fonts" -> if (layer != null) AdvancedFontPanelContent(viewModel, layer)
        "Font Size" -> if (layer != null) FontSizePanelContent(viewModel, layer)
        "Color" -> if (layer != null) ColorPanelContent(viewModel, layer)
        "Background" -> if (layer != null) BGColorPanelContent(viewModel, layer)
        "Rotate" -> if (layer != null) RotatePanelContent(viewModel, layer)
        "Resize" -> if (layer != null) ResizePanelContent(viewModel, layer)
        "Edit Text" -> { /* Handled inline */ }
        "Format" -> if (layer != null) TextFormatPanelContent(viewModel, layer)
        "Move" -> if (layer != null) MovePanelContent(viewModel, layer)
    }
}

@Composable
private fun NudgeButton(icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (enabled) Color(0xFFEEEEEE) else Color(0xFFF8F8F8))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (enabled) Color.Black else Color.LightGray)
    }
}

@Composable
private fun MovePanelContent(viewModel: FrameViewModel, layer: FrameLayer) {
    val nudge = 15f
    // Only pan if it's a photo slot AND not a sticker AND is an IMAGE type
    val isPhotoInside = layer.isPhotoSlot && !layer.isSticker && layer.type == LayerType.IMAGE
    
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (isPhotoInside) "Pan Image Inside" else "Nudge Position", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Up
            NudgeButton(Icons.Default.KeyboardArrowUp, enabled = !layer.isLocked) { 
                if (isPhotoInside) viewModel.panPhoto(layer, 0f, -nudge)
                else viewModel.updateLayerPosition(layer, 0f, -nudge)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Left
                NudgeButton(Icons.Default.KeyboardArrowLeft, enabled = !layer.isLocked) { 
                    if (isPhotoInside) viewModel.panPhoto(layer, -nudge, 0f)
                    else viewModel.updateLayerPosition(layer, -nudge, 0f)
                }
                Spacer(Modifier.width(48.dp))
                // Right
                NudgeButton(Icons.Default.KeyboardArrowRight, enabled = !layer.isLocked) { 
                    if (isPhotoInside) viewModel.panPhoto(layer, nudge, 0f)
                    else viewModel.updateLayerPosition(layer, nudge, 0f)
                }
            }
            
            // Down
            NudgeButton(Icons.Default.KeyboardArrowDown, enabled = !layer.isLocked) { 
                if (isPhotoInside) viewModel.panPhoto(layer, 0f, nudge)
                else viewModel.updateLayerPosition(layer, 0f, nudge)
            }
        }
    }
}

// Sub Editor Content Panels
@Composable
private fun FilterAdjustPanelContent(viewModel: FrameViewModel, layer: FrameLayer?, onClose: () -> Unit) {
    var selectedTab by remember { mutableStateOf("FILTER") }
    
    Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
        // Tab Switcher with Checkmark
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 15.dp)
                .height(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.fillMaxHeight(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.width(100.dp).clickable { selectedTab = "FILTER" },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "FILTER", 
                        fontSize = 14.sp, 
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == "FILTER") Color.Black else Color.Gray
                    )
                    Spacer(Modifier.height(4.dp))
                    if (selectedTab == "FILTER") {
                        Box(Modifier.width(24.dp).height(2.dp).background(Color(0xFF3F51B5)))
                    } else {
                        Spacer(Modifier.height(2.dp))
                    }
                }
                
                Column(
                    modifier = Modifier.width(100.dp).clickable { selectedTab = "ADJUST" },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "ADJUST", 
                        fontSize = 14.sp, 
                        fontWeight = FontWeight.Bold,
                        color = if (selectedTab == "ADJUST") Color.Black else Color.Gray
                    )
                    Spacer(Modifier.height(4.dp))
                    if (selectedTab == "ADJUST") {
                        Box(Modifier.width(24.dp).height(2.dp).background(Color(0xFF3F51B5)))
                    } else {
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
            
            // Right Checkmark
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(20.dp), tint = Color(0xFF3F51B5))
            }
        }
        
        Spacer(Modifier.height(12.dp))

        if (selectedTab == "FILTER") {
            FilterTabContent(viewModel, layer)
        } else {
            AdjustTabContent(viewModel, layer)
        }
    }
}

@Composable
private fun FilterTabContent(viewModel: FrameViewModel, layer: FrameLayer?) {
    val categoryFilters = mapOf(
        "Trending" to listOf("none" to "ORIGINAL", "bright" to "BRIGHT", "story" to "STORY", "grayscale" to "NATURAL", "warm" to "WARM", "cool" to "DEW"),
        "Foodie" to listOf("none" to "ORIGINAL", "warm" to "YUMMY", "bright" to "FRESH", "story" to "ZEST", "sepia" to "CRISP"),
        "Light FX" to listOf("none" to "ORIGINAL", "cool" to "GLOW", "invert" to "NEON", "bright" to "FLARE", "story" to "BEAM"),
        "Summer" to listOf("none" to "ORIGINAL", "bright" to "SUNNY", "warm" to "GOLDEN", "cool" to "BREEZE", "story" to "TROPIC"),
        "Travel" to listOf("none" to "ORIGINAL", "warm" to "WANDER", "bright" to "VOYAGE", "story" to "EXPLORE", "cool" to "DEST"),
        "Portrait" to listOf("none" to "ORIGINAL", "warm" to "SKIN", "bright" to "GLOW", "story" to "SOFT", "grayscale" to "PRO"),
        "B&W" to listOf("none" to "ORIGINAL", "grayscale" to "MONO", "story" to "NOIR", "bright" to "HIGH", "sepia" to "CLASSIC"),
        "Spring" to listOf("none" to "ORIGINAL", "bright" to "BLOOM", "warm" to "PASTEL", "story" to "FRESH", "cool" to "PETAL"),
        "Autumn" to listOf("none" to "ORIGINAL", "sepia" to "AMBER", "warm" to "HARVEST", "story" to "RUST", "grayscale" to "LEAF"),
        "Retro" to listOf("none" to "ORIGINAL", "sepia" to "VHS", "grayscale" to "GRAIN", "warm" to "FILM", "invert" to "NEG"),
        "Romance" to listOf("none" to "ORIGINAL", "warm" to "BLUSH", "story" to "DREAMY", "bright" to "VELVET", "cool" to "HEART"),
        "Chic" to listOf("none" to "ORIGINAL", "grayscale" to "VOGUE", "bright" to "MINIMAL", "story" to "SLEEK", "warm" to "ELITE"),
        "Fantasy" to listOf("none" to "ORIGINAL", "invert" to "MYSTIC", "bright" to "ETHEREAL", "story" to "AURA", "cool" to "GLOW"),
        "Aesthetic" to listOf("none" to "ORIGINAL", "grayscale" to "MOODY", "sepia" to "DUST", "story" to "VAPOR", "bright" to "PURE")
    )
    
    val categories = categoryFilters.keys.toList()
    var selectedCategory by remember { mutableStateOf("Trending") }
    val currentFilters = categoryFilters[selectedCategory] ?: emptyList()

    Column {
        // Categories Row with Static None Button
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // STATIC NONE BUTTON (Fixed on left)
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, end = 8.dp)
                    .size(36.dp) // <--- MANUALLY CHANGE HEIGHT/WIDTH FOR NONE BUTTON HERE
                    .clip(CircleShape)
                    .background(Color(0xFFF5F5F5))
                    .border(1.dp, Color.LightGray, CircleShape)
                    .clickable { 
                        if (layer != null) {
                            viewModel.updateLayerFilter(layer, "none")
                            viewModel.updateLayerBrightness(layer, 0f)
                            viewModel.updateLayerContrast(layer, 1f)
                            viewModel.updateLayerSaturation(layer, 1f)
                            viewModel.updateLayerWarmth(layer, 0f)
                            viewModel.updateLayerFade(layer, 0f)
                            viewModel.updateLayerHighlights(layer, 1f)
                            viewModel.updateLayerShadows(layer, 1f)
                        } else {
                            viewModel.resetGlobalFiltersAndAdjustments()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Block, null, modifier = Modifier.size(20.dp), tint = Color.Gray)
            }

            // Scrollable Categories
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .height(36.dp) // <--- MANUALLY CHANGE CATEGORY HEIGHT HERE
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (isSelected) Color(0xFF3F51B5).copy(alpha = 0.1f) else Color(0xFFF0F0F0))
                            .border(if (isSelected) 1.dp else 0.dp, Color(0xFF3F51B5), RoundedCornerShape(18.dp))
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(cat, fontSize = 13.sp, color = if (isSelected) Color(0xFF3F51B5) else Color.Gray, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }

        // Filter Previews Row with Static More Button
        Row(
            modifier = Modifier.fillMaxWidth().height(80.dp).padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // STATIC MORE BUTTON (Fixed on left)
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 8.dp).width(50.dp), // <--- MANUALLY CHANGE MORE BUTTON COLUMN WIDTH HERE
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(70.dp) // <--- MANUALLY CHANGE MORE BUTTON BOX SIZE HERE
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF0F0F0)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Store, null, tint = Color.Gray)
                        Text("More", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }

            // Scrollable Filter Previews
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(currentFilters) { (id, name) ->
                    val isSelected = layer?.filter == id
                    Column(
                        modifier = Modifier.width(70.dp).clickable {
                            if (layer != null) viewModel.updateLayerFilter(layer, id)
                            else viewModel.applyGlobalFilter(id)
                        },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(70.dp) // <--- MANUALLY CHANGE FILTER PREVIEW SIZE HERE
                                .clip(RoundedCornerShape(8.dp))
                                .border(if (isSelected) 2.dp else 0.dp, Color(0xFF3F51B5), RoundedCornerShape(8.dp))
                        ) {
                            // High-quality coffee placeholder image
                            Image(
                                painter = painterResource(id = R.drawable.filter_preview_coffee),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            
                            // Dark gray bottom bar with filter name
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(22.dp) // <--- MANUALLY CHANGE LABEL BAR HEIGHT HERE
                                    .align(Alignment.BottomCenter)
                                    .background(Color(0xFF4B4F55)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    name.uppercase(), 
                                    fontSize = 9.sp, 
                                    color = Color.White, 
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdjustTabContent(viewModel: FrameViewModel, layer: FrameLayer?) {
    var currentAdjustment by remember { mutableStateOf("Brightness") }
    
    // Global state for when no layer is selected
    var globalBrightness by remember { mutableStateOf(0f) }
    var globalContrast by remember { mutableStateOf(1f) }
    var globalSaturation by remember { mutableStateOf(1f) }
    var globalWarmth by remember { mutableStateOf(0f) }
    var globalFade by remember { mutableStateOf(0f) }
    var globalHighlights by remember { mutableStateOf(1f) }
    var globalShadows by remember { mutableStateOf(1f) }

    val adjustments = listOf(
        Triple("Brightness", Icons.Default.WbSunny, layer?.brightness ?: globalBrightness),
        Triple("Contrast", Icons.Default.Contrast, (layer?.contrast ?: globalContrast) - 1f), 
        Triple("Warmth", Icons.Default.Thermostat, layer?.warmth ?: globalWarmth),
        Triple("Saturation", Icons.Default.WaterDrop, (layer?.saturation ?: globalSaturation) - 1f), 
        Triple("Fade", Icons.Default.FormatLineSpacing, ((layer?.fade ?: globalFade) * 2f) - 1f), // Map 0..1 to -1..1
        Triple("Highlights", Icons.Default.KeyboardDoubleArrowUp, (layer?.highlights ?: globalHighlights) - 1f),
        Triple("Shadows", Icons.Default.KeyboardDoubleArrowDown, (layer?.shadows ?: globalShadows) - 1f)
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        // Slider
        val currentVal = adjustments.find { it.first == currentAdjustment }?.third ?: 0f
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(
                value = currentVal,
                onValueChange = { newVal ->
                    if (layer != null) {
                        when (currentAdjustment) {
                            "Brightness" -> viewModel.updateLayerBrightness(layer, newVal)
                            "Contrast" -> viewModel.updateLayerContrast(layer, newVal + 1f)
                            "Saturation" -> viewModel.updateLayerSaturation(layer, newVal + 1f)
                            "Warmth" -> viewModel.updateLayerWarmth(layer, newVal)
                            "Fade" -> viewModel.updateLayerFade(layer, (newVal + 1f) / 2f)
                            "Highlights" -> viewModel.updateLayerHighlights(layer, newVal + 1f)
                            "Shadows" -> viewModel.updateLayerShadows(layer, newVal + 1f)
                        }
                    } else {
                        when (currentAdjustment) {
                            "Brightness" -> { globalBrightness = newVal; viewModel.applyGlobalBrightness(newVal) }
                            "Contrast" -> { globalContrast = newVal + 1f; viewModel.applyGlobalContrast(newVal + 1f) }
                            "Saturation" -> { globalSaturation = newVal + 1f; viewModel.applyGlobalSaturation(newVal + 1f) }
                            "Warmth" -> { globalWarmth = newVal; viewModel.applyGlobalWarmth(newVal) }
                            "Fade" -> { globalFade = (newVal + 1f) / 2f; viewModel.applyGlobalFade((newVal + 1f) / 2f) }
                            "Highlights" -> { globalHighlights = newVal + 1f; viewModel.applyGlobalHighlights(newVal + 1f) }
                            "Shadows" -> { globalShadows = newVal + 1f; viewModel.applyGlobalShadows(newVal + 1f) }
                        }
                    }
                },
                valueRange = -1f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.DarkGray,
                    activeTrackColor = Color.DarkGray,
                    inactiveTrackColor = Color.LightGray
                )
            )
            Text(
                "${(currentVal * 100).toInt()}", 
                fontSize = 12.sp, 
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.End
            )
        }

        // Adjustment Icons
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(adjustments) { (name, icon, value) ->
                val isSelected = currentAdjustment == name
                val isChanged = abs(value) > 0.01f
                
                Column(
                    modifier = Modifier.clickable { currentAdjustment = name },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFFF0F0F0) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            icon, 
                            null, 
                            tint = if (isSelected) Color.Black else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        if (isChanged && !isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3F51B5))
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-4).dp, y = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        name, 
                        fontSize = 10.sp, 
                        color = if (isSelected) Color.Black else Color.Gray,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun StickersPanelContent(viewModel: FrameViewModel, uiState: FrameUiState, onClose: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(1) } // 0:Recent, 1:All, 2:Smileys, 3:People, 4:Animals, 5:Food, 6:Activities, 7:Travel, 8:Objects, 9:Symbols, 10:Flags, 11:Flower, 12:Sakura, 13:Bunny, 14:Premium
    
    val floraStickers = listOf("daisy.png", "sakura.png")
    val bunnyStickers = listOf("bunny.png")
    val premiumStickers = listOf("nezuko.png")

    Column(modifier = Modifier.fillMaxWidth().height(200.dp).background(Color.White)) {
        // 1. Header with Scrollable Tabs
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Scrollable Icon Bar
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 0. Recent (History)
                TabIconSmall(Icons.Default.History, selectedTab == 0) { selectedTab = 0 }
                
                // Emoji Categories (1-9)
                TabIconSmall(Icons.AutoMirrored.Filled.List, selectedTab == 1) { selectedTab = 1 } // All (Fallback) - or just start from Smileys
                TabIconSmall(Icons.Default.SentimentSatisfiedAlt, selectedTab == 2) { selectedTab = 2 } // Smileys
                TabIconSmall(Icons.Default.EmojiPeople, selectedTab == 3) { selectedTab = 3 } // People
                TabIconSmall(Icons.Default.Pets, selectedTab == 4) { selectedTab = 4 } // Animals
                TabIconSmall(Icons.Default.Restaurant, selectedTab == 5) { selectedTab = 5 } // Food
                TabIconSmall(Icons.Default.SportsBasketball, selectedTab == 6) { selectedTab = 6 } // Activities
                TabIconSmall(Icons.Default.Train, selectedTab == 7) { selectedTab = 7 } // Travel
                TabIconSmall(Icons.Default.Lightbulb, selectedTab == 8) { selectedTab = 8 } // Objects
                TabIconSmall(Icons.Default.Translate, selectedTab == 9) { selectedTab = 9 } // Symbols
                TabIconSmall(Icons.Default.Flag, selectedTab == 10) { selectedTab = 10 } // Flags
                
                // Local Stickers (11-13)
                TabIconSmall(Icons.Default.LocalFlorist, selectedTab == 11) { selectedTab = 11 } // Flower
                TabIconSmall(Icons.Default.FilterVintage, selectedTab == 12) { selectedTab = 12 } // Sakura
                TabIconSmall(Icons.Default.ChildCare, selectedTab == 13) { selectedTab = 13 } // Bunny
                TabIconSmall(Icons.Default.AutoAwesome, selectedTab == 14) { selectedTab = 14 } // Premium
            }
            
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Check, null, tint = Color(0xFF3F51B5), modifier = Modifier.size(20.dp))
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.3f))

        // 2. Content Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            when (selectedTab) {
                0 -> { // Recent
                    items(uiState.recentEmojis) { emoji ->
                        EmojiItem(emoji) { viewModel.addEmojiLayer(emoji) }
                    }
                }
                1 -> { // All (Smileys)
                    items(EmojiData.smileys) { emoji ->
                        EmojiItem(emoji) { viewModel.addEmojiLayer(emoji) }
                    }
                }
                2 -> items(EmojiData.smileys) { emoji -> EmojiItem(emoji) { viewModel.addEmojiLayer(emoji) } }
                3 -> items(EmojiData.people) { emoji -> EmojiItem(emoji) { viewModel.addEmojiLayer(emoji) } }
                4 -> items(EmojiData.animals) { emoji -> EmojiItem(emoji) { viewModel.addEmojiLayer(emoji) } }
                5 -> items(EmojiData.food) { emoji -> EmojiItem(emoji) { viewModel.addEmojiLayer(emoji) } }
                6 -> items(EmojiData.activities) { emoji -> EmojiItem(emoji) { viewModel.addEmojiLayer(emoji) } }
                7 -> items(EmojiData.travel) { emoji -> EmojiItem(emoji) { viewModel.addEmojiLayer(emoji) } }
                8 -> items(EmojiData.objects) { emoji -> EmojiItem(emoji) { viewModel.addEmojiLayer(emoji) } }
                9 -> items(EmojiData.symbols) { emoji -> EmojiItem(emoji) { viewModel.addEmojiLayer(emoji) } }
                10 -> items(EmojiData.flags) { emoji -> EmojiItem(emoji) { viewModel.addEmojiLayer(emoji) } }
                
                11 -> { // Flower
                    items(floraStickers) { path ->
                        StickerGridItem("stickers/$path") { viewModel.addStickerLayer("stickers/$path") }
                    }
                }
                12 -> { // Sakura
                    items(floraStickers.filter { it.contains("sakura") }) { path ->
                        StickerGridItem("stickers/$path") { viewModel.addStickerLayer("stickers/$path") }
                    }
                }
                13 -> { // Bunny
                    items(bunnyStickers) { path ->
                        StickerGridItem("stickers/$path") { viewModel.addStickerLayer("stickers/$path") }
                    }
                }
                14 -> { // Premium (Nezuko)
                    items(premiumStickers) { path ->
                        StickerGridItem("stickers/$path") { viewModel.addStickerLayer("stickers/$path") }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabIconSmall(icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, 
            null, 
            tint = if (isSelected) Color(0xFF3F51B5) else Color.Gray,
            modifier = Modifier.size(22.dp)
        )
        if (isSelected) {
            Box(Modifier.align(Alignment.BottomCenter).width(16.dp).height(2.dp).background(Color(0xFF3F51B5)))
        }
    }
}

@Composable
private fun EmojiItem(emoji: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.aspectRatio(1f).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 24.sp)
    }
}

@Composable
private fun StickerGridItem(assetPath: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF5F5F5)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = "file:///android_asset/$assetPath",
            contentDescription = null,
            modifier = Modifier.fillMaxSize(0.8f),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun OpacityPanelContent(viewModel: FrameViewModel, layer: FrameLayer) {
    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (layer.type == LayerType.IMAGE) {
            // Simplified Opacity for Images
            SliderCol("Opacity", layer.opacity * 100f, 0f, 100f) { viewModel.updateLayerOpacity(layer, it / 100f) }
            Spacer(Modifier.height(8.dp))
        } else {
            // Full Styling for Text/Others
            // Row 1: Layer Opacity | BG Opacity
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    SliderCol("Opacity", layer.opacity * 100f, 0f, 100f) { viewModel.updateLayerOpacity(layer, it / 100f) }
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    val currentBgColor = Color(layer.backgroundColor)
                    val bgAlpha = if (layer.backgroundColor == android.graphics.Color.TRANSPARENT) 0f else currentBgColor.alpha
                    SliderCol("BG Opacity", bgAlpha * 100f, 0f, 100f) { alphaPercent ->
                        val alpha = alphaPercent / 100f
                        if (layer.backgroundColor != android.graphics.Color.TRANSPARENT) {
                            val newColor = Color(layer.backgroundColor).copy(alpha = alpha).toArgb()
                            viewModel.updateLayerBackgroundColor(layer, newColor)
                        } else if (alpha > 0) {
                            val newColor = Color.Black.copy(alpha = alpha).toArgb()
                            viewModel.updateLayerBackgroundColor(layer, newColor)
                        }
                    }
                }
            }

            // Row 2: Corner Radius | Rotation
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    SliderCol("Radius", layer.backgroundRadius, 0f, 100f) { viewModel.updateLayerBackgroundRadius(layer, it) }
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    SliderCol("Rotation", layer.rotation, 0f, 360f) { viewModel.setLayerRotation(layer, it) }
                }
            }

            ColorSection("Background Color", layer.backgroundColor, showNone = true) { selectedColor ->
                val bgAlpha = if (layer.backgroundColor == android.graphics.Color.TRANSPARENT) 1f else Color(layer.backgroundColor).alpha
                val colorWithAlpha = if (selectedColor == Color.Transparent) {
                    android.graphics.Color.TRANSPARENT
                } else {
                    selectedColor.copy(alpha = bgAlpha).toArgb()
                }
                viewModel.updateLayerBackgroundColor(layer, colorWithAlpha)
            }
        }
    }
}

@Composable
private fun AdvancedFontPanelContent(viewModel: FrameViewModel, layer: FrameLayer) {
    val fonts = listOf(
        "Roboto", "Serif", "Sans-Serif", "Cursive", "Monospace",
        "Georgia", "Verdana", "Courier", "Trebuchet", "Impact", "Arial", "Times"
    )
    val displayNames = listOf(
        "Roboto-Medium", "Roboto-Bold", "Permanent Marker", "BEBAS", "Roboto-Thin", "Caviar Dreams", "Aleo", "Amatic SC", "Daniel", "Satisfy", "Lobster", "Bangers"
    )
    
    // Simple category mapping based on indexes for demonstration
    val fontCategories = listOf("Regular", "Bold", "Handwritten", "Doodle", "Decorative", "English", "Regular", "Handwritten", "Doodle", "Decorative", "English", "Decorative")
    
    val categories = listOf("All", "My", "Hot", "Bold", "Handwritten", "Regular", "English", "Doodle", "Decorative", "Espanol")
    var selectedCat by remember { mutableStateOf("All") }

    val filteredIndices = remember(selectedCat) {
        if (selectedCat == "All") displayNames.indices.toList()
        else if (selectedCat == "My") displayNames.indices.toList() // Mock same as All
        else if (selectedCat == "Hot") displayNames.indices.toList() // Mock same as All
        else if (selectedCat == "Espanol") displayNames.indices.toList() // Mock
        else {
            displayNames.indices.filter { fontCategories.getOrElse(it) { "Regular" } == selectedCat }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Category Tabs
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Storefront, null, tint = Color.Gray, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.Folder, null, tint = Color.Gray, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(categories) { cat ->
                    val isSel = selectedCat == cat
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .widthIn(min = 60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSel) Color(0xFF3F51B5) else Color(0xFFF5F5F5))
                            .clickable { selectedCat = cat }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Text(
                            text = cat, 
                            fontSize = 11.sp, 
                            color = if (isSel) Color.White else Color.Gray, 
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Font Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredIndices) { idx ->
                val name = displayNames[idx]
                val fontName = fonts.getOrElse(idx) { "Roboto" }
                val isSel = layer.font == fontName
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) Color(0xFFEEEEEE) else Color(0xFFF9F9F9))
                        .border(1.dp, if (isSel) Color(0xFF3F51B5) else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { viewModel.updateLayerFont(layer, fontName) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.Text(
                            text = "Collage", 
                            fontSize = 16.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = Color.Black,
                            fontFamily = getFontFamily(fontName)
                        )
                        androidx.compose.material3.Text(name, fontSize = 8.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

private fun getFontFamily(fontName: String): FontFamily {
    return when (fontName.lowercase()) {
        "serif" -> FontFamily.Serif
        "monospace" -> FontFamily.Monospace
        "cursive" -> FontFamily.Cursive
        "sans-serif" -> FontFamily.SansSerif
        else -> FontFamily.Default
    }
}

private enum class StyleSubTab(val label: String) {
    Text("Text"), Background("Background"), Border("Border"), Shadow("Shadow"), Align("Align")
}

@Composable
private fun StylePanelContent(viewModel: FrameViewModel, layer: FrameLayer) {
    var selectedSubTab by remember { mutableStateOf(StyleSubTab.Text) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Sub-tab Selection (Pills)
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(StyleSubTab.values()) { tab ->
                val isSel = selectedSubTab == tab
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSel) Color(0xFF3F51B5) else Color(0xFFEEEEEE))
                        .clickable { selectedSubTab = tab }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab.label,
                        fontSize = 14.sp,
                        color = if (isSel) Color.White else Color.Gray,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.3f))

        // 2. Sub-tab Content
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        ) {
            when (selectedSubTab) {
                StyleSubTab.Text -> TextStyleContent(viewModel, layer)
                StyleSubTab.Background -> BackgroundStyleContent(viewModel, layer)
                StyleSubTab.Border -> BorderStyleContent(viewModel, layer)
                StyleSubTab.Shadow -> ShadowStyleContent(viewModel, layer)
                StyleSubTab.Align -> AlignStyleContent(viewModel, layer)
            }
        }
    }
}

@Composable
private fun TextStyleContent(viewModel: FrameViewModel, layer: FrameLayer) {
    // Color Picker
    StyleColorRow(
        selectedColor = layer.color,
        onColorSelected = { viewModel.updateLayerColor(layer, it.toArgb()) }
    )
    
    // Sliders
    StyleSlider("FontSize", layer.fontSize, 8f, 200f, Icons.Default.TextFields) { viewModel.updateLayerFontSize(layer, it) }
    StyleSlider("Opacity", layer.opacity * 100f, 0f, 100f, Icons.Default.GridOn) { viewModel.updateLayerOpacity(layer, it / 100f) }
}

@Composable
private fun BackgroundStyleContent(viewModel: FrameViewModel, layer: FrameLayer) {
    StyleColorRow(
        selectedColor = layer.backgroundColor,
        showNone = true,
        onColorSelected = { viewModel.updateLayerBackgroundColor(layer, it.toArgb()) }
    )
    
    StyleSlider("Opacity", layer.backgroundOpacity * 100f, 0f, 100f, Icons.Default.GridOn) { viewModel.updateLayerBackgroundOpacity(layer, it / 100f) }
    StyleSlider("Radius", layer.backgroundRadius, 0f, 50f, Icons.Default.RoundedCorner) { viewModel.updateLayerBackgroundRadius(layer, it) }
}

@Composable
private fun BorderStyleContent(viewModel: FrameViewModel, layer: FrameLayer) {
    StyleColorRow(
        selectedColor = layer.strokeColor,
        showNone = true,
        onColorSelected = { viewModel.updateLayerStroke(layer, color = it.toArgb()) }
    )
    
    StyleSlider("Width", layer.strokeWidth, 0f, 20f, Icons.Default.LineWeight) { viewModel.updateLayerStroke(layer, width = it) }
}

@Composable
private fun ShadowStyleContent(viewModel: FrameViewModel, layer: FrameLayer) {
    StyleColorRow(
        selectedColor = layer.shadowColor,
        showNone = true,
        onColorSelected = { viewModel.updateLayerShadow(layer, color = it.toArgb()) }
    )
    
    StyleSlider("Blur", layer.shadowBlur, 0f, 30f, Icons.Default.BlurOn) { viewModel.updateLayerShadow(layer, blur = it) }
    StyleSlider("Offset", layer.shadowOffsetX, -20f, 20f, Icons.Default.OpenWith) { viewModel.updateLayerShadow(layer, offsetX = it, offsetY = it) }
}

@Composable
private fun AlignStyleContent(viewModel: FrameViewModel, layer: FrameLayer) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Group 1: Formatting (U, B, I, S)
            StyleGroup {
                StyleGroupButton(onClick = { viewModel.toggleLayerUnderline(layer) }, isSelected = layer.isUnderline) {
                    Icon(Icons.Default.FormatUnderlined, null, tint = if (layer.isUnderline) Color(0xFF3F51B5) else Color.Gray, modifier = Modifier.size(20.dp))
                }
                StyleGroupButton(onClick = { viewModel.toggleLayerBold(layer) }, isSelected = layer.isBold) {
                    Icon(Icons.Default.FormatBold, null, tint = if (layer.isBold) Color(0xFF3F51B5) else Color.Gray, modifier = Modifier.size(20.dp))
                }
                StyleGroupButton(onClick = { viewModel.toggleLayerItalic(layer) }, isSelected = layer.isItalic) {
                    Icon(Icons.Default.FormatItalic, null, tint = if (layer.isItalic) Color(0xFF3F51B5) else Color.Gray, modifier = Modifier.size(20.dp))
                }
                StyleGroupButton(onClick = { viewModel.toggleLayerStrikethrough(layer) }, isSelected = layer.isStrikethrough) {
                    Icon(Icons.Default.StrikethroughS, null, tint = if (layer.isStrikethrough) Color(0xFF3F51B5) else Color.Gray, modifier = Modifier.size(20.dp))
                }
            }
            
            // Group 2: Alignment
            StyleGroup {
                StyleGroupButton(onClick = { viewModel.updateLayerJustification(layer, "left") }, isSelected = layer.justification == "left") {
                    Icon(Icons.AutoMirrored.Filled.FormatAlignLeft, null, tint = if (layer.justification == "left") Color(0xFF3F51B5) else Color.Gray, modifier = Modifier.size(20.dp))
                }
                StyleGroupButton(onClick = { viewModel.updateLayerJustification(layer, "center") }, isSelected = layer.justification == "center") {
                    Icon(Icons.Default.FormatAlignCenter, null, tint = if (layer.justification == "center") Color(0xFF3F51B5) else Color.Gray, modifier = Modifier.size(20.dp))
                }
                StyleGroupButton(onClick = { viewModel.updateLayerJustification(layer, "right") }, isSelected = layer.justification == "right") {
                    Icon(Icons.AutoMirrored.Filled.FormatAlignRight, null, tint = if (layer.justification == "right") Color(0xFF3F51B5) else Color.Gray, modifier = Modifier.size(20.dp))
                }
            }

            // Group 3: Case
            StyleGroup {
                StyleGroupButton(onClick = { viewModel.updateLayerTextCase(layer, "titlecase") }, isSelected = layer.textCase == "titlecase") {
                    Text("Ao", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (layer.textCase == "titlecase") Color(0xFF3F51B5) else Color.Gray)
                }
                StyleGroupButton(onClick = { viewModel.updateLayerTextCase(layer, "uppercase") }, isSelected = layer.textCase == "uppercase") {
                    Text("AA", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (layer.textCase == "uppercase") Color(0xFF3F51B5) else Color.Gray)
                }
                StyleGroupButton(onClick = { viewModel.updateLayerTextCase(layer, "none") }, isSelected = layer.textCase == "none") {
                    Text("Aa", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (layer.textCase == "none") Color(0xFF3F51B5) else Color.Gray)
                }
                StyleGroupButton(onClick = { viewModel.updateLayerTextCase(layer, "lowercase") }, isSelected = layer.textCase == "lowercase") {
                    Text("aa", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (layer.textCase == "lowercase") Color(0xFF3F51B5) else Color.Gray)
                }
            }
        }

        StyleSlider("Spacing", layer.letterSpacing * 10f, -10f, 50f, Icons.Default.FormatSize) { viewModel.updateLayerLetterSpacing(layer, it / 10f) }
        StyleSlider("LineHeight", layer.lineHeight * 50f, -50f, 100f, Icons.Default.FormatLineSpacing) { viewModel.updateLayerLineHeight(layer, it / 50f) }
    }
}

@Composable
private fun StyleGroup(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F5F5))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun StyleGroupButton(onClick: () -> Unit, isSelected: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) Color(0xFFE8EAF6) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// Remove unused AlignmentButton and CaseButton

@Composable
private fun StyleSlider(label: String, value: Float, min: Float, max: Float, icon: ImageVector, onValueChange: (Float) -> Unit) {
    var internalValue by remember(value) { mutableStateOf(value) }
    
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Slider(
            value = internalValue,
            onValueChange = { 
                internalValue = it
                onValueChange(it)
            },
            valueRange = min..max,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Color(0xFF3F51B5), activeTrackColor = Color(0xFF3F51B5).copy(alpha = 0.5f))
        )
        Spacer(Modifier.width(5.dp))
        Text("${internalValue.roundToInt()}", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(20.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun StyleColorRow(selectedColor: Int, showNone: Boolean = false, onColorSelected: (Color) -> Unit) {
    val colors = listOf(
        Color.White, Color.LightGray, Color.Gray, Color.DarkGray, Color.Black,
        Color(0xFFFFCCCC), Color(0xFFFF9999), Color(0xFFFF6666), Color(0xFFFF3333),
        Color(0xFFCCFFCC), Color(0xFF99FF99), Color(0xFF66FF66), Color(0xFF33FF33),
        Color(0xFFCCCCFF), Color(0xFF9999FF), Color(0xFF6666FF), Color(0xFF3333FF)
    )
    
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFFF5F5F5)).clickable { /* Picker */ },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Colorize, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }
        
        if (showNone) {
            item {
                val isNone = selectedColor == android.graphics.Color.TRANSPARENT
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isNone) Color(0xFFE8EAF6) else Color.Transparent)
                        .border(1.dp, if (isNone) Color(0xFF3F51B5) else Color.LightGray, CircleShape)
                        .clickable { onColorSelected(Color.Transparent) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Block, null, tint = if (isNone) Color(0xFF3F51B5) else Color.Gray, modifier = Modifier.size(16.dp))
                }
            }
        }

        items(colors) { color ->
            val isSel = selectedColor == color.toArgb()
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(if (isSel) 2.dp else 1.dp, if (isSel) Color(0xFF3F51B5) else Color.LightGray, CircleShape)
                    .clickable { onColorSelected(color) }
            )
        }
    }
}

@Composable
private fun PresetPanelContent(viewModel: FrameViewModel, layer: FrameLayer) {
    var selectedCategory by remember { mutableStateOf("Hot") }
    val categories = listOf("None", "Mood", "Hot", "Bubble", "Simple")
    
    val allPresets = listOf(
        Pair("Modern", "Simple"), Pair("Minimal", "Simple"), Pair("Elegant", "Simple"), 
        Pair("Dark", "Simple"), Pair("SoftCloud", "Simple"), Pair("Classic", "Simple"),
        Pair("Broken", "Hot"), Pair("OMG", "Hot"), Pair("so fetch", "Hot"),
        Pair("ABSOLUTELY CHILL", "Hot"), Pair("KindaLoveThis", "Hot"), Pair("Neon", "Hot"),
        Pair("LoveStory", "Mood"), Pair("SmallJoy", "Mood"), Pair("AtHome", "Mood"), Pair("LatteLove", "Mood"),
        Pair("DoingMyBest", "Mood"), Pair("KindaLoveThis", "Bubble"),
        Pair("BubblePink", "Bubble"), Pair("ThinkPink", "Bubble"), Pair("ThinkWhite", "Bubble"),
        Pair("HeartPremium", "Bubble"), Pair("BubblePremium", "Bubble"),
        Pair("SASSY BUT CLASSY", "Bubble"), Pair("WEEKEND MODE", "Bubble"),
        Pair("obsessed", "Bubble"), Pair("love ya", "Bubble")
    )
    
    val filteredPresets = if (selectedCategory == "None") emptyList() 
                         else allPresets.filter { it.second == selectedCategory }.map { it.first }

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Category Tabs (Pills)
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(categories) { cat ->
                val isSel = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSel) Color(0xFF3F51B5) else Color(0xFFEEEEEE))
                        .clickable { selectedCategory = cat }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (cat == "None") {
                        Icon(Icons.Default.Block, null, tint = if (isSel) Color.White else Color.Gray, modifier = Modifier.size(18.dp))
                    } else {
                        androidx.compose.material3.Text(
                            text = cat,
                            fontSize = 12.sp,
                            color = if (isSel) Color.White else Color.Gray,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.3f))

        // 2. Preset Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredPresets) { preset ->
                PresetPreviewItem(
                    presetId = preset,
                    isSelected = layer.presetId == preset,
                    onClick = { 
                        viewModel.applyTextPreset(layer, preset)
                    }
                )
            }
        }
    }
}
@Composable
private fun CurvePanelContent(viewModel: FrameViewModel, layer: FrameLayer) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 25.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp) // ✅ FIXED SPACING
    ) {

        // 1. Slider with Icons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Down Curve Icon
            Canvas(modifier = Modifier.size(24.dp)) {
                drawArc(
                    color = Color.Black,
                    startAngle = 30f,
                    sweepAngle = 120f,
                    useCenter = false,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }

            Slider(
                value = layer.curve,
                onValueChange = { viewModel.updateLayerCurve(layer, it) },
                valueRange = -1f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color.Black,
                    activeTrackColor = Color.LightGray,
                    inactiveTrackColor = Color.LightGray,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )

            // Up Curve Icon
            Canvas(modifier = Modifier.size(24.dp)) {
                drawArc(
                    color = Color.Black,
                    startAngle = 210f,
                    sweepAngle = 120f,
                    useCenter = false,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )
            }
        }

        // 2. Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 30.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {

            // None Button
            CurveOptionButton(
                selected = layer.curve == 0f,
                onClick = { viewModel.updateLayerCurve(layer, 0f) }
            ) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    drawCircle(
                        color = Color.Gray,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawLine(
                        color = Color.Gray,
                        start = Offset(6.dp.toPx(), 18.dp.toPx()),
                        end = Offset(18.dp.toPx(), 6.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }

            // Arc Button
            CurveOptionButton(
                selected = layer.curveType == "arc" && layer.curve != 0f,
                onClick = {
                    viewModel.updateLayerCurveType(layer, "arc")
                    if (layer.curve == 0f) {
                        viewModel.updateLayerCurve(layer, 0.5f)
                    }
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "ABC",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Gray
                    )
                    Canvas(modifier = Modifier.size(width = 24.dp, height = 8.dp)) {
                        drawArc(
                            color = Color.Gray,
                            startAngle = 210f,
                            sweepAngle = 120f,
                            useCenter = false,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
            }

            // Wave Button
            CurveOptionButton(
                selected = layer.curveType == "wave",
                onClick = {
                    viewModel.updateLayerCurveType(layer, "wave")
                    if (layer.curve == 0f) {
                        viewModel.updateLayerCurve(layer, 0.5f)
                    }
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    val textColor =
                        if (layer.curveType == "wave") Color.Black else Color.LightGray
                    val iconColor =
                        if (layer.curveType == "wave") Color.Black else Color.LightGray

                    Text(
                        "ABC",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = textColor
                    )

                    Canvas(modifier = Modifier.size(width = 24.dp, height = 8.dp)) {
                        val path = Path().apply {
                            moveTo(0f, size.height / 2f)
                            cubicTo(
                                size.width * 0.25f, 0f,
                                size.width * 0.25f, 0f,
                                size.width * 0.5f, size.height / 2f
                            )
                            cubicTo(
                                size.width * 0.75f, size.height,
                                size.width * 0.75f, size.height,
                                size.width, size.height / 2f
                            )
                        }
                        drawPath(
                            path = path,
                            color = iconColor,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurveOptionButton(
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
        if (selected) {
            // Optional: add a small blue dot or circle if you want to match the "blue highlight" look in some editors, 
            // but the screenshot appears quite minimal.
        }
    }
}

@Composable
private fun FormatBtnCompact(icon: ImageVector, active: Boolean, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) AppColors.Primary.copy(alpha = 0.1f) else Color(0xFFF5F5F5))
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (active) AppColors.Primary else Color.Black, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FontSizePanelContent(viewModel: FrameViewModel, layer: FrameLayer) {
    Column(modifier = Modifier.padding(16.dp)) {
        SliderCol("Font Size", layer.fontSize, 8f, 300f) { viewModel.updateLayerFontSize(layer, it) }
    }
}

@Composable
private fun ColorPanelContent(viewModel: FrameViewModel, layer: FrameLayer) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Color", "Border", "Shadow")

    Column {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = AppColors.Primary,
            divider = {},
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = AppColors.Primary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 14.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp).verticalScroll(rememberScrollState())) {
            when (selectedTab) {
                0 -> {
                    ColorSection("Text Color", layer.color, showNone = false) {
                        viewModel.updateLayerColor(layer, it.toArgb())
                    }
                }
                1 -> {
                    SliderCol("Border Width", layer.strokeWidth, 0f, 50f) {
                        viewModel.updateLayerStroke(layer, width = it)
                    }
                    Spacer(Modifier.height(8.dp))
                    ColorSection("Border Color", layer.strokeColor, showNone = true) {
                        viewModel.updateLayerStroke(layer, color = it.toArgb())
                    }
                }
                2 -> {
                    SliderCol("Shadow Blur", layer.shadowBlur, 0f, 40f) { viewModel.updateLayerShadow(layer, blur = it) }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f)) {
                            SliderCol("X", layer.shadowOffsetX, -30f, 30f) { viewModel.updateLayerShadow(layer, offsetX = it) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            SliderCol("Y", layer.shadowOffsetY, -30f, 30f) { viewModel.updateLayerShadow(layer, offsetY = it) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ColorSection("Shadow Color", layer.shadowColor, showNone = true) { 
                        viewModel.updateLayerShadow(layer, color = it.toArgb()) 
                    }
                }
            }
        }
    }
}

@Composable
private fun BGColorPanelContent(viewModel: FrameViewModel, layer: FrameLayer) {
    Column(modifier = Modifier.padding(24.dp)) {
        ColorSection("Background Color", layer.backgroundColor, showNone = true) {
            viewModel.updateLayerBackgroundColor(layer, it.toArgb())
        }
    }
}

@Composable
private fun ColorSection(title: String, selectedColor: Int, showNone: Boolean, onColorSelected: (Color) -> Unit) {
    Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Black.copy(alpha=0.6f))
    Spacer(Modifier.height(4.dp))
    ColorRow(selectedColor, showNone = showNone, onCh = onColorSelected)
}

@Composable
private fun RotatePanelContent(viewModel: FrameViewModel, layer: FrameLayer) {
    Column(modifier = Modifier.padding(16.dp)) {
        SliderCol("Rotation", layer.rotation, 0f, 360f) { viewModel.setLayerRotation(layer, it) }
    }
}

@Composable
private fun ResizePanelContent(viewModel: FrameViewModel, layer: FrameLayer) {
    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) {
                SliderCol("Width", layer.width, 20f, 1500f) { viewModel.setLayerSize(layer, it, layer.height) }
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                SliderCol("Height", layer.height, 20f, 1500f) { viewModel.setLayerSize(layer, layer.width, it) }
            }
        }
    }
}

@Composable
private fun TextFormatPanelContent(viewModel: FrameViewModel, layer: FrameLayer) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Style Group
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                FormatBtn(Icons.Default.FormatBold, layer.isBold) { viewModel.toggleLayerBold(layer) }
                FormatBtn(Icons.Default.FormatItalic, layer.isItalic) { viewModel.toggleLayerItalic(layer) }
                FormatBtn(Icons.Default.FormatUnderlined, layer.isUnderline) { viewModel.toggleLayerUnderline(layer) }
                FormatBtn(Icons.Default.FormatStrikethrough, layer.isStrikethrough) { viewModel.toggleLayerStrikethrough(layer) }
            }
            
            Box(Modifier.width(1.dp).height(20.dp).background(Color.LightGray.copy(alpha=0.5f)))

            // Alignment Group
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                FormatBtn(Icons.Default.FormatAlignLeft, layer.justification == "left") { viewModel.updateLayerJustification(layer, "left") }
                FormatBtn(Icons.Default.FormatAlignCenter, layer.justification == "center") { viewModel.updateLayerJustification(layer, "center") }
                FormatBtn(Icons.Default.FormatAlignRight, layer.justification == "right") { viewModel.updateLayerJustification(layer, "right") }
            }
        }
        
        Spacer(Modifier.height(12.dp))
        SliderCol("Font Size", layer.fontSize, 8f, 300f, { viewModel.updateLayerFontSize(layer, it) })
    }
}

@Composable
private fun FormatBtn(icon: ImageVector, active: Boolean, onTap: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .padding(2.dp)
            .background(if (active) AppColors.Primary.copy(alpha=0.1f) else Color.Transparent, RoundedCornerShape(6.dp))
            .border(1.dp, if (active) AppColors.Primary else Color.LightGray.copy(alpha=0.6f), RoundedCornerShape(6.dp))
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (active) AppColors.Primary else Color.Black.copy(alpha=0.7f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SliderCol(label: String, value: Float, min: Float, max: Float, onCh: (Float) -> Unit) {
    Column {
        Text("$label: ${String.format("%.1f", value)}", fontSize = 12.sp, color = Color.Black.copy(alpha=0.54f), modifier = Modifier.padding(start = 4.dp))
        Slider(value = value.coerceIn(min, max), onValueChange = onCh, valueRange = min..max, colors = SliderDefaults.colors(thumbColor = AppColors.Primary, activeTrackColor = AppColors.Primary))
    }
}

@Composable
private fun ColorRow(selColorInt: Int, showNone: Boolean = false, onCh: (Color) -> Unit) {
    val colors = buildList {
        if (showNone) add(Color.Transparent)
        addAll(listOf(
            Color.Black, Color.White, 
            Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
            Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF00BCD4),
            Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39),
            Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722),
            Color(0xFF795548), Color(0xFF9E9E9E), Color(0xFF607D8B)
        ))
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(colors) { c ->
            val isNone = c == Color.Transparent
            val isSel = if (isNone) selColorInt == android.graphics.Color.TRANSPARENT else c.toArgb() == selColorInt
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(if (isNone) Color.White else c, CircleShape)
                    .border(
                        width = if (isSel) 3.dp else 1.dp,
                        color = if (isSel) AppColors.Primary else Color.LightGray.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable { onCh(c) },
                contentAlignment = Alignment.Center
            ) {
                if (isNone) {
                    Icon(Icons.Default.Block, null, tint = Color.Red, modifier = Modifier.size(24.dp))
                } else if (isSel) {
                    Icon(
                        Icons.Default.Check,
                        null,
                        tint = if (c == Color.White || c == Color.Yellow || c == Color.Cyan) Color.Black else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}


@Composable
private fun ColorRowSlim(selColorInt: Int, showNone: Boolean = false, onCh: (Color) -> Unit) {
    val colors = buildList {
        if (showNone) add(Color.Transparent)
        addAll(listOf(
            Color.Black, Color.White, 
            Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7),
            Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4), Color(0xFF00BCD4),
            Color(0xFF009688), Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39),
            Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722),
            Color(0xFF795548), Color(0xFF9E9E9E), Color(0xFF607D8B)
        ))
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().height(40.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(colors) { c ->
            val isNone = c == Color.Transparent
            val isSel = if (isNone) selColorInt == android.graphics.Color.TRANSPARENT else c.toArgb() == selColorInt
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(if (isNone) Color.White else c, CircleShape)
                    .border(
                        width = if (isSel) 2.dp else 1.dp,
                        color = if (isSel) Color(0xFF3F51B5) else Color.LightGray.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
                    .clickable { onCh(c) },
                contentAlignment = Alignment.Center
            ) {
                if (isNone) {
                    Icon(Icons.Default.Block, null, tint = Color.Red, modifier = Modifier.size(16.dp))
                } else if (isSel) {
                    Icon(
                        Icons.Default.Check,
                        null,
                        tint = if (c == Color.White || c == Color.Yellow || c == Color.Cyan) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

private fun uriToFile(context: Context, uri: Uri): File? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val tempFile = File.createTempFile("picked_", ".jpg", context.cacheDir)
        tempFile.outputStream().use { output -> inputStream.copyTo(output) }
        tempFile
    } catch (_: Exception) {
        null
    }
}

private suspend fun saveImage(context: Context, bitmap: Bitmap?) {
    if (bitmap == null) return
    withContext(Dispatchers.IO) {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        val fileName = "frame_${System.currentTimeMillis()}.png"
        val file = File(dir, fileName)
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}

@Composable
private fun PresetPreviewItem(
    presetId: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Simplified mapping for preview
    val (style, previewText) = when (presetId) {
        "Modern" -> Quad(Color.White, Color.Black, null, null) to "Modern"
        "Minimal" -> Quad(Color.White, Color.Black, "cloud", null) to "Simple"
        "Elegant" -> Quad(Color(0xFF37474F), Color.White, null, null) to "Elegant"
        "Dark" -> Quad(Color.Black, Color.White, "burst", null) to "Dark"
        "SoftCloud" -> Quad(Color.White, Color(0xFFF06292), "cloud", null) to "Soft"
        "Classic" -> Quad(Color.White, Color.Black, "bubble_right", null) to "Classic"
        "Neon" -> Quad(Color.Black, Color.Cyan, null, null) to "Neon"
        "LoveStory" -> Quad(Color(0xFFFCE4EC), Color(0xFFC2185B), "heart", null) to "Love"
        "KindaLoveThis" -> Quad(Color(0xFFF06292), Color.White, "bubble_right", null) to "Love"
        "SASSY BUT CLASSY" -> Quad(Color(0xFFFFF176), Color(0xFF795548), "bubble_left", null) to "Sassy"
        "obsessed" -> Quad(Color.White, Color.Black, "cloud", null) to "Obsessed"
        "Broken" -> Quad(Color.Black, Color.White, "heart", null) to "Broken"
        "OMG" -> Quad(Color.White, Color.Black, "burst", null) to "OMG"
        "love ya" -> Quad(Color(0xFFFCE4EC), Color(0xFFE91E63), "cloud", null) to "LoveYa"
        "so fetch" -> Quad(Color(0xFFF06292), Color.White, "heart", null) to "Fetch"
        "WEEKEND MODE" -> Quad(Color.White, Color(0xFF4CAF50), "bubble_right", null) to "Weekend"
        "ABSOLUTELY CHILL" -> Quad(Color(0xFFB2EBF2), Color(0xFF0097A7), "burst", null) to "Chill"
        "SmallJoy" -> Quad(Color.White, Color.Black, "bubble_left", null) to "Joy"
        "AtHome" -> Quad(Color(0xFFFCE4EC), Color.Black, null, null) to "Home"
        "LatteLove" -> Quad(Color.White, Color(0xFF3E2723), "cloud", null) to "Coffee"
        "BubblePink" -> Quad(Color.Transparent, Color.Black, null, "presets/bubble_pink.png") to "Pink"
        "ThinkPink" -> Quad(Color.Transparent, Color.Black, null, "presets/think_pink.png") to "Think"
        "ThinkWhite" -> Quad(Color.Transparent, Color.Black, null, "presets/think_white.png") to "Think"
        "HeartPremium" -> Quad(Color.Transparent, Color.White, null, "presets/heart_premium.png") to "Heart"
        "BubblePremium" -> Quad(Color.Transparent, Color.Black, null, "presets/bubble_premium.png") to "Premium"
        "Stamp1999" -> Quad(Color.White, Color.Gray, null, null) to "1999"
        "PremiumBubble" -> Quad(Color.Transparent, Color.White, null, "presets/bg_bubble_premium.png") to "Premium"
        else -> Quad(Color(0xFFF5F5F5), Color.Gray, null, null) to presetId
    }
    val (bgColor, textColor, shapeName, bgImage) = style

    val shape = if (shapeName != null) getPreviewShape(shapeName) else RoundedCornerShape(12.dp)
    val isPng = bgImage != null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .then(
                    if (!isPng) {
                        Modifier
                            .shadow(if (isSelected) 4.dp else 0.dp, shape)
                            .background(bgColor, shape)
                            .border(if (isSelected) 2.dp else 0.5.dp, if (isSelected) Color.Black else Color.LightGray.copy(alpha = 0.5f), shape)
                    } else {
                        // For PNG stickers, don't show the square background/shadow/border unless selected
                        if (isSelected) Modifier.border(2.dp, Color(0xFF3F51B5), RoundedCornerShape(12.dp))
                        else Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (bgImage != null) {
                val model = if (bgImage.startsWith("http") || bgImage.startsWith("file")) bgImage 
                            else "file:///android_asset/$bgImage"
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }
            Text(
                text = previewText,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

private fun getPreviewShape(name: String): androidx.compose.ui.graphics.Shape {
    return when (name) {
        "heart" -> HeartShape
        "cloud" -> CloudShape
        "burst" -> BurstShape
        "bubble_left" -> SpeechBubbleShape(isLeft = true)
        "bubble_right" -> SpeechBubbleShape(isLeft = false)
        else -> RoundedCornerShape(12.dp)
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun isOnlyEmoji(text: String?): Boolean {
    if (text == null || text.isBlank()) return false
    // A simple check: if it has at least one emoji and no letters/digits.
    val emojiPattern = "[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+".toRegex()
    val hasEmoji = emojiPattern.find(text) != null
    val hasText = "[a-zA-Z0-9]".toRegex().find(text) != null
    return hasEmoji && !hasText
}
