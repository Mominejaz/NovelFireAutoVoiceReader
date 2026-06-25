package com.example.novelvoicereader.domain.model

sealed interface SleepTimer {
    data object Off : SleepTimer
    data class Minutes(val value: Int) : SleepTimer
    data class Chapters(val value: Int) : SleepTimer

    fun label(): String = when (this) {
        Off -> "Timer off"
        is Minutes -> "${value}m timer"
        is Chapters -> if (value == 1) "Stop after chapter" else "$value chapters"
    }
}
