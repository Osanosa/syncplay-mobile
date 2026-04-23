package app.room.models

import androidx.compose.ui.graphics.Color
import com.yuroyami.syncplay.utils.hashedUsernameColor

data class MessagePalette(
    val timestampColor: Color,
    val selftagColor: Color,
    val friendtagColor: Color,
    val systemmsgColor: Color,
    val usermsgColor: Color,
    val errormsgColor: Color,
    val includeTimestamp: Boolean = true,
    val useHashedUsernameColors: Boolean = true,
    val isDarkTheme: Boolean = true
) {
    fun usernameTagColor(username: String, isMainUser: Boolean): Color {
        return if (useHashedUsernameColors && username.isNotBlank()) {
            hashedUsernameColor(username, isDarkTheme)
        } else if (isMainUser) {
            selftagColor
        } else {
            friendtagColor
        }
    }
}
