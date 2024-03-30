package com.yuroyami.syncplay.player.mpv

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import cafe.adriel.lyricist.Lyricist
import com.yuroyami.syncplay.databinding.MpvviewBinding
import com.yuroyami.syncplay.lyricist.Stringies
import com.yuroyami.syncplay.models.Chapter
import com.yuroyami.syncplay.models.MediaFile
import com.yuroyami.syncplay.models.Track
import com.yuroyami.syncplay.player.BasePlayer
import com.yuroyami.syncplay.player.PlayerUtils
import com.yuroyami.syncplay.player.PlayerUtils.trackProgress
import com.yuroyami.syncplay.protocol.JsonSender
import com.yuroyami.syncplay.utils.RoomUtils.checkFileMismatches
import com.yuroyami.syncplay.utils.RoomUtils.sendPlayback
import com.yuroyami.syncplay.utils.collectInfoLocalAndroid
import com.yuroyami.syncplay.utils.getFileName
import com.yuroyami.syncplay.utils.loggy
import com.yuroyami.syncplay.utils.timeStamper
import com.yuroyami.syncplay.watchroom.dispatchOSD
import com.yuroyami.syncplay.watchroom.isSoloMode
import com.yuroyami.syncplay.watchroom.lyricist
import com.yuroyami.syncplay.watchroom.viewmodel
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.roundToLong

class MpvPlayer : BasePlayer() {

    override val engine = ENGINE.ANDROID_MPV

    var mpvPos = 0L
    private lateinit var observer: MPVLib.EventObserver
    private var ismpvInit = false
    private lateinit var mpvView: MPVView
    private lateinit var ctx: Context

    override val canChangeAspectRatio: Boolean
        get() = true

    override val supportsChapters: Boolean
        get() = true

    override fun initialize() {
        ctx = mpvView.context.applicationContext

        copyAssets(ctx)
    }

    @Composable
    override fun VideoPlayer(modifier: Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                mpvView = MpvviewBinding.inflate(LayoutInflater.from(context)).mpvview
                initialize()
                return@AndroidView mpvView
            },
            update = {})
    }

    override fun hasMedia(): Boolean {
        val c = MPVLib.getPropertyInt("playlist-count")
        return c != null && c > 0
    }

    override fun isPlaying(): Boolean {
        return if (!ismpvInit) false
        else !mpvView.paused
    }

    override fun analyzeTracks(mediafile: MediaFile) {
        viewmodel?.media?.subtitleTracks?.clear()
        viewmodel?.media?.audioTracks?.clear()
        val count = MPVLib.getPropertyInt("track-list/count")!!
        // Note that because events are async, properties might disappear at any moment
        // so use ?: continue instead of !!
        for (i in 0 until count) {
            val type = MPVLib.getPropertyString("track-list/$i/type") ?: continue
            if (type != "audio" && type != "sub") continue
            val mpvId = MPVLib.getPropertyInt("track-list/$i/id") ?: continue
            val lang = MPVLib.getPropertyString("track-list/$i/lang")
            val title = MPVLib.getPropertyString("track-list/$i/title")
            val selected = MPVLib.getPropertyBoolean("track-list/$i/selected") ?: false

            /** Speculating the track name based on whatever info there is on it */
            val trackName = if (!lang.isNullOrEmpty() && !title.isNullOrEmpty())
                "$title [$lang]"
            else if (!lang.isNullOrEmpty() && title.isNullOrEmpty()) {
                "$title [UND]"
            } else if (!title.isNullOrEmpty() && lang.isNullOrEmpty())
                "Track [$lang]"
            else "Track $mpvId [UND]"

            Log.e("trck", "Found track $mpvId: $type, $title [$lang], $selected")
            when (type) {
                "audio" -> {
                    viewmodel?.media?.audioTracks?.add(
                        Track(
                            name = trackName,
                            index = mpvId,
                            trackType = TRACKTYPE.AUDIO,
                        ).apply {
                            this.selected.value = selected
                        }
                    )
                }

                "sub" -> {
                    viewmodel?.media?.subtitleTracks?.add(
                        Track(
                            name = trackName,
                            index = mpvId,
                            trackType = TRACKTYPE.SUBTITLE,
                        ).apply {
                            this.selected.value = selected
                        }
                    )
                }
            }
        }
    }

    override fun selectTrack(type: TRACKTYPE, index: Int) {
        when (type) {
            TRACKTYPE.SUBTITLE -> {
                if (index >= 0) {
                    MPVLib.setPropertyInt("sid", index)
                } else if (index == -1) {
                    MPVLib.setPropertyString("sid", "no")
                }

                viewmodel?.currentTrackChoices?.subtitleSelectionIndexMpv = index
            }

            TRACKTYPE.AUDIO -> {
                if (index >= 0) {
                    MPVLib.setPropertyInt("aid", index)
                } else if (index == -1) {
                    MPVLib.setPropertyString("aid", "no")
                }

                viewmodel?.currentTrackChoices?.audioSelectionIndexMpv = index
            }
        }
    }

    override fun analyzeChapters(mediafile: MediaFile) {
        if (!ismpvInit) return
        val chapters = mpvView.loadChapters()
        if (chapters.isEmpty()) return
        mediafile.chapters.clear()
        mediafile.chapters.addAll(chapters.map {
            val timestamp = " (${timeStamper(it.time.roundToLong())})"
            Chapter(
                it.index,
                (it.title ?: "Chapter ${it.index}") + timestamp,
                (it.time * 1000).roundToLong()
            )
        })
    }

    override fun jumpToChapter(chapter: Chapter) {
        if (!ismpvInit) return
        MPVLib.setPropertyInt("chapter", chapter.index)
    }

    override fun skipChapter() {
        if (!ismpvInit) return

        MPVLib.command(arrayOf("add", "chapter", "1"))
    }

    override fun reapplyTrackChoices() {
        val subIndex = viewmodel?.currentTrackChoices?.subtitleSelectionIndexMpv
        val audioIndex = viewmodel?.currentTrackChoices?.audioSelectionIndexMpv

        with(viewmodel?.player ?: return) {
            if (subIndex != null) selectTrack(TRACKTYPE.SUBTITLE, subIndex)
            if (audioIndex != null) selectTrack(TRACKTYPE.AUDIO, audioIndex)
        }
    }

    override fun loadExternalSub(uri: String) {
        if (hasMedia()) {
            val filename = getFileName(uri = uri).toString()
            val extension = filename.substring(filename.length - 4).lowercase()

            val mimeTypeValid = (extension.contains("srt")
                    || extension.contains("ass")
                    || extension.contains("ssa")
                    || extension.contains("ttml")
                    || extension.contains("vtt"))

            if (mimeTypeValid) {
                ctx.resolveUri(uri.toUri())?.let {
                    MPVLib.command(arrayOf("sub-add", it, "cached"))
                }
                playerScopeMain.dispatchOSD(lyricist.strings.roomSelectedSub(filename))
            } else {
                playerScopeMain.dispatchOSD(lyricist.strings.roomSelectedSubError)
            }
        } else {
            playerScopeMain.dispatchOSD(lyricist.strings.roomSubErrorLoadVidFirst)
        }
    }

    override fun injectVideo(uri: String?, isUrl: Boolean) {
        /* Changing UI (hiding artwork, showing media controls) */
        viewmodel?.hasVideoG?.value = true
        val ctx = mpvView.context ?: return

        playerScopeMain.launch {
            /* Creating a media file from the selected file */
            if (uri != null || viewmodel?.media == null) {
                viewmodel?.media = MediaFile()
                viewmodel?.media?.uri = uri

                /* Obtaining info from it (size and name) */
                if (isUrl) {
                    viewmodel?.media?.url = uri.toString()
                    viewmodel?.media?.let { collectInfoURL(it) }
                } else {
                    viewmodel?.media?.let { collectInfoLocal(it) }
                }

                /* Checking mismatches with others in room */
                checkFileMismatches()
            }
            /* Injecting the media into exoplayer */
            try {

                delay(500)
                uri?.let {
                    if (!isUrl) {
                        ctx.resolveUri(it.toUri())?.let { it2 ->
                            loggy("Final path $it2", 301)
                            if (!ismpvInit) {
                                mpvView.initialize(ctx.filesDir.path, ctx.cacheDir.path)
                                ismpvInit = true
                            }
                            mpvObserverAttach()
                            mpvView.playFile(it2)
                            mpvView.surfaceCreated(mpvView.holder)
                        }
                    } else {
                        if (!ismpvInit) {
                            mpvView.initialize(ctx.filesDir.path, ctx.cacheDir.path)
                            ismpvInit = true
                        }
                        mpvObserverAttach()
                        mpvView.playFile(uri.toString())
                        mpvView.surfaceCreated(mpvView.holder)
                    }
                }

                /* Goes back to the beginning for everyone */
                if (!isSoloMode) {
                    viewmodel!!.p.currentVideoPosition = 0.0
                }
            } catch (e: IOException) {
                /* If, for some reason, the video didn't wanna load */
                e.printStackTrace()
                playerScopeMain.dispatchOSD("There was a problem loading this file.")
            }

            /* Finally, show a a toast to the user that the media file has been added */
            val lyricist = Lyricist("en", Stringies)
            playerScopeMain.dispatchOSD(lyricist.strings.roomSelectedVid("${viewmodel?.media?.fileName}"))

        }
    }

    override fun pause() {
        if (!ismpvInit) return

        playerScopeIO.launch {
            mpvView.paused = true
        }
    }

    override fun play() {
        if (!ismpvInit) return

        playerScopeIO.launch {
            mpvView.paused = false
        }
    }

    override fun isSeekable(): Boolean {
        return true
    }

    override fun seekTo(toPositionMs: Long) {
        if (!ismpvInit) return

        playerScopeIO.launch {
            mpvView.timePos = toPositionMs.toInt() / 1000
        }
    }

    override fun currentPositionMs(): Long {
        return mpvPos
    }

    override fun switchAspectRatio(): String {
        val currentAspect = MPVLib.getPropertyString("video-aspect-override")
        val currentPanscan = MPVLib.getPropertyDouble("panscan")

        loggy("currentAspect: $currentAspect and currentPanscan: $currentPanscan", 0)

        val aspectRatios = listOf(
            "-1.000000" to "Original" ,
            "1.777778" to "16:9",
            "1.600000" to "16:10",
            "1.333333" to "4:3",
            "2.350000" to "2.35:1",
            "panscan" to "Pan/Scan"
        )

        var enablePanscan = false
        val nextAspect = if (currentPanscan == 1.0) {
            aspectRatios[0]
        } else if (currentAspect == "2.350000") {
            enablePanscan = true
            aspectRatios[5]
        } else {
            aspectRatios[aspectRatios.indexOfFirst { it.first == currentAspect } + 1]
        }

        if (enablePanscan) {
            MPVLib.setPropertyString("video-aspect-override", "-1")
            MPVLib.setPropertyDouble("panscan", 1.0)
        } else {
            MPVLib.setPropertyString("video-aspect-override", nextAspect.first)
            MPVLib.setPropertyDouble("panscan", 0.0)
        }

        return nextAspect.second
    }

    override fun collectInfoLocal(mediafile: MediaFile) {
        collectInfoLocalAndroid(mediafile, ctx)
    }

    override fun changeSubtitleSize(newSize: Int) {

        val s: Double = when {
            newSize == 16 -> 1.0
            newSize > 16 -> {
                1.0 + (newSize - 16) * 0.05
            }

            else -> {
                1.0 - (16 - newSize) * (1.0 / 16)
            }
        }

        MPVLib.setPropertyDouble("sub-scale", s)
    }

    /** MPV EXCLUSIVE */
    private fun mpvObserverAttach() {
        removeObserver()

        observer = object : MPVLib.EventObserver {
            override fun eventProperty(property: String) {
            }

            override fun eventProperty(property: String, value: Long) {
                when (property) {
                    "time-pos" -> mpvPos = value * 1000
                    "duration" -> viewmodel?.timeFull?.longValue = value
                    //"file-size" -> value
                }
            }

            override fun eventProperty(property: String, value: Boolean) {
                when (property) {
                    "pause" -> {
                        viewmodel?.isNowPlaying?.value = !value //Just to inform UI

                        //Tell server about playback state change
                        if (!isSoloMode) {
                            sendPlayback(!value)
                            viewmodel!!.p.paused = value
                        }
                    }
                }
            }

            override fun eventProperty(property: String, value: String) {
            }

            override fun event(eventId: Int) {
                when (eventId) {
                    MPVLib.mpvEventId.MPV_EVENT_START_FILE -> {
                        viewmodel?.hasVideoG?.value = true

                        if (isSoloMode) return
                        playerScopeIO.launch {
                            while (true) {
                                if (viewmodel != null) {
                                    if (viewmodel!!.timeFull.longValue.toDouble() > 0) {
                                        viewmodel?.media?.fileDuration = viewmodel?.timeFull?.longValue?.toDouble()!!
                                        viewmodel!!.p.sendPacket(JsonSender.sendFile(viewmodel?.media ?: return@launch))
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        mpvView.addObserver(observer)

        playerScopeMain.trackProgress(intervalMillis = 500L)
    }

    //TODO
    private fun copyAssets(context: Context) {
        val assetManager = context.assets
        val files = arrayOf("subfont.ttf", "cacert.pem")
        val configDir = context.filesDir.path
        for (filename in files) {
            var ins: InputStream? = null
            var out: OutputStream? = null
            try {
                ins = assetManager.open(filename, AssetManager.ACCESS_STREAMING)
                val outFile = File("$configDir/$filename")
                // Note that .available() officially returns an *estimated* number of bytes available
                // this is only true for generic streams, asset streams return the full file size
                if (outFile.length() == ins.available().toLong()) {
                    loggy("Skipping copy of asset file (exists same size): $filename", 302)
                    continue
                }
                out = FileOutputStream(outFile)
                ins.copyTo(out)
                loggy("Copied asset file: $filename", 303)
            } catch (e: IOException) {
                loggy("Failed to copy asset file: $filename", 304)
            } finally {
                ins?.close()
                out?.close()
            }
        }
    }

    private fun Context.resolveUri(data: Uri): String? {
        val filepath = when (data.scheme) {
            "file" -> data.path
            "content" -> openContentFd(this, data)
            "http", "https", "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh", "tcp", "udp"
            -> data.toString()

            else -> null
        }

        if (filepath == null)
            Log.e("mpv", "unknown scheme: ${data.scheme}")
        return filepath
    }


    private fun openContentFd(context: Context, uri: Uri): String? {
        val resolver = context.applicationContext.contentResolver
        Log.e("mpv", "Resolving content URI: $uri")
        val fd = try {
            val desc = resolver.openFileDescriptor(uri, "r")
            desc!!.detachFd()
        } catch (e: Exception) {
            Log.e("mpv", "Failed to open content fd: $e")
            return null
        }
        // See if we skip the indirection and read the real file directly
        val path = findRealPath(fd)
        if (path != null) {
            Log.e("mpv", "Found real file path: $path")
            ParcelFileDescriptor.adoptFd(fd).close() // we don't need that anymore
            return path
        }
        // Else, pass the fd to mpv
        return "fdclose://${fd}"
    }


    private fun findRealPath(fd: Int): String? {
        var ins: InputStream? = null
        try {
            val path = File("/proc/self/fd/${fd}").canonicalPath
            if (!path.startsWith("/proc") && File(path).canRead()) {
                // Double check that we can read it
                ins = FileInputStream(path)
                ins.read()
                return path
            }
        } catch (_: Exception) {
        } finally {
            ins?.close()
        }
        return null
    }

    fun removeObserver() {
        if (::observer.isInitialized) {
            mpvView.removeObserver(observer)
        }
    }

    fun toggleHardwareAcceleration(b: Boolean) {
        if (!ismpvInit) return
        MPVLib.setOptionString("hwdec", if (b) "auto" else "no")
    }

    fun toggleGpuNext(b: Boolean) {
        if (!ismpvInit) return
        MPVLib.setOptionString("vo", if (b) "gpu-next" else "gpu")
    }

    fun toggleInterpolation(b: Boolean) {
        if (!ismpvInit) return
        MPVLib.setOptionString("interpolation", if (b) "yes" else "no")
    }

    fun toggleDebugMode(i: Int) {
        if (!ismpvInit) return
        loggy("STATS $i", 0)
        MPVLib.command(arrayOf("script-binding", "stats/display-page-$i"))
    }
}