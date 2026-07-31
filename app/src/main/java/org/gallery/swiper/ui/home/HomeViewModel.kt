package org.gallery.swiper.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gallery.swiper.data.AppDatabase
import org.gallery.swiper.data.model.PhotoMonth
import org.gallery.swiper.data.repository.PhotoRepository

data class HomeUiState(
    val months: List<PhotoMonth> = emptyList(),
    val isLoading: Boolean = true,
    val hasPermission: Boolean = false,
    val error: String? = null,
    val totalReviewed: Int = 0,
    val totalDeleted: Int = 0,
    val totalSpaceSaved: Long = 0L,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository.getInstance(application)
    private val db = AppDatabase.getInstance(application)
    private val swipeDecisionDao = db.swipeDecisionDao()
    private val statsDao = db.statsDao()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            statsDao.getStats().collect { stats ->
                _uiState.value = _uiState.value.copy(
                    totalReviewed = stats?.totalReviewed ?: 0,
                    totalDeleted = stats?.totalDeleted ?: 0,
                    totalSpaceSaved = stats?.totalSpaceSaved ?: 0L,
                )
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            if (_uiState.value.months.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            }
            try {
                val result = withContext(Dispatchers.IO) {
                    coroutineScope {
                        val monthsDeferred = async { repository.getPhotosByMonth() }
                        val pendingDeferred = async { swipeDecisionDao.getAllPendingFlat() }
                        val committedDeferred = async { swipeDecisionDao.getCommittedCountsPerMonth() }
                        Triple(monthsDeferred.await(), pendingDeferred.await(), committedDeferred.await())
                    }
                }
                val months = result.first
                val pendingRows = result.second
                val committedCounts = result.third
                val pendingByMonth = pendingRows.groupBy { it.monthKey }
                val committedMap = committedCounts.associate { it.monthKey to it.cnt }

                val enriched = months.map { month ->
                    val pending = pendingByMonth[month.key] ?: emptyList()
                    val pendingDeleted = pending.count { it.decision == "DELETE" }
                    val pendingKept = pending.count { it.decision == "KEEP" || it.decision == "BOOKMARK" }
                    val committed = committedMap[month.key] ?: 0
                    val totalReviewed = (pendingDeleted + pendingKept + committed)
                        .coerceAtMost(month.totalCount)
                    month.copy(
                        reviewedCount = totalReviewed,
                        keptCount = pendingKept.coerceAtMost(month.totalCount),
                        deletedCount = pendingDeleted,
                    )
                }

                _uiState.value = _uiState.value.copy(
                    months = enriched, isLoading = false,
                )
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Failed to load photos", e)
                val current = _uiState.value
                if (current.months.isEmpty()) {
                    _uiState.value = current.copy(
                        isLoading = false, error = "Couldn't load photos: ${e.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun refresh() {
        if (_uiState.value.hasPermission) {
            repository.invalidateCache()
            loadData()
        }
    }

    fun setPermissionGranted() {
        _uiState.value = _uiState.value.copy(hasPermission = true)
        loadData()
    }
}
