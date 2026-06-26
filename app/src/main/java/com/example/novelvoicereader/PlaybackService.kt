package com.example.novelvoicereader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import java.util.Locale
import java.util.UUID

class PlaybackService : Service(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var chunks: List<String> = emptyList()
    private var currentIndex = 0
    private var title = "Novel Voice Reader"
    private var speechRate = 1.0f
    private var voiceName: String? = null
    private var currentUrl = ""
    private var currentText = ""
    private var previousChapterUrl: String? = null
    private var nextChapterUrl: String? = null
    private var autoContinue = true
    private var ttsReady = false
    private var isPlaying = false
    private lateinit var prefs: SharedPreferences
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("NovelVoiceReaderPrefs", MODE_PRIVATE)
        createNotificationChannel()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { title }
                speechRate = intent.getFloatExtra(EXTRA_RATE, 1.0f)
                voiceName = intent.getStringExtra(EXTRA_VOICE_NAME)
                currentIndex = intent.getIntExtra(EXTRA_INDEX, currentIndex)
                currentUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
                currentText = prefs.getString(KEY_CURRENT_TEXT, "").orEmpty()
                chunks = splitIntoChunks(currentText)
                previousChapterUrl = intent.getStringExtra(EXTRA_PREVIOUS_URL)?.takeIf { it.isNotBlank() }
                nextChapterUrl = intent.getStringExtra(EXTRA_NEXT_URL)?.takeIf { it.isNotBlank() }
                autoContinue = intent.getBooleanExtra(EXTRA_AUTO_CONTINUE, true)
                startForeground(NOTIFICATION_ID, buildNotification())
                playWhenReady()
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> {
                if (chunks.isEmpty()) {
                    sendPlaybackUpdate(error = true)
                    stopSelf()
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification())
                    playWhenReady()
                }
            }
            ACTION_SEEK -> seekTo(intent.getIntExtra(EXTRA_INDEX, currentIndex))
            ACTION_SET_VOICE -> updateVoice(intent.getStringExtra(EXTRA_VOICE_NAME))
            ACTION_STOP -> stopPlayback()
        }

        return START_NOT_STICKY
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts.language = Locale.US
            applySelectedVoice()
            tts.setSpeechRate(speechRate)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    currentIndex += 1
                    playbackIndex = currentIndex
                    if (currentIndex >= chunks.size) {
                        if (!continueWithNextDownloadedChapter()) {
                            isPlaying = false
                            isPlaybackActive = false
                            releaseWakeLock()
                            sendPlaybackUpdate(finished = true)
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    } else {
                        sendPlaybackUpdate()
                        speakCurrent()
                    }
                }

                override fun onError(utteranceId: String?) {
                    isPlaying = false
                    isPlaybackActive = false
                    releaseWakeLock()
                    sendPlaybackUpdate(error = true)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            })
            playWhenReady()
        } else {
            sendPlaybackUpdate(error = true)
            stopSelf()
        }
    }

    private fun playWhenReady() {
        if (!ttsReady || chunks.isEmpty()) return
        currentIndex = currentIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
        isPlaying = true
        isPlaybackActive = true
        playbackIndex = currentIndex
        acquireWakeLock()
        applySelectedVoice()
        tts.setSpeechRate(speechRate)
        sendPlaybackUpdate()
        speakCurrent()
    }

    private fun applySelectedVoice() {
        val selectedName = voiceName
        val selectedVoice = if (selectedName == null) {
            defaultEnglishVoice()
        } else {
            tts.voices?.firstOrNull { it.name == selectedName }
        } ?: return
        tts.voice = selectedVoice
    }

    private fun defaultEnglishVoice(): android.speech.tts.Voice? {
        return tts.defaultVoice
            ?.takeIf { it.locale.language.equals(Locale.ENGLISH.language, ignoreCase = true) }
    }

    private fun updateVoice(selectedName: String?) {
        voiceName = selectedName
        if (!ttsReady) return

        applySelectedVoice()
        if (isPlaying && chunks.isNotEmpty()) {
            tts.stop()
            speakCurrent()
        } else {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun speakCurrent() {
        if (!isPlaying || currentIndex !in chunks.indices) return
        acquireWakeLock()
        val utteranceId = "service_chunk_${UUID.randomUUID()}"
        tts.speak(chunks[currentIndex], TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun pause() {
        tts.stop()
        isPlaying = false
        isPlaybackActive = false
        playbackIndex = currentIndex
        releaseWakeLock()
        sendPlaybackUpdate()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun seekTo(index: Int) {
        currentIndex = index.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
        playbackIndex = currentIndex
        if (!ttsReady || chunks.isEmpty()) {
            sendPlaybackUpdate(error = chunks.isEmpty())
            if (chunks.isEmpty()) stopSelf()
            return
        }

        isPlaying = true
        isPlaybackActive = true
        acquireWakeLock()
        tts.stop()
        startForeground(NOTIFICATION_ID, buildNotification())
        sendPlaybackUpdate()
        speakCurrent()
    }

    private fun stopPlayback() {
        tts.stop()
        isPlaying = false
        isPlaybackActive = false
        playbackIndex = currentIndex
        releaseWakeLock()
        sendPlaybackUpdate(stopped = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun sendPlaybackUpdate(
        finished: Boolean = false,
        stopped: Boolean = false,
        error: Boolean = false,
        chapterChanged: Boolean = false
    ) {
        val intent = Intent(ACTION_PROGRESS)
            .setPackage(packageName)
            .putExtra(EXTRA_INDEX, currentIndex)
            .putExtra(EXTRA_IS_PLAYING, isPlaying)
            .putExtra(EXTRA_FINISHED, finished)
            .putExtra(EXTRA_STOPPED, stopped)
            .putExtra(EXTRA_ERROR, error)
            .putExtra(EXTRA_CHAPTER_CHANGED, chapterChanged)
            .putExtra(EXTRA_URL, currentUrl)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_PREVIOUS_URL, previousChapterUrl.orEmpty())
            .putExtra(EXTRA_NEXT_URL, nextChapterUrl.orEmpty())
        sendBroadcast(intent)
    }

    private fun continueWithNextDownloadedChapter(): Boolean {
        if (!autoContinue) return false

        val targetUrl = (nextChapterUrl ?: getChapterUrl(currentUrl, 1)).orEmpty()
        val cachedChapter = findLibraryChapter(targetUrl) ?: return false
        if (cachedChapter.text.isBlank()) return false

        currentUrl = cachedChapter.url
        title = cachedChapter.title.ifBlank { cachedChapter.url }
        currentText = cachedChapter.text
        previousChapterUrl = cachedChapter.previousUrl
        nextChapterUrl = cachedChapter.nextUrl ?: getChapterUrl(cachedChapter.url, 1)
        currentIndex = 0
        playbackIndex = 0
        chunks = splitIntoChunks(cachedChapter.text)
        if (chunks.isEmpty()) return false

        saveCurrentChapter(cachedChapter.copy(chunkIndex = 0))
        sendPlaybackUpdate(chapterChanged = true)
        speakCurrent()
        return true
    }

    private fun splitIntoChunks(text: String): List<String> {
        val sentenceRegex = Regex("""(?s)\S.+?(?:[.!?]+["')\]]*|\n\n|$)""")
        return sentenceRegex.findAll(text)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun findLibraryChapter(url: String): CachedChapter? {
        val target = url.normalizedUrl()
        if (target.isBlank()) return null

        return libraryChapters().firstOrNull { it.url.normalizedUrl() == target }
    }

    private fun libraryChapters(): List<CachedChapter> {
        val rawLibrary = prefs.getString(KEY_LIBRARY, "[]").orEmpty()
        val array = runCatching { JSONArray(rawLibrary) }.getOrElse { JSONArray() }
        return (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val url = json.optString("url")
            if (url.isBlank()) return@mapNotNull null

            CachedChapter(
                url = url,
                title = json.optString("title", url).ifBlank { url },
                text = json.optString("text"),
                chunkIndex = json.optInt("chunk", 0),
                updatedAt = json.optLong("updatedAt", 0L),
                previousUrl = json.optString("previousUrl").takeIf { it.isNotBlank() },
                nextUrl = json.optString("nextUrl").takeIf { it.isNotBlank() }
            )
        }
    }

    private fun saveCurrentChapter(chapter: CachedChapter) {
        prefs.edit()
            .putString(KEY_CURRENT_URL, chapter.url)
            .putString(KEY_CURRENT_TITLE, chapter.title)
            .putString(KEY_CURRENT_TEXT, chapter.text)
            .putInt(KEY_CURRENT_CHUNK, chapter.chunkIndex)
            .putLong(KEY_CURRENT_UPDATED_AT, System.currentTimeMillis())
            .putString(KEY_CURRENT_PREVIOUS_URL, chapter.previousUrl.orEmpty())
            .putString(KEY_CURRENT_NEXT_URL, chapter.nextUrl.orEmpty())
            .putString(KEY_LAST_URL, chapter.url)
            .apply()
    }

    private fun getChapterUrl(sourceUrl: String, offset: Int): String? {
        val patterns = listOf(
            Regex("chapter-(\\d+)"),
            Regex("chapter/(\\d+)"),
            Regex("chapter_(\\d+)"),
            Regex("ch-(\\d+)"),
            Regex("(?<=/)(\\d+)(?=/?$)")
        )

        for (regex in patterns) {
            val match = regex.find(sourceUrl) ?: continue
            val chapterNumber = match.groupValues[1].toIntOrNull() ?: continue
            val targetNumber = (chapterNumber + offset).coerceAtLeast(1)
            return sourceUrl.replaceRange(match.groups[1]!!.range, targetNumber.toString())
        }

        return null
    }

    private fun acquireWakeLock() {
        val existing = wakeLock
        if (existing?.isHeld == true) return

        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:PlaybackWakeLock")
            .apply { acquire(30 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun String.normalizedUrl(): String = trim().trimEnd('/')

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_book)
            .setContentTitle(title)
            .setContentText(notificationText())
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent())
            .addAction(
                if (isPlaying) R.drawable.ic_timer else R.drawable.ic_book,
                if (isPlaying) "Pause" else "Resume",
                serviceIntent(if (isPlaying) ACTION_PAUSE else ACTION_RESUME)
            )
            .addAction(R.drawable.ic_arrow_forward, "Stop", serviceIntent(ACTION_STOP))
            .build()

    private fun notificationText(): String {
        return if (chunks.isEmpty()) {
            "Preparing playback"
        } else {
            "Sentence ${(currentIndex + 1).coerceAtMost(chunks.size)} of ${chunks.size}"
        }
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun serviceIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(action)
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reading playback",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isPlaybackActive = false
        tts.stop()
        tts.shutdown()
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var isPlaybackActive: Boolean = false
            private set

        @Volatile
        var playbackIndex: Int = 0
            private set

        const val ACTION_PLAY = "com.example.novelvoicereader.action.PLAY"
        const val ACTION_PAUSE = "com.example.novelvoicereader.action.PAUSE"
        const val ACTION_RESUME = "com.example.novelvoicereader.action.RESUME"
        const val ACTION_SEEK = "com.example.novelvoicereader.action.SEEK"
        const val ACTION_SET_VOICE = "com.example.novelvoicereader.action.SET_VOICE"
        const val ACTION_STOP = "com.example.novelvoicereader.action.STOP"
        const val ACTION_PROGRESS = "com.example.novelvoicereader.action.PROGRESS"

        const val EXTRA_CHUNKS = "chunks"
        const val EXTRA_INDEX = "index"
        const val EXTRA_TITLE = "title"
        const val EXTRA_URL = "url"
        const val EXTRA_TEXT = "text"
        const val EXTRA_PREVIOUS_URL = "previous_url"
        const val EXTRA_NEXT_URL = "next_url"
        const val EXTRA_AUTO_CONTINUE = "auto_continue"
        const val EXTRA_CHAPTER_CHANGED = "chapter_changed"
        const val EXTRA_RATE = "rate"
        const val EXTRA_VOICE_NAME = "voice_name"
        const val EXTRA_IS_PLAYING = "is_playing"
        const val EXTRA_FINISHED = "finished"
        const val EXTRA_STOPPED = "stopped"
        const val EXTRA_ERROR = "error"

        private const val CHANNEL_ID = "reading_playback"
        private const val NOTIFICATION_ID = 1001
        private const val KEY_LAST_URL = "last_url"
        private const val KEY_CURRENT_URL = "current_url"
        private const val KEY_CURRENT_TITLE = "current_title"
        private const val KEY_CURRENT_TEXT = "current_text"
        private const val KEY_CURRENT_CHUNK = "current_chunk"
        private const val KEY_CURRENT_UPDATED_AT = "current_updated_at"
        private const val KEY_CURRENT_PREVIOUS_URL = "current_previous_url"
        private const val KEY_CURRENT_NEXT_URL = "current_next_url"
        private const val KEY_LIBRARY = "library"

        fun startIntent(
            context: Context,
            title: String,
            index: Int,
            rate: Float,
            voiceName: String?,
            url: String,
            previousUrl: String?,
            nextUrl: String?,
            autoContinue: Boolean
        ): Intent {
            return Intent(context, PlaybackService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_INDEX, index)
                .putExtra(EXTRA_RATE, rate)
                .putExtra(EXTRA_VOICE_NAME, voiceName)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_PREVIOUS_URL, previousUrl.orEmpty())
                .putExtra(EXTRA_NEXT_URL, nextUrl.orEmpty())
                .putExtra(EXTRA_AUTO_CONTINUE, autoContinue)
        }
    }

    private data class CachedChapter(
        val url: String,
        val title: String,
        val text: String,
        val chunkIndex: Int,
        val updatedAt: Long,
        val previousUrl: String?,
        val nextUrl: String?
    )
}
