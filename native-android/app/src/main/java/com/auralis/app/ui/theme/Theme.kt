package com.auralis.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.auralis.app.R

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

private val GoogleFontsProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val MontserratFamily = FontFamily(
    Font(GoogleFont("Montserrat"), GoogleFontsProvider, FontWeight.Normal),
    Font(GoogleFont("Montserrat"), GoogleFontsProvider, FontWeight.Medium),
    Font(GoogleFont("Montserrat"), GoogleFontsProvider, FontWeight.SemiBold),
    Font(GoogleFont("Montserrat"), GoogleFontsProvider, FontWeight.Bold),
    Font(GoogleFont("Montserrat"), GoogleFontsProvider, FontWeight.ExtraBold),
    Font(GoogleFont("Montserrat"), GoogleFontsProvider, FontWeight.Black)
)

private val StudioTypography = Typography()

@Composable
fun AuralisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioScheme,
        typography = StudioTypography
    ) {
        // Text styles inherit Montserrat unless a component deliberately selects a
        // specialist family (for example, monospace shader/code editors).
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodyLarge.copy(
                fontFamily = MontserratFamily
            ),
            content = content
        )
    }
}
