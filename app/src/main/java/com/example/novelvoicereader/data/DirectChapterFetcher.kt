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

            parse(html)
        } finally {
            connection.disconnect()
        }
    }

    fun parse(html: String): ChapterContent {
        val title = extractTitle(html)
        val candidateHtml = chapterCandidates(html).maxByOrNull { stripToText(it).length } ?: html
        val text = cleanupChapterText(stripToText(candidateHtml), title)
        if (text.length < 200) error("chapter text was too short")
        return ChapterContent(title = title.ifBlank { "Untitled chapter" }, text = text)
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
        val titleWords = title.wordsForMatching()
        val lines = rawText
            .split('\n')
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .filterNot { line ->
                val lower = line.lowercase(Locale.US)
                val overlap = if (titleWords.isEmpty()) 0.0 else {
                    titleWords.intersect(line.wordsForMatching()).size.toDouble() / titleWords.size.toDouble()
                }

                line.length < 2 ||
                    lower.startsWith("translator:") ||
                    lower.startsWith("editor:") ||
                    lower.startsWith("restore scroll position") ||
                    lower.contains("table of contents") ||
                    lower.contains("previous chapter") ||
                    lower.contains("next chapter") ||
                    lower.contains("report chapter") ||
                    lower.contains("chapter list") ||
                    Regex("^chapter\\s+\\d+\\b", RegexOption.IGNORE_CASE).matches(line) ||
                    Regex("\\[\\s*[\\d,]+\\s+words\\s*]", RegexOption.IGNORE_CASE).containsMatchIn(line) ||
                    overlap >= 0.75
            }

        val proseStart = lines.indexOfFirst { line ->
            line.split(Regex("\\s+")).size >= 8 &&
                !Regex("^chapter\\s+\\d+\\b", RegexOption.IGNORE_CASE).containsMatchIn(line)
        }.let { if (it == -1) 0 else it }

        return lines
            .drop(proseStart)
            .joinToString("\n\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun String.wordsForMatching(): Set<String> {
        return lowercase(Locale.US)
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }
            .toSet()
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
}
