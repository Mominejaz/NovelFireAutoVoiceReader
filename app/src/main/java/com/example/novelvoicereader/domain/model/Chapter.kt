package com.example.novelvoicereader.domain.model

data class Chapter(
    val url: String,
    val title: String,
    val text: String,
    val chunkIndex: Int,
    val updatedAt: Long
)
