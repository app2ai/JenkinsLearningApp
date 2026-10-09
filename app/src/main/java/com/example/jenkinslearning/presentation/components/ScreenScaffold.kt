package com.example.jenkinslearning.presentation.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Per-screen scaffold (top bar + snackbar). The app-level scaffold owns the
 * bottom navigation bar and passes its padding down; contentWindowInsets is
 * zero here so system-bar insets aren't applied twice. The TopAppBar
 * handles the status bar inset itself.
 */
@Composable
fun ScreenScaffold(
    topBar: @Composable () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        content = content,
    )
}
