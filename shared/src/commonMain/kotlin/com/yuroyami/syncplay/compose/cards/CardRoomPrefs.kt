package com.yuroyami.syncplay.compose.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.TopCenter
import androidx.compose.ui.Alignment.Companion.TopEnd
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.yuroyami.syncplay.compose.ComposeUtils.FancyIcon2
import com.yuroyami.syncplay.compose.ComposeUtils.FancyText2
import com.yuroyami.syncplay.settings.SettingsUI
import com.yuroyami.syncplay.settings.SettingsUI.SettingsGrid
import com.yuroyami.syncplay.settings.SettingsUI.SettingsGridLayout
import com.yuroyami.syncplay.ui.Paletting
import com.yuroyami.syncplay.watchroom.LocalRoomSettings
import com.yuroyami.syncplay.watchroom.lyricist
import org.jetbrains.compose.resources.Font
import syncplaymobile.shared.generated.resources.Directive4_Regular
import syncplaymobile.shared.generated.resources.Res

object CardRoomPrefs {

    @Composable
    fun InRoomSettingsCard() {
        val settingState = remember { mutableIntStateOf(1) }

        Card(
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(width = 1.dp, brush = Brush.linearGradient(colors = Paletting.SP_GRADIENT.map { it.copy(alpha = 0.5f) })),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(0.5f)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                FancyText2(
                    modifier = Modifier.align(TopCenter).padding(6.dp),
                    string = lyricist.strings.roomCardTitleInRoomPrefs,
                    solid = Color.Transparent,
                    size = 16f,
                    font = Font(Res.font.Directive4_Regular)
                )

                if (settingState.intValue == 2) {
                    FancyIcon2(
                        modifier = Modifier.align(TopEnd).padding(6.dp),
                        icon = Icons.AutoMirrored.Filled.Redo, size = 32, shadowColor = Color.DarkGray,
                        onClick = { settingState.intValue = 1 }
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize().padding(top = 32.dp).align(TopCenter)
                ) {
                    SettingsGrid(
                        modifier = Modifier.fillMaxSize(),
                        layoutOrientation = SettingsGridLayout.SETTINGS_GRID_HORIZONTAL_FLOW,
                        settingcategories = LocalRoomSettings.current,
                        state = settingState,
                        titleSize = 9f,
                        cardSize = 48f,
                        onCardClicked = {
                            settingState.intValue = 2
                        }
                    )
                }
            }
        }
    }
}