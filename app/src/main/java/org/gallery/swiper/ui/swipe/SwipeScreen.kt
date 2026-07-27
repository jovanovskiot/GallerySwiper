package org.gallery.swiper.ui.swipe

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.imageLoader
import coil.request.ImageRequest
import org.gallery.swiper.data.model.Decision
import org.gallery.swiper.R
import org.gallery.swiper.ui.components.SwipeableCard
import org.gallery.swiper.ui.theme.BookmarkBlue
import org.gallery.swiper.ui.theme.DeleteRed
import org.gallery.swiper.ui.theme.KeepGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeScreen(
    monthKey: String,
    onFinish: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: SwipeViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val navigateToReview by viewModel.navigateToReview.collectAsState()
    val context = LocalContext.current

    BackHandler {
        onBack()
    }

    LaunchedEffect(navigateToReview) {
        navigateToReview?.let {
            viewModel.onNavigatedToReview()
            onFinish(it)
        }
    }

    LaunchedEffect(monthKey) {
        viewModel.loadMonth(monthKey)
    }

    LaunchedEffect(state.currentIndex) {
        if (state.isLoading || state.photos.isEmpty()) return@LaunchedEffect
        val total = state.photos.size
        val next = state.currentIndex + 1
        if (next >= total) return@LaunchedEffect
        val end = (next + 1).coerceAtMost(total - 1)
        for (i in next..end) {
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(state.photos[i].uri)
                    .crossfade(true)
                    .build()
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.monthLabel, fontSize = 16.sp)
                        if (state.photos.isNotEmpty()) {
                            LinearProgressIndicator(
                                progress = {
                                    (state.currentIndex.toFloat() / state.photos.size)
                                        .coerceIn(0f, 1f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            if (!state.isFinished && state.currentIndex < state.photos.size) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { viewModel.swipeLeft() },
                        colors = ButtonDefaults.buttonColors(containerColor = DeleteRed),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.delete), modifier = Modifier.size(32.dp))
                    }

                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = state.currentIndex > 0,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = stringResource(R.string.undo),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    IconButton(onClick = { viewModel.toggleBookmark() }) {
                        val isBookmarked = state.currentIndex < state.photos.size &&
                                state.decisions[state.photos[state.currentIndex].id] == Decision.BOOKMARK
                        Icon(
                            if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = stringResource(R.string.bookmark),
                            tint = if (isBookmarked) BookmarkBlue else MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Button(
                        onClick = { viewModel.swipeRight() },
                        colors = ButtonDefaults.buttonColors(containerColor = KeepGreen),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = stringResource(R.string.keep), modifier = Modifier.size(32.dp))
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.photos.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.no_photos_month),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { onBack() }) {
                            Text(stringResource(R.string.go_back))
                        }
                    }
                }

                state.currentIndex >= state.photos.size || state.isFinished -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.month_complete),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.review_decisions_desc))
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { onFinish(monthKey) }) {
                            Text(stringResource(R.string.review_confirm))
                        }
                    }
                }

                else -> {
                    SwipeableCard(
                        imageUri = state.photos[state.currentIndex].uri.toString(),
                        isVideo = state.photos[state.currentIndex].isVideo,
                        onSwipeLeft = { viewModel.swipeLeft() },
                        onSwipeRight = { viewModel.swipeRight() },
                        onUndo = { viewModel.undo() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                    )

                    Text(
                        text = "${state.currentIndex + 1} / ${state.photos.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
