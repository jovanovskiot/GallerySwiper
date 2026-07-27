package org.gallery.swiper.ui.bookmarks

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gallery.swiper.data.AppDatabase
import org.gallery.swiper.data.model.Photo

data class BookmarksUiState(
    val bookmarkedPhotos: List<Photo> = emptyList(),
    val isLoading: Boolean = true,
)

class BookmarksViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val bookmarkDao = db.bookmarkDao()

    private val _uiState = MutableStateFlow(BookmarksUiState())
    val uiState: StateFlow<BookmarksUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            bookmarkDao.getAllBookmarks().collect { entities ->
                val photos = entities.map { entity ->
                    Photo(
                        id = entity.photoId,
                        uri = Uri.parse(entity.uri),
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

    fun removeBookmark(photo: Photo): Job {
        return viewModelScope.launch {
            withContext(Dispatchers.IO) {
                bookmarkDao.deleteByPhotoId(photo.id)
            }
        }
    }
}
