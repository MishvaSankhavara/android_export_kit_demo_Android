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
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import com.example.android_export_kit_demo.model.DrawingStroke
import com.example.android_export_kit_demo.model.FrameLayer
import kotlin.math.abs
import com.frameeditor.R
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.android_export_kit_demo.model.FrameModel
import com.example.android_export_kit_demo.model.LayerType
import com.example.android_export_kit_demo.AppColors
import com.example.android_export_kit_demo.view.FrameCanvasView
import com.example.android_export_kit_demo.viewmodel.FrameUiState
import com.example.android_export_kit_demo.viewmodel.FrameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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

                    if (activeSubEditor != null && !isText) {
                        IntegratedPanel(
                            title = activeSubEditor ?: "",
                            onClose = { activeSubEditor = null },
                            content = {
                                SubEditorContent(activeSubEditor!!, sel, viewModel, frame, onClose = { activeSubEditor = null })
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
                        if (uiState.selectedLayer === layer) {
                            if (layer.type == LayerType.TEXT) {
                                activeSubEditor = "Edit Text"
                            } else if (layer.type == LayerType.IMAGE && layer.isPhotoSlot) {
                                showImageSheet = true
                            }
                        } else {
                            viewModel.selectLayer(layer)
                            if (layer.type == LayerType.TEXT) {
                                textInput = layer.text ?: ""
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
        GenActionItem(Icons.Outlined.EmojiEmotions, "Stickers") { onOpenSubEditor("Stickers") }
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
    val showSubEditor = activeSubEditor != null && activeSubEditor.isNotEmpty() && activeSubEditor != "Edit Text"

    Column(modifier = Modifier.fillMaxWidth().animateContentSize(tween(200))) {
        // Sub-editor panel — animated in/out above the input row
        AnimatedVisibility(
            visible = showSubEditor,
            enter = expandVertically(tween(200)) + fadeIn(tween(150)),
            exit  = shrinkVertically(tween(200)) + fadeOut(tween(150))
        ) {
            Column {
                Divider(thickness = 1.dp, color = Color.LightGray)
                Box(modifier = Modifier.heightIn(max = 280.dp)) {
                    if (showSubEditor) SubEditorContent(activeSubEditor!!, layer, viewModel, frame, onClose = onDone)
                }
            }
        }

        Divider(thickness = 1.dp, color = Color.LightGray)

        // Text input field + Done button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = textInput,
                onValueChange = onTextInputChange,
                modifier = Modifier.weight(1f),
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                ),
                placeholder = {
                    Text(
                        "Enter text…",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = Color.Black.copy(alpha = 0.3f),
                        fontSize = 16.sp
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                enabled = !layer.isLocked,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFA259FF),
                    unfocusedBorderColor = Color(0xFFDDDDDD),
                    cursorColor = Color(0xFFA259FF),
                    disabledBorderColor = Color(0xFFEEEEEE),
                    disabledTextColor = Color.Gray
                )
            )
            Spacer(Modifier.width(8.dp))
            // Done button — uses onDone to properly null out activeSubEditor
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFFA259FF), RoundedCornerShape(12.dp))
                    .clickable { onDone() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }

        Divider(thickness = 1.dp, color = Color.LightGray)

        // Tool icons row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextToolIcon(Icons.Outlined.Palette, "Color", activeSubEditor == "Color", enabled = !layer.isLocked) { onOpenSubEditor("Color") }
            TextToolIcon(Icons.Outlined.FontDownload, "Fonts", activeSubEditor == "Fonts", enabled = !layer.isLocked) { onOpenSubEditor("Fonts") }
            TextToolIcon(Icons.Default.TextFormat, "Format", activeSubEditor == "Format", enabled = !layer.isLocked) { onOpenSubEditor("Format") }
            TextToolIcon(Icons.Default.Tune, "Opacity", activeSubEditor == "Opacity", enabled = !layer.isLocked) { onOpenSubEditor("Opacity") }
            TextToolIcon(Icons.Default.ControlCamera, "Move", false, enabled = !layer.isLocked) { onOpenSubEditor("Move") }
            TextToolIcon(Icons.Default.FlipToFront, "Front", false, enabled = !layer.isLocked) { viewModel.bringLayerToFront(layer) }
            TextToolIcon(if (layer.isLocked) Icons.Default.Lock else Icons.Default.LockOpen, if (layer.isLocked) "Unlock" else "Lock", false, enabled = true) { viewModel.toggleLayerLock(layer) }
            TextToolIcon(Icons.Outlined.Delete, "Delete", false, color = Color.Red) { viewModel.deleteLayer(layer); viewModel.deselectAll() }
        }
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
    onClose: () -> Unit
) {
    when (activeSubEditor) {
        "Filters" -> FilterAdjustPanelContent(viewModel, layer, onClose)
        "Stickers" -> StickersPanelContent(viewModel)
        "Opacity" -> if (layer != null) OpacityPanelContent(viewModel, layer)
        "Fonts" -> if (layer != null) FontPanelContent(viewModel, layer)
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



@Composable
private fun CircleBtn(icon: ImageVector, color: Color? = null, onTap: () -> Unit) {
    Box(
        modifier = Modifier.size(44.dp).border(1.dp, Color.LightGray, CircleShape).background(Color.White, CircleShape).clickable(onClick = onTap),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = color ?: Color.Black.copy(alpha=0.87f))
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

private fun FiltersPanelContent(viewModel: FrameViewModel, layer: FrameLayer?) {
    // Deprecated by FilterAdjustPanelContent
}

@Composable
private fun StickersPanelContent(viewModel: FrameViewModel) {
    val context = LocalContext.current


    // Emoji characters rendered as large text stickers
    val emojiStickers = listOf(
        "😀", "😍", "🎉", "🌟", "🔥", "❤️", "👍", "🎶",
        "🦄", "🐶", "🌈", "🍕", "✨", "💎", "🏆", "😂"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // ── Emoji stickers row ──────────────────────────────────────────────
        Text(
            text     = "Emoji",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color    = Color.Black.copy(alpha = 0.54f),
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            modifier       = Modifier.fillMaxWidth()
        ) {
            items(emojiStickers) { emoji ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF5F5F5))
                        .clickable { viewModel.addTextStickerLayer(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 28.sp)
                }
            }
        }

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontPanelContent(viewModel: FrameViewModel, layer: FrameLayer) {
    val fonts = listOf(
        "Roboto", "Serif", "Sans-Serif", "Cursive", "Monospace",
        "Georgia", "Verdana", "Courier", "Trebuchet", "Impact", "Arial", "Times"
    )
    val displayNames = listOf(
        "Roboto", "Serif", "Sans", "Cursive", "Mono",
        "Georgia", "Verdana", "Courier", "Trebuchet", "Impact", "Arial Blk", "Times NR"
    )

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
        // Font Grid
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = 4,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            fonts.forEachIndexed { index, font ->
                val isSel = layer.font == font
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) Color(0xFFF0F0F0) else Color(0xFFF9F9F9))
                        .border(1.dp, if (isSel) AppColors.Primary else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable { viewModel.updateLayerFont(layer, font) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "AaBb",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontFamily = when(font) {
                                "Serif" -> FontFamily.Serif
                                "Monospace" -> FontFamily.Monospace
                                "Cursive" -> FontFamily.Cursive
                                else -> FontFamily.Default
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            displayNames[index],
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
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
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
