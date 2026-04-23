package app.utils

import androidx.compose.ui.graphics.Color

fun hashedUsernameColor(username: String, isDarkMode: Boolean): Color {
    val digest = md5(username)
    val hue = (((digest[0].toInt() and 0xFF) shl 8) or (digest[1].toInt() and 0xFF)) % 360
    val saturation = if (isDarkMode) 0.75f else 0.65f
    val lightness = if (isDarkMode) 0.68f else 0.38f

    return hslColor(
        hue = hue.toFloat(),
        saturation = saturation,
        lightness = lightness
    )
}

private fun hslColor(
    hue: Float,
    saturation: Float,
    lightness: Float
): Color {
    val normalizedHue = ((hue % 360f) + 360f) % 360f / 360f
    val q = if (lightness < 0.5f) {
        lightness * (1f + saturation)
    } else {
        lightness + saturation - lightness * saturation
    }
    val p = 2f * lightness - q

    fun hueToChannel(t: Float): Float {
        var wrapped = t
        if (wrapped < 0f) wrapped += 1f
        if (wrapped > 1f) wrapped -= 1f

        return when {
            wrapped < 1f / 6f -> p + (q - p) * 6f * wrapped
            wrapped < 1f / 2f -> q
            wrapped < 2f / 3f -> p + (q - p) * (2f / 3f - wrapped) * 6f
            else -> p
        }
    }

    return if (saturation == 0f) {
        Color(lightness, lightness, lightness, 1f)
    } else {
        Color(
            red = hueToChannel(normalizedHue + 1f / 3f),
            green = hueToChannel(normalizedHue),
            blue = hueToChannel(normalizedHue - 1f / 3f),
            alpha = 1f
        )
    }
}
