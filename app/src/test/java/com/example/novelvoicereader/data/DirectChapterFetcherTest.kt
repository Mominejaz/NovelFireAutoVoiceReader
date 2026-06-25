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

    private fun longSentence(seed: String): String {
        return List(4) { seed }.joinToString(" ")
    }
}
