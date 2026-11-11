package com.cvc953.localplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cvc953.localplayer.R
import com.cvc953.localplayer.model.Song

/** A folder that contains songs (directly or in subfolders). */
data class FolderNode(
    val path: String,
    val name: String,
    val depth: Int,
    val count: Int,
)

object FolderIndex {
    private fun dirOf(song: Song): String? = song.filePath?.substringBeforeLast('/', "")?.takeIf { it.isNotEmpty() }

    /** The longest folder that contains every song ("" when unknown). */
    fun rootOf(songs: List<Song>): String {
        val dirs = songs.mapNotNull { dirOf(it) }
        if (dirs.isEmpty()) return ""
        var prefix = dirs.first()
        for (dir in dirs) {
            while (!(dir == prefix || dir.startsWith("$prefix/"))) {
                prefix = prefix.substringBeforeLast('/', "")
                if (prefix.isEmpty()) return ""
            }
        }
        return prefix
    }

    /** Immediate subfolders of [dir] that hold songs, with recursive song counts. */
    fun children(
        songs: List<Song>,
        dir: String,
    ): List<FolderNode> {
        val counts = java.util.TreeMap<String, Int>(String.CASE_INSENSITIVE_ORDER)
        val prefix = "$dir/"
        for (song in songs) {
            val songDir = dirOf(song) ?: continue
            if (!songDir.startsWith(prefix)) continue
            val childPath = prefix + songDir.removePrefix(prefix).substringBefore('/')
            counts[childPath] = (counts[childPath] ?: 0) + 1
        }
        return counts.map { (path, count) -> FolderNode(path, path.substringAfterLast('/'), 0, count) }
    }

    /** Songs sitting directly in [dir], not in its subfolders. */
    fun songsDirectlyIn(
        songs: List<Song>,
        dir: String,
    ): List<Song> = songs.filter { dirOf(it) == dir }

    /** Every folder that holds songs (parents first) with recursive counts; used by the exclusion list. */
    fun all(songs: List<Song>): List<FolderNode> {
        val root = rootOf(songs)
        val counts = java.util.TreeMap<String, Int>(String.CASE_INSENSITIVE_ORDER)
        for (song in songs) {
            var current = dirOf(song) ?: continue
            while (current.length > root.length && current.startsWith(root)) {
                counts[current] = (counts[current] ?: 0) + 1
                current = current.substringBeforeLast('/', "")
                if (current.isEmpty()) break
            }
        }
        return counts.map { (path, count) ->
            val rest = path.removePrefix(root).trim('/')
            FolderNode(path, path.substringAfterLast('/'), rest.count { it == '/' }, count)
        }
    }
}

/** A subfolder row in the Songs tab's folder view. */
@Suppress("ktlint:standard:function-naming")
@Composable
fun FolderRowItem(
    node: FolderNode,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(end = 12.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = node.name,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(text = node.count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The "go up one folder" row. */
@Suppress("ktlint:standard:function-naming")
@Composable
fun FolderUpRow(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(end = 12.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "..", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Pick folders to hide from the library. A hidden folder hides its subfolders too. */
@Suppress("ktlint:standard:function-naming")
@Composable
fun ExcludedFoldersDialog(
    songs: List<Song>,
    excluded: Set<String>,
    onChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val folders = FolderIndex.all(songs)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_excluded_folders_title)) },
        text = {
            if (folders.isEmpty()) {
                Text(stringResource(R.string.settings_excluded_folders_empty))
            } else {
                LazyColumn {
                    items(folders) { node ->
                        val coveredByParent = excluded.any { node.path.startsWith("$it/") }
                        val checked = coveredByParent || node.path in excluded
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = (node.depth * 16).dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                enabled = !coveredByParent,
                                onCheckedChange = { isChecked ->
                                    onChange(if (isChecked) excluded + node.path else excluded - node.path)
                                },
                            )
                            Text(
                                text = "${node.name} (${node.count})",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.folder_filter_close)) }
        },
    )
}
