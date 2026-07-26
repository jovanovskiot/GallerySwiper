package org.gallery.swiper.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.gallery.swiper.data.AppDatabase

data class StatsUiState(
    val totalReviewed: Int = 0,
    val totalDeleted: Int = 0,
    val totalKept: Int = 0,
    val totalSpaceSaved: Long = 0L,
    val reviewStreak: Int = 0,
    val completedMonths: Int = 0,
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val statsDao = db.statsDao()
    private val swipeDecisionDao = db.swipeDecisionDao()

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            statsDao.getStats().collect { stats ->
                val completed = withContext(Dispatchers.IO) {
                    swipeDecisionDao.getCompletedMonthCount()
                }
                _uiState.value = StatsUiState(
                    totalReviewed = stats?.totalReviewed ?: 0,
                    totalDeleted = stats?.totalDeleted ?: 0,
                    totalKept = stats?.totalKept ?: 0,
                    totalSpaceSaved = stats?.totalSpaceSaved ?: 0L,
                    reviewStreak = stats?.reviewStreak ?: 0,
                    completedMonths = completed,
                )
            }
        }
    }
}
