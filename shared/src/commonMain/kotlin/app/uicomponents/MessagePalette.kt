package app.uicomponents

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.preferences.Preferences.COLOR_ERRORMSG
import app.preferences.Preferences.COLOR_FRIENDTAG
import app.preferences.Preferences.COLOR_HASH_USERNAMES
import app.preferences.Preferences.COLOR_SELFTAG
import app.preferences.Preferences.COLOR_SYSTEMMSG
import app.preferences.Preferences.COLOR_TIMESTAMP
import app.preferences.Preferences.COLOR_USERMSG
import app.preferences.Preferences.MSG_ACTIVATE_STAMP
import app.preferences.watchPref
import app.room.models.MessagePalette

val messagePalette: State<MessagePalette>
    @Composable get() {
        val colorTimestamp = COLOR_TIMESTAMP.watchPref()
        val colorHashUsernames = COLOR_HASH_USERNAMES.watchPref()
        val colorSelftag = COLOR_SELFTAG.watchPref()
        val colorFriendtag = COLOR_FRIENDTAG.watchPref()
        val colorSystem = COLOR_SYSTEMMSG.watchPref()
        val colorUserchat = COLOR_USERMSG.watchPref()
        val colorError = COLOR_ERRORMSG.watchPref()
        val msgIncludeTimestamp = MSG_ACTIVATE_STAMP.watchPref()
        val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f

        return derivedStateOf {
            MessagePalette(
                timestampColor = Color(colorTimestamp.value),
                selftagColor = Color(colorSelftag.value),
                friendtagColor = Color(colorFriendtag.value),
                systemmsgColor = Color(colorSystem.value),
                usermsgColor = Color(colorUserchat.value),
                errormsgColor = Color(colorError.value),
                includeTimestamp = msgIncludeTimestamp.value,
                useHashedUsernameColors = colorHashUsernames.value,
                isDarkTheme = isDarkTheme
            )
        }
    }
