/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.linkpreview.api

/**
 * Какую ссылку из текста показывать карточкой: первую http(s)-ссылку, как TG. Ссылки на сообщения
 * и людей Matrix (`matrix.to`) Element рисует пилюлями, для них карточки нет.
 */
object LinkPreviewUrls {
    private val urlRegex = Regex("""https?://[^\s<>"'`]+""", RegexOption.IGNORE_CASE)
    private const val TRAILING_PUNCTUATION = ".,;:!?)]}»…"
    private val skippedHosts = setOf("matrix.to")

    fun firstPreviewableUrl(text: String): String? =
        urlRegex.findAll(text)
            .map { it.value.trimTrailingPunctuation() }
            .firstOrNull { url -> url.host()?.let { it !in skippedHosts && it.contains('.') } == true }

    private fun String.trimTrailingPunctuation(): String {
        var end = length
        while (end > 0 && this[end - 1] in TRAILING_PUNCTUATION) {
            // Скобка в конце — часть ссылки, если в ней есть парная открывающая (вики-ссылки).
            if (this[end - 1] == ')' && substring(0, end - 1).count { it == '(' } > substring(0, end - 1).count { it == ')' }) break
            end--
        }
        return substring(0, end)
    }

    private fun String.host(): String? =
        substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
            .substringAfterLast('@').substringBefore(':').lowercase().takeIf { it.isNotEmpty() }
}
