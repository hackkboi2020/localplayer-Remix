package com.cvc953.localplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
    /** Every folder with songs under the common root, with recursive song counts, parents before children. */
    fun build(songs: List<Song>): List<FolderNode> {
        val dirs =
            songs
                .mapNotNull { it.filePath?.substringBeforeLast('/', "") }
                .filter { it.isNotEmpty() }
        if (dirs.isEmpty()) return emptyList()

        val root = commonDir(dirs)
        val counts = java.util.TreeMap<String, Int>(String.CASE_INSENSITIVE_ORDER)
        for (dir in dirs) {
            var current = dir
            while (current.length > root.length && current.startsWith(root)) {
                counts[current] = (counts[current] ?: 0) + 1
                current = current.substringBeforeLast('/', "")
                if (current.isEmpty()) break
            }
        }
        return counts.map { (path, count) ->
            FolderNode(
                path = path,
                name = path.substringAfterLast('/'),
                depth = path.removePrefix(root).trim('/').count { it == '/' },
                count = count,
            )
        }
    }

    private fun commonDir(dirs: List<String>): String {
        var prefix = dirs.first()
        for (dir in dirs) {
            while (!(dir == prefix || dir.startsWith("$prefix/"))) {
                prefix = prefix.substringBeforeLast('/', "")
                if (prefix.isEmpty()) return ""
            }
        }
        return prefix
    }

    fun songsIn(
        songs: List<Song>,
        folderPath: String,
    ): List<Song> = songs.filter { it.filePath?.startsWith("$folderPath/") == true }
}

/** Chip showing the current folder filter; tapping it opens the folder list. */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ktlint:standard:function-naming")
@Composable
fun FolderFilterBar(
    songs: List<Song>,
    selectedFolder: String?,
    onFolderSelected: (String?) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    val nodes = remember(songs) { FolderIndex.build(songs) }
    if (nodes.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = { showDialog = true },
            label = {
                Text(
                    text = selectedFolder?.substringAfterLast('/') ?: stringResource(R.string.folder_filter_all),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
            },
        )
        if (selectedFolder != null) {
            IconButton(onClick = { onFolderSelected(null) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.folder_filter_all))
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.folder_filter_title)) },
            text = {
                LazyColumn {
                    item {
                        FolderRow(
                            label = "${stringResource(R.string.folder_filter_all)} (${songs.size})",
                            depth = 0,
                            selected = selectedFolder == null,
                        ) {
                            onFolderSelected(null)
                            showDialog = false
                        }
                    }
                    items(nodes) { node ->
                        FolderRow(
                            label = "${node.name} (${node.count})",
                            depth = node.depth + 1,
                            selected = selectedFolder == node.path,
                        ) {
                            onFolderSelected(node.path)
                            showDialog = false
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.folder_filter_close)) }
            },
        )
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun FolderRow(
    label: String,
    depth: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = (depth * 16).dp, top = 10.dp, bottom = 10.dp),
    )
}
