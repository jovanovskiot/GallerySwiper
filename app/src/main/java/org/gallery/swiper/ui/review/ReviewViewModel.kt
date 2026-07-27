package org.gallery.swiper.ui.review

import android.app.Application
import android.content.IntentSender
import android.net.Uri
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
import org.gallery.swiper.data.entity.BookmarkEntity
import org.gallery.swiper.data.entity.StatsEntity
import org.gallery.swiper.data.entity.SwipeDecisionEntity
import org.gallery.swiper.data.model.Decision
import org.gallery.swiper.data.model.Photo
import org.gallery.swiper.data.repository.PhotoRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ReviewUiState(
    val keepPhotos: List<Photo> = emptyList(),
    val deletePhotos: List<Photo> = emptyList(),
    val isCommitting: Boolean = false,
    val commitDone: Boolean = false,
    val intentSender: IntentSender? = null,
)

class ReviewViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)
    private val db = AppDatabase.getInstance(application)
    private val swipeDecisionDao = db.swipeDecisionDao()
    private val statsDao = db.statsDao()
    private val bookmarkDao = db.bookmarkDao()

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private var monthKey: String = ""
    private var bookmarkedPhotoIds: Set<Long> = emptySet()

    fun loadDecisions(key: String) {
        monthKey = key
        viewModelScope.launch {
            val decisions = withContext(Dispatchers.IO) {
                swipeDecisionDao.getPendingDecisions(key)
            }
            val parsed = decisions.mapNotNull { entity ->
                val d = try { Decision.valueOf(entity.decision) } catch (_: Exception) { null }
                if (d == null) null else entity to d
            }

            val toKeep = parsed.filter { it.second == Decision.KEEP }.map { entityToPhoto(it.first) }
            val toDelete = parsed.filter { it.second == Decision.DELETE }.map { entityToPhoto(it.first) }
            val bookmarked = parsed.filter { it.second == Decision.BOOKMARK }.map { entityToPhoto(it.first) }
            bookmarkedPhotoIds = bookmarked.map { it.id }.toSet()

            _uiState.value = ReviewUiState(
                keepPhotos = toKeep + bookmarked,
                deletePhotos = toDelete,
            )
        }
    }

    fun moveToDelete(photo: Photo) {
        val state = _uiState.value
        _uiState.value = state.copy(
            keepPhotos = state.keepPhotos.filter { it.id != photo.id },
            deletePhotos = state.deletePhotos + photo,
        )
        updateInDb(photo, Decision.DELETE)
    }

    fun moveToKeep(photo: Photo) {
        val state = _uiState.value
        _uiState.value = state.copy(
            deletePhotos = state.deletePhotos.filter { it.id != photo.id },
            keepPhotos = state.keepPhotos + photo,
        )
        val d = if (bookmarkedPhotoIds.contains(photo.id)) Decision.BOOKMARK else Decision.KEEP
        updateInDb(photo, d)
    }

    private fun updateInDb(photo: Photo, decision: Decision) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    swipeDecisionDao.removeDecision(photo.id, monthKey)
                    swipeDecisionDao.upsert(
                        SwipeDecisionEntity(
                            monthKey = monthKey, photoId = photo.id,
                            uri = photo.uri.toString(), decision = decision.name,
                            size = photo.size, mimeType = photo.mimeType,
                            dateTaken = photo.dateTaken,
                            width = photo.width,
                            height = photo.height,
                            isCommitted = false,
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("ReviewViewModel", "Failed to update decision", e)
            }
        }
    }

    fun requestCommit() {
        val state = _uiState.value
        if (state.deletePhotos.isEmpty()) {
            commitConfirmed(fromIntentSender = false)
            return
        }

        val uris = state.deletePhotos.map { it.uri }
        val sender = repository.getDeletionPendingIntentSender(uris)
        if (sender != null) {
            _uiState.value = state.copy(intentSender = sender)
            return
        }

        commitConfirmed(fromIntentSender = false)
    }

    fun commitConfirmed(fromIntentSender: Boolean = false) {
        _uiState.value = _uiState.value.copy(isCommitting = true, intentSender = null)
        viewModelScope.launch {
            val state = _uiState.value
            val deletedSpace = state.deletePhotos.sumOf { it.size }
            val deletedCount = state.deletePhotos.size
            val keptCount = state.keepPhotos.size

            withContext(Dispatchers.IO) {
                val deleteUris = state.deletePhotos.map { it.uri }

                // Only trash files when NOT coming from the IntentSender path
                var trashFailed = false
                if (!fromIntentSender && deleteUris.isNotEmpty()) {
                    trashFailed = !repository.sendToTrash(deleteUris)
                    if (trashFailed) {
                        Log.e("ReviewViewModel", "Failed to trash photos")
                    }
                }

                // Invalidate cache so gallery reloads fresh
                repository.invalidateCache()

                val finalDecisions = mutableListOf<SwipeDecisionEntity>()
                state.deletePhotos.forEach { photo ->
                    finalDecisions.add(
                        SwipeDecisionEntity(
                            monthKey = monthKey, photoId = photo.id,
                            uri = photo.uri.toString(), decision = Decision.DELETE.name,
                            size = photo.size, mimeType = photo.mimeType,
                            dateTaken = photo.dateTaken,
                            width = photo.width,
                            height = photo.height,
                            isCommitted = true,
                        )
                    )
                }
                state.keepPhotos.forEach { photo ->
                    val d = if (bookmarkedPhotoIds.contains(photo.id)) Decision.BOOKMARK else Decision.KEEP
                    finalDecisions.add(
                        SwipeDecisionEntity(
                            monthKey = monthKey, photoId = photo.id,
                            uri = photo.uri.toString(), decision = d.name,
                            size = photo.size, mimeType = photo.mimeType,
                            dateTaken = photo.dateTaken,
                            width = photo.width,
                            height = photo.height,
                            isCommitted = true,
                        )
                    )
                }

                swipeDecisionDao.replaceMonth(monthKey, finalDecisions)

                statsDao.initIfEmpty(StatsEntity())
                statsDao.addReviewed(deletedCount + keptCount)
                statsDao.addDeleted(deletedCount, deletedSpace)
                statsDao.addKept(keptCount)

                val lastDate = statsDao.getLastReviewDate()
                val today = System.currentTimeMillis()
                if (lastDate == null) {
                    statsDao.resetStreak(today)
                } else {
                    val todayDate = LocalDate.now(ZoneId.systemDefault())
                    val lastDateLocal = Instant.ofEpochMilli(lastDate)
                        .atZone(ZoneId.systemDefault()).toLocalDate()
                    if (todayDate.minusDays(1) == lastDateLocal) {
                        statsDao.incrementStreak(today)
                    } else if (todayDate != lastDateLocal) {
                        statsDao.resetStreak(today)
                    }
                }

                bookmarkedPhotoIds.forEach { pid ->
                    val photo = state.keepPhotos.find { it.id == pid }
                    if (photo != null) {
                        bookmarkDao.insert(
                            BookmarkEntity(
                                photoId = photo.id, uri = photo.uri.toString(),
                                dateTaken = photo.dateTaken, mimeType = photo.mimeType,
                                width = photo.width, height = photo.height, size = photo.size,
                            )
                        )
                    }
                }
            }

            _uiState.value = state.copy(isCommitting = false, commitDone = true)
        }
    }

    fun clearIntentSender() {
        _uiState.value = _uiState.value.copy(intentSender = null)
    }

    private fun entityToPhoto(entity: SwipeDecisionEntity): Photo {
        return Photo(
            id = entity.photoId,
            uri = Uri.parse(entity.uri),
            dateTaken = entity.dateTaken,
            size = entity.size,
            mimeType = entity.mimeType,
            width = entity.width,
            height = entity.height,
        )
    }
}
