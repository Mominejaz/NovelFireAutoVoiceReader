package com.example.novelvoicereader.data

object WebContentBlocker {
    private val blockedHosts = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "google-analytics.com",
        "adservice.google.com",
        "taboola.com",
        "outbrain.com",
        "popads.net",
        "propellerads.com",
        "adnxs.com"
    )

    private val blockedPathParts = listOf(
        "/ads/",
        "/advert",
        "/popup",
        "interstitial",
        "analytics",
        "tracker"
    )

    fun shouldBlock(url: String): Boolean {
        val normalized = url.lowercase()
        return blockedHosts.any { normalized.contains(it) } ||
            blockedPathParts.any { normalized.contains(it) }
    }
}
