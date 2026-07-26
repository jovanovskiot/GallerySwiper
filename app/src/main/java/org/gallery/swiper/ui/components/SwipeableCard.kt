package org.gallery.swiper.ui.components

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import org.gallery.swiper.ui.theme.DeleteRed
import org.gallery.swiper.ui.theme.KeepGreen
import kotlin.math.abs

private const val SWIPE_THRESHOLD = 300f

@Composable
fun SwipeableCard(
    imageUri: String,
    isVideo: Boolean = false,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier,
    onUndo: () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
) {
    var offsetX by remember(imageUri) { mutableFloatStateOf(0f) }
    val animOffset = remember(imageUri) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val rawProgress = abs(animOffset.value) / SWIPE_THRESHOLD
    val dismissProgress = rawProgress.coerceIn(0f, 1f)
    val context = LocalContext.current

    var isVideoPlaying by remember(imageUri) { mutableStateOf(false) }

    val player = remember(imageUri) {
        if (isVideo) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(imageUri)))
                prepare()
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = false
                volume = 0f
            }
        } else null
    }

    DisposableEffect(imageUri) {
        onDispose { player?.release() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = animOffset.value
                rotationZ = (animOffset.value / 20f).coerceIn(-12f, 12f)
                scaleX = (1f - (animOffset.value / (SWIPE_THRESHOLD * 2) * 0.05f)).coerceAtLeast(0.8f)
                scaleY = (1f - (animOffset.value / (SWIPE_THRESHOLD * 2) * 0.05f)).coerceAtLeast(0.8f)
            }
            .pointerInput(imageUri) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        scope.launch { animOffset.snapTo(offsetX) }
                    },
                    onDragEnd = {
                        if (abs(offsetX) > SWIPE_THRESHOLD) {
                            if (offsetX > 0) onSwipeRight() else onSwipeLeft()
                        } else {
                            scope.launch {
                                animOffset.animateTo(0f, tween(200))
                                offsetX = 0f
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            animOffset.animateTo(0f, tween(200))
                            offsetX = 0f
                        }
                    },
                )
            }
            .pointerInput(imageUri) {
                detectTapGestures {
                    if (!isVideo) {
                        onUndo()
                        scope.launch {
                            snackbarHostState?.showSnackbar("Last decision undone")
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
        ) {
            Box {
                if (isVideo && isVideoPlaying) {
                    player?.let { exo ->
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).also { pv ->
                                    pv.player = exo
                                    pv.useController = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black),
                        )
                    }
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }

                if (isVideo) {
                    IconButton(
                        onClick = {
                            isVideoPlaying = !isVideoPlaying
                            player?.playWhenReady = isVideoPlaying
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            if (isVideoPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isVideoPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }

                if (dismissProgress > 0.1f) {
                    val isKeep = animOffset.value > 0
                    val overlayColor = if (isKeep) KeepGreen else DeleteRed
                    val label = if (isKeep) "KEEP" else "DELETE"

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(overlayColor.copy(alpha = dismissProgress * 0.4f)),
                        contentAlignment = if (isKeep) Alignment.CenterStart else Alignment.CenterEnd,
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(overlayColor.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
