package com.example.novelvoicereader

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var statusText: TextView
    private lateinit var progressText: TextView
    private lateinit var timeText: TextView
    private lateinit var chapterProgressBar: ProgressBar
    private lateinit var audioSeekBar: SeekBar

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var nextButton: Button
    private lateinit var playPauseButton: Button
    private lateinit var skipBackButton: Button
    private lateinit var skipForwardButton: Button
    private lateinit var skipForward60Button: Button
    private lateinit var tts: TextToSpeech

    private var chunks: List<String> = emptyList()
    private var currentChunkIndex = 0
    private var isPlaying = false
    private var userIsDraggingSeekBar = false

    private var nextUrl: String? = null
    private var ttsReady = false
    private var autoContinue = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        urlInput = findViewById(R.id.urlInput)

        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        nextButton = findViewById(R.id.nextButton)

        statusText = findViewById(R.id.statusText)
        progressText = findViewById(R.id.progressText)
        timeText = findViewById(R.id.timeText)
        chapterProgressBar = findViewById(R.id.chapterProgressBar)
        audioSeekBar = findViewById(R.id.audioSeekBar)

        playPauseButton = findViewById(R.id.playPauseButton)
        skipBackButton = findViewById(R.id.skipBackButton)
        skipForwardButton = findViewById(R.id.skipForwardButton)
        skipForward60Button = findViewById(R.id.skipForward60Button)

        tts = TextToSpeech(this, this)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadsImagesAutomatically = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                statusText.text = "Status: page loaded. Trying to extract text..."

                if (autoContinue) {
                    extractChapterAndRead()
                }
            }
        }

        startButton.setOnClickListener {
            autoContinue = true

            val url = urlInput.text.toString().trim()

            if (url.isNotEmpty()) {
                statusText.text = "Status: loading page..."
                webView.loadUrl(url)
            } else {
                statusText.text = "Status: paste a chapter URL first"
            }
        }

        stopButton.setOnClickListener {
            autoContinue = false
            tts.stop()
            isPlaying = false
            playPauseButton.text = "Play"
            statusText.text = "Status: stopped"
        }

        nextButton.setOnClickListener {
            goToNextChapter()
        }

        playPauseButton.setOnClickListener {
            if (isPlaying) {
                pauseSpeech()
            } else {
                resumeSpeech()
            }
        }

        skipBackButton.setOnClickListener {
            skipChunks(-2)
        }

        skipForwardButton.setOnClickListener {
            skipChunks(2)
        }

        skipForward60Button.setOnClickListener {
            skipChunks(8)
        }

        audioSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && chunks.isNotEmpty()) {
                    val targetIndex = ((progress / 100f) * (chunks.size - 1)).toInt()
                    currentChunkIndex = targetIndex.coerceIn(0, chunks.size - 1)
                    updatePlayerProgress()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userIsDraggingSeekBar = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userIsDraggingSeekBar = false

                if (chunks.isNotEmpty()) {
                    tts.stop()
                    isPlaying = true
                    speakCurrentChunk()
                }
            }
        })

        updatePlayerProgress()
        playPauseButton.text = "Play"
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US

            val selectedVoice = tts.voices
                ?.firstOrNull { it.name == "en-us-x-sfg-network" }
                ?: tts.voices?.firstOrNull { it.name == "en-us-x-sfg-local" }
                ?: tts.voices?.firstOrNull { it.name == "en-us-x-iog-network" }
                ?: tts.voices?.firstOrNull { it.name == "en-us-x-iog-local" }

            if (selectedVoice != null) {
                tts.voice = selectedVoice
                statusText.text = "Status: using voice ${selectedVoice.name}"
            } else {
                statusText.text = "Status: preferred voice not found, using default"
            }

            tts.setSpeechRate(0.98f)
            tts.setPitch(1.0f)
            ttsReady = true

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                }

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        if (!isPlaying) return@runOnUiThread

                        currentChunkIndex += 1
                        updatePlayerProgress()

                        if (currentChunkIndex >= chunks.size) {
                            isPlaying = false
                            playPauseButton.text = "Play"
                            statusText.text = "Status: chapter finished"

                            if (autoContinue) {
                                goToNextChapter()
                            }
                        } else {
                            speakCurrentChunk()
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        statusText.text = "Status: TTS error"
                        isPlaying = false
                        playPauseButton.text = "Play"
                    }
                }
            })

        } else {
            statusText.text = "Status: TTS failed to start"
        }
    }
    private fun extractChapterAndRead() {
        if (!ttsReady) {
            statusText.text = "Status: TTS not ready yet"
            return
        }

        val js = """
            (function() {
                function cleanText(text) {
                    return text
                        .replace(/\r/g, '')
                        .replace(/\t/g, ' ')
                        .replace(/[ ]{2,}/g, ' ')
                        .replace(/\n{3,}/g, '\n\n')
                        .trim();
                }

                var fullText = document.body ? document.body.innerText : '';
                fullText = cleanText(fullText);

                var titleMatch = fullText.match(/Chapter\s+\d+:\s+.+/i);
                var title = titleMatch ? titleMatch[0].trim() : document.title;

                var startIndex = titleMatch ? fullText.indexOf(titleMatch[0]) : 0;

                var endMarkers = [
                    'Share to your friends',
                    'Tip: You can use left, right keyboard keys',
                    'If you find any errors',
                    'Report',
                    'Novel Ranking',
                    'Latest Chapters'
                ];

                var endIndex = fullText.length;

                for (var i = 0; i < endMarkers.length; i++) {
                    var markerIndex = fullText.indexOf(endMarkers[i], startIndex);
                    if (markerIndex !== -1 && markerIndex < endIndex) {
                        endIndex = markerIndex;
                    }
                }

                var chapterText = fullText.substring(startIndex, endIndex);
                chapterText = cleanText(chapterText);

                return JSON.stringify({
                    title: title,
                    text: chapterText
                });
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            try {
                if (result == null || result == "null") {
                    statusText.text = "Status: JavaScript returned null"
                    tts.speak(
                        "The page did not return any chapter text.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "null_result"
                    )
                    return@evaluateJavascript
                }

                val fixedResult = if (result.startsWith("\"")) {
                    result.substring(1, result.length - 1)
                        .replace("\\\"", "\"")
                        .replace("\\n", "\n")
                        .replace("\\t", " ")
                        .replace("\\\\", "\\")
                } else {
                    result
                }

                val json = JSONObject(fixedResult)

                val text = json.optString("text")

                statusText.text = "Status: clean chapter extracted: ${text.length} characters"

                if (text.isBlank()) {
                    tts.speak(
                        "I could not find the chapter text on this page.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        "no_text"
                    )
                    return@evaluateJavascript
                }

                speakInChunks(text)

            } catch (e: Exception) {
                statusText.text = "Status: cleanup error: ${e.message}"

                tts.speak(
                    "There was an error cleaning the chapter text.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "cleanup_error"
                )
            }
        }
    }

    private fun goToNextChapter() {
        tts.stop()

        isPlaying = false
        currentChunkIndex = 0
        chunks = emptyList()
        updatePlayerProgress()

        val currentUrl = webView.url ?: urlInput.text.toString().trim()

        if (currentUrl.isBlank()) {
            statusText.text = "Status: no current URL found"
            return
        }

        val nextChapterUrl = getNextChapterUrl(currentUrl)

        if (nextChapterUrl == currentUrl) {
            statusText.text = "Status: could not calculate next chapter URL"
            return
        }

        nextUrl = nextChapterUrl
        urlInput.setText(nextChapterUrl)
        statusText.text = "Status: loading next chapter..."
        autoContinue = true
        webView.loadUrl(nextChapterUrl)
    }

    private fun getNextChapterUrl(currentUrl: String): String {
        val regex = Regex("chapter-(\\d+)")
        val match = regex.find(currentUrl) ?: return currentUrl

        val chapterNumber = match.groupValues[1].toIntOrNull() ?: return currentUrl
        val nextChapterNumber = chapterNumber + 1

        return currentUrl.replace("chapter-$chapterNumber", "chapter-$nextChapterNumber")
    }

    private fun speakInChunks(text: String) {
        tts.stop()

        chunks = text
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        currentChunkIndex = 0
        isPlaying = true

        playPauseButton.text = "Pause"
        statusText.text = "Status: reading chapter..."

        updatePlayerProgress()
        speakCurrentChunk()
    }

    private fun speakCurrentChunk() {
        if (chunks.isEmpty()) {
            statusText.text = "Status: no chapter loaded"
            isPlaying = false
            playPauseButton.text = "Play"
            return
        }

        if (currentChunkIndex >= chunks.size) {
            isPlaying = false
            playPauseButton.text = "Play"
            statusText.text = "Status: chapter finished"

            if (autoContinue) {
                goToNextChapter()
            }

            return
        }

        isPlaying = true
        playPauseButton.text = "Pause"
        updatePlayerProgress()

        val utteranceId = if (currentChunkIndex == chunks.lastIndex) {
            "chapter_end_${UUID.randomUUID()}"
        } else {
            "chunk_${UUID.randomUUID()}"
        }

        tts.speak(
            chunks[currentChunkIndex],
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )
    }

    private fun pauseSpeech() {
        if (!isPlaying) return

        tts.stop()
        isPlaying = false
        playPauseButton.text = "Play"
        statusText.text = "Status: paused"
    }

    private fun resumeSpeech() {
        if (chunks.isEmpty()) {
            statusText.text = "Status: no chapter loaded"
            return
        }

        isPlaying = true
        playPauseButton.text = "Pause"
        statusText.text = "Status: resumed"
        speakCurrentChunk()
    }

    private fun skipChunks(amount: Int) {
        if (chunks.isEmpty()) {
            statusText.text = "Status: no chapter loaded"
            return
        }

        tts.stop()

        currentChunkIndex = (currentChunkIndex + amount).coerceIn(0, chunks.size - 1)

        statusText.text = if (amount > 0) {
            "Status: skipped forward"
        } else {
            "Status: skipped back"
        }

        updatePlayerProgress()

        if (isPlaying) {
            speakCurrentChunk()
        }
    }

    private fun updatePlayerProgress() {
        val percent = if (chunks.isNotEmpty()) {
            ((currentChunkIndex.toFloat() / chunks.size.toFloat()) * 100).toInt()
        } else {
            0
        }.coerceIn(0, 100)

        chapterProgressBar.progress = percent

        if (!userIsDraggingSeekBar) {
            audioSeekBar.progress = percent
        }

        progressText.text = "Chapter Progress: $percent%"

        timeText.text = if (chunks.isNotEmpty()) {
            "$percent% played — sentence ${currentChunkIndex + 1} of ${chunks.size}"
        } else {
            "0% played"
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}