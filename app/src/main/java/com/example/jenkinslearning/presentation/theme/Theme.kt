package com.example.jenkinslearning.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.jenkinslearning.domain.model.BuildStatus

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F3A5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3F5),
    onPrimaryContainer = Color(0xFF0B1F36),
    secondary = Color(0xFF3D6B4F),
    secondaryContainer = Color(0xFFD5EBDC),
    tertiary = Color(0xFF8A5A00),
    tertiaryContainer = Color(0xFFFFE3B0),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE7EAF0),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7EE),
    onPrimary = Color(0xFF0B1F36),
    primaryContainer = Color(0xFF2B4C75),
    onPrimaryContainer = Color(0xFFD6E3F5),
    secondary = Color(0xFFA6D3B5),
    secondaryContainer = Color(0xFF285238),
    tertiary = Color(0xFFF5C26B),
    tertiaryContainer = Color(0xFF5E3F00),
    background = Color(0xFF111418),
    surface = Color(0xFF181C21),
    surfaceVariant = Color(0xFF2A3038),
    error = Color(0xFFF2B8B5),
)

@Composable
fun JenkinsLearningTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}

/** Status colors follow Jenkins' own convention (blue/green, yellow, red, grey). */
object StatusColors {
    fun container(status: BuildStatus, dark: Boolean): Color = when (status) {
        BuildStatus.SUCCESS -> if (dark) Color(0xFF1F4D2E) else Color(0xFFD7F0DE)
        BuildStatus.UNSTABLE -> if (dark) Color(0xFF5B4300) else Color(0xFFFFEDB8)
        BuildStatus.FAILURE -> if (dark) Color(0xFF5C1D1A) else Color(0xFFF9D6D3)
        BuildStatus.IN_PROGRESS -> if (dark) Color(0xFF203F66) else Color(0xFFD6E6FA)
        BuildStatus.ABORTED, BuildStatus.NOT_BUILT, BuildStatus.DISABLED, BuildStatus.UNKNOWN ->
            if (dark) Color(0xFF353A42) else Color(0xFFE6E8EC)
    }

    fun content(status: BuildStatus, dark: Boolean): Color = when (status) {
        BuildStatus.SUCCESS -> if (dark) Color(0xFFA6E3B6) else Color(0xFF1B5E31)
        BuildStatus.UNSTABLE -> if (dark) Color(0xFFFFD970) else Color(0xFF6B4E00)
        BuildStatus.FAILURE -> if (dark) Color(0xFFFFB4AB) else Color(0xFF8C1D18)
        BuildStatus.IN_PROGRESS -> if (dark) Color(0xFFA9C7EE) else Color(0xFF1F3A5F)
        BuildStatus.ABORTED, BuildStatus.NOT_BUILT, BuildStatus.DISABLED, BuildStatus.UNKNOWN ->
            if (dark) Color(0xFFC4C7CE) else Color(0xFF4A4F57)
    }
}
