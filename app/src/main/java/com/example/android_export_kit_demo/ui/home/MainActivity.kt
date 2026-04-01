package com.example.android_export_kit_demo.ui.home

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.android_export_kit_demo.model.FrameModel
import com.example.android_export_kit_demo.ui.FrameEditorScreen
import com.example.android_export_kit_demo.FrameEditorTheme
import com.example.android_export_kit_demo.uime.HomeScreen
import com.example.android_export_kit_demo.uime.SelectPhotoScreen
import com.example.android_export_kit_demo.viewmodel.FrameViewModel
import com.example.android_export_kit_demo.viewmodel.FrameViewModelFactory

// ─────────────────────────────────────────────
// Navigation destinations
// ─────────────────────────────────────────────

private sealed class Screen {
    object Home : Screen()
    data class SelectPhoto(val frame: FrameModel) : Screen()
    object Editor : Screen()
}

// ─────────────────────────────────────────────
// MainActivity — single-activity architecture
// Replaces Flutter's runApp + MaterialApp + Navigator
// ─────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FrameEditorTheme {
                FrameEditorApp()
            }
        }
    }
}

// ─────────────────────────────────────────────
// Root Composable — simple back-stack navigation
// ─────────────────────────────────────────────

@Composable
private fun FrameEditorApp() {
    val viewModel: FrameViewModel = viewModel(
        factory = FrameViewModelFactory(
            androidx.compose.ui.platform.LocalContext.current
        )
    )

    // Simple back-stack: list of screens
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val currentScreen = backStack.last()

    fun navigate(screen: Screen) { backStack.add(screen) }
    fun navigateBack() { if (backStack.size > 1) backStack.removeLast() }
    fun navigateAndReplace(screen: Screen) {
        backStack.removeLast()
        backStack.add(screen)
    }

    fun navigateHome() {
        while (backStack.size > 1) {
            backStack.removeLast()
        }
    }

    when (val screen = currentScreen) {
        is Screen.Home -> {
            HomeScreen(
                viewModel = viewModel,
                onFrameSelected = { frame ->
                    navigate(Screen.SelectPhoto(frame))
                }
            )
        }

        is Screen.SelectPhoto -> {
            androidx.activity.compose.BackHandler(enabled = true) {
                navigateBack()
            }
            SelectPhotoScreen(
                frame = screen.frame,
                viewModel = viewModel,
                onProceed = { navigate(Screen.Editor) },
                onBack = { navigateBack() }
            )
        }

        is Screen.Editor -> {
            androidx.activity.compose.BackHandler(enabled = true) {
                navigateHome()
            }
            FrameEditorScreen(
                viewModel = viewModel,
                onBack = { navigateHome() }
            )
        }
    }
}
