package com.yuroyami.syncplay.watchroom

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType.Companion.LongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yuroyami.syncplay.player.PlayerUtils
import com.yuroyami.syncplay.utils.getSystemMaxVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

lateinit var gestureCallback: GestureCallback
var dragdistance = 0f
var initialVolume = 0
var initialBrightness = 0f

@Composable
fun GestureInterceptor(
    gestureState: State<Boolean>, videoState: State<Boolean>, onSingleTap: () -> Unit
) {
    val scope = rememberCoroutineScope { Dispatchers.IO }

    val g by gestureState
    val v by videoState

    val volumeSteps = getSystemMaxVolume()

    val seekLeftInteraction = remember { MutableInteractionSource() }
    val seekRightInteraction = remember { MutableInteractionSource() }

    val dimensions = LocalScreenSize.current

    //TODO: Individual gesture toggling option

    var currentBrightness by remember { mutableFloatStateOf(-1f) }
    var currentVolume by remember { mutableIntStateOf(-1) }

    var vertdragOffset by remember { mutableStateOf(Offset.Zero) }

    Box {
        var fastForward by remember { mutableStateOf(false) }
        var fastRewind by remember { mutableStateOf(false) }
        if (g) {
            Box(
                modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.1f)
                    .clickable(
                        enabled = false,
                        interactionSource = seekLeftInteraction,
                        indication = ripple(
                            bounded = false, color = Color(100, 100, 100, 190)
                        )
                    ) {})
            {
                AnimatedVisibility(
                    visible = fastRewind,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {

                    Icon(
                        imageVector = Icons.Default.FastRewind,
                        contentDescription = "",
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(64.dp)
                    )
                }

            }

            Box(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.1f)
                    .clickable(
                        enabled = false,
                        interactionSource = seekRightInteraction,
                        indication = ripple(
                            bounded = false, color = Color(100, 100, 100, 190)
                        )
                    ) {

                    }) {
                AnimatedVisibility(
                    visible = fastForward,
                    enter = scaleIn() + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {

                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = "",
                        tint = Color.White,
                        modifier = Modifier.align(Alignment.Center).size(64.dp)
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize().pointerInput(g, v) {
                detectTapGestures(

                    onPress = { offset ->
                        if (g && v && offset.x > dimensions.wPX.times(0.65f)) {
                            val press = PressInteraction.Press(offset)

                            val job = scope.launch {
                                delay(1000) // wait 1 second before fast forwarding
                                fastForward = true
                                seekRightInteraction.emit(press)
                                while (isActive) {
                                    PlayerUtils.seekFrwrd()
                                    seekRightInteraction.emit(press)
                                    delay(200)
                                    seekRightInteraction.emit(PressInteraction.Release(press))
                                }
                            }
                            tryAwaitRelease()
                            job.cancel()
                            fastForward = false
                            seekRightInteraction.emit(PressInteraction.Release(press))

                        }
                        if (g && v && offset.x < dimensions.wPX.times(0.35f)) {
                            val press = PressInteraction.Press(offset)
                            val job = scope.launch {
                                delay(1000) // wait 1 second before rewinding
                                fastRewind = true
                                seekLeftInteraction.emit(press)
                                while (isActive) {
                                    PlayerUtils.seekBckwd()
                                    seekLeftInteraction.emit(press)
                                    delay(200)
                                    seekLeftInteraction.emit(PressInteraction.Release(press))
                                }
                            }
                            tryAwaitRelease()
                            job.cancel()
                            fastRewind = false
                            seekLeftInteraction.emit(PressInteraction.Release(press))

                        }
                    },
                    onDoubleTap = if (g && v) { offset ->
                        scope.launch {
                            if (offset.x < dimensions.wPX.times(0.35f)) {
                                PlayerUtils.seekBckwd()

                                val press = PressInteraction.Press(Offset.Zero)
                                seekLeftInteraction.emit(press)
                                delay(200)
                                seekLeftInteraction.emit(PressInteraction.Release(press))
                            }
                            if (offset.x > dimensions.wPX.times(0.65f)) {
                                PlayerUtils.seekFrwrd()

                                val press = PressInteraction.Press(Offset.Zero)
                                seekRightInteraction.emit(press)
                                delay(150)
                                seekRightInteraction.emit(PressInteraction.Release(press))
                            }
                        }
                    } else null,
                    onTap = { onSingleTap.invoke() },
                )
            }.pointerInput(g, v) {
                if (g && v) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            initialBrightness = gestureCallback.getCurrentBrightness()
                            initialVolume = gestureCallback.getCurrentVolume()
                        },
                        onDragEnd = {
                            dragdistance = 0F
                            currentBrightness = -1f
                            currentVolume = -1
                        },
                        onVerticalDrag = { pntr, f ->
                            dragdistance += f
                            vertdragOffset = pntr.position

                            if (pntr.position.x >= dimensions.wPX * 0.5f) {
                                // Volume adjusting
                                val h = dimensions.hPX / 1.5
                                val maxVolume = gestureCallback.getMaxVolume()
                                var newVolume = (initialVolume + (-dragdistance * maxVolume / h)).roundToInt()
                                if (newVolume > maxVolume) newVolume = maxVolume
                                if (newVolume < 0) newVolume = 0

                                currentVolume = newVolume
                                gestureCallback.changeCurrentVolume(newVolume)
                            } else {
                                // Brightness adjusting in 5% increments
                                val h = dimensions.hPX / 1.5
                                val maxBright = gestureCallback.getMaxBrightness()
                                var newBright = initialBrightness + (-dragdistance * maxBright / h).toFloat()
                                // Quantize to nearest 5%
                                newBright = if (newBright > 0.1f) (newBright / 0.05f).roundToInt() * 0.05f else newBright

                                currentBrightness = newBright.coerceIn(0f, 1f)
                                gestureCallback.changeCurrentBrightness(newBright.coerceIn(0f, 1f))
                            }
                        }
                    )
                }
            }

            ) {}
            with(LocalDensity.current) {
                if (currentBrightness != -1f) {
                    Row(
                        modifier = Modifier.offset(
                            (vertdragOffset.x + 100).toDp(), vertdragOffset.y.toDp()
                        ).clip(RoundedCornerShape(25)).background(Color.LightGray),
                        verticalAlignment = CenterVertically
                    ) {
                        Icon(imageVector = Icons.Filled.Brightness6, "")
                        //TODO: Delegate 'times(100).toInt()' to platform
                        Text(
                            "Brightness: ${currentBrightness.times(100).toInt()}%",
                            color = Color.Black
                        )
                    }
                }

                if (currentVolume != -1) {
                    Row(
                        modifier = Modifier.offset(
                            (vertdragOffset.x - 500).toDp(), vertdragOffset.y.toDp()
                        ).clip(RoundedCornerShape(25)).background(Color.LightGray),
                        verticalAlignment = CenterVertically
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, "")
                        Text("Volume: $currentVolume/$volumeSteps", color = Color.Black)
                    }
                }
            }
        }
    }
}