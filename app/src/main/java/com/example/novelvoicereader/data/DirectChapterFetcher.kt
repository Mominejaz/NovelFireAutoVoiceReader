package com.example.novelvoicereader.data

import com.example.novelvoicereader.domain.model.ChapterContent
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class DirectChapterFetcher {

    fun fetch(url: String): Result<ChapterContent> = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Android) AppleWebKit/537.36 NovelVoiceReader/1.0"
            )
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code")

            val charset = connection.contentType
                ?.substringAfter("charset=", "")
                ?.substringBefore(";")
                ?.takeIf { it.isNotBlank() }
                ?: "UTF-8"
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                .let { if (charset.equals("UTF-8", ignoreCase = true)) it else it }

            parse(html, url)
        } finally {
            connection.disconnect()
        }
    }

    fun parse(html: String, sourceUrl: String? = null): ChapterContent {
        val title = extractTitle(html)
        val candidateHtml = chapterCandidates(html).maxByOrNull { stripToText(it).length } ?: html
        val text = cleanupChapterText(stripToText(candidateHtml), title)
        val chapterLinks = extractChapterLinks(html, sourceUrl)
        if (text.length < 200) error("chapter text was too short")
        return ChapterContent(
            title = title.ifBlank { "Untitled chapter" },
            text = text,
            previousUrl = chapterLinks.previousUrl,
            nextUrl = chapterLinks.nextUrl
        )
    }

    private fun extractTitle(html: String): String {
        val titlePatterns = listOf(
            Regex("""(?is)<h1[^>]*>(.*?)</h1>"""),
            Regex("""(?is)<[^>]+class=["'][^"']*(?:chapter-title|entry-title|title)[^"']*["'][^>]*>(.*?)</[^>]+>"""),
            Regex("""(?is)<title[^>]*>(.*?)</title>""")
        )

        return titlePatterns
            .firstNotNullOfOrNull { pattern -> pattern.find(html)?.groupValues?.getOrNull(1)?.let(::stripToText) }
            ?.substringBefore(" - Novel")
            ?.substringBefore(" | Novel")
            ?.trim()
            .orEmpty()
    }

    private fun chapterCandidates(html: String): List<String> {
        val candidates = mutableListOf<String>()
        val patterns = listOf(
            Regex("""(?is)<article\b[^>]*>(.*?)</article>"""),
            Regex("""(?is)<main\b[^>]*>(.*?)</main>"""),
            Regex("""(?is)<(?:div|section)\b[^>]*(?:id|class)=["'][^"']*(?:chapter|chapter-content|entry-content|reader|content)[^"']*["'][^>]*>(.*?)</(?:div|section)>""")
        )

        patterns.forEach { pattern ->
            pattern.findAll(html).forEach { match ->
                match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }?.let(candidates::add)
            }
        }

        Regex("""(?is)<body\b[^>]*>(.*?)</body>""")
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(candidates::add)

        return candidates
    }

    private fun extractChapterLinks(html: String, sourceUrl: String?): ChapterLinks {
        if (sourceUrl.isNullOrBlank()) return ChapterLinks()

        val anchorPattern = Regex("""(?is)<a\b([^>]*)>(.*?)</a>""")
        val hrefPattern = Regex("""(?is)\bhref\s*=\s*["']([^"']+)["']""")
        val relPattern = Regex("""(?is)\brel\s*=\s*["']([^"']+)["']""")
        val classPattern = Regex("""(?is)\bclass\s*=\s*["']([^"']+)["']""")
        val titlePattern = Regex("""(?is)\btitle\s*=\s*["']([^"']+)["']""")
        val ariaPattern = Regex("""(?is)\baria-label\s*=\s*["']([^"']+)["']""")

        var previousCandidate: ScoredLink? = null
        var nextCandidate: ScoredLink? = null

        anchorPattern.findAll(html).forEach { match ->
            val attributes = match.groupValues.getOrNull(1).orEmpty()
            val href = hrefPattern.find(attributes)?.groupValues?.getOrNull(1).orEmpty()
            val resolvedUrl = resolveUrl(sourceUrl, href) ?: return@forEach
            val label = listOf(
                stripToText(match.groupValues.getOrNull(2).orEmpty()),
                relPattern.find(attributes)?.groupValues?.getOrNull(1).orEmpty(),
                classPattern.find(attributes)?.groupValues?.getOrNull(1).orEmpty(),
                titlePattern.find(attributes)?.groupValues?.getOrNull(1).orEmpty(),
                ariaPattern.find(attributes)?.groupValues?.getOrNull(1).orEmpty()
            ).joinToString(" ").lowercase(Locale.US)

            val previousScore = chapterLinkScore(label, isNext = false)
            if (previousScore > 0 && previousScore > (previousCandidate?.score ?: 0)) {
                previousCandidate = ScoredLink(resolvedUrl, previousScore)
            }

            val nextScore = chapterLinkScore(label, isNext = true)
            if (nextScore > 0 && nextScore > (nextCandidate?.score ?: 0)) {
                nextCandidate = ScoredLink(resolvedUrl, nextScore)
            }
        }

        return ChapterLinks(
            previousUrl = previousCandidate?.url,
            nextUrl = nextCandidate?.url
        )
    }

    private fun chapterLinkScore(label: String, isNext: Boolean): Int {
        val primaryWords = if (isNext) listOf("next", "forward") else listOf("previous", "prev", "back")
        val directionScore = primaryWords.sumOf { word -> if (label.contains(word)) 10 else 0 }
        if (directionScore == 0) return 0

        val chapterScore = if (label.contains("chapter")) 4 else 0
        val relScore = if (isNext && label.contains("next")) 8 else if (!isNext && label.contains("prev")) 8 else 0
        return directionScore + chapterScore + relScore
    }

    private fun resolveUrl(sourceUrl: String, href: String): String? {
        if (href.isBlank()) return null
        val trimmed = href.trim()
        if (
            trimmed.startsWith("#") ||
            trimmed.startsWith("javascript:", ignoreCase = true) ||
            trimmed.startsWith("mailto:", ignoreCase = true)
        ) {
            return null
        }

        return runCatching { URL(URL(sourceUrl), trimmed).toString() }.getOrNull()
    }

    private fun stripToText(rawHtml: String): String {
        return rawHtml
            .replace(Regex("""(?is)<script\b.*?</script>"""), " ")
            .replace(Regex("""(?is)<style\b.*?</style>"""), " ")
            .replace(Regex("""(?is)<noscript\b.*?</noscript>"""), " ")
            .replace(Regex("""(?is)<(?:nav|header|footer|aside|form|button|iframe)\b.*?</(?:nav|header|footer|aside|form|button|iframe)>"""), " ")
            .replace(Regex("""(?i)<br\s*/?>"""), "\n")
            .replace(Regex("""(?i)</(?:p|div|section|article|h1|h2|h3|li)>"""), "\n")
            .replace(Regex("""(?is)<[^>]+>"""), " ")
            .decodeHtmlEntities()
            .replace("\r", "\n")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex(" *\n *"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun cleanupChapterText(rawText: String, title: String): String {
        val normalizedTitle = title.normalizedForComparison()
        val lines = rawText
            .split('\n')
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .trimChapterPreamble()
            .filterNot { line ->
                val lower = line.lowercase(Locale.US)

                line.length < 2 ||
                    lower.startsWith("translator:") ||
                    lower.startsWith("editor:") ||
                    lower.startsWith("restore scroll position") ||
                    lower == "table of contents" ||
                    lower == "previous chapter" ||
                    lower == "next chapter" ||
                    lower == "report chapter" ||
                    lower == "chapter list" ||
                    lower == "advertisement" ||
                    Regex("\\[\\s*[\\d,]+\\s+words\\s*]", RegexOption.IGNORE_CASE).containsMatchIn(line) ||
                    (normalizedTitle.isNotBlank() && line.normalizedForComparison() == normalizedTitle)
            }

        val contentLines = lines.takeUntilFooter()

        return contentLines
            .joinToString("\n\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun List<String>.trimChapterPreamble(): List<String> {
        val searchLimit = minOf(size, MAX_PREAMBLE_LINES)
        val headingIndex = take(searchLimit).indexOfLast { it.isChapterHeading() }
        if (headingIndex == -1) return this

        val heading = this[headingIndex]
        val headingWithoutChapter = heading
            .replaceFirst(Regex("^chapter\\s+", RegexOption.IGNORE_CASE), "")
            .normalizedForComparison()

        return drop(headingIndex + 1).dropWhile { line ->
            val normalizedLine = line.normalizedForComparison()
            normalizedLine.isNotBlank() && normalizedLine == headingWithoutChapter
        }
    }

    private fun String.isChapterHeading(): Boolean {
        return length <= MAX_CHAPTER_HEADING_LENGTH &&
            Regex("^chapter\\s+\\d+\\b", RegexOption.IGNORE_CASE).containsMatchIn(this)
    }

    private fun List<String>.takeUntilFooter(): List<String> {
        val footerIndex = indices.firstOrNull { index ->
            this[index].isStrongChapterFooterMarker() &&
                take(index).sumOf { it.length } >= MIN_PROSE_BEFORE_FOOTER
        } ?: -1
        return if (footerIndex == -1) this else take(footerIndex)
    }

    private fun String.isStrongChapterFooterMarker(): Boolean {
        val lower = lowercase(Locale.US)
        return lower.startsWith("share to your friends") ||
            lower.startsWith("tip: you can use left, right keyboard keys") ||
            lower.startsWith("tap the middle of the screen") ||
            lower.startsWith("if you find any errors") ||
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

    private fun String.normalizedForComparison(): String {
        return lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }

    private fun String.decodeHtmlEntities(): String {
        return replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("""&#(\d+);""")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString().orEmpty()
            }
    }

    private data class ChapterLinks(
        val previousUrl: String? = null,
        val nextUrl: String? = null
    )

    private data class ScoredLink(
        val url: String,
        val score: Int
    )

    private companion object {
        const val MIN_PROSE_BEFORE_FOOTER = 80
        const val MAX_PREAMBLE_LINES = 20
        const val MAX_CHAPTER_HEADING_LENGTH = 160
    }
}
