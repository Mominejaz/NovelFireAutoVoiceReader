package com.example.novelvoicereader.domain.usecase

import com.example.novelvoicereader.domain.model.Chapter
import com.example.novelvoicereader.domain.repository.ChapterRepository

class SaveCurrentChapterUseCase(
    private val repository: ChapterRepository
) {
    operator fun invoke(chapter: Chapter) = repository.saveCurrentChapter(chapter)
}
