package com.example.novelvoicereader.domain.usecase

import com.example.novelvoicereader.domain.repository.ChapterRepository

class GetLibraryUseCase(
    private val repository: ChapterRepository
) {
    operator fun invoke() = repository.getLibrary()
}
