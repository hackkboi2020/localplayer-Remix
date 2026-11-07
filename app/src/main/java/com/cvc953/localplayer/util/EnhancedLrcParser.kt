package com.cvc953.localplayer.util

import com.cvc953.localplayer.model.TtmlLine
import com.cvc953.localplayer.model.TtmlLyrics
import com.cvc953.localplayer.model.TtmlSyllable

/**
 * Parses "Enhanced LRC" (word-timed LRC), e.g.
 *   [01:08.00]<01:08.00>far <01:08.50>up<01:09.00>
 *   [bg:<01:08.40>ooh<01:08.90>]
 * Each <mm:ss.xx> tag marks when the text after it starts. A tag with no text after it
 * (usually at the end of a line) marks when the previous word ends. A `[bg:...]` line right
 * after a lyric line holds its background vocals (same tag syntax); they become background
 * syllables, which the word-by-word view draws smaller, under the main line.
 * The result uses the same model as TTML, so the existing word-by-word view renders it.
 */
object EnhancedLrcParser {
    private val lineTag = Regex("""\[(\d+):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordTag = Regex("""<(\d+):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val backgroundLine = Regex("""^\[bg:(.*)]$""", RegexOption.IGNORE_CASE)

    fun isEnhanced(content: String): Boolean = wordTag.containsMatchIn(content) && lineTag.containsMatchIn(content)

    /** Removes word tags and background-line wrappers, leaving plain LRC text. */
    fun stripWordTags(content: String): String =
        content
            .lines()
            .filterNot { backgroundLine.matches(it.trim()) }
            .joinToString("\n")
            .replace(wordTag, "")

    private fun toMs(
        min: String,
        sec: String,
        frac: String,
    ): Long {
        val fractionMs =
            when (frac.length) {
                0 -> 0L
                1 -> frac.toLong() * 100
                2 -> frac.toLong() * 10
                else -> frac.take(3).toLong()
            }
        return min.toLong() * 60_000 + sec.toLong() * 1_000 + fractionMs
    }

    private class RawLine(
        val startMs: Long,
        val body: String,
        var bgBody: String? = null,
    )

    fun parse(content: String): TtmlLyrics? {
        val raw = mutableListOf<RawLine>()
        for (line in content.lines()) {
            val trimmed = line.trim()
            val bg = backgroundLine.matchEntire(trimmed)
            if (bg != null) {
                raw.lastOrNull()?.bgBody = bg.groupValues[1]
                continue
            }
            val match = lineTag.find(trimmed) ?: continue
            if (match.range.first != 0) continue
            val start = toMs(match.groupValues[1], match.groupValues[2], match.groupValues[3])
            raw.add(RawLine(start, trimmed.substring(match.range.last + 1)))
        }
        if (raw.isEmpty()) return null

        val lines = mutableListOf<TtmlLine>()
        raw.forEachIndexed { index, rawLine ->
            val nextStart = raw.getOrNull(index + 1)?.startMs
            var main = buildSyllables(rawLine.startMs, rawLine.body, nextStart, isBackground = false)
            var background =
                rawLine.bgBody?.let { buildSyllables(rawLine.startMs, it, nextStart, isBackground = true) } ?: emptyList()

            if (main.isEmpty() && background.isNotEmpty()) {
                main = background.map { it.copy(isBackground = false) }
                background = emptyList()
            }
            if (main.isEmpty()) return@forEachIndexed

            main = withSustain(main)
            background = withSustain(background)

            val text =
                buildString {
                    main.forEachIndexed { i, syl ->
                        if (i > 0 && !syl.continuesWord) append(" ")
                        append(syl.text)
                    }
                }.trim()
            val lineEnd = (main + background).maxOf { it.timeMs + it.durationMs }
            lines.add(
                TtmlLine(
                    timeMs = rawLine.startMs,
                    durationMs = (lineEnd - rawLine.startMs).coerceAtLeast(1L),
                    text = text,
                    syllabus = main + background,
                ),
            )
        }

        if (lines.isEmpty()) return null
        return TtmlLyrics(type = "Word", lines = lines)
    }

    /** Same sustain rule as the TTML parser: a gap before the next syllable. */
    private fun withSustain(syllables: List<TtmlSyllable>): List<TtmlSyllable> =
        syllables.mapIndexed { i, syl ->
            val next = syllables.getOrNull(i + 1)
            if (next != null && next.timeMs - (syl.timeMs + syl.durationMs) > 50) syl.copy(isSustained = true) else syl
        }

    private fun buildSyllables(
        lineStartMs: Long,
        body: String,
        nextLineStartMs: Long?,
        isBackground: Boolean,
    ): List<TtmlSyllable> {
        val tags = wordTag.findAll(body).toList()
        if (tags.isEmpty()) {
            val text = body.trim()
            if (text.isEmpty()) return emptyList()
            val end = if (nextLineStartMs != null && nextLineStartMs > lineStartMs) nextLineStartMs else lineStartMs + 3000
            return listOf(
                TtmlSyllable(text = text, timeMs = lineStartMs, durationMs = end - lineStartMs, isBackground = isBackground),
            )
        }

        // (time, text) segments in order of appearance
        val segments = mutableListOf<Pair<Long, String>>()
        val leading = body.substring(0, tags.first().range.first)
        if (leading.isNotBlank()) segments.add(lineStartMs to leading)
        tags.forEachIndexed { i, tag ->
            val time = toMs(tag.groupValues[1], tag.groupValues[2], tag.groupValues[3])
            val textEnd = tags.getOrNull(i + 1)?.range?.first ?: body.length
            segments.add(time to body.substring(tag.range.last + 1, textEnd))
        }

        val syllables = mutableListOf<TtmlSyllable>()
        for (k in segments.indices) {
            val (time, text) = segments[k]
            if (text.isBlank()) continue

            val nextWordIdx = (k + 1 until segments.size).firstOrNull { segments[it].second.isNotBlank() }
            val nextSeg = segments.getOrNull(k + 1)
            val end =
                when {
                    nextSeg != null && nextSeg.second.isBlank() && nextSeg.first > time -> nextSeg.first
                    nextWordIdx != null -> segments[nextWordIdx].first
                    nextLineStartMs != null && nextLineStartMs - time in 1..4000 -> nextLineStartMs
                    else -> time + 700
                }
            val nextWordText = nextWordIdx?.let { segments[it].second }
            val between = if (nextWordIdx != null) segments.subList(k + 1, nextWordIdx).joinToString("") { it.second } else ""
            val continues =
                nextWordText != null &&
                    !text.last().isWhitespace() &&
                    !nextWordText.first().isWhitespace() &&
                    between.isEmpty()

            syllables.add(
                TtmlSyllable(
                    text = text.trim(),
                    timeMs = time,
                    durationMs = (end - time).coerceAtLeast(1L),
                    isBackground = isBackground,
                    continuesWord = continues,
                ),
            )
        }
        return syllables
    }
}
