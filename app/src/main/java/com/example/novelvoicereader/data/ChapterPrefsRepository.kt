package com.example.novelvoicereader.data

import android.content.SharedPreferences
import com.example.novelvoicereader.domain.model.Chapter
import com.example.novelvoicereader.domain.repository.ChapterRepository
import org.json.JSONArray
import org.json.JSONObject

class ChapterPrefsRepository(
    private val prefs: SharedPreferences
) : ChapterRepository {

    override fun getCurrentChapter(): Chapter? {
        val url = prefs.getString(KEY_CURRENT_URL, "").orEmpty()
        val title = prefs.getString(KEY_CURRENT_TITLE, "").orEmpty()
        val text = prefs.getString(KEY_CURRENT_TEXT, "").orEmpty()
        if (url.isBlank() && text.isBlank()) return null

        return Chapter(
            url = url,
            title = title.ifBlank { "No chapter loaded" },
            text = text,
            chunkIndex = prefs.getInt(KEY_CURRENT_CHUNK, 0),
            updatedAt = prefs.getLong(KEY_CURRENT_UPDATED_AT, System.currentTimeMillis()),
            previousUrl = prefs.getString(KEY_CURRENT_PREVIOUS_URL, null)?.takeIf { it.isNotBlank() },
            nextUrl = prefs.getString(KEY_CURRENT_NEXT_URL, null)?.takeIf { it.isNotBlank() }
        )
    }

    override fun saveCurrentChapter(chapter: Chapter) {
        prefs.edit()
            .putString(KEY_CURRENT_URL, chapter.url)
            .putString(KEY_CURRENT_TITLE, chapter.title)
            .putString(KEY_CURRENT_TEXT, chapter.text)
            .putInt(KEY_CURRENT_CHUNK, chapter.chunkIndex)
            .putLong(KEY_CURRENT_UPDATED_AT, chapter.updatedAt)
            .putString(KEY_CURRENT_PREVIOUS_URL, chapter.previousUrl.orEmpty())
            .putString(KEY_CURRENT_NEXT_URL, chapter.nextUrl.orEmpty())
            .putString(KEY_LAST_URL, chapter.url)
            .apply()
    }

    override fun getLibrary(): List<Chapter> {
        val library = libraryArray()
        return (0 until library.length())
            .mapNotNull { library.optJSONObject(it)?.toChapter() }
            .sortedByDescending { it.updatedAt }
    }

    override fun getLibraryChapter(url: String): Chapter? {
        val normalizedUrl = url.normalizedUrl()
        if (normalizedUrl.isBlank()) return null

        return getLibrary().firstOrNull { it.url.normalizedUrl() == normalizedUrl }
    }

    override fun saveToLibrary(chapter: Chapter) {
        val updated = JSONArray()
        getLibrary()
            .filterNot { it.url == chapter.url }
            .forEach { updated.put(it.toJson()) }

        updated.put(chapter.toJson())
        prefs.edit().putString(KEY_LIBRARY, updated.toString()).apply()
    }

    override fun removeFromLibrary(url: String) {
        val updated = JSONArray()
        getLibrary()
            .filterNot { it.url == url }
            .forEach { updated.put(it.toJson()) }

        prefs.edit().putString(KEY_LIBRARY, updated.toString()).apply()
    }

    private fun libraryArray(): JSONArray {
        return try {
            JSONArray(prefs.getString(KEY_LIBRARY, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
    }

    private fun JSONObject.toChapter(): Chapter? {
        val url = optString("url")
        if (url.isBlank()) return null

        return Chapter(
            url = url,
            title = optString("title", url).ifBlank { url },
            text = optString("text"),
            chunkIndex = optInt("chunk", 0),
            updatedAt = optLong("updatedAt", 0L),
            previousUrl = optString("previousUrl").takeIf { it.isNotBlank() },
            nextUrl = optString("nextUrl").takeIf { it.isNotBlank() }
        )
    }

    private fun Chapter.toJson(): JSONObject {
        return JSONObject()
            .put("url", url)
            .put("title", title)
            .put("text", text)
            .put("chunk", chunkIndex)
            .put("updatedAt", updatedAt)
            .put("previousUrl", previousUrl.orEmpty())
            .put("nextUrl", nextUrl.orEmpty())
    }

    private fun String.normalizedUrl(): String {
        return trim().trimEnd('/')
    }

    companion object {
        private const val KEY_LAST_URL = "last_url"
        private const val KEY_CURRENT_URL = "current_url"
        private const val KEY_CURRENT_TITLE = "current_title"
        private const val KEY_CURRENT_TEXT = "current_text"
        private const val KEY_CURRENT_CHUNK = "current_chunk"
        private const val KEY_CURRENT_UPDATED_AT = "current_updated_at"
        private const val KEY_CURRENT_PREVIOUS_URL = "current_previous_url"
        private const val KEY_CURRENT_NEXT_URL = "current_next_url"
        private const val KEY_LIBRARY = "library"
    }
}
