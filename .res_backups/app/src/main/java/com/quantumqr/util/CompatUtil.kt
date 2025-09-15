package com.quantumqr.util

object CompatUtil {
    fun isUrl(text: String): Boolean = try {
        android.util.Patterns.WEB_URL.matcher(text).matches()
    } catch (_: Throwable) {
        Regex("^(https?://|www\\.)", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }
}