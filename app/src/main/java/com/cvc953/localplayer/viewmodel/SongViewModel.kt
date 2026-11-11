package com.cvc953.localplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cvc953.localplayer.controller.SongController
import com.cvc953.localplayer.model.Song
import com.cvc953.localplayer.model.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.cvc953.localplayer.ui.theme.LibraryViewStore

class SongViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val controller = SongController(getApplication())
    private val repository = SongRepository.getInstance(getApplication())

    private val _searchQuery = MutableStateFlow("")
    private val _selectedSong = MutableStateFlow<Song?>(null)
    val selectedSong: StateFlow<Song?> = _selectedSong
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val songs: StateFlow<List<Song>> =
        combine(repository.songs, _searchQuery, LibraryViewStore.excludedFolders) { allLibrary, query, excluded ->
            // Songs inside an excluded folder (or its subfolders) never show up in the library
            val library =
                if (excluded.isEmpty()) {
                    allLibrary
                } else {
                    allLibrary.filterNot { song ->
                        excluded.any { folder -> song.filePath?.startsWith("$folder/") == true }
                    }
                }
            if (query.isBlank()) {
                library
            } else {
                library.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, repository.loadSongs())

    val isScanning: StateFlow<Boolean> = repository.isFirstScanning

    init {
        repository.ensureLoadedAsync()
    }

    fun manualRefreshLibrary() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                controller.forceRescan()
            } catch (e: Exception) {
                android.util.Log.e("SongViewModel", "manualRefreshLibrary: Error", e)
                _error.value = "Error actualizando la biblioteca: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchSongs(query: String) {
        _searchQuery.value = query
    }

    fun selectSong(song: Song) {
        _selectedSong.value = song
    }

    fun clearSelection() {
        _selectedSong.value = null
    }

    /**
     * Elimina una canción del dispositivo: archivo físico, MediaStore y estado en memoria.
     * El repositorio propaga la baja a todas las categorías.
     */
    fun deleteSong(
        song: Song,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = controller.deleteSong(song)
            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    onSuccess()
                } else {
                    onError(result.exceptionOrNull()?.message ?: "Error desconocido al eliminar")
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
