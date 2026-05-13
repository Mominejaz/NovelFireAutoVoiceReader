package com.example.novelvoicereader.domain.repository

import com.example.novelvoicereader.domain.model.Chapter

interface ChapterRepository {
    fun getCurrentChapter(): Chapter?
    fun saveCurrentChapter(chapter: Chapter)
    fun getLibrary(): List<Chapter>
    fun saveToLibrary(chapter: Chapter)
    fun removeFromLibrary(url: String)
}
