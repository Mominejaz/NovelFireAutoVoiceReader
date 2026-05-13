package com.example.novelvoicereader

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.novelvoicereader.data.ChapterPrefsRepository
import com.example.novelvoicereader.data.WebContentBlocker
import com.example.novelvoicereader.domain.model.Chapter
import com.example.novelvoicereader.domain.model.SleepTimer
import com.example.novelvoicereader.domain.repository.ChapterRepository
import com.example.novelvoicereader.domain.usecase.GetCurrentChapterUseCase
import com.example.novelvoicereader.domain.usecase.GetLibraryUseCase
import com.example.novelvoicereader.domain.usecase.SaveChapterToLibraryUseCase
import com.example.novelvoicereader.domain.usecase.SaveCurrentChapterUseCase
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var statusText: TextView
    private lateinit var chapterTitleText: TextView
    private lateinit var progressText: TextView
    private lateinit var timeText: TextView
    private lateinit var currentSentenceText: TextView
    private lateinit var chapterProgressBar: ProgressBar
    private lateinit var audioSeekBar: SeekBar

    private lateinit var webView: WebView
    private lateinit var readerScrollView: ScrollView
    private lateinit var readerTextView: TextView
    private lateinit var urlInput: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var nextButton: Button
    private lateinit var saveButton: Button
    private lateinit var libraryButton: Button
    private lateinit var voiceButton: Button
    private lateinit var timerButton: Button
    private lateinit var downloadButton: Button
    private lateinit var readerModeButton: Button
    private lateinit var playPauseButton: Button
    private lateinit var miniPlayPauseButton: Button
    private lateinit var collapsePlayerButton: Button
    private lateinit var expandPlayerButton: Button
    private lateinit var skipBackButton: Button
    private lateinit var skipForwardButton: Button
    private lateinit var skipForward60Button: Button
    private lateinit var miniPlayerControls: View
    private lateinit var playbackControls: View
    private lateinit var playerPanel: View
    private lateinit var tts: TextToSpeech
    private lateinit var prefs: SharedPreferences
    private lateinit var chapterRepository: ChapterRepository
    private lateinit var getCurrentChapter: GetCurrentChapterUseCase
    private lateinit var getLibrary: GetLibraryUseCase
    private lateinit var saveCurrentChapterUseCase: SaveCurrentChapterUseCase
    private lateinit var saveChapterToLibrary: SaveChapterToLibraryUseCase

    private var chunks: List<String> = emptyList()
    private var chunkRanges: List<IntRange> = emptyList()
    private var currentChunkIndex = 0
    private var currentTitle = "No chapter loaded"
    private var currentText = ""
    private var currentUrl = ""
    private var isPlaying = false
    private var userIsDraggingSeekBar = false
    private var ttsReady = false
    private var autoContinue = true
    private var playerCollapsed = false
    private var readerModeEnabled = false
    private var sleepTimer: SleepTimer = SleepTimer.Off
    private var remainingTimerChapters = 0
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private val sleepTimerRunnable = Runnable { stopForSleepTimer() }

    private val preferredVoiceNames = listOf(
        "river",
        "en-us-river",
        "en-us-x-river-network",
        "en-us-x-river-local",
        "en-us-x-sfg-network",
        "en-us-x-sfg-local",
        "en-us-x-iog-network",
        "en-us-x-iog-local"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences("NovelVoiceReaderPrefs", MODE_PRIVATE)
        chapterRepository = ChapterPrefsRepository(prefs)
        getCurrentChapter = GetCurrentChapterUseCase(chapterRepository)
        getLibrary = GetLibraryUseCase(chapterRepository)
        saveCurrentChapterUseCase = SaveCurrentChapterUseCase(chapterRepository)
        saveChapterToLibrary = SaveChapterToLibraryUseCase(chapterRepository)

        setContentView(R.layout.activity_main)
        bindViews()
        configureWebView()
        configureControls()

        tts = TextToSpeech(this, this)

        restoreCurrentChapter()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun bindViews() {
        webView = findViewById(R.id.webView)
        readerScrollView = findViewById(R.id.readerScrollView)
        readerTextView = findViewById(R.id.readerTextView)
        urlInput = findViewById(R.id.urlInput)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        nextButton = findViewById(R.id.nextButton)
        saveButton = findViewById(R.id.saveButton)
        libraryButton = findViewById(R.id.libraryButton)
        voiceButton = findViewById(R.id.voiceButton)
        timerButton = findViewById(R.id.timerButton)
        downloadButton = findViewById(R.id.downloadButton)
        readerModeButton = findViewById(R.id.readerModeButton)
        collapsePlayerButton = findViewById(R.id.collapsePlayerButton)
        expandPlayerButton = findViewById(R.id.expandPlayerButton)
        miniPlayerControls = findViewById(R.id.miniPlayerControls)
        playbackControls = findViewById(R.id.playbackControls)
        playerPanel = findViewById(R.id.playerPanel)
        statusText = findViewById(R.id.statusText)
        chapterTitleText = findViewById(R.id.chapterTitleText)
        progressText = findViewById(R.id.progressText)
        timeText = findViewById(R.id.timeText)
        currentSentenceText = findViewById(R.id.currentSentenceText)
        chapterProgressBar = findViewById(R.id.chapterProgressBar)
        audioSeekBar = findViewById(R.id.audioSeekBar)
        playPauseButton = findViewById(R.id.playPauseButton)
        miniPlayPauseButton = findViewById(R.id.miniPlayPauseButton)
        skipBackButton = findViewById(R.id.skipBackButton)
        skipForwardButton = findViewById(R.id.skipForwardButton)
        skipForward60Button = findViewById(R.id.skipForward60Button)
    }

    private fun configureWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadsImagesAutomatically = false
        webView.settings.javaScriptCanOpenWindowsAutomatically = false
        webView.settings.setSupportMultipleWindows(false)
        webView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN && !playerCollapsed) {
                setPlayerCollapsed(true)
            }
            false
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                statusText.text = "Status: blocked popup window"
                return false
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val targetUrl = request?.url?.toString().orEmpty()
                if (targetUrl.isBlank()) return false

                if (WebContentBlocker.shouldBlock(targetUrl)) {
                    statusText.text = "Status: blocked redirect/ad"
                    return true
                }

                return false
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val targetUrl = request?.url?.toString().orEmpty()
                return if (WebContentBlocker.shouldBlock(targetUrl)) {
                    WebResourceResponse("text/plain", "utf-8", null)
                } else {
                    super.shouldInterceptRequest(view, request)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                currentUrl = url.orEmpty()
                if (currentUrl.isNotBlank()) {
                    prefs.edit().putString(KEY_LAST_URL, currentUrl).apply()
                }
                statusText.text = "Status: page loaded. Extracting chapter..."

                if (autoContinue) {
                    extractChapterAndRead()
                }
            }
        }
    }

    private fun configureControls() {
        startButton.setOnClickListener {
            autoContinue = true
            val url = urlInput.text.toString().trim()

            if (url.isNotEmpty()) {
                statusText.text = "Status: loading page..."
                currentUrl = url
                showReaderMode(false)
                webView.loadUrl(url)
            } else {
                statusText.text = "Status: paste a chapter URL first"
            }
        }

        stopButton.setOnClickListener {
            autoContinue = false
            tts.stop()
            isPlaying = false
            setPlaybackButtonText("Play")
            saveCurrentChapter()
            statusText.text = "Status: stopped"
        }

        nextButton.setOnClickListener {
            goToNextChapter()
        }

        saveButton.setOnClickListener {
            saveCurrentChapter()
            saveCurrentChapterToLibrary()
        }

        libraryButton.setOnClickListener {
            showLibrary()
        }

        voiceButton.setOnClickListener {
            showVoicePicker()
        }

        collapsePlayerButton.setOnClickListener {
            setPlayerCollapsed(!playerCollapsed)
        }

        expandPlayerButton.setOnClickListener {
            setPlayerCollapsed(false)
        }

        timerButton.setOnClickListener {
            showSleepTimerPicker()
        }

        downloadButton.setOnClickListener {
            saveCurrentChapter()
            saveCurrentChapterToLibrary()
            statusText.text = if (currentText.isBlank()) {
                "Status: saved URL. Load chapter to download text."
            } else {
                "Status: chapter downloaded for offline listening"
            }
        }

        readerModeButton.setOnClickListener {
            if (currentText.isBlank()) {
                showReaderMode(false)
                statusText.text = "Status: reader mode needs extracted chapter text"
            } else {
                showReaderMode(!readerModeEnabled)
            }
        }

        playPauseButton.setOnClickListener {
            if (isPlaying) {
                pauseSpeech()
            } else {
                resumeSpeech()
            }
        }

        miniPlayPauseButton.setOnClickListener {
            if (isPlaying) {
                pauseSpeech()
            } else {
                resumeSpeech()
            }
        }

        skipBackButton.setOnClickListener { skipChunks(-2) }
        skipForwardButton.setOnClickListener { skipChunks(2) }
        skipForward60Button.setOnClickListener { skipChunks(8) }

        audioSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && chunks.isNotEmpty()) {
                    val targetIndex = ((progress / 100f) * (chunks.size - 1)).toInt()
                    currentChunkIndex = targetIndex.coerceIn(0, chunks.size - 1)
                    updatePlayerProgress()
                    saveCurrentChapter()
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

        setPlaybackButtonText("Play")
        setPlayerCollapsed(prefs.getBoolean(KEY_PLAYER_COLLAPSED, false))
        updatePlayerProgress()
    }

    private fun setPlayerCollapsed(collapsed: Boolean) {
        playerCollapsed = collapsed
        prefs.edit().putBoolean(KEY_PLAYER_COLLAPSED, collapsed).apply()

        val expandedVisibility = if (collapsed) View.GONE else View.VISIBLE
        val miniVisibility = if (collapsed) View.VISIBLE else View.GONE

        currentSentenceText.visibility = expandedVisibility
        chapterProgressBar.visibility = expandedVisibility
        audioSeekBar.visibility = expandedVisibility
        playbackControls.visibility = expandedVisibility
        nextButton.visibility = expandedVisibility
        stopButton.visibility = expandedVisibility
        miniPlayerControls.visibility = miniVisibility
        collapsePlayerButton.text = if (collapsed) "Show" else "Hide"

        val params = playerPanel.layoutParams as ConstraintLayout.LayoutParams
        params.marginStart = 0
        playerPanel.layoutParams = params
        updatePlayerProgress()
    }

    private fun setPlaybackButtonText(text: String) {
        playPauseButton.text = text
        miniPlayPauseButton.text = text
    }

    private fun showReaderMode(enabled: Boolean) {
        readerModeEnabled = enabled && currentText.isNotBlank()
        readerScrollView.visibility = if (readerModeEnabled) View.VISIBLE else View.GONE
        webView.visibility = if (readerModeEnabled) View.GONE else View.VISIBLE
        readerModeButton.text = if (readerModeEnabled) "Browser" else "Reader"

        if (readerModeEnabled) {
            updateReaderText()
            setPlayerCollapsed(true)
        }
    }

    private fun updateReaderText() {
        if (currentText.isBlank()) {
            readerTextView.text = "Reader mode will appear after a chapter is extracted."
            return
        }

        val spannable = SpannableString(currentText)
        if (chunkRanges.isNotEmpty()) {
            val range = chunkRanges[currentChunkIndex.coerceIn(0, chunkRanges.lastIndex)]
            if (range.first >= 0 && range.last < currentText.length) {
                val start = range.first
                val end = (range.last + 1).coerceAtMost(currentText.length)
                spannable.setSpan(
                    BackgroundColorSpan(Color.parseColor("#2A303B")),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    ForegroundColorSpan(Color.parseColor("#AECBFA")),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        readerTextView.text = spannable

        if (chunks.isNotEmpty()) {
            val percent = currentChunkIndex.toFloat() / chunks.size.toFloat()
            readerScrollView.post {
                val maxScroll = (readerTextView.height - readerScrollView.height).coerceAtLeast(0)
                readerScrollView.smoothScrollTo(0, (maxScroll * percent).toInt())
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            applySavedOrPreferredVoice()
            tts.setSpeechRate(prefs.getFloat(KEY_SPEECH_RATE, 0.98f))
            tts.setPitch(1.0f)
            ttsReady = true

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        if (!isPlaying) return@runOnUiThread

                        currentChunkIndex += 1
                        updatePlayerProgress()
                        saveCurrentChapter()

                        if (currentChunkIndex >= chunks.size) {
                            isPlaying = false
                            setPlaybackButtonText("Play")
                            statusText.text = "Status: chapter finished"
                            saveCurrentChapterToLibrary(silent = true)

                            if (onChapterFinishedForSleepTimer()) {
                                return@runOnUiThread
                            }

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
                        setPlaybackButtonText("Play")
                    }
                }
            })
        } else {
            statusText.text = "Status: TTS failed to start"
        }
    }

    private fun applySavedOrPreferredVoice() {
        val availableVoices = englishVoices()
        val savedVoiceName = prefs.getString(KEY_SELECTED_VOICE, null)
        val selectedVoice = availableVoices.firstOrNull { it.name == savedVoiceName }
            ?: availableVoices.firstOrNull { voice -> voice.name.lowercase(Locale.US).contains("river") }
            ?: preferredVoiceNames.firstNotNullOfOrNull { preferred ->
                availableVoices.firstOrNull { it.name.lowercase(Locale.US).contains(preferred) }
            }
            ?: availableVoices.firstOrNull()

        if (selectedVoice != null) {
            tts.voice = selectedVoice
            prefs.edit().putString(KEY_SELECTED_VOICE, selectedVoice.name).apply()
            updateVoiceButton(selectedVoice)
            statusText.text = if (selectedVoice.name.contains("river", ignoreCase = true)) {
                "Status: using River listening voice"
            } else {
                "Status: River voice not installed. Using ${selectedVoice.name}"
            }
        } else {
            voiceButton.text = "Voice"
            statusText.text = "Status: no English TTS voice found"
        }
    }

    private fun showVoicePicker() {
        if (!ttsReady) {
            statusText.text = "Status: TTS not ready yet"
            return
        }

        val voices = englishVoices()
        if (voices.isEmpty()) {
            statusText.text = "Status: no voices installed"
            openTtsSettingsPrompt()
            return
        }

        val labels = voices.map { voiceLabel(it) }.toTypedArray()
        val hasRiver = voices.any { it.name.contains("river", ignoreCase = true) }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Listening voice")
            .setItems(labels) { _, which ->
                val selectedVoice = voices[which]
                tts.voice = selectedVoice
                prefs.edit().putString(KEY_SELECTED_VOICE, selectedVoice.name).apply()
                updateVoiceButton(selectedVoice)
                statusText.text = "Status: voice set to ${voiceLabel(selectedVoice)}"
                tts.speak("This is the selected listening voice.", TextToSpeech.QUEUE_FLUSH, null, "voice_test")
            }
            .setNegativeButton("Cancel", null)

        if (!hasRiver) {
            dialog.setMessage("River is not installed on this device. Install or enable it in Android Text-to-speech settings, then reopen this picker.")
            dialog.setPositiveButton("TTS settings") { _, _ ->
                openTtsSettings()
            }
        }

        dialog.show()
    }

    private fun openTtsSettingsPrompt() {
        AlertDialog.Builder(this)
            .setTitle("Install a voice")
            .setMessage("No English TTS voices are available. Open Android Text-to-speech settings to install River or another compatible voice.")
            .setPositiveButton("TTS settings") { _, _ ->
                openTtsSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openTtsSettings() {
        val ttsSettingsIntent = Intent("com.android.settings.TTS_SETTINGS")
        if (ttsSettingsIntent.resolveActivity(packageManager) != null) {
            startActivity(ttsSettingsIntent)
        } else {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        }
    }

    private fun showSleepTimerPicker() {
        val timers = listOf(
            SleepTimer.Off,
            SleepTimer.Minutes(15),
            SleepTimer.Minutes(30),
            SleepTimer.Minutes(60),
            SleepTimer.Chapters(1),
            SleepTimer.Chapters(2),
            SleepTimer.Chapters(3)
        )
        val labels = timers.map { it.label() }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Auto shutoff")
            .setItems(labels) { _, which ->
                setSleepTimer(timers[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setSleepTimer(timer: SleepTimer) {
        sleepTimer = timer
        timerButton.text = timer.label()
        sleepTimerHandler.removeCallbacks(sleepTimerRunnable)

        when (timer) {
            SleepTimer.Off -> {
                remainingTimerChapters = 0
                statusText.text = "Status: sleep timer off"
            }

            is SleepTimer.Minutes -> {
                remainingTimerChapters = 0
                sleepTimerHandler.postDelayed(sleepTimerRunnable, timer.value * 60_000L)
                statusText.text = "Status: sleep timer set for ${timer.value} minutes"
            }

            is SleepTimer.Chapters -> {
                remainingTimerChapters = timer.value
                statusText.text = "Status: will stop after ${timer.value} chapter(s)"
            }
        }
    }

    private fun stopForSleepTimer() {
        autoContinue = false
        tts.stop()
        isPlaying = false
        setPlaybackButtonText("Play")
        sleepTimer = SleepTimer.Off
        timerButton.text = sleepTimer.label()
        saveCurrentChapter()
        statusText.text = "Status: sleep timer stopped playback"
    }

    private fun onChapterFinishedForSleepTimer(): Boolean {
        val timer = sleepTimer
        if (timer !is SleepTimer.Chapters) return false

        remainingTimerChapters -= 1
        if (remainingTimerChapters <= 0) {
            stopForSleepTimer()
            return true
        }

        timerButton.text = SleepTimer.Chapters(remainingTimerChapters).label()
        return false
    }

    private fun englishVoices(): List<Voice> {
        return tts.voices
            ?.filter { it.locale.language.equals(Locale.ENGLISH.language, ignoreCase = true) }
            ?.sortedWith(compareByDescending<Voice> { it.name.contains("river", ignoreCase = true) }
                .thenByDescending { it.name.contains("network", ignoreCase = true) }
                .thenBy { it.name })
            .orEmpty()
    }

    private fun voiceLabel(voice: Voice): String {
        val prefix = if (voice.name.contains("river", ignoreCase = true)) "River - " else ""
        val type = if (voice.name.contains("network", ignoreCase = true)) "network" else "local"
        return "$prefix${voice.locale.displayName} ($type) - ${voice.name}"
    }

    private fun updateVoiceButton(voice: Voice) {
        voiceButton.text = if (voice.name.contains("river", ignoreCase = true)) {
            "River voice"
        } else {
            "Voice"
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        val url = Regex("https?://\\S+").find(sharedText)?.value?.trimEnd(',', '.', ')')

        if (url != null) {
            urlInput.setText(url)
            currentUrl = url
            statusText.text = "Status: shared URL ready"
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
                    return (text || '')
                        .replace(/\r/g, '')
                        .replace(/\t/g, ' ')
                        .replace(/[ ]{2,}/g, ' ')
                        .replace(/\n{3,}/g, '\n\n')
                        .trim();
                }

                var cloned = document.body ? document.body.cloneNode(true) : null;
                if (!cloned) return JSON.stringify({ title: document.title || 'Untitled chapter', text: '' });

                cloned.querySelectorAll('script, style, noscript, nav, header, footer, iframe, form, button, aside').forEach(function(node) {
                    node.remove();
                });

                var candidates = Array.prototype.slice.call(cloned.querySelectorAll('article, main, .chapter, .chapter-content, .entry-content, .content, #chapter, #content'));
                if (candidates.length === 0) candidates = [cloned];

                var best = candidates
                    .map(function(node) { return { node: node, text: cleanText(node.innerText || '') }; })
                    .sort(function(a, b) { return b.text.length - a.text.length; })[0];

                var fullText = cleanText(best ? best.text : cloned.innerText);
                var titleNode = document.querySelector('h1, .chapter-title, .entry-title');
                var title = cleanText(titleNode ? titleNode.innerText : document.title);
                var titleMatch = fullText.match(/Chapter\s+\d+[:\-\s].+/i);
                if (!title && titleMatch) title = cleanText(titleMatch[0]);
                if (!title) title = 'Untitled chapter';

                var startIndex = titleMatch ? fullText.indexOf(titleMatch[0]) : 0;
                var endMarkers = [
                    'Share to your friends',
                    'Tip: You can use left, right keyboard keys',
                    'If you find any errors',
                    'Report',
                    'Novel Ranking',
                    'Latest Chapters',
                    'Previous Chapter',
                    'Next Chapter',
                    'Comments'
                ];

                var endIndex = fullText.length;
                for (var i = 0; i < endMarkers.length; i++) {
                    var markerIndex = fullText.indexOf(endMarkers[i], Math.max(startIndex, 0));
                    if (markerIndex !== -1 && markerIndex < endIndex) endIndex = markerIndex;
                }

                var chapterText = cleanText(fullText.substring(Math.max(startIndex, 0), endIndex));
                return JSON.stringify({ title: title, text: chapterText });
            })();
        """.trimIndent()

        webView.evaluateJavascript(js) { result ->
            try {
                if (result == null || result == "null") {
                    statusText.text = "Status: JavaScript returned null"
                    return@evaluateJavascript
                }

                val json = JSONObject(unpackJavascriptString(result))
                val title = json.optString("title", "Untitled chapter").ifBlank { "Untitled chapter" }
                val text = normalizeChapterText(json.optString("text"), title)

                if (text.isBlank()) {
                    statusText.text = "Status: no chapter text found"
                    tts.speak("I could not find the chapter text on this page.", TextToSpeech.QUEUE_FLUSH, null, "no_text")
                    return@evaluateJavascript
                }

                currentTitle = title
                currentText = text
                currentUrl = webView.url ?: urlInput.text.toString().trim()
                statusText.text = "Status: extracted ${text.length} characters"
                prepareChunks(text, resetPosition = true)
                saveCurrentChapter()
                saveCurrentChapterToLibrary(silent = true)
                showReaderMode(true)
                speakCurrentChunk()
            } catch (e: Exception) {
                statusText.text = "Status: cleanup error: ${e.message}"
                tts.speak("There was an error cleaning the chapter text.", TextToSpeech.QUEUE_FLUSH, null, "cleanup_error")
            }
        }
    }

    private fun unpackJavascriptString(result: String): String {
        return if (result.startsWith("\"")) {
            result.substring(1, result.length - 1)
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\t", " ")
                .replace("\\\\", "\\")
        } else {
            result
        }
    }

    private fun normalizeChapterText(rawText: String, title: String): String {
        val titleWords = title
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .toSet()

        val filteredLines = rawText
            .replace("\r", "\n")
            .split('\n')
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .filterNot { line ->
                val lower = line.lowercase(Locale.US)
                val compact = lower.replace(Regex("[^a-z0-9]"), "")
                val lineWords = lower
                    .replace(Regex("[^a-z0-9\\s]"), " ")
                    .split(Regex("\\s+"))
                    .filter { it.length > 2 }
                    .toSet()
                val titleOverlap = if (titleWords.isEmpty()) 0.0 else {
                    titleWords.intersect(lineWords).size.toDouble() / titleWords.size.toDouble()
                }

                compact.length < 2 ||
                    Regex("^chapter\\s+\\d+\\b", RegexOption.IGNORE_CASE).matches(line) ||
                    Regex("\\[\\s*[\\d,]+\\s+words\\s*]", RegexOption.IGNORE_CASE).containsMatchIn(line) ||
                    lower.startsWith("restore scroll position") ||
                    lower.startsWith("translator:") ||
                    lower.startsWith("editor:") ||
                    lower.contains("table of contents") ||
                    lower.contains("previous chapter") ||
                    lower.contains("next chapter") ||
                    lower.contains("report chapter") ||
                    titleOverlap >= 0.75
            }

        val proseStart = filteredLines.indexOfFirst { line ->
            line.split(Regex("\\s+")).size >= 8 &&
                !line.contains("[") &&
                !Regex("^chapter\\s+\\d+\\b", RegexOption.IGNORE_CASE).containsMatchIn(line)
        }.let { if (it == -1) 0 else it }

        return filteredLines
            .drop(proseStart)
            .joinToString("\n\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun goToNextChapter() {
        tts.stop()
        isPlaying = false
        currentChunkIndex = 0
        chunks = emptyList()
        updatePlayerProgress()

        val sourceUrl = webView.url ?: currentUrl.ifBlank { urlInput.text.toString().trim() }
        if (sourceUrl.isBlank()) {
            statusText.text = "Status: no current URL found"
            return
        }

        val nextChapterUrl = getNextChapterUrl(sourceUrl)
        if (nextChapterUrl == sourceUrl) {
            statusText.text = "Status: could not calculate next chapter URL"
            return
        }

        currentUrl = nextChapterUrl
        urlInput.setText(nextChapterUrl)
        statusText.text = "Status: loading next chapter..."
        autoContinue = true
        showReaderMode(false)
        webView.loadUrl(nextChapterUrl)
    }

    private fun getNextChapterUrl(sourceUrl: String): String {
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
            return sourceUrl.replaceRange(match.groups[1]!!.range, (chapterNumber + 1).toString())
        }

        return sourceUrl
    }

    private fun prepareChunks(text: String, resetPosition: Boolean) {
        tts.stop()
        val sentenceRegex = Regex("""(?s)\S.+?(?:[.!?]+["')\]]*|\n\n|$)""")
        val matches = sentenceRegex.findAll(text).toList()
        chunks = matches
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
        chunkRanges = matches
            .filter { it.value.trim().isNotBlank() }
            .map {
                val leadingWhitespace = it.value.indexOfFirst { char -> !char.isWhitespace() }.coerceAtLeast(0)
                val trailingWhitespace = it.value.indexOfLast { char -> !char.isWhitespace() }
                (it.range.first + leadingWhitespace)..(it.range.first + trailingWhitespace)
            }

        if (resetPosition) currentChunkIndex = 0
        currentChunkIndex = currentChunkIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
        isPlaying = chunks.isNotEmpty()
        setPlaybackButtonText(if (isPlaying) "Pause" else "Play")
        updatePlayerProgress()
    }

    private fun speakCurrentChunk() {
        if (!ttsReady) {
            statusText.text = "Status: TTS not ready yet"
            return
        }

        if (chunks.isEmpty()) {
            statusText.text = "Status: no chapter loaded"
            isPlaying = false
            setPlaybackButtonText("Play")
            return
        }

        if (currentChunkIndex >= chunks.size) {
            isPlaying = false
            setPlaybackButtonText("Play")
            statusText.text = "Status: chapter finished"
            saveCurrentChapterToLibrary(silent = true)
            if (onChapterFinishedForSleepTimer()) return
            if (autoContinue) goToNextChapter()
            return
        }

        isPlaying = true
        setPlaybackButtonText("Pause")
        statusText.text = "Status: reading chapter..."
        updatePlayerProgress()
        saveCurrentChapter()

        val utteranceId = "chunk_${UUID.randomUUID()}"
        tts.speak(chunks[currentChunkIndex], TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun pauseSpeech() {
        if (!isPlaying) return

        tts.stop()
        isPlaying = false
        setPlaybackButtonText("Play")
        saveCurrentChapter()
        statusText.text = "Status: paused"
    }

    private fun resumeSpeech() {
        if (chunks.isEmpty() && currentText.isNotBlank()) {
            prepareChunks(currentText, resetPosition = false)
        }

        if (chunks.isEmpty()) {
            statusText.text = "Status: no chapter loaded"
            return
        }

        isPlaying = true
        setPlaybackButtonText("Pause")
        speakCurrentChunk()
    }

    private fun skipChunks(amount: Int) {
        if (chunks.isEmpty()) {
            statusText.text = "Status: no chapter loaded"
            return
        }

        tts.stop()
        currentChunkIndex = (currentChunkIndex + amount).coerceIn(0, chunks.size - 1)
        statusText.text = if (amount > 0) "Status: skipped forward" else "Status: skipped back"
        updatePlayerProgress()
        saveCurrentChapter()

        if (isPlaying) speakCurrentChunk()
    }

    private fun updatePlayerProgress() {
        val percent = if (chunks.isNotEmpty()) {
            ((currentChunkIndex.toFloat() / chunks.size.toFloat()) * 100).toInt()
        } else {
            0
        }.coerceIn(0, 100)

        chapterTitleText.text = currentTitle
        chapterProgressBar.progress = percent

        if (!userIsDraggingSeekBar) {
            audioSeekBar.progress = percent
        }

        progressText.text = if (playerCollapsed) {
            "$percent%"
        } else {
            "Chapter Progress: $percent%"
        }
        timeText.text = if (chunks.isNotEmpty()) {
            "$percent% played - sentence ${currentChunkIndex + 1} of ${chunks.size}"
        } else {
            "0% played"
        }

        currentSentenceText.text = if (chunks.isNotEmpty()) {
            chunks[currentChunkIndex.coerceIn(0, chunks.lastIndex)]
        } else {
            "Current sentence will appear here"
        }

        if (readerModeEnabled) {
            updateReaderText()
        }
    }

    private fun restoreCurrentChapter() {
        val chapter = getCurrentChapter()
        currentUrl = chapter?.url ?: prefs.getString(KEY_LAST_URL, "").orEmpty()
        currentTitle = chapter?.title ?: "No chapter loaded"
        currentText = chapter?.text.orEmpty()
        currentChunkIndex = chapter?.chunkIndex ?: 0
        if (currentText.isNotBlank()) {
            currentText = normalizeChapterText(currentText, currentTitle)
        }

        if (currentUrl.isNotBlank()) {
            urlInput.setText(currentUrl)
        }

        if (currentText.isNotBlank()) {
            prepareChunks(currentText, resetPosition = false)
            isPlaying = false
            setPlaybackButtonText("Play")
            showReaderMode(true)
            statusText.text = "Status: restored saved chapter"
        } else {
            updatePlayerProgress()
        }
    }

    private fun saveCurrentChapter() {
        val url = currentUrl.ifBlank { urlInput.text.toString().trim() }
        if (url.isBlank() && currentText.isBlank()) return

        saveCurrentChapterUseCase(
            Chapter(
                url = url,
                title = currentTitle.ifBlank { "Untitled chapter" },
                text = currentText,
                chunkIndex = currentChunkIndex,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun saveCurrentChapterToLibrary(silent: Boolean = false) {
        val url = currentUrl.ifBlank { urlInput.text.toString().trim() }
        if (url.isBlank()) {
            if (!silent) statusText.text = "Status: nothing to save yet"
            return
        }

        saveChapterToLibrary(
            Chapter(
                url = url,
                title = currentTitle.ifBlank { url },
                text = currentText,
                chunkIndex = currentChunkIndex,
                updatedAt = System.currentTimeMillis()
            )
        )
        if (!silent) statusText.text = "Status: saved to library"
    }

    private fun showLibrary() {
        val items = getLibrary()
        if (items.isEmpty()) {
            statusText.text = "Status: library is empty"
            return
        }

        val labels = items.map {
            val offlineMark = if (it.text.isNotBlank()) "Downloaded" else "URL only"
            "${it.title.ifBlank { it.url }} - $offlineMark"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Library")
            .setItems(labels) { _, which ->
                val item = items[which]
                loadLibraryItem(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadLibraryItem(item: Chapter) {
        currentUrl = item.url
        currentTitle = item.title
        currentText = item.text
        currentChunkIndex = item.chunkIndex
        if (currentText.isNotBlank()) {
            currentText = normalizeChapterText(currentText, currentTitle)
        }
        urlInput.setText(currentUrl)

        if (currentText.isBlank()) {
            statusText.text = "Status: loading saved URL..."
            webView.loadUrl(currentUrl)
        } else {
            prepareChunks(currentText, resetPosition = false)
            isPlaying = false
            setPlaybackButtonText("Play")
            showReaderMode(true)
            saveCurrentChapter()
            statusText.text = "Status: loaded from library"
        }
    }

    override fun onDestroy() {
        sleepTimerHandler.removeCallbacks(sleepTimerRunnable)
        saveCurrentChapter()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val KEY_LAST_URL = "last_url"
        private const val KEY_SELECTED_VOICE = "selected_voice"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_PLAYER_COLLAPSED = "player_collapsed"
    }
}
