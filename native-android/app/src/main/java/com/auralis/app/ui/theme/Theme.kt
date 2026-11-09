package com.auralis.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StudioScheme = darkColorScheme(
    primary = StudioAccent,
    onPrimary = StudioBlack,
    background = StudioBlack,
    onBackground = StudioWhite,
    surface = StudioPanel,
    onSurface = StudioWhite,
    surfaceVariant = StudioRaised,
    onSurfaceVariant = StudioMuted,
    outline = StudioLine
)

@Composable
fun AuralisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioScheme,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}
