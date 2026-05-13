package com.example.novelvoicereader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.UUID

class PlaybackService : Service(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var chunks: List<String> = emptyList()
    private var currentIndex = 0
    private var title = "Novel Voice Reader"
    private var speechRate = 1.0f
    private var voiceName: String? = null
    private var ttsReady = false
    private var isPlaying = false

    override fun onCreate() {
        super.onCreate()
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
                chunks = intent.getStringArrayListExtra(EXTRA_CHUNKS).orEmpty()
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
                    if (currentIndex >= chunks.size) {
                        isPlaying = false
                        sendPlaybackUpdate(finished = true)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        sendPlaybackUpdate()
                        speakCurrent()
                    }
                }

                override fun onError(utteranceId: String?) {
                    isPlaying = false
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
        applySelectedVoice()
        tts.setSpeechRate(speechRate)
        sendPlaybackUpdate()
        speakCurrent()
    }

    private fun applySelectedVoice() {
        val selectedName = voiceName ?: return
        val selectedVoice = tts.voices?.firstOrNull { it.name == selectedName } ?: return
        tts.voice = selectedVoice
    }

    private fun speakCurrent() {
        if (!isPlaying || currentIndex !in chunks.indices) return
        val utteranceId = "service_chunk_${UUID.randomUUID()}"
        tts.speak(chunks[currentIndex], TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun pause() {
        tts.stop()
        isPlaying = false
        sendPlaybackUpdate()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun seekTo(index: Int) {
        currentIndex = index.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
        if (!ttsReady || chunks.isEmpty()) {
            sendPlaybackUpdate(error = chunks.isEmpty())
            if (chunks.isEmpty()) stopSelf()
            return
        }

        isPlaying = true
        tts.stop()
        startForeground(NOTIFICATION_ID, buildNotification())
        sendPlaybackUpdate()
        speakCurrent()
    }

    private fun stopPlayback() {
        tts.stop()
        isPlaying = false
        sendPlaybackUpdate(stopped = true)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun sendPlaybackUpdate(
        finished: Boolean = false,
        stopped: Boolean = false,
        error: Boolean = false
    ) {
        val intent = Intent(ACTION_PROGRESS)
            .setPackage(packageName)
            .putExtra(EXTRA_INDEX, currentIndex)
            .putExtra(EXTRA_IS_PLAYING, isPlaying)
            .putExtra(EXTRA_FINISHED, finished)
            .putExtra(EXTRA_STOPPED, stopped)
            .putExtra(EXTRA_ERROR, error)
        sendBroadcast(intent)
    }

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
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "com.example.novelvoicereader.action.PLAY"
        const val ACTION_PAUSE = "com.example.novelvoicereader.action.PAUSE"
        const val ACTION_RESUME = "com.example.novelvoicereader.action.RESUME"
        const val ACTION_SEEK = "com.example.novelvoicereader.action.SEEK"
        const val ACTION_STOP = "com.example.novelvoicereader.action.STOP"
        const val ACTION_PROGRESS = "com.example.novelvoicereader.action.PROGRESS"

        const val EXTRA_CHUNKS = "chunks"
        const val EXTRA_INDEX = "index"
        const val EXTRA_TITLE = "title"
        const val EXTRA_RATE = "rate"
        const val EXTRA_VOICE_NAME = "voice_name"
        const val EXTRA_IS_PLAYING = "is_playing"
        const val EXTRA_FINISHED = "finished"
        const val EXTRA_STOPPED = "stopped"
        const val EXTRA_ERROR = "error"

        private const val CHANNEL_ID = "reading_playback"
        private const val NOTIFICATION_ID = 1001

        fun startIntent(
            context: Context,
            title: String,
            chunks: ArrayList<String>,
            index: Int,
            rate: Float,
            voiceName: String?
        ): Intent {
            return Intent(context, PlaybackService::class.java)
                .setAction(ACTION_PLAY)
                .putExtra(EXTRA_TITLE, title)
                .putStringArrayListExtra(EXTRA_CHUNKS, chunks)
                .putExtra(EXTRA_INDEX, index)
                .putExtra(EXTRA_RATE, rate)
                .putExtra(EXTRA_VOICE_NAME, voiceName)
        }
    }
}
