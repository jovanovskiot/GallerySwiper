package org.gallery.swiper.ui.review

import android.app.Application
import android.content.ContentUris
import android.content.IntentSender
import android.net.Uri
import android.provider.MediaStore
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
import org.gallery.swiper.data.entity.SwipeDecisionEntity
import org.gallery.swiper.data.model.Decision
import org.gallery.swiper.data.model.Photo
import org.gallery.swiper.data.repository.PhotoRepository
import java.util.Calendar

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
            withContext(Dispatchers.IO) {
                swipeDecisionDao.removeDecision(photo.id, monthKey)
                swipeDecisionDao.upsert(
                    SwipeDecisionEntity(
                        monthKey = monthKey,
                        photoId = photo.id,
                        uri = photo.uri.toString(),
                        decision = decision.name,
                        size = photo.size,
                        isCommitted = false,
                    )
                )
            }
        }
    }

    fun requestCommit() {
        val state = _uiState.value
        if (state.deletePhotos.isEmpty()) {
            commitConfirmed()
            return
        }

        val uris = state.deletePhotos.map { it.uri }
        val sender = repository.getDeletionPendingIntentSender(uris)
        if (sender != null) {
            _uiState.value = state.copy(intentSender = sender)
            return
        }

        commitConfirmed()
    }

    fun commitConfirmed() {
        _uiState.value = _uiState.value.copy(isCommitting = true, intentSender = null)
        viewModelScope.launch {
            val state = _uiState.value
            val deletedSpace = state.deletePhotos.sumOf { it.size }
            val deletedCount = state.deletePhotos.size
            val keptCount = state.keepPhotos.size

            withContext(Dispatchers.IO) {
                val finalDecisions = mutableListOf<SwipeDecisionEntity>()
                state.deletePhotos.forEach { photo ->
                    finalDecisions.add(
                        SwipeDecisionEntity(
                            monthKey = monthKey,
                            photoId = photo.id,
                            uri = photo.uri.toString(),
                            decision = Decision.DELETE.name,
                            size = photo.size,
                            isCommitted = true,
                        )
                    )
                }
                state.keepPhotos.forEach { photo ->
                    val d = if (bookmarkedPhotoIds.contains(photo.id)) Decision.BOOKMARK else Decision.KEEP
                    finalDecisions.add(
                        SwipeDecisionEntity(
                            monthKey = monthKey,
                            photoId = photo.id,
                            uri = photo.uri.toString(),
                            decision = d.name,
                            size = photo.size,
                            isCommitted = true,
                        )
                    )
                }

                // Atomically replace month decisions
                swipeDecisionDao.replaceMonth(monthKey, finalDecisions)

                // Move photos to trash (after DB is safely updated)
                val deleteUris = state.deletePhotos.map { it.uri }
                if (deleteUris.isNotEmpty()) {
                    repository.sendToTrash(deleteUris)
                }

                // Stats
                statsDao.upsert(org.gallery.swiper.data.entity.StatsEntity())
                statsDao.addReviewed(deletedCount + keptCount)
                statsDao.addDeleted(deletedCount, deletedSpace)
                statsDao.addKept(keptCount)

                val lastDate = statsDao.getLastReviewDate()
                val today = System.currentTimeMillis()
                if (lastDate == null) {
                    statsDao.resetStreak(today)
                } else if (today - lastDate < 48 * 60 * 60 * 1000L) {
                    statsDao.incrementStreak(today)
                } else {
                    statsDao.resetStreak(today)
                }

                // Only bookmark originally bookmarked photos
                bookmarkedPhotoIds.forEach { pid ->
                    val photo = state.keepPhotos.find { it.id == pid }
                    if (photo != null) {
                        bookmarkDao.insert(
                            BookmarkEntity(
                                photoId = photo.id,
                                uri = photo.uri.toString(),
                                dateTaken = photo.dateTaken,
                                mimeType = photo.mimeType,
                                width = photo.width,
                                height = photo.height,
                                size = photo.size,
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
        val thumbUri = ContentUris.withAppendedId(
            MediaStore.Images.Thumbnails.EXTERNAL_CONTENT_URI, entity.photoId
        )
        return Photo(
            id = entity.photoId,
            uri = Uri.parse(entity.uri),
            thumbnailUri = thumbUri,
            dateTaken = 0, size = entity.size,
            mimeType = "image/*", width = 0, height = 0,
        )
    }
}
