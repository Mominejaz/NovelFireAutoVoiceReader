package com.example.novelvoicereader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectChapterFetcherTest {

    private val fetcher = DirectChapterFetcher()

    @Test
    fun parse_removesNovelFireFooterBeforeTtsText() {
        val html = """
            <html>
                <head><title>Chapter 1686 - Really Can - Novel Fire</title></head>
                <body>
                    <main class="chapter-content">
                        <h1>Chapter 1686 - Really Can</h1>
                        <p>Restore scroll position</p>
                        <p>1686 Really Can</p>
                        <p>${longSentence("Han Sen kept moving through the ruins while the armored enemies pressed closer.")}</p>
                        <p>${longSentence("Old Cat watched from a distance and offered advice that sounded more annoying than useful.")}</p>
                        <p>${longSentence("The light inside the broken metal board became brighter as the fight continued.")}</p>
                        <p>Share to your friends</p>
                        <p>ADVERTISEMENT</p>
                        <p>Tip: You can use left, right keyboard keys to browse between chapters.</p>
                        <p>If you find any errors (non-standard content, ads redirect, broken links, etc..), Please let us know so we can fix it as soon as possible.</p>
                        <p>Report</p>
                    </main>
                    <a rel="prev" href="/book/super-gene/chapter-1685">Previous Chapter</a>
                    <a rel="next" href="/book/super-gene/chapter-1687">Next Chapter</a>
                </body>
            </html>
        """.trimIndent()

        val content = fetcher.parse(html, "https://novelfire.net/book/super-gene/chapter-1686")

        assertTrue(content.text.contains("armored enemies pressed closer"))
        assertFalse(content.text.contains("Share to your friends"))
        assertFalse(content.text.contains("ADVERTISEMENT"))
        assertFalse(content.text.contains("keyboard keys"))
        assertFalse(content.text.contains("If you find any errors"))
        assertEquals("https://novelfire.net/book/super-gene/chapter-1685", content.previousUrl)
        assertEquals("https://novelfire.net/book/super-gene/chapter-1687", content.nextUrl)
    }

    @Test
    fun parse_preservesShortOpeningLinesAndTitleWordsInsideProse() {
        val html = """
            <html>
                <head><title>Chapter 42 - The Report</title></head>
                <body>
                    <main class="chapter-content">
                        <p>Title</p>
                        <p>Author</p>
                        <p>Novel Super Gene Chapter 42 - The Report</p>
                        <h1>Chapter 42 - The Report</h1>
                        <p>42 The Report</p>
                        <p>"Run!"</p>
                        <p>Report.</p>
                        <p>The report was still in her hand when the doors finally opened.</p>
                        <p>${longSentence("Nobody knew whether the chapter would end with an answer, but they kept reading.")}</p>
                    </main>
                </body>
            </html>
        """.trimIndent()

        val content = fetcher.parse(html)

        assertTrue(content.text.startsWith("\"Run!\""))
        assertTrue(content.text.contains("Report."))
        assertTrue(content.text.contains("The report was still in her hand"))
    }

    @Test
    fun parse_divineDaoLibrary_usesChapterContainerAndRealNavigationButtons() {
        val html = """
            <html>
                <head><title>Martial Peak – Chapter 4, The Black Book</title></head>
                <body>
                    <main>
                        <div class="chapter-index">
                            <p>${longSentence("Chapter index and site chrome must never become narration.")}</p>
                            <p>${longSentence("This deliberately outweighs the real chapter container.")}</p>
                            <a href="/story/martial-peak/martial-peak-chapter-1159-next-time-be-careful-what-you-buy/">
                                Chapter 1159, Next Time, Be Careful What You Buy
                            </a>
                        </div>
                        <a href="/story/martial-peak/martial-peak-chapter-3-147-losses/"
                           class="button _secondary _navigation _prev"><span>Previous</span></a>
                        <header><h1 class="chapter__title">Chapter 4, The Black Book</h1></header>
                        <section id="chapter-content" class="chapter__content content-section"
                            data-action="mouseup->fictioneer-suggestion#toggleFloatingButton">
                            <div class="chapter-formatting"
                                data-action="mousedown->fictioneer-chapter#fastClick">
                                <p><strong>Translator &ndash; Erza</strong></p>
                                <p><strong>Finalized Editor &ndash; Silavin</strong></p>
                                <p>${longSentence("Once his body was clean, Yang Kai picked up the medicine bottle and examined it.")}</p>
                                <p>${longSentence("The mysterious black book waited silently while the room grew dark around him.")}</p>
                            </div>
                        </section>
                        <a href="/story/martial-peak/martial-peak-chapter-5/"
                           class="button _secondary _navigation _next"><span>Next</span></a>
                    </main>
                </body>
            </html>
        """.trimIndent()

        val content = fetcher.parse(
            html,
            "https://www.divinedaolibrary.com/story/martial-peak/martial-peak-chapter-4/"
        )

        assertEquals("Chapter 4, The Black Book", content.title)
        assertTrue(content.text.startsWith("Once his body was clean"))
        assertTrue(content.text.contains("mysterious black book"))
        assertFalse(content.text.contains("Translator"))
        assertFalse(content.text.contains("fictioneer-"))
        assertFalse(content.text.contains("chapter index and site chrome", ignoreCase = true))
        assertEquals(
            "https://www.divinedaolibrary.com/story/martial-peak/martial-peak-chapter-3-147-losses/",
            content.previousUrl
        )
        assertEquals(
            "https://www.divinedaolibrary.com/story/martial-peak/martial-peak-chapter-5/",
            content.nextUrl
        )
    }

    @Test
    fun parse_lightNovelWorld_usesProtectedChapterTextAndNavigationButtons() {
        val html = """
            <html>
                <head>
                    <title>Chapter 93: Chapter 93 - 92: Cultivators&#x27; Market_1 - Who Let Him Cultivate?! by The Whitest Crow | Light Novel World</title>
                </head>
                <body>
                    <div class="chapter-reader">
                        <div class="chapter-container">
                            <div class="chapter-header">
                                <a href="/novel/who-let-him-cultivate/" class="novel-title">Who Let Him Cultivate?!</a>
                                <h1 class="chapter-title">Chapter 93 - 92: Cultivators&#x27; Market_1</h1>
                            </div>
                            <div class="chapter-nav">
                                <a href="/novel/who-let-him-cultivate/chapter/92/" class="nav-btn prev-btn">
                                    <svg><path d="M15 18l-6-6 6-6"/></svg>
                                </a>
                                <div class="chapter-selector">
                                    <select><option>Chapter 93 - 92: Cultivators&#x27;...</option></select>
                                    <div class="chapter-loading">Loading chapters...</div>
                                </div>
                                <a href="/novel/who-let-him-cultivate/chapter/94/" class="nav-btn next-btn">
                                    <svg><path d="M9 18l6-6-6-6"/></svg>
                                </a>
                            </div>
                            <div class="chapter-content">
                                <div class="chapter-text protected-content" id="chapterText" data-protected="true">
                                    <div class="chapter-ad-container" data-ad-position="1">
                                        <div class="ad-unit"><script>window.pubfuturetag = window.pubfuturetag || [];</script></div>
                                    </div>
                                    <style>.chapter-ad-container { min-height: 250px; }</style>
                                    <p>${longSentence("The four of them all bought books to their liking, with Man Gu buying a copy of Great Xia's Unofficial History.")}</p>
                                    <p>${longSentence("Many of the stories recorded in the book, even Meng Jingzhou, who knew the inside story, found quite wild.")}</p>
                                    <div class="chapter-ad-container" data-ad-position="2">
                                        <div class="ad-unit">ADVERTISEMENT</div>
                                    </div>
                                    <p>${longSentence("Lu Yang noticed that on his table were a bagua plate, dice, the four treasures of the study, nine copper coins, and a crystal ball.")}</p>
                                </div>
                            </div>
                            <div class="chapter-nav">
                                <a href="/novel/who-let-him-cultivate/chapter/92/" class="nav-btn prev-btn">Previous</a>
                                <a href="/novel/who-let-him-cultivate/chapter/94/" class="nav-btn next-btn">Next</a>
                            </div>
                        </div>
                    </div>
                    <section class="comments">
                        <h3>Chapter 93 - Chapter 93 - 92: Cultivators&#x27; Market_1</h3>
                        <p>If you find any errors (non-standard content, ads redirect, broken links, etc..), Please let us know so we can fix it as soon as possible.</p>
                        <button>Submit Report</button>
                    </section>
                </body>
            </html>
        """.trimIndent()

        val content = fetcher.parse(
            html,
            "https://lightnovelworld.org/novel/who-let-him-cultivate/chapter/93/"
        )

        assertEquals("Chapter 93 - 92: Cultivators' Market_1", content.title)
        assertTrue(content.text.startsWith("The four of them all bought books"))
        assertTrue(content.text.contains("crystal ball"))
        assertFalse(content.text.contains("Loading chapters"))
        assertFalse(content.text.contains("ADVERTISEMENT"))
        assertFalse(content.text.contains("chapter-ad-container"))
        assertFalse(content.text.contains("pubfuturetag"))
        assertFalse(content.text.contains("If you find any errors"))
        assertFalse(content.text.contains("Submit Report"))
        assertEquals(
            "https://lightnovelworld.org/novel/who-let-him-cultivate/chapter/92/",
            content.previousUrl
        )
        assertEquals(
            "https://lightnovelworld.org/novel/who-let-him-cultivate/chapter/94/",
            content.nextUrl
        )
    }

    @Test
    fun parse_lightNovelPub_usesProtectedChapterTextAndSkipsDisabledPreviousButton() {
        val html = """
            <html>
                <head>
                    <title>Chapter 1: Chapter 1 - 1: Nightmare Begins - Shadow Slave by Guiltythree | Light Novel Pub</title>
                </head>
                <body>
                    <div class="chapter-reader">
                        <div class="chapter-container">
                            <div class="chapter-header">
                                <h1 class="chapter-title">Chapter 1 - 1: Nightmare Begins</h1>
                            </div>
                            <div class="chapter-nav">
                                <button class="nav-btn prev-btn disabled">Previous</button>
                                <a href="/novel/shadow-slave/chapter/2/" class="nav-btn next-btn">Next</a>
                            </div>
                            <div class="chapter-content">
                                <div class="chapter-text protected-content" id="chapterText" data-protected="true">
                                    <div class="chapter-ad-container" data-ad-position="1">
                                        <div class="ad-unit"><script>loadAdvertisement()</script></div>
                                    </div>
                                    <p>${longSentence("A frail young man slowly opened his eyes and found himself surrounded by endless darkness.")}</p>
                                    <p>${longSentence("The cold floor beneath him felt real enough, but the nightmare still clung to his thoughts.")}</p>
                                    <div class="chapter-ad-container" data-ad-position="2">
                                        <div class="ad-unit">ADVERTISEMENT</div>
                                    </div>
                                    <p>${longSentence("Somewhere far away, a voice whispered that the trial had only just begun.")}</p>
                                </div>
                            </div>
                            <section class="comments">
                                <h3 class="comments-title">Chapter Comments</h3>
                                <p>Sign in to leave a comment.</p>
                            </section>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val content = fetcher.parse(
            html,
            "https://lightnovelpub.org/novel/shadow-slave/chapter/1/"
        )

        assertEquals("Chapter 1 - 1: Nightmare Begins", content.title)
        assertTrue(content.text.startsWith("A frail young man slowly opened his eyes"))
        assertTrue(content.text.contains("trial had only just begun"))
        assertFalse(content.text.contains("ADVERTISEMENT"))
        assertFalse(content.text.contains("chapter-ad-container"))
        assertFalse(content.text.contains("Chapter Comments"))
        assertFalse(content.text.contains("Sign in"))
        assertEquals(null, content.previousUrl)
        assertEquals(
            "https://lightnovelpub.org/novel/shadow-slave/chapter/2/",
            content.nextUrl
        )
    }

    @Test
    fun parse_royalRoad_usesChapterInnerAndHeadNavigationLinks() {
        val html = """
            <html>
                <head>
                    <title>Not a Chapter - The Sacred Beast Sect | Royal Road</title>
                    <link rel='prev' href='/fiction/21049/the-sacred-beast-sect/chapter/301650/end-of-the-exams'/>
                    <link rel='next' href='/fiction/21049/the-sacred-beast-sect/chapter/302436/aftermath'/>
                </head>
                <body>
                    <header><a href="/fictions/best-rated">Best Rated</a></header>
                    <div class="portlet-title">
                        <button>Reader Preferences</button>
                    </div>
                    <div class="chapter-inner chapter-content">
                        <p>${longSentence("So I decided to make the cosmology and power rankings clear in the universe of The Sacred Beast Sect.")}</p>
                        <p>&nbsp;</p>
                        <p><strong>The Three Realms:</strong></p>
                        <p>${longSentence("Lets start with the mortal half and call it the Mortal realm, a cosmos consisting of numerous galaxies and worlds.")}</p>
                    </div>
                    <section class="author-note">
                        <h2>About the author</h2>
                        <p>${longSentence("This author box and comments area should not become narration.")}</p>
                    </section>
                    <section class="comments">
                        <h2>Comments</h2>
                        <p>Join the discussion on Royal Road.</p>
                    </section>
                </body>
            </html>
        """.trimIndent()

        val content = fetcher.parse(
            html,
            "https://www.royalroad.com/fiction/21049/the-sacred-beast-sect/chapter/301918/not-a-chapter-information-on-the-setting-of-this"
        )

        assertEquals("Not a Chapter", content.title)
        assertTrue(content.text.startsWith("So I decided to make the cosmology"))
        assertTrue(content.text.contains("The Three Realms:"))
        assertTrue(content.text.contains("Mortal realm"))
        assertFalse(content.text.contains("Reader Preferences"))
        assertFalse(content.text.contains("About the author"))
        assertFalse(content.text.contains("comments area should not become narration"))
        assertFalse(content.text.contains("Join the discussion"))
        assertEquals(
            "https://www.royalroad.com/fiction/21049/the-sacred-beast-sect/chapter/301650/end-of-the-exams",
            content.previousUrl
        )
        assertEquals(
            "https://www.royalroad.com/fiction/21049/the-sacred-beast-sect/chapter/302436/aftermath",
            content.nextUrl
        )
    }

    @Test
    fun parse_novelBuddy_usesNextDataChapterContentAndNavigation() {
        val html = """
            <html>
                <head>
                    <title data-next-head="">Super Gene - Chapter 3462End - Epilogue | NovelBuddy</title>
                </head>
                <body>
                    <aside>
                        <a href="/popular">Popular</a>
                        <a href="/hot-chapters">Hot Chapters</a>
                    </aside>
                    <main>
                        <button>Reader settings</button>
                        <p>This app shell text should not be read.</p>
                    </main>
                    <script id="__NEXT_DATA__" type="application/json">{
                        "props": {
                            "pageProps": {
                                "initialChapter": {
                                    "url": "/super-gene/chapter-3462end-epilogue",
                                    "name": "Chapter 3462End - Epilogue",
                                    "content": "\n\u003cp\u003e Chapter 3462 Epilogue\u003c/p\u003e\u003cp\u003e ${longSentence("On a nameless island, Han Sen and his family were having a vacation.")}\u003c/p\u003e\u003cp\u003e Novelfire admin to another admin should not be narrated.\u003c/p\u003e\u003cp\u003e New novel chapters are published on NovelFire.\u003c/p\u003e\u003cp\u003e freewebnovel.com\u003c/p\u003e\u003cp\u003e ${longSentence("The old and heavy door closed, then vanished as if it had never existed.")}\u003c/p\u003e",
                                    "summary": "Chapter summary"
                                },
                                "nextChapter": null,
                                "previousChapter": {
                                    "url": "/super-gene/chapter-3461-by-dollars-name-the-end",
                                    "name": "Chapter 3461 - By Dollar's Name (The End)"
                                }
                            }
                        }
                    }</script>
                </body>
            </html>
        """.trimIndent()

        val content = fetcher.parse(
            html,
            "https://novelbuddy.com/super-gene/chapter-3462end-epilogue"
        )

        assertEquals("Super Gene - Chapter 3462End - Epilogue", content.title)
        assertTrue(content.text.startsWith("On a nameless island"))
        assertTrue(content.text.contains("Han Sen and his family"))
        assertTrue(content.text.contains("heavy door closed"))
        assertFalse(content.text.contains("Popular"))
        assertFalse(content.text.contains("Reader settings"))
        assertFalse(content.text.contains("Novelfire admin", ignoreCase = true))
        assertFalse(content.text.contains("New novel chapters", ignoreCase = true))
        assertFalse(content.text.contains("freewebnovel", ignoreCase = true))
        assertEquals(
            "https://novelbuddy.com/super-gene/chapter-3461-by-dollars-name-the-end",
            content.previousUrl
        )
        assertEquals(null, content.nextUrl)
    }

    @Test
    fun parse_novelRoll_usesProseArticleAndDataNavigation() {
        val html = """
            <html>
                <head>
                    <title>I Can Copy Talents novel Chapter 2150: 2150 read online | NovelRoll</title>
                    <link rel="prev" href="https://novelroll.com/book/i-can-copy-talents/chapter-2149" />
                </head>
                <body>
                    <nav>
                        <a href="/book/i-can-copy-talents">Chapter list</a>
                    </nav>
                    <section class="chapter-section">
                        <article class="prose prose-lg"
                            data-chapter-url="/book/i-can-copy-talents/chapter-2150"
                            data-chapter-name="Chapter 2150: 2150"
                            data-prev-url="/book/i-can-copy-talents/chapter-2149"
                            data-next-url="/book/i-can-copy-talents/chapter-2151">
                            <h1>
                                <a href="/book/i-can-copy-talents">I Can Copy Talents</a>
                                Chapter 2150: 2150
                            </h1>
                            <div class="not-prose">~6 minute read · 1,394 words</div>
                            <button>Listen to chapter</button>
                            <details>
                                <summary>Previously on I Can Copy Talents...</summary>
                                <div class="collapse-content">
                                    The recap should not become the first narrated paragraph.
                                </div>
                            </details>
                            <p>${longSentence("Now that he had reached the summit of the ancient pagoda, Ye Tian calmly studied the endless lights below.")}</p>
                            <p>${longSentence("The invisible chains around the market shattered, and the cultivators finally saw the path beyond the starry gate.")}</p>
                        </article>
                    </section>
                    <section id="comments">
                        <p>Comments should stay out of the spoken chapter.</p>
                    </section>
                    <footer>
                        <p>Privacy policy</p>
                    </footer>
                </body>
            </html>
        """.trimIndent()

        val content = fetcher.parse(
            html,
            "https://novelroll.com/book/i-can-copy-talents/chapter-2150"
        )

        assertEquals("Chapter 2150: 2150", content.title)
        assertTrue(content.text.startsWith("Now that he had reached the summit"))
        assertTrue(content.text.contains("cultivators finally saw the path"))
        assertFalse(content.text.contains("I Can Copy Talents"))
        assertFalse(content.text.contains("minute read"))
        assertFalse(content.text.contains("1,394 words"))
        assertFalse(content.text.contains("Listen to chapter"))
        assertFalse(content.text.contains("Previously on"))
        assertFalse(content.text.contains("recap should not"))
        assertFalse(content.text.contains("Comments should stay out"))
        assertFalse(content.text.contains("Privacy policy"))
        assertEquals(
            "https://novelroll.com/book/i-can-copy-talents/chapter-2149",
            content.previousUrl
        )
        assertEquals(
            "https://novelroll.com/book/i-can-copy-talents/chapter-2151",
            content.nextUrl
        )
    }

    @Test
    fun parse_novelBin_usesChrContentAndSkipsDisabledPreviousLink() {
        val html = """
            <html>
                <head>
                    <title>Turning #Chapter 1 - Read Turning Chapter 1 Online - All Page - Novel Bin</title>
                </head>
                <body>
                    <main>
                        <div id="chapter" class="chapter container">
                            <a class="novel-title" href="https://novelbin.com/b/turning">Turning</a>
                            <h2>
                                <a class="chr-title" href="https://novelbin.com/b/turning/chapter-1" title="Chapter 1">
                                    <span class="chr-text">Chapter 1</span>
                                </a>
                            </h2>
                            <div class="chr-nav" id="chr-nav-top">
                                <a disabled class="btn btn-success js-chapter-nav"
                                    data-chapter-nav="prev"
                                    data-chapter-url=""
                                    href="javascript:void(0)">
                                    Prev Chapter
                                </a>
                                <button type="button" class="btn btn-success chr-jump">Chapter list</button>
                                <a class="btn btn-success js-chapter-nav"
                                    data-chapter-nav="next"
                                    data-chapter-url="https://novelbin.com/b/turning/chapter-2"
                                    title="Chapter 2"
                                    href="https://novelbin.com/b/turning/chapter-2">
                                    Next Chapter
                                </a>
                            </div>
                            <div id="chr-content" class="chr-c">
                                <div class="js-ad-slot" data-ad-slot="chapter-top">ADs by Google</div>
                                <p>Chapter 1</p>
                                <p>${longSentence("Listen, criminal Yudrain Aile, the voice echoed above his head as the silent hall watched him closely.")}</p>
                                <p>${longSentence("Yuder smirked bitterly to himself while the accusations continued without anyone asking for the truth.")}</p>
                                <div class="js-ad-slot" data-ad-slot="chapter-bottom">Advertisement</div>
                            </div>
                            <div class="chr-nav" id="chr-nav-bottom">
                                <a class="btn btn-success js-chapter-nav"
                                    data-chapter-nav="next"
                                    href="https://novelbin.com/b/turning/chapter-2">
                                    Next Chapter
                                </a>
                            </div>
                            <a id="chr-error">Report chapter</a>
                            <div class="box-notice">
                                Tip: You can use left, right, A and D keyboard keys to browse between chapters.
                            </div>
                        </div>
                    </main>
                    <footer>
                        <strong>Novel Bin</strong>
                        Read light novel, web novel, korean novel and chinese novel online for free.
                    </footer>
                </body>
            </html>
        """.trimIndent()

        val content = fetcher.parse(
            html,
            "https://novelbin.me/novel-book/turning/chapter-1"
        )

        assertEquals("Chapter 1", content.title)
        assertTrue(content.text.startsWith("Listen, criminal Yudrain Aile"))
        assertTrue(content.text.contains("the accusations continued"))
        assertFalse(content.text.contains("ADs by Google"))
        assertFalse(content.text.contains("Advertisement"))
        assertFalse(content.text.contains("Chapter list"))
        assertFalse(content.text.contains("Report chapter"))
        assertFalse(content.text.contains("keyboard keys"))
        assertFalse(content.text.contains("Novel Bin"))
        assertEquals(null, content.previousUrl)
        assertEquals(
            "https://novelbin.com/b/turning/chapter-2",
            content.nextUrl
        )
    }

    @Test
    fun parse_novelFull_usesChapterContentAndNavigationButtons() {
        val html = """
            <html>
                <head>
                    <title>Read The 99th Divorce Chapter 1: Who Was the Murderer online for free - NovelFull</title>
                </head>
                <body>
                    <header>
                        <form>Search novels</form>
                    </header>
                    <main id="container">
                        <div id="chapter" class="chapter container">
                            <a class="truyen-title" href="/the-99th-divorce.html">The 99th Divorce</a>
                            <h2>
                                <a class="chapter-title" href="/the-99th-divorce/chapter-1-who-was-the-murderer.html"
                                    title="Chapter 1: Who Was the Murderer">
                                    <span class="chapter-text">Chapter 1: Who Was the Murderer</span>
                                </a>
                            </h2>
                            <div class="chapter-nav" id="chapter-nav-top">
                                <a class="btn btn-success" disabled id="prev_chap">Prev Chapter</a>
                                <button type="button" class="btn btn-success chapter_jump">Chapter list</button>
                                <a class="btn btn-success"
                                    href="/the-99th-divorce/chapter-2-sleeping-with-a-woman-as-ugly-as-i-am.html"
                                    title="Chapter 2: Sleeping with A Woman as Ugly as I Am"
                                    id="next_chap">Next Chapter</a>
                            </div>
                            <div id="chapter-content" class="chapter-c">
                                <div align="center">
                                    <iframe src="//ad.a-ads.com/2267708/?size=300x250"></iframe>
                                </div>
                                <p>Chapter 1: Who Was the Murderer</p>
                                <p>Translator: Nyoi-Bo Studio Editor: Nyoi-Bo Studio</p>
                                <p>${longSentence("At three in the morning, Su Qianci hurried to the Li household and asked to see him.")}</p>
                                <p>${longSentence("The guards stared at her scarred face coldly while the iron gate remained shut.")}</p>
                                <div class="ads ads-holder ads-middle text-center">Sponsored story block</div>
                            </div>
                            <div class="chapter-nav" id="chapter-nav-bottom">
                                <a class="btn btn-success" disabled id="prev_chap_bottom">Prev Chapter</a>
                                <a class="btn btn-success"
                                    href="/the-99th-divorce/chapter-2-sleeping-with-a-woman-as-ugly-as-i-am.html"
                                    id="next_chap_bottom">Next Chapter</a>
                            </div>
                            <a id="chapter_error">Report chapter</a>
                            <div id="chapter_comment">Comments should not be narrated.</div>
                        </div>
                    </main>
                    <footer>
                        <p>Completed novels and latest chapters</p>
                    </footer>
                </body>
            </html>
        """.trimIndent()

        val content = fetcher.parse(
            html,
            "https://novelfull.net/the-99th-divorce/chapter-1-who-was-the-murderer.html"
        )

        assertEquals("Chapter 1: Who Was the Murderer", content.title)
        assertTrue(content.text.startsWith("At three in the morning"))
        assertTrue(content.text.contains("iron gate remained shut"))
        assertFalse(content.text.contains("Translator:"))
        assertFalse(content.text.contains("Sponsored story block"))
        assertFalse(content.text.contains("Chapter list"))
        assertFalse(content.text.contains("Report chapter"))
        assertFalse(content.text.contains("Comments should not"))
        assertFalse(content.text.contains("Completed novels"))
        assertEquals(null, content.previousUrl)
        assertEquals(
            "https://novelfull.net/the-99th-divorce/chapter-2-sleeping-with-a-woman-as-ugly-as-i-am.html",
            content.nextUrl
        )
    }

    @Test
    fun parse_wuxiaWorld_usesFrViewProseAndNextChapterAnchor() {
        val html = """
            <html>
                <head>
                    <title data-rh="true">Child of Light - Volume 1: Chapter 1 - The First Chapter</title>
                </head>
                <body>
                    <div class="chapter-header">
                        <a href="/novel/child-of-light">Child of Light</a>
                        <button disabled data-testid="prev-chapter-button">Previous</button>
                        <button data-testid="next-chapter-button">Next</button>
                    </div>
                    <h4 data-testid="heading">
                        <span data-testid="title">Volume 1: Chapter 1 - The First Chapter</span>
                    </h4>
                    <div class="chapter-content">
                        <div class="fr-view prose max-w-none">
                            <p><strong><u><span>Volume 1: Chapter 1 - The First Chapter</span></u></strong></p>
                            <p><span>${longSentence("Early morning, it was so bright that Zhang Gong had to open his lazy eyelids.")}</span></p>
                            <p><span>${longSentence("Hearing his mother's pleasant voice, he immediately jumped down from bed and hurried toward breakfast.")}</span></p>
                        </div>
                        <section>
                            <a href="/novel/child-of-light/col-volume-1-chapter-2">
                                <button type="button">NEXT CHAPTER</button>
                            </a>
                        </section>
                    </div>
                    <section class="comments">
                        <p>Comments should never become spoken prose.</p>
                    </section>
                    <footer>
                        <p>Terms of Service and app store badges</p>
                    </footer>
                </body>
            </html>
        """.trimIndent()

        val content = fetcher.parse(
            html,
            "https://www.wuxiaworld.com/novel/child-of-light/col-volume-1-chapter-1"
        )

        assertEquals("Volume 1: Chapter 1 - The First Chapter", content.title)
        assertTrue(content.text.startsWith("Early morning"))
        assertTrue(content.text.contains("hurried toward breakfast"))
        assertFalse(content.text.contains("Child of Light"))
        assertFalse(content.text.contains("NEXT CHAPTER"))
        assertFalse(content.text.contains("Comments should never"))
        assertFalse(content.text.contains("Terms of Service"))
        assertEquals(null, content.previousUrl)
        assertEquals(
            "https://www.wuxiaworld.com/novel/child-of-light/col-volume-1-chapter-2",
            content.nextUrl
        )
    }

    private fun longSentence(seed: String): String {
        return List(4) { seed }.joinToString(" ")
    }
}
