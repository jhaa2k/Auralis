package com.auralis.app.ui

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.auralis.app.playback.AuralisPlaybackService
import com.auralis.app.ui.theme.StudioAccent
import com.auralis.app.ui.theme.StudioBlack
import com.auralis.app.ui.theme.StudioLine
import com.auralis.app.ui.theme.StudioMuted
import com.auralis.app.ui.theme.StudioPanel
import com.auralis.app.ui.theme.StudioRaised
import com.auralis.app.ui.theme.StudioWhite
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun AuralisApp() {
    val context = LocalContext.current
    var mediaController by remember { mutableStateOf<MediaController?>(null) }
    var selectedTrackUri by remember { mutableStateOf<Uri?>(null) }
    var selectedTrackName by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackPosition by remember { mutableLongStateOf(0L) }
    var playbackDuration by remember { mutableLongStateOf(0L) }
    var playerMessage by remember { mutableStateOf("CONNECTING TO AUDIO ENGINE") }
    var selectedTab by remember { mutableStateOf(0) }

    DisposableEffect(context) {
        val token = SessionToken(
            context,
            ComponentName(context, AuralisPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        var active = true
        future.addListener(
            {
                if (active) {
                    runCatching { future.get() }
                        .onSuccess { mediaController = it }
                        .onFailure { playerMessage = "AUDIO ENGINE CONNECTION FAILED" }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
        onDispose {
            active = false
            MediaController.releaseFuture(future)
            mediaController = null
        }
    }

    DisposableEffect(mediaController) {
        val player = mediaController
        if (player == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    playbackDuration = player.duration.coerceAtLeast(0L)
                    playerMessage = when (playbackState) {
                        Player.STATE_BUFFERING -> "BUFFERING"
                        Player.STATE_READY -> if (playingOrPaused(player)) "PLAYING" else "READY"
                        Player.STATE_ENDED -> "PLAYBACK COMPLETE"
                        else -> if (player.mediaItemCount == 0) "AUDIO ENGINE READY" else "READY"
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    playbackPosition = 0L
                    playbackDuration = player.duration.coerceAtLeast(0L)
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    playerMessage = "PLAYBACK ERROR: ${error.errorCodeName}"
                    isPlaying = false
                }
            }
            player.addListener(listener)
            onDispose { player.removeListener(listener) }
        }
    }

    LaunchedEffect(mediaController, selectedTrackUri, selectedTrackName) {
        val player = mediaController
        val uri = selectedTrackUri
        val name = selectedTrackName
        if (player != null && uri != null) {
            val item = MediaItem.Builder()
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(name ?: "Unknown track").build()
                )
                .build()
            player.setMediaItem(item)
            player.prepare()
            player.play()
        }
    }

    LaunchedEffect(mediaController, isPlaying) {
        while (isPlaying && mediaController != null) {
            playbackPosition = mediaController?.currentPosition?.coerceAtLeast(0L) ?: 0L
            playbackDuration = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
            delay(500)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            selectedTrackName = queryDisplayName(context, uri)
            selectedTrackUri = uri
            playerMessage = "LOADING TRACK"
        }
    }

    Scaffold(
        containerColor = StudioBlack,
        bottomBar = {
            NavigationBar(containerColor = StudioPanel, contentColor = StudioWhite) {
                val tabs = listOf("STUDIO", "LAYERS", "AUDIO", "SETTINGS")
                val icons = listOf(
                    Icons.Outlined.GraphicEq,
                    Icons.Outlined.Layers,
                    Icons.Outlined.AudioFile,
                    Icons.Outlined.Settings
                )
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                icons[index],
                                contentDescription = label,
                                modifier = Modifier.size(19.dp)
                            )
                        },
                        label = {
                            Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                        }
                    )
                }
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .background(StudioBlack)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(54.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "AURALIS",
                    color = StudioWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.weight(1f))
                Text("NATIVE / 0.2", color = StudioMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            }

            Text("VISUALIZER WORKSPACE", color = StudioMuted, fontSize = 10.sp, letterSpacing = 1.5.sp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        Brush.linearGradient(listOf(StudioRaised, StudioBlack, StudioPanel)),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(1.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = StudioAccent,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "YOUR CANVAS",
                        color = StudioWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        if (selectedTrackName == null) "Import a track to start building"
                        else "Track loaded • visualizer engine coming next",
                        color = StudioMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Surface(
                color = StudioPanel,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, StudioLine),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                selectedTrackName ?: "NO TRACK LOADED",
                                color = StudioWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                playerMessage,
                                color = StudioMuted,
                                fontSize = 9.sp,
                                letterSpacing = 0.7.sp
                            )
                        }
                        Text(
                            "${formatTime(playbackPosition)} / ${formatTime(playbackDuration)}",
                            color = StudioMuted,
                            fontSize = 10.sp
                        )
                    }

                    Slider(
                        value = if (playbackDuration > 0L) {
                            playbackPosition.toFloat().coerceIn(0f, playbackDuration.toFloat())
                        } else 0f,
                        onValueChange = { value ->
                            playbackPosition = value.toLong()
                        },
                        onValueChangeFinished = {
                            if (playbackDuration > 0L) {
                                mediaController?.seekTo(playbackPosition)
                            }
                        },
                        valueRange = 0f..(playbackDuration.coerceAtLeast(1L).toFloat()),
                        enabled = mediaController != null && playbackDuration > 0L
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { audioPicker.launch(arrayOf("audio/*")) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioAccent,
                                contentColor = StudioBlack
                            ),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Outlined.AudioFile,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Text("  IMPORT AUDIO", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                mediaController?.let { player ->
                                    if (player.isPlaying) player.pause()
                                    else if (player.mediaItemCount > 0) player.play()
                                }
                            },
                            enabled = (mediaController?.mediaItemCount ?: 0) > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioRaised,
                                contentColor = StudioWhite,
                                disabledContainerColor = StudioRaised,
                                disabledContentColor = StudioMuted
                            ),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                if (isPlaying) " PAUSE" else " PLAY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Text(
                "MEDIA3 PLAYBACK  •  ANDROID DOCUMENT PICKER",
                color = StudioMuted,
                fontSize = 9.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

private fun queryDisplayName(
    context: android.content.Context,
    uri: Uri
): String {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    return runCatching {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Selected audio"
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun playingOrPaused(player: Player): Boolean = player.isPlaying
