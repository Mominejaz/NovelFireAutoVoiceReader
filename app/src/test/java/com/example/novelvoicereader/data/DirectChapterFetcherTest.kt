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

    private fun longSentence(seed: String): String {
        return List(4) { seed }.joinToString(" ")
    }
}
