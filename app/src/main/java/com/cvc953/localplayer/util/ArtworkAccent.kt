package com.cvc953.localplayer.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * Turns the dominant color of the album art into an accent that stays readable on the
 * dark player: keeps the cover's hue, avoids colors that are too dark or washed out.
 * Grayscale covers stay neutral (light gray) instead of being tinted.
 */
fun Color.toReadableAccent(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(this.toArgb(), hsv)
    if (hsv[1] >= 0.12f) {
        hsv[1] = hsv[1].coerceAtLeast(0.35f)
    }
    hsv[2] = hsv[2].coerceIn(0.65f, 0.95f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Black or white, whichever is readable on top of this color. */
fun Color.onAccentColor(): Color = if (this.luminance() > 0.5f) Color.Black else Color.White
