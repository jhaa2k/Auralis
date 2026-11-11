package com.auralis.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
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

private val StudioTypography = Typography()

@Composable
fun AuralisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioScheme,
        typography = StudioTypography,
        content = content
    )
}
