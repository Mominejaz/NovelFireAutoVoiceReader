package com.example.novelvoicereader.domain.model

data class ChapterContent(
    val title: String,
    val text: String,
    val previousUrl: String? = null,
    val nextUrl: String? = null
)
