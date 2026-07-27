package org.gallery.swiper.ui.swipe

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gallery.swiper.data.AppDatabase
import org.gallery.swiper.data.entity.SwipeDecisionEntity
import org.gallery.swiper.data.model.Decision
import org.gallery.swiper.data.model.Photo
import org.gallery.swiper.data.repository.PhotoRepository
import org.gallery.swiper.util.DateUtils

data class SwipeUiState(
    val photos: List<Photo> = emptyList(),
    val currentIndex: Int = 0,
    val isFinished: Boolean = false,
    val isLoading: Boolean = true,
    val monthKey: String = "",
    val monthLabel: String = "",
    val decisions: Map<Long, Decision> = emptyMap(),
)

class SwipeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository.getInstance(application)
    private val db = AppDatabase.getInstance(application)
    private val swipeDecisionDao = db.swipeDecisionDao()

    private val _uiState = MutableStateFlow(SwipeUiState())
    val uiState: StateFlow<SwipeUiState> = _uiState.asStateFlow()

    private val _navigateToReview = MutableStateFlow<String?>(null)
    val navigateToReview: StateFlow<String?> = _navigateToReview.asStateFlow()

    private data class SwipeLoadResult(
        val sortedPhotos: List<Photo>,
        val pendingMap: Map<Long, Decision>,
        val startIndex: Int,
        val monthLabel: String,
    )

    fun loadMonth(monthKey: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val months = repository.getPhotosByMonth()
                val month = months.find { it.key == monthKey } ?: return@withContext null

                val pendingDecisions = swipeDecisionDao.getPendingDecisions(monthKey)
                val committedDecisions = swipeDecisionDao.getCommittedDecisions(monthKey)

                val sortedPhotos = month.photos.sortedByDescending { it.dateTaken }
                val pendingMap = pendingDecisions.associate { entity ->
                    entity.photoId to try { Decision.valueOf(entity.decision) } catch (_: Exception) { Decision.KEEP }
                }
                val startIndex = if (committedDecisions.size >= sortedPhotos.size) {
                    sortedPhotos.size
                } else {
                    val skipIds = pendingDecisions.map { it.photoId }.toSet() +
                            committedDecisions.map { it.photoId }.toSet()
                    sortedPhotos.indexOfFirst { it.id !in skipIds }
                        .let { if (it == -1) sortedPhotos.size else it }
                }

                SwipeLoadResult(
                    sortedPhotos = sortedPhotos,
                    pendingMap = pendingMap,
                    startIndex = startIndex,
                    monthLabel = DateUtils.formatMonthYear(month.year, month.month),
                )
            }

            if (result == null) return@launch

            _uiState.value = SwipeUiState(
                photos = result.sortedPhotos,
                currentIndex = result.startIndex,
                monthKey = monthKey,
                monthLabel = result.monthLabel,
                decisions = result.pendingMap,
                isFinished = result.startIndex >= result.sortedPhotos.size,
                isLoading = false,
            )
        }
    }

    fun swipeLeft() {
        val state = _uiState.value
        if (state.currentIndex >= state.photos.size) return
        recordDecision(state.photos[state.currentIndex], Decision.DELETE)
        advance()
    }

    fun swipeRight() {
        val state = _uiState.value
        if (state.currentIndex >= state.photos.size) return
        recordDecision(state.photos[state.currentIndex], Decision.KEEP)
        advance()
    }

    fun toggleBookmark() {
        val state = _uiState.value
        if (state.currentIndex >= state.photos.size) return
        val photo = state.photos[state.currentIndex]
        val current = state.decisions[photo.id]
        val newDecision = if (current == Decision.BOOKMARK) null else Decision.BOOKMARK
        recordDecision(photo, newDecision)
        advance()
    }

    private fun recordDecision(photo: Photo, decision: Decision?) {
        val state = _uiState.value
        val updatedDecisions = state.decisions.toMutableMap()
        if (decision != null) {
            updatedDecisions[photo.id] = decision
        } else {
            updatedDecisions.remove(photo.id)
        }
        _uiState.value = state.copy(decisions = updatedDecisions)

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (decision != null) {
                        swipeDecisionDao.upsert(
                            SwipeDecisionEntity(
                                monthKey = state.monthKey,
                                photoId = photo.id,
                                uri = photo.uri.toString(),
                                decision = decision.name,
                                size = photo.size,
                                mimeType = photo.mimeType,
                                dateTaken = photo.dateTaken,
                                width = photo.width,
                                height = photo.height,
                                isCommitted = false,
                            )
                        )
                    } else {
                        swipeDecisionDao.removeDecision(photo.id, state.monthKey)
                    }
                }
            } catch (e: Exception) {
                Log.e("SwipeViewModel", "Failed to record decision", e)
            }
        }
    }

    private fun advance() {
        val state = _uiState.value
        val nextIndex = state.currentIndex + 1
        if (nextIndex >= state.photos.size) {
            _uiState.value = state.copy(currentIndex = nextIndex, isFinished = true)
            _navigateToReview.value = state.monthKey
        } else {
            _uiState.value = state.copy(currentIndex = nextIndex)
        }
    }

    fun undo() {
        val state = _uiState.value
        val prevIndex = state.currentIndex - 1
        if (prevIndex < 0) return
        val photo = state.photos[prevIndex]
        val updatedDecisions = state.decisions.toMutableMap()
        updatedDecisions.remove(photo.id)
        _uiState.value = state.copy(
            currentIndex = prevIndex, isFinished = false, decisions = updatedDecisions,
        )
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                swipeDecisionDao.removeDecision(photo.id, state.monthKey)
            }
        }
    }

    fun onNavigatedToReview() {
        _navigateToReview.value = null
    }
}
