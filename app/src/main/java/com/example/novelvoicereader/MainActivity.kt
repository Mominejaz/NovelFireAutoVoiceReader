package com.example.novelvoicereader

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.SharedPreferences
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.text.SpannableString
import android.text.Spanned
import android.text.InputType
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
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.novelvoicereader.data.ChapterPrefsRepository
import com.example.novelvoicereader.data.DirectChapterFetcher
import com.example.novelvoicereader.data.WebContentBlocker
import com.example.novelvoicereader.domain.model.Chapter
import com.example.novelvoicereader.domain.model.ChapterContent
import com.example.novelvoicereader.domain.model.SleepTimer
import com.example.novelvoicereader.domain.repository.ChapterRepository
import com.example.novelvoicereader.domain.usecase.GetCurrentChapterUseCase
import com.example.novelvoicereader.domain.usecase.GetLibraryUseCase
import com.example.novelvoicereader.domain.usecase.RemoveChapterFromLibraryUseCase
import com.example.novelvoicereader.domain.usecase.SaveChapterToLibraryUseCase
import com.example.novelvoicereader.domain.usecase.SaveCurrentChapterUseCase
import org.json.JSONObject
import java.util.Locale

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
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var saveButton: Button
    private lateinit var libraryButton: Button
    private lateinit var voiceButton: Button
    private lateinit var moreButton: Button
    private lateinit var timerButton: Button
    private lateinit var speedButton: Button
    private lateinit var downloadButton: Button
    private lateinit var readerModeButton: Button
    private lateinit var playPauseButton: Button
    private lateinit var miniPlayPauseButton: Button
    private lateinit var collapsePlayerButton: Button
    private lateinit var skipBackButton: Button
    private lateinit var skipForwardButton: Button
    private lateinit var skipForward60Button: Button
    private lateinit var miniSkipBackButton: Button
    private lateinit var miniSkipForwardButton: Button
    private lateinit var miniPlayerControls: View
    private lateinit var playbackControls: View
    private lateinit var chapterNavControls: View
    private lateinit var playerPanel: View
    private lateinit var tts: TextToSpeech
    private lateinit var prefs: SharedPreferences
    private lateinit var chapterRepository: ChapterRepository
    private lateinit var getCurrentChapter: GetCurrentChapterUseCase
    private lateinit var getLibrary: GetLibraryUseCase
    private lateinit var saveCurrentChapterUseCase: SaveCurrentChapterUseCase
    private lateinit var saveChapterToLibrary: SaveChapterToLibraryUseCase
    private lateinit var removeChapterFromLibrary: RemoveChapterFromLibraryUseCase
    private val directChapterFetcher = DirectChapterFetcher()

    private var chunks: List<String> = emptyList()
    private var chunkRanges: List<IntRange> = emptyList()
    private var currentChunkIndex = 0
    private var currentTitle = "No chapter loaded"
    private var currentText = ""
    private var currentUrl = ""
    private var currentPreviousChapterUrl: String? = null
    private var currentNextChapterUrl: String? = null
    private var isPlaying = false
    private var userIsDraggingSeekBar = false
    private var ttsReady = false
    private var autoContinue = true
    private var suppressNextBrowserExtraction = false
    private var playerCollapsed = false
    private var readerModeEnabled = false
    private var sleepTimer: SleepTimer = SleepTimer.Off
    private var remainingTimerChapters = 0
    private val sleepTimerHandler = Handler(Looper.getMainLooper())
    private val sleepTimerRunnable = Runnable { stopForSleepTimer() }
    private val playbackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != PlaybackService.ACTION_PROGRESS) return
            handlePlaybackProgress(intent)
        }
    }

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
        removeChapterFromLibrary = RemoveChapterFromLibraryUseCase(chapterRepository)

        setContentView(R.layout.activity_main)
        configureSystemBarSpacing()
        bindViews()
        configureWebView()
        configureControls()
        requestNotificationPermissionIfNeeded()
        registerPlaybackReceiver()

        tts = TextToSpeech(this, this)

        restoreCurrentChapter()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun configureSystemBarSpacing() {
        val root = findViewById<View>(R.id.rootContainer)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                16.dp() + bars.left,
                48.dp() + bars.top,
                16.dp() + bars.right,
                16.dp() + bars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
    }

    private fun registerPlaybackReceiver() {
        ContextCompat.registerReceiver(
            this,
            playbackReceiver,
            IntentFilter(PlaybackService.ACTION_PROGRESS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun bindViews() {
        webView = findViewById(R.id.webView)
        readerScrollView = findViewById(R.id.readerScrollView)
        readerTextView = findViewById(R.id.readerTextView)
        urlInput = findViewById(R.id.urlInput)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)
        saveButton = findViewById(R.id.saveButton)
        libraryButton = findViewById(R.id.libraryButton)
        voiceButton = findViewById(R.id.voiceButton)
        moreButton = findViewById(R.id.moreButton)
        timerButton = findViewById(R.id.timerButton)
        speedButton = findViewById(R.id.speedButton)
        downloadButton = findViewById(R.id.downloadButton)
        readerModeButton = findViewById(R.id.readerModeButton)
        collapsePlayerButton = findViewById(R.id.collapsePlayerButton)
        miniPlayerControls = findViewById(R.id.miniPlayerControls)
        playbackControls = findViewById(R.id.playbackControls)
        chapterNavControls = findViewById(R.id.chapterNavControls)
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
        miniSkipBackButton = findViewById(R.id.miniSkipBackButton)
        miniSkipForwardButton = findViewById(R.id.miniSkipForwardButton)
    }

    private fun configureWebView() {
        webView.setBackgroundColor(Color.parseColor("#111318"))
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

                if (suppressNextBrowserExtraction) {
                    suppressNextBrowserExtraction = false
                    statusText.text = "Status: browser loaded"
                    return
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
                statusText.text = "Status: fetching chapter text..."
                currentUrl = url
                fetchChapterDirectly(url, speakAfterLoad = true)
            } else {
                statusText.text = "Status: paste a chapter URL first"
            }
        }

        stopButton.setOnClickListener {
            autoContinue = false
            stopPlaybackService()
            isPlaying = false
            setPlaybackButtonText("Play")
            saveCurrentChapter()
            statusText.text = "Status: stopped"
        }

        nextButton.setOnClickListener {
            goToNextChapter()
        }

        previousButton.setOnClickListener {
            goToPreviousChapter()
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

        moreButton.setOnClickListener {
            showMoreMenu()
        }

        collapsePlayerButton.setOnClickListener {
            setPlayerCollapsed(!playerCollapsed)
        }

        timerButton.setOnClickListener {
            showSleepTimerPicker()
        }

        speedButton.setOnClickListener {
            showSpeedPicker()
        }

        downloadButton.setOnClickListener {
            showDownloadPicker()
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
        miniSkipBackButton.setOnClickListener { skipChunks(-2) }
        miniSkipForwardButton.setOnClickListener { skipChunks(2) }

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
                    isPlaying = true
                    speakCurrentChunk()
                }
            }
        })

        setPlaybackButtonText("Play")
        updateSpeedButton()
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
        chapterNavControls.visibility = expandedVisibility
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

    private fun showMoreMenu() {
        PopupMenu(this, moreButton).apply {
            menu.add("Timer").setOnMenuItemClickListener {
                showSleepTimerPicker()
                true
            }
            menu.add("Speed: ${prefs.getFloat(KEY_SPEECH_RATE, 1.0f).speedLabel()}").setOnMenuItemClickListener {
                showSpeedPicker()
                true
            }
            menu.add("Queue chapters").setOnMenuItemClickListener {
                showDownloadPicker()
                true
            }
            menu.add("Reader text size").setOnMenuItemClickListener {
                showReaderTextSizePicker()
                true
            }
            menu.add("Share chapter").setOnMenuItemClickListener {
                shareCurrentChapter()
                true
            }
            menu.add(if (readerModeEnabled) "Open browser" else "Open reader").setOnMenuItemClickListener {
                if (currentText.isBlank()) {
                    showReaderMode(false)
                    statusText.text = "Status: reader mode needs extracted chapter text"
                } else {
                    showReaderMode(!readerModeEnabled)
                }
                true
            }
            menu.add("How to use").setOnMenuItemClickListener {
                showHowToUsePage()
                true
            }
            menu.add("About app").setOnMenuItemClickListener {
                showAboutPage()
                true
            }
            show()
        }
    }

    private fun showHowToUsePage() {
        showInfoPage(
            title = "How to use",
            intro = "A quick guide for turning a novel chapter into clean, listenable audio.",
            sections = listOf(
                "Start reading" to "Paste a chapter URL, then tap Read. The app extracts the chapter text, switches to reader mode, and starts playback.",
                "Control playback" to "Use Play/Pause, the progress slider, and the -15, +15, +60 controls to move through the chapter. Hide the player when you want more reading space.",
                "Chapters" to "Use Previous and Next from the bottom player. The app looks for real chapter links first, then falls back to changing the chapter number in the URL.",
                "Queue offline chapters" to "Open More, choose Queue chapters, then save the current chapter or preload the next 5 to 25 chapters for later.",
                "Reader comfort" to "Open More, choose Reader text size, and pick the size that feels best for long sessions.",
                "Share a chapter" to "Open More, choose Share chapter, and send the current link to another app.",
                "Voice and speed" to "Open More for speed, or tap Voice to choose a text-to-speech voice. River is preferred when it is installed on the phone.",
                "Screen off listening" to "Playback runs through a foreground playback service, so the loaded chapter can keep playing while the screen is locked."
            )
        )
    }

    private fun showAboutPage() {
        showInfoPage(
            title = "About Novel Voice Reader",
            intro = "Novel Voice Reader is built for long-form web fiction: less browser clutter, more listening, and a calmer reading surface.",
            sections = listOf(
                "What it is for" to "Light novels, web novels, serial fiction, long articles, and saved chapters you want to listen to hands-free.",
                "What makes it useful" to "Clean chapter extraction, reader mode, saved library chapters, queue preloading, sleep timers, speed control, sharing, adjustable text, and background playback for the current chapter.",
                "Current limits" to "Some sites hide chapter text or navigation behind scripts. Those may still need browser mode or site-specific extraction improvements.",
                "Next level ideas" to "A real background queue, website compatibility presets, export/share options, richer library organization, and a signed release build for testers.",
                "Version" to "1.0"
            )
        )
    }

    private fun showInfoPage(
        title: String,
        intro: String,
        sections: List<Pair<String, String>>
    ) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp(), 20.dp(), 22.dp(), 10.dp())
        }

        content.addView(TextView(this).apply {
            text = title
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            includeFontPadding = false
        })

        content.addView(TextView(this).apply {
            text = intro
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            textSize = 14f
            setLineSpacing(4.dp().toFloat(), 1f)
            setPadding(0, 10.dp(), 0, 8.dp())
        })

        sections.forEach { (heading, body) ->
            content.addView(TextView(this).apply {
                text = heading
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent_strong))
                textSize = 14f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                includeFontPadding = false
                setPadding(0, 14.dp(), 0, 4.dp())
            })

            content.addView(TextView(this).apply {
                text = body
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                textSize = 14f
                setLineSpacing(4.dp().toFloat(), 1f)
            })
        }

        val scroller = ScrollView(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.surface))
            addView(content)
        }

        AlertDialog.Builder(this)
            .setView(scroller)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun showReaderMode(enabled: Boolean) {
        readerModeEnabled = enabled && currentText.isNotBlank()
        readerScrollView.visibility = if (readerModeEnabled) View.VISIBLE else View.GONE
        webView.visibility = if (readerModeEnabled) View.GONE else View.VISIBLE
        readerModeButton.text = if (readerModeEnabled) "Browser" else "Reader"

        if (readerModeEnabled) {
            updateReaderText()
            setPlayerCollapsed(true)
        } else {
            openCurrentChapterInBrowserIfNeeded()
        }
    }

    private fun openCurrentChapterInBrowserIfNeeded() {
        val url = currentUrl.ifBlank { urlInput.text.toString().trim() }
        if (url.isBlank()) {
            statusText.text = "Status: browser ready. Paste a URL to load a page."
            return
        }

        val loadedUrl = webView.url.orEmpty()
        if (loadedUrl == url) {
            statusText.text = "Status: browser open"
            return
        }

        suppressNextBrowserExtraction = true
        webView.loadUrl(url)
        statusText.text = "Status: opening browser page..."
    }

    private fun updateReaderText() {
        if (currentText.isBlank()) {
            readerTextView.text = "Reader mode will appear after a chapter is extracted."
            return
        }

        readerTextView.textSize = prefs.getFloat(KEY_READER_TEXT_SIZE, DEFAULT_READER_TEXT_SIZE)
        val spannable = SpannableString(currentText)
        var activeRange: IntRange? = null
        if (chunkRanges.isNotEmpty()) {
            val activeIndex = currentChunkIndex.coerceIn(0, chunkRanges.lastIndex)
            activeRange = chunkRanges[activeIndex]
            val passageRange = readingPassageRange(activeIndex)

            if (passageRange != null) {
                spannable.setSpan(
                    BackgroundColorSpan(Color.parseColor("#202A38")),
                    passageRange.first,
                    (passageRange.last + 1).coerceAtMost(currentText.length),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            if (activeRange.first >= 0 && activeRange.last < currentText.length) {
                val start = activeRange.first
                val end = (activeRange.last + 1).coerceAtMost(currentText.length)
                spannable.setSpan(
                    BackgroundColorSpan(Color.parseColor("#34445D")),
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

        if (chunks.isNotEmpty() && activeRange != null) {
            scrollReaderToActiveRange(activeRange)
        }
    }

    private fun readingPassageRange(activeIndex: Int): IntRange? {
        if (chunkRanges.isEmpty()) return null

        val startIndex = (activeIndex - 1).coerceAtLeast(0)
        val endIndex = (activeIndex + 1).coerceAtMost(chunkRanges.lastIndex)
        val start = chunkRanges[startIndex].first.coerceAtLeast(0)
        val end = chunkRanges[endIndex].last.coerceAtMost(currentText.lastIndex)
        return if (start <= end) start..end else null
    }

    private fun scrollReaderToActiveRange(activeRange: IntRange) {
        readerScrollView.post {
            val layout = readerTextView.layout
            val maxScroll = (readerTextView.height - readerScrollView.height).coerceAtLeast(0)
            val targetScroll = if (layout != null && activeRange.first in currentText.indices) {
                val line = layout.getLineForOffset(activeRange.first)
                val lineTop = layout.getLineTop(line)
                (lineTop - readerScrollView.height / 3).coerceIn(0, maxScroll)
            } else {
                val percent = currentChunkIndex.toFloat() / chunks.size.toFloat()
                (maxScroll * percent).toInt()
            }

            readerScrollView.smoothScrollTo(0, targetScroll)
        }
    }

    private fun showReaderTextSizePicker() {
        val sizes = floatArrayOf(16f, 18f, 20f, 22f, 24f)
        val labels = arrayOf("Compact", "Comfortable", "Large", "Extra large", "Huge")
        val savedSize = prefs.getFloat(KEY_READER_TEXT_SIZE, DEFAULT_READER_TEXT_SIZE)
        val checkedIndex = sizes.indexOfFirst { kotlin.math.abs(it - savedSize) < 0.1f }

        AlertDialog.Builder(this)
            .setTitle("Reader text size")
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                prefs.edit().putFloat(KEY_READER_TEXT_SIZE, sizes[which]).apply()
                readerTextView.textSize = sizes[which]
                if (readerModeEnabled) updateReaderText()
                statusText.text = "Status: reader text set to ${labels[which].lowercase(Locale.US)}"
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareCurrentChapter() {
        val url = currentUrl.ifBlank { urlInput.text.toString().trim() }
        if (url.isBlank()) {
            statusText.text = "Status: no chapter link to share"
            return
        }

        val title = currentTitle.takeIf { it.isNotBlank() && it != "No chapter loaded" }
            ?: "Novel Voice Reader chapter"
        val shareText = "$title\n$url"

        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(title, url))

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, shareText)
        }

        startActivity(Intent.createChooser(intent, "Share chapter"))
        statusText.text = "Status: chapter link copied and ready to share"
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            applySavedOrPreferredVoice()
            tts.setSpeechRate(prefs.getFloat(KEY_SPEECH_RATE, 1.0f))
            tts.setPitch(1.0f)
            ttsReady = true

            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = Unit
                override fun onError(utteranceId: String?) = Unit
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
        runCatching {
            startActivity(ttsSettingsIntent)
        }.onFailure {
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
        timerButton.text = timer.shortLabel()
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
        stopPlaybackService()
        isPlaying = false
        setPlaybackButtonText("Play")
        sleepTimer = SleepTimer.Off
        timerButton.text = sleepTimer.shortLabel()
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

        timerButton.text = SleepTimer.Chapters(remainingTimerChapters).shortLabel()
        return false
    }

    private fun showSpeedPicker() {
        val speeds = floatArrayOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val labels = speeds.map { it.speedLabel() }.toTypedArray()
        val savedSpeed = prefs.getFloat(KEY_SPEECH_RATE, 1.0f)
        val checkedIndex = speeds.indexOfFirst { kotlin.math.abs(it - savedSpeed) < 0.01f }

        AlertDialog.Builder(this)
            .setTitle("Reading speed")
            .setSingleChoiceItems(labels, checkedIndex) { dialog, which ->
                setSpeechSpeed(speeds[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setSpeechSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_SPEECH_RATE, speed).apply()
        if (ttsReady) {
            tts.setSpeechRate(speed)
        }
        updateSpeedButton()
        statusText.text = "Status: reading speed set to ${speed.speedLabel()}"

        if (isPlaying) {
            speakCurrentChunk()
        }
    }

    private fun handlePlaybackProgress(intent: Intent) {
        currentChunkIndex = intent.getIntExtra(PlaybackService.EXTRA_INDEX, currentChunkIndex)
            .coerceIn(0, (chunks.size - 1).coerceAtLeast(0))
        isPlaying = intent.getBooleanExtra(PlaybackService.EXTRA_IS_PLAYING, isPlaying)

        val stopped = intent.getBooleanExtra(PlaybackService.EXTRA_STOPPED, false)
        val finished = intent.getBooleanExtra(PlaybackService.EXTRA_FINISHED, false)
        val error = intent.getBooleanExtra(PlaybackService.EXTRA_ERROR, false)

        if (error) {
            isPlaying = false
            setPlaybackButtonText("Play")
            statusText.text = "Status: background playback error"
        } else if (stopped) {
            isPlaying = false
            setPlaybackButtonText("Play")
            statusText.text = "Status: stopped"
        } else if (finished) {
            isPlaying = false
            setPlaybackButtonText("Play")
            statusText.text = "Status: chapter finished"
            saveCurrentChapterToLibrary(silent = true)

            if (!onChapterFinishedForSleepTimer() && autoContinue) {
                goToNextChapter()
            }
        } else {
            setPlaybackButtonText(if (isPlaying) "Pause" else "Play")
            statusText.text = if (isPlaying) "Status: reading in background service..." else "Status: paused"
        }

        updatePlayerProgress()
        saveCurrentChapter()
    }

    private fun updateSpeedButton() {
        speedButton.text = prefs.getFloat(KEY_SPEECH_RATE, 1.0f).speedLabel()
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

                function linkFor(direction) {
                    var selector = direction === 'next'
                        ? 'a[rel="next"], a.next, .next a, .nav-next a'
                        : 'a[rel="prev"], a[rel="previous"], a.prev, .prev a, .previous a, .nav-previous a';
                    var direct = document.querySelector(selector);
                    if (direct && direct.href) return direct.href;

                    var labels = direction === 'next'
                        ? ['next chapter', 'next']
                        : ['previous chapter', 'previous', 'prev'];
                    var anchors = Array.prototype.slice.call(document.querySelectorAll('a[href]'));
                    for (var j = 0; j < anchors.length; j++) {
                        var linkText = cleanText((anchors[j].innerText || '') + ' ' + (anchors[j].title || '') + ' ' + (anchors[j].getAttribute('aria-label') || '')).toLowerCase();
                        for (var k = 0; k < labels.length; k++) {
                            if (linkText.indexOf(labels[k]) !== -1) return anchors[j].href;
                        }
                    }
                    return '';
                }

                var chapterText = cleanText(fullText.substring(Math.max(startIndex, 0), endIndex));
                return JSON.stringify({
                    title: title,
                    text: chapterText,
                    previousUrl: linkFor('previous'),
                    nextUrl: linkFor('next')
                });
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
                currentPreviousChapterUrl = json.optString("previousUrl").takeIf { it.isNotBlank() }
                currentNextChapterUrl = json.optString("nextUrl").takeIf { it.isNotBlank() }
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

    private fun fetchChapterDirectly(url: String, speakAfterLoad: Boolean) {
        if (url.isBlank()) {
            statusText.text = "Status: paste a chapter URL first"
            return
        }

        autoContinue = true
        currentUrl = url
        urlInput.setText(url)
        statusText.text = "Status: fetching chapter text..."

        Thread {
            val result = directChapterFetcher.fetch(url)
            runOnUiThread {
                result
                    .onSuccess { content ->
                        applyFetchedChapter(url, content, speakAfterLoad)
                    }
                    .onFailure { error ->
                        statusText.text = "Status: lightweight reader failed: ${error.message ?: "could not extract text"}"
                        loadBrowserForExtraction(url)
                    }
            }
        }.start()
    }

    private fun loadBrowserForExtraction(url: String) {
        if (url.isBlank()) return

        readerModeEnabled = false
        readerScrollView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        readerModeButton.text = "Reader"
        currentUrl = url
        urlInput.setText(url)
        autoContinue = true
        suppressNextBrowserExtraction = false
        statusText.text = "Status: trying browser extractor..."
        webView.loadUrl(url)
    }

    private fun applyFetchedChapter(url: String, content: ChapterContent, speakAfterLoad: Boolean) {
        val title = content.title.ifBlank { "Untitled chapter" }
        val text = normalizeChapterText(content.text, title)

        if (text.isBlank()) {
            statusText.text = "Status: could not extract chapter text"
            return
        }

        currentTitle = title
        currentText = text
        currentUrl = url
        currentPreviousChapterUrl = content.previousUrl
        currentNextChapterUrl = content.nextUrl
        currentChunkIndex = 0
        prepareChunks(text, resetPosition = true)
        saveCurrentChapter()
        saveCurrentChapterToLibrary(silent = true)
        showReaderMode(true)
        statusText.text = "Status: loaded lightweight reader (${text.length} characters)"

        if (speakAfterLoad) {
            if (ttsReady) {
                speakCurrentChunk()
            } else {
                isPlaying = false
                setPlaybackButtonText("Play")
                statusText.text = "Status: chapter loaded. TTS is still starting."
            }
        }
    }

    private fun showDownloadPicker() {
        val options = arrayOf(
            "Save current chapter",
            "Custom amount...",
            "Next 5 chapters",
            "Next 10 chapters",
            "Next 15 chapters",
            "Next 20 chapters",
            "Next 25 chapters"
        )

        AlertDialog.Builder(this)
            .setTitle("Preload chapters")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        saveCurrentChapter()
                        saveCurrentChapterToLibrary()
                    }
                    1 -> showCustomDownloadCountDialog()
                    else -> preloadFutureChapters((which - 1) * 5)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomDownloadCountDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "1-25"
            setText("5")
            selectAll()
        }

        AlertDialog.Builder(this)
            .setTitle("How many future chapters?")
            .setView(input)
            .setPositiveButton("Download") { _, _ ->
                val count = input.text.toString().toIntOrNull()?.coerceIn(1, 25) ?: 5
                preloadFutureChapters(count)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun preloadFutureChapters(count: Int) {
        val startUrl = currentUrl.ifBlank { urlInput.text.toString().trim() }
        if (startUrl.isBlank()) {
            statusText.text = "Status: paste or load a chapter URL first"
            return
        }

        statusText.text = "Status: preloading next $count chapter(s)..."

        Thread {
            var nextUrl = currentNextChapterUrl ?: getChapterUrl(startUrl, 1)
            var saved = 0
            var failed = 0

            repeat(count.coerceIn(1, 25)) {
                if (nextUrl.isBlank()) {
                    failed += 1
                    return@repeat
                }

                directChapterFetcher.fetch(nextUrl)
                    .onSuccess { content ->
                        val title = content.title.ifBlank { nextUrl }
                        val text = normalizeChapterText(content.text, title)
                        if (text.isNotBlank()) {
                            saveChapterToLibrary(
                                Chapter(
                                    url = nextUrl,
                                    title = title,
                                    text = text,
                                    chunkIndex = 0,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            saved += 1
                            nextUrl = content.nextUrl ?: getChapterUrl(nextUrl, 1)
                        } else {
                            failed += 1
                            nextUrl = getChapterUrl(nextUrl, 1)
                        }
                    }
                    .onFailure {
                        failed += 1
                        nextUrl = getChapterUrl(nextUrl, 1)
                    }
            }

            runOnUiThread {
                statusText.text = "Status: preloaded $saved chapter(s), $failed failed"
            }
        }.start()
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

        val proseLines = filteredLines.drop(proseStart)
        val contentLines = proseLines.takeUntilChapterFooter()

        return contentLines
            .joinToString("\n\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun List<String>.takeUntilChapterFooter(): List<String> {
        val footerIndex = indexOfFirst { it.isChapterFooterMarker() }
        return if (footerIndex == -1) this else take(footerIndex)
    }

    private fun String.isChapterFooterMarker(): Boolean {
        val lower = lowercase(Locale.US)
        return lower.startsWith("share to your friends") ||
            lower.startsWith("advertisement") ||
            lower.startsWith("tip: you can use left, right keyboard keys") ||
            lower.startsWith("tap the middle of the screen") ||
            lower.startsWith("if you find any errors") ||
            lower == "report" ||
            lower.startsWith("novel ranking") ||
            lower.startsWith("latest chapters") ||
            lower.startsWith("latest novels") ||
            lower.startsWith("completed novels") ||
            lower.startsWith("privacy policy") ||
            lower.startsWith("terms of service") ||
            lower.startsWith("contact us") ||
            lower.startsWith("made with") ||
            lower.startsWith("disclaimer:")
    }

    private fun goToNextChapter() {
        goToRelativeChapter(1)
    }

    private fun goToPreviousChapter() {
        goToRelativeChapter(-1)
    }

    private fun goToRelativeChapter(offset: Int) {
        stopPlaybackService()
        isPlaying = false
        currentChunkIndex = 0
        chunks = emptyList()
        updatePlayerProgress()

        val sourceUrl = currentUrl.ifBlank { webView.url ?: urlInput.text.toString().trim() }
        if (sourceUrl.isBlank()) {
            statusText.text = "Status: no current URL found"
            return
        }

        val targetChapterUrl = if (offset > 0) {
            currentNextChapterUrl ?: getChapterUrl(sourceUrl, offset)
        } else {
            currentPreviousChapterUrl ?: getChapterUrl(sourceUrl, offset)
        }
        if (targetChapterUrl == sourceUrl) {
            statusText.text = "Status: could not calculate ${if (offset > 0) "next" else "previous"} chapter URL"
            return
        }

        currentUrl = targetChapterUrl
        urlInput.setText(targetChapterUrl)
        statusText.text = "Status: loading ${if (offset > 0) "next" else "previous"} chapter..."
        autoContinue = true
        fetchChapterDirectly(targetChapterUrl, speakAfterLoad = true)
    }

    private fun getNextChapterUrl(sourceUrl: String): String {
        return getChapterUrl(sourceUrl, 1)
    }

    private fun getChapterUrl(sourceUrl: String, offset: Int): String {
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

        return sourceUrl
    }

    private fun prepareChunks(text: String, resetPosition: Boolean) {
        if (!isPlaying) {
            tts.stop()
        }
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
        ContextCompat.startForegroundService(
            this,
            PlaybackService.startIntent(
                context = this,
                title = currentTitle.ifBlank { "Novel Voice Reader" },
                chunks = ArrayList(chunks),
                index = currentChunkIndex,
                rate = prefs.getFloat(KEY_SPEECH_RATE, 1.0f),
                voiceName = prefs.getString(KEY_SELECTED_VOICE, null)
            )
        )
    }

    private fun pauseSpeech() {
        if (!isPlaying) return

        sendPlaybackAction(PlaybackService.ACTION_PAUSE)
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

        val shouldResumePlayback = isPlaying
        currentChunkIndex = (currentChunkIndex + amount).coerceIn(0, chunks.size - 1)
        statusText.text = if (amount > 0) "Status: skipped forward" else "Status: skipped back"
        updatePlayerProgress()
        saveCurrentChapter()

        if (shouldResumePlayback) {
            seekPlaybackService(currentChunkIndex)
        }
    }

    private fun sendPlaybackAction(action: String) {
        startService(Intent(this, PlaybackService::class.java).setAction(action))
    }

    private fun stopPlaybackService() {
        sendPlaybackAction(PlaybackService.ACTION_STOP)
    }

    private fun seekPlaybackService(index: Int) {
        startService(
            Intent(this, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_SEEK)
                .putExtra(PlaybackService.EXTRA_INDEX, index)
        )
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
                showLibraryItemActions(item)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLibraryItemActions(item: Chapter) {
        AlertDialog.Builder(this)
            .setTitle(item.title.ifBlank { item.url })
            .setItems(arrayOf("Open", "Remove saved chapter")) { _, which ->
                when (which) {
                    0 -> loadLibraryItem(item)
                    1 -> confirmRemoveLibraryItem(item)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmRemoveLibraryItem(item: Chapter) {
        AlertDialog.Builder(this)
            .setTitle("Remove chapter?")
            .setMessage(item.title.ifBlank { item.url })
            .setPositiveButton("Remove") { _, _ ->
                removeChapterFromLibrary(item.url)
                statusText.text = "Status: removed saved chapter"
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
        runCatching { unregisterReceiver(playbackReceiver) }
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    private fun SleepTimer.shortLabel(): String {
        return when (this) {
            SleepTimer.Off -> "Timer"
            is SleepTimer.Minutes -> "${value}m"
            is SleepTimer.Chapters -> "${value} ch"
        }
    }

    private fun Int.dp(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }

    private fun Float.speedLabel(): String {
        return if (this % 1.0f == 0f) {
            "${this.toInt()}x"
        } else {
            "${this}x"
        }
    }

    companion object {
        private const val KEY_LAST_URL = "last_url"
        private const val KEY_SELECTED_VOICE = "selected_voice"
        private const val KEY_SPEECH_RATE = "speech_rate"
        private const val KEY_PLAYER_COLLAPSED = "player_collapsed"
        private const val KEY_READER_TEXT_SIZE = "reader_text_size"
        private const val DEFAULT_READER_TEXT_SIZE = 18f
        private const val REQUEST_NOTIFICATIONS = 42
    }
}
