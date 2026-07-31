package org.gallery.swiper.ui.bookmarks

import android.app.Application
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gallery.swiper.data.AppDatabase
import org.gallery.swiper.data.model.Decision
import org.gallery.swiper.data.model.Photo
import org.gallery.swiper.data.repository.PhotoRepository

data class BookmarksUiState(
    val bookmarkedPhotos: List<Photo> = emptyList(),
    val isLoading: Boolean = true,
)

class BookmarksViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val bookmarkDao = db.bookmarkDao()
    private val swipeDecisionDao = db.swipeDecisionDao()
    private val statsDao = db.statsDao()
    private val repository = PhotoRepository.getInstance(application)

    private val _uiState = MutableStateFlow(BookmarksUiState())
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bookmarkDao.getAllBookmarks().collect { entities ->
                val photos = entities.map { entity ->
                    Photo(
                        id = entity.photoId,
                        uri = entity.uri.toUri(),
                        dateTaken = entity.dateTaken,
                        size = entity.size,
                        mimeType = entity.mimeType,
                        width = entity.width,
                        height = entity.height,
                    )
                }
                _uiState.value = BookmarksUiState(
                    bookmarkedPhotos = photos,
                    isLoading = false,
                )
            }
        }
    }

    fun keepPhoto(photo: Photo) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookmarkDao.deleteByPhotoId(photo.id)
                swipeDecisionDao.updateCommittedDecision(photo.id, Decision.KEEP.name)
            }
        }
    }

    suspend fun deletePhoto(photo: Photo): Boolean = withContext(Dispatchers.IO) {
        if (!repository.sendToTrash(listOf(photo.uri))) {
            Log.w("BookmarksViewModel", "Failed to trash ${photo.uri}")
            return@withContext false
        }
        bookmarkDao.deleteByPhotoId(photo.id)
        swipeDecisionDao.updateCommittedDecision(photo.id, Decision.DELETE.name)
        statsDao.addDeleted(1, photo.size)
        repository.invalidateCache()
        true
    }
}
