package com.example.parser

import java.util.concurrent.ConcurrentHashMap

object AlertDeduplicator {
    // Key: "AppName:SenderName:Amount", Value: Long (timestamp in millis)
    private val processedAlerts = ConcurrentHashMap<String, Long>()
    private const val DEDUPLICATION_WINDOW_MS = 6000L // 6 seconds exclusion window

    fun isDuplicate(appName: String, senderName: String, amount: String): Boolean {
        val now = System.currentTimeMillis()
        val key = "$appName:$senderName:$amount".lowercase().trim()
        val lastTime = processedAlerts[key]

        // Clean up old entries to prevent memory leak
        processedAlerts.entries.removeIf { now - it.value > DEDUPLICATION_WINDOW_MS * 2 }

        if (lastTime != null && (now - lastTime) < DEDUPLICATION_WINDOW_MS) {
            return true
        }

        processedAlerts[key] = now
        return false
    }
}
