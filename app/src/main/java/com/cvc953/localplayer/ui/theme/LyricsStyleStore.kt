package com.cvc953.localplayer.ui.theme

import android.graphics.Typeface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.cvc953.localplayer.preferences.AppPrefs
import java.io.File
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    /** Shifts lyric timing: positive makes lyrics appear earlier, negative later. Fixes out-of-sync files. */
    var timingOffsetMs: Int by mutableStateOf(0)
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
        timingOffsetMs = prefs.getLyricsTimingOffsetMs()
        rebuildCustomFamily()
    }

    private fun rebuildCustomFamily() {
        customFamily =
            customFontPath?.let { path ->
                val file = File(path)
                if (!file.exists()) {
                    null
                } else {
                    runCatching { FontFamily(Typeface.createFromFile(file)) }.getOrNull()
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

    /** Returns null when the font was applied, otherwise a short reason it can't be used (nothing changes). */
    fun setCustomFont(
        prefs: AppPrefs,
        file: File,
    ): String? {
        val size = file.length()
        if (size < 64L) return "the copied file is empty ($size bytes)"

        // TTF/OTF/TTC files start with a known tag; anything else (zip, woff, html...) isn't loadable
        val header = ByteArray(4)
        file.inputStream().use { it.read(header) }
        val tag = String(header, Charsets.ISO_8859_1)
        val isFont = tag == "OTTO" || tag == "true" || tag == "ttcf" || header.contentEquals(byteArrayOf(0, 1, 0, 0))
        if (!isFont) return "it isn't a TTF/OTF font file"

        val failure =
            try {
                Typeface.createFromFile(file)
                null
            } catch (e: Throwable) {
                e.message ?: e.javaClass.simpleName
            }
        if (failure != null) return "Android couldn't load it ($failure)"

        customFontPath = file.absolutePath
        prefs.setLyricsFontPath(file.absolutePath)
        rebuildCustomFamily()
        setFontMode(prefs, "custom")
        return null
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

    fun setTimingOffset(
        prefs: AppPrefs,
        ms: Int,
    ) {
        timingOffsetMs = ms.coerceIn(-1500, 1500)
        prefs.setLyricsTimingOffsetMs(timingOffsetMs)
    }

    fun resetAll(prefs: AppPrefs) {
        setFontMode(prefs, "default")
        setColor(prefs, null)
        setSizeScale(prefs, 1f)
    }
}

/** Library view options: the folder view on the Songs tab, and folders hidden from the library. */
object LibraryViewStore {
    var folderViewEnabled: Boolean by mutableStateOf(false)
        private set

    private val excluded = MutableStateFlow<Set<String>>(emptySet())
    val excludedFolders: StateFlow<Set<String>> = excluded

    fun load(prefs: AppPrefs) {
        folderViewEnabled = prefs.isFolderViewEnabled()
        excluded.value = prefs.getExcludedFolders()
    }

    fun setFolderViewEnabled(
        prefs: AppPrefs,
        enabled: Boolean,
    ) {
        folderViewEnabled = enabled
        prefs.setFolderViewEnabled(enabled)
    }

    fun setExcludedFolders(
        prefs: AppPrefs,
        folders: Set<String>,
    ) {
        excluded.value = folders
        prefs.setExcludedFolders(folders)
    }
}

/** Bumped by the refresh button so the player reloads the current song's lyrics from disk. */
object LyricsRefreshStore {
    var tick: Int by mutableStateOf(0)
        private set

    fun bump() {
        tick += 1
    }
}

/** Player look: accent from the artwork or a custom one, and the background used when dynamic color is off. */
object PlayerStyleStore {
    var artworkAccent: Boolean by mutableStateOf(true)
        private set
    var accentHex: String? by mutableStateOf(null) // custom accent, used when artworkAccent is off
        private set
    var backgroundMode: String by mutableStateOf("default") // default | solid | gradient
        private set
    var backgroundHex1: String by mutableStateOf("#101820")
        private set
    var backgroundHex2: String by mutableStateOf("#2A3A55")
        private set

    private fun parse(hex: String): Color? = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()

    val accentColor: Color?
        get() = accentHex?.let { parse(it) }

    /** Null means "use the normal theme background". */
    val backgroundBrush: Brush?
        get() {
            val first = parse(backgroundHex1)
            val second = parse(backgroundHex2)
            return when {
                backgroundMode == "solid" && first != null -> Brush.verticalGradient(listOf(first, first))
                backgroundMode == "gradient" && first != null && second != null ->
                    Brush.verticalGradient(listOf(first, second))
                else -> null
            }
        }

    val backgroundIsLight: Boolean
        get() {
            val first = parse(backgroundHex1) ?: return false
            val second = if (backgroundMode == "gradient") parse(backgroundHex2) ?: first else first
            return (first.luminance() + second.luminance()) / 2f > 0.5f
        }

    fun load(prefs: AppPrefs) {
        artworkAccent = prefs.isArtworkAccentEnabled()
        accentHex = prefs.getPlayerAccentHex()
        backgroundMode = prefs.getPlayerBackgroundMode()
        backgroundHex1 = prefs.getPlayerBackgroundHex1()
        backgroundHex2 = prefs.getPlayerBackgroundHex2()
    }

    fun setArtworkAccent(
        prefs: AppPrefs,
        enabled: Boolean,
    ) {
        artworkAccent = enabled
        prefs.setArtworkAccentEnabled(enabled)
    }

    fun setAccentHex(
        prefs: AppPrefs,
        hex: String?,
    ) {
        accentHex = hex
        prefs.setPlayerAccentHex(hex)
    }

    fun setBackgroundMode(
        prefs: AppPrefs,
        mode: String,
    ) {
        backgroundMode = mode
        prefs.setPlayerBackgroundMode(mode)
    }

    fun setBackgroundHex1(
        prefs: AppPrefs,
        hex: String,
    ) {
        backgroundHex1 = hex
        prefs.setPlayerBackgroundHex1(hex)
    }

    fun setBackgroundHex2(
        prefs: AppPrefs,
        hex: String,
    ) {
        backgroundHex2 = hex
        prefs.setPlayerBackgroundHex2(hex)
    }
}
