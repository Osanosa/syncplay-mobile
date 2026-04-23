package app.room.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalRoomViewmodel
import app.player.Playback
import app.protocol.models.ConnectionState
import app.room.RoomViewmodel
import app.room.models.Message
import app.room.ui.statinfo.PingIndicator
import app.uicomponents.AnimatedImage
import app.uicomponents.messagePalette
import app.utils.timeStamper
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import syncplaymobile.shared.generated.resources.Res
import syncplaymobile.shared.generated.resources.room_chat_mode_no_chat
import syncplaymobile.shared.generated.resources.room_chat_mode_seek_back_long
import syncplaymobile.shared.generated.resources.room_chat_mode_seek_back_short
import syncplaymobile.shared.generated.resources.room_chat_mode_seek_forward_long
import syncplaymobile.shared.generated.resources.room_chat_mode_seek_forward_short
import syncplaymobile.shared.generated.resources.room_chat_mode_title
import syncplaymobile.shared.generated.resources.room_details_current_room
import syncplaymobile.shared.generated.resources.room_ping_connected
import syncplaymobile.shared.generated.resources.room_ping_disconnected

@Composable
fun ChatOnlyRoomScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    val viewmodel = LocalRoomViewmodel.current
    val focusManager = LocalFocusManager.current
    val supportsChat by viewmodel.protocol.supportsChat.collectAsState()
    val ping by viewmodel.ping.collectAsState()
    val userList by viewmodel.session.userList.collectAsState()
    val currentTimeMs by viewmodel.playerManager.timeCurrentMillis.collectAsState()
    val fullTimeMs by viewmodel.playerManager.timeFullMillis.collectAsState()
    val isPlaying by viewmodel.playerManager.isNowPlaying.collectAsState()
    val chatMessages by viewmodel.session.messageSequence.collectAsState()
    val gifPanelVisible by viewmodel.uiState.gifPanelVisible.collectAsState()
    val msg by viewmodel.uiState.msg.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val isConnected = remember(viewmodel.networkManager.state.value, userList) {
        viewmodel.networkManager.state.value == ConnectionState.CONNECTED || userList.isNotEmpty()
    }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.lastIndex)
            listState.animateScrollBy(Float.MAX_VALUE)
        }
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledIconButton(
                    onClick = {
                        focusManager.clearFocus()
                        onBack?.invoke() ?: viewmodel.goHome()
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = stringResource(Res.string.room_chat_mode_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(Res.string.room_details_current_room, viewmodel.session.currentRoom),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (!isConnected) {
                            stringResource(Res.string.room_ping_disconnected)
                        } else if (ping == null) {
                            stringResource(Res.string.room_ping_connected, "...")
                        } else {
                            stringResource(Res.string.room_ping_connected, ping.toString())
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                    PingIndicator(ping)
                }
            }

            if (viewmodel.osdMsg.value.isNotBlank()) {
                Text(
                    text = viewmodel.osdMsg.value,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
            }

            ChatOnlyPlaybackControls(
                isPlaying = isPlaying,
                currentTimeMs = currentTimeMs,
                fullTimeMs = fullTimeMs,
                onTogglePlayback = {
                    viewmodel.dispatcher.controlPlayback(
                        if (isPlaying) Playback.PAUSE else Playback.PLAY,
                        tellServer = true
                    )
                },
                onSeek = { seconds ->
                    seekRelative(viewmodel = viewmodel, deltaSeconds = seconds)
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            ) {
                if (!supportsChat) {
                    Text(
                        modifier = Modifier.align(Alignment.Center),
                        text = stringResource(Res.string.room_chat_mode_no_chat),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(chatMessages) { index, message ->
                            SideEffect { message.seen = true }
                            val previousMessage = chatMessages.getOrNull(index - 1)
                            val showSenderInfo = previousMessage == null ||
                                previousMessage.sender == null ||
                                previousMessage.sender != message.sender ||
                                previousMessage.isMainUser != message.isMainUser

                            ChatOnlyMessageBubble(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusable(enabled = false)
                                    .clickable(enabled = false) {}
                                    .onSizeChanged {
                                        if (index == chatMessages.lastIndex) {
                                            coroutineScope.launch {
                                                listState.animateScrollToItem(chatMessages.lastIndex)
                                                listState.animateScrollBy(Float.MAX_VALUE)
                                            }
                                        }
                                    },
                                message = message,
                                showSenderInfo = showSenderInfo
                            )
                        }
                    }
                }
            }

            if (supportsChat && gifPanelVisible) {
                GifPanel(
                    query = msg,
                    onGifSelected = { gifUrl ->
                        viewmodel.dispatcher.sendMessage(gifUrl)
                        viewmodel.uiState.gifPanelVisible.value = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 320.dp)
                )
            }

            if (supportsChat) {
                ChatTextField(
                    modifier = Modifier.fillMaxWidth(),
                    viewmodel = viewmodel,
                    gifPanelVisible = gifPanelVisible,
                    isHUDVisible = true
                )
            }
        }
    }
}

@Composable
private fun ChatOnlyPlaybackControls(
    isPlaying: Boolean,
    currentTimeMs: Long,
    fullTimeMs: Long,
    onTogglePlayback: () -> Unit,
    onSeek: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatOnlyControlButton(
            label = null,
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            onClick = onTogglePlayback
        )
        ChatOnlyControlButton(
            label = stringResource(Res.string.room_chat_mode_seek_back_short),
            icon = Icons.Filled.FastRewind,
            onClick = { onSeek(-15) }
        )
        ChatOnlyControlButton(
            label = stringResource(Res.string.room_chat_mode_seek_back_long),
            icon = Icons.Filled.FastRewind,
            onClick = { onSeek(-60) }
        )
        ChatOnlyControlButton(
            label = stringResource(Res.string.room_chat_mode_seek_forward_short),
            icon = Icons.Filled.FastForward,
            onClick = { onSeek(15) }
        )
        ChatOnlyControlButton(
            label = stringResource(Res.string.room_chat_mode_seek_forward_long),
            icon = Icons.Filled.FastForward,
            onClick = { onSeek(60) }
        )

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
        ) {
            Text(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                text = "${timeStamper(currentTimeMs)} / ${if (fullTimeMs > 0L) timeStamper(fullTimeMs) else "???"}",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ChatOnlyControlButton(
    label: String?,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null
            )
            label?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ChatOnlyMessageBubble(
    message: Message,
    showSenderInfo: Boolean,
    modifier: Modifier = Modifier
) {
    val palette by messagePalette
    val alignMine = message.sender != null && message.isMainUser
    val bubbleAlignment = when {
        message.sender == null -> Alignment.Center
        alignMine -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }
    val bubbleShape = remember(alignMine) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (alignMine) 16.dp else 8.dp,
            bottomEnd = if (alignMine) 8.dp else 16.dp
        )
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = bubbleAlignment
    ) {
        if (message.sender == null) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    text = message.content,
                    color = if (message.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }
        } else {
            Column(
                modifier = Modifier.widthIn(max = 420.dp),
                horizontalAlignment = if (alignMine) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showSenderInfo) {
                    Text(
                        text = buildAnnotatedString {
                            val sender = message.sender.orEmpty()
                            withStyle(
                                SpanStyle(
                                    color = palette.usernameTagColor(sender, message.isMainUser),
                                    fontWeight = FontWeight.SemiBold
                                )
                            ) {
                                append(sender)
                            }
                            withStyle(
                                SpanStyle(color = palette.timestampColor)
                            ) {
                                append(" - ${message.timestamp}")
                            }
                        },
                        fontSize = 11.sp
                    )
                }

                if (message.isImageUrl) {
                    ChatOnlyInlineImageBubble(
                        mediaUrl = message.content,
                        shape = bubbleShape,
                        fallbackText = message.content,
                        alignMine = alignMine
                    )
                } else {
                    Surface(
                        shape = bubbleShape,
                        color = if (alignMine) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        }
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            text = message.content,
                            color = if (alignMine) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            fontSize = 15.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatOnlyInlineImageBubble(
    mediaUrl: String,
    shape: RoundedCornerShape,
    fallbackText: String,
    alignMine: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.78f),
        shape = shape,
        color = if (alignMine) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
        } else {
            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
        }
    ) {
        AnimatedImage(
            url = mediaUrl,
            contentDescription = mediaUrl,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape),
            contentScale = ContentScale.FillWidth
        )
    }
}

private fun seekRelative(
    viewmodel: RoomViewmodel,
    deltaSeconds: Int
) {
    viewmodel.player.playerScopeIO.launch {
        val currentMs = viewmodel.player.currentPositionMs()
        val maxPosition = viewmodel.playerManager.timeFullMillis.value
            .takeIf { it > 0L }
            ?: viewmodel.playerManager.media.value?.fileDuration?.toLong()?.times(1000L)
            ?: Long.MAX_VALUE

        val newPosition = (currentMs + deltaSeconds * 1000L).coerceIn(0L, maxPosition)

        viewmodel.dispatcher.sendSeek(newPosition)
        viewmodel.player.seekTo(newPosition)
    }
}
