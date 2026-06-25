package com.example.novelvoicereader.domain.usecase

import com.example.novelvoicereader.domain.repository.ChapterRepository

class RemoveChapterFromLibraryUseCase(
    private val repository: ChapterRepository
) {
    operator fun invoke(url: String) {
        repository.removeFromLibrary(url)
    }
}
