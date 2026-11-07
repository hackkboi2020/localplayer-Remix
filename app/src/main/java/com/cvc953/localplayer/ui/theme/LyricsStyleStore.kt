package com.cvc953.localplayer.ui.theme

import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.cvc953.localplayer.preferences.AppPrefs
import java.io.File

/**
 * Lyrics appearance chosen in Settings. Every value defaults to "unchanged": with the
 * defaults the lyrics look exactly as before (null font family, no color override, 1x size).
 * Backed by Compose state, so changes show up immediately.
 */
object LyricsStyleStore {
    var fontMode: String by mutableStateOf("default") // default | sans | serif | mono | custom
        private set
    var customFontPath: String? by mutableStateOf(null)
        private set
    var colorHex: String? by mutableStateOf(null) // null = default colors
        private set
    var sizeScale: Float by mutableStateOf(1f)
        private set

    /** Null means "keep the original font". */
    val fontFamily: FontFamily?
        get() =
            when (fontMode) {
                "sans" -> FontFamily.SansSerif
                "serif" -> FontFamily.Serif
                "mono" -> FontFamily.Monospace
                "custom" -> customFamily
                else -> null
            }

    val activeColor: Color?
        get() = colorHex?.let { hex -> runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull() }

    private var customFamily: FontFamily? by mutableStateOf(null)

    fun load(prefs: AppPrefs) {
        fontMode = prefs.getLyricsFontMode()
        customFontPath = prefs.getLyricsFontPath()
        colorHex = prefs.getLyricsColorHex()
        sizeScale = prefs.getLyricsSizeScale()
        rebuildCustomFamily()
    }

    private fun rebuildCustomFamily() {
        customFamily =
            customFontPath?.let { path ->
                val file = File(path)
                if (!file.exists()) {
                    null
                } else {
                    runCatching { FontFamily(Font(file, FontWeight.Normal), Font(file, FontWeight.Bold)) }.getOrNull()
                }
            }
    }

    fun setFontMode(
        prefs: AppPrefs,
        mode: String,
    ) {
        fontMode = mode
        prefs.setLyricsFontMode(mode)
    }

    /** Returns false (and changes nothing) when the file can't be loaded as a font. */
    fun setCustomFont(
        prefs: AppPrefs,
        file: File,
    ): Boolean {
        val usable = runCatching { Typeface.createFromFile(file) != null }.getOrDefault(false)
        if (!usable) return false
        customFontPath = file.absolutePath
        prefs.setLyricsFontPath(file.absolutePath)
        rebuildCustomFamily()
        setFontMode(prefs, "custom")
        return true
    }

    fun setColor(
        prefs: AppPrefs,
        hex: String?,
    ) {
        colorHex = hex
        prefs.setLyricsColorHex(hex)
    }

    fun setSizeScale(
        prefs: AppPrefs,
        scale: Float,
    ) {
        sizeScale = scale
        prefs.setLyricsSizeScale(scale)
    }

    fun resetAll(prefs: AppPrefs) {
        setFontMode(prefs, "default")
        setColor(prefs, null)
        setSizeScale(prefs, 1f)
    }
}

/** Whether the Songs tab shows the folder filter (toggle in Settings → Library). */
object LibraryViewStore {
    var folderFilterEnabled: Boolean by mutableStateOf(false)
        private set

    fun load(prefs: AppPrefs) {
        folderFilterEnabled = prefs.isFolderFilterEnabled()
    }

    fun setFolderFilterEnabled(
        prefs: AppPrefs,
        enabled: Boolean,
    ) {
        folderFilterEnabled = enabled
        prefs.setFolderFilterEnabled(enabled)
    }
}
