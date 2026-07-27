package org.gallery.swiper.ui.review

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.gallery.swiper.R
import org.gallery.swiper.data.model.Photo
import org.gallery.swiper.ui.theme.DeleteRed
import org.gallery.swiper.ui.theme.GreenCard
import org.gallery.swiper.ui.theme.KeepGreen
import org.gallery.swiper.ui.theme.RedCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    monthKey: String,
    onDone: () -> Unit,
    viewModel: ReviewViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.commitConfirmed(fromIntentSender = true)
        } else {
            viewModel.clearIntentSender()
        }
    }

    LaunchedEffect(state.intentSender) {
        state.intentSender?.let { sender ->
            deleteLauncher.launch(
                IntentSenderRequest.Builder(sender).build()
            )
        }
    }

    LaunchedEffect(monthKey) {
        viewModel.loadDecisions(monthKey)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review_decisions)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.commitDone -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.done), style = MaterialTheme.typography.headlineLarge, color = KeepGreen)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.deleted_photos, state.deletePhotos.size))
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = onDone) { Text(stringResource(R.string.back_to_home)) }
                    }
                }
            }

            state.isCommitting -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.moving_to_trash))
                    }
                }
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {

                    if (state.deletePhotos.isNotEmpty() && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                        Text(
                            stringResource(R.string.permanent_delete_warning),
                            color = DeleteRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    Text(
                        stringResource(R.string.photos_to_delete, state.deletePhotos.size),
                        color = DeleteRed,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    if (state.deletePhotos.isEmpty()) {
                        Text(
                            stringResource(R.string.no_photos_marked_delete),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.heightIn(max = 240.dp),
                        ) {
                            items(state.deletePhotos, key = { it.id }) { photo ->
                                PhotoThumbnail(photo, isDelete = true) {
                                    viewModel.moveToKeep(photo)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        stringResource(R.string.photos_to_keep, state.keepPhotos.size),
                        color = KeepGreen,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    if (state.keepPhotos.isEmpty()) {
                        Text(
                            stringResource(R.string.no_photos_marked_keep),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            items(state.keepPhotos, key = { it.id }) { photo ->
                                PhotoThumbnail(photo, isDelete = false) {
                                    viewModel.moveToDelete(photo)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { viewModel.requestCommit() },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.deletePhotos.isNotEmpty()) DeleteRed else KeepGreen,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (state.deletePhotos.isEmpty()) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.confirm_nothing_to_delete))
                        } else {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.size(8.dp))
                            Text(stringResource(R.string.delete_photos_btn, state.deletePhotos.size))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoThumbnail(photo: Photo, isDelete: Boolean, onMove: () -> Unit) {
    Card(
        modifier = Modifier.padding(4.dp).fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDelete) RedCard else GreenCard,
        ),
    ) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(photo.thumbnailUri)
                    .size(256)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            IconButton(
                onClick = onMove,
                modifier = Modifier.align(Alignment.BottomEnd).size(28.dp),
            ) {
                Icon(
                    if (isDelete) Icons.AutoMirrored.Filled.Undo else Icons.Default.Delete,
                    contentDescription = if (isDelete) stringResource(R.string.keep) else stringResource(R.string.delete),
                    tint = if (isDelete) KeepGreen else DeleteRed,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
