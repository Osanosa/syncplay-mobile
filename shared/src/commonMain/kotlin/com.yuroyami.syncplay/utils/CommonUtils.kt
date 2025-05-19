package com.yuroyami.syncplay.utils

import com.yuroyami.syncplay.watchroom.viewmodel
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object CommonUtils {

    /** Generates the current clock timestamp */
    fun generateClockstamp(): String {
        val c = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).time
        return "${c.hour.fixDigits()}:${c.minute.fixDigits()}:${c.second.fixDigits()}"
    }

    private fun Int.fixDigits() = this.toString().padStart(2, '0')

    suspend fun beginPingUpdate() {
        while (true) {
            viewmodel?.p?.ping?.value = if (viewmodel?.p?.isSocketValid() == true) { //if (p.connection?.socket?.isActive == true && p.connection != null) {
                pingIcmp(viewmodel!!.p.session.serverHost, 32)
            } else null
            delay(1000)
        }
    }

    val vidExs = listOf(
        "mp4", "3gp", "av1", "mkv", "m4v", "mov", "wmv", "flv", "avi", "webm",
        "ogg", "ogv", "mpeg", "mpg", "m2v", "ts", "mts", "m2ts", "vob",
        "divx", "xvid", "asf", "rm", "rmvb", "qt", "f4v", "mxf", "m1v", "m2v",
        "3g2", "mpg2", "mpg4", "h264", "h265", "hevc", "mjpeg", "mjpg", "mod",
        "tod", "dat", "wma", "wav", "amv", "mtv", "swf"
    )

    val ccExs = listOf("srt", "sub", "sbv", "ass", "ssa", "usf", "idx", "vtt", "smi", "rt", "txt")

    val playlistExs = listOf("txt", "m3u")

    val langMap = mapOf(
        "العربية" to "ar", //Arabic
        //"Deutsch (ChatGPT)" to "de", //German
        "English" to "en", //English
        "español" to "es", //Spanish
        "Français" to "fr", //French
        //"हिन्दी (ChatGPT)" to "hi", //Hindi
        //"Italiano (ChatGPT)" to "it", //Italian
        //"日本語 (ChatGPT)" to "ja", //Japanese
        //"한국말 (ChatGPT)" to "ko", //Korean
        //"português (ChatGPT)" to "pt", //Portuguese
        "Pусский" to "ru", //Russian
        //"Türkçe (ChatGPT)" to "tr", //Turkish
        "中文" to "zh", //Chinese (Simplified)
    )
}