/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.eventformatter.impl

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import io.element.android.libraries.eventformatter.api.LATEST_EVENT_THUMBNAIL_ID

/** Символ-заполнитель под inline-картинку, его же ждёт Compose. */
private const val THUMBNAIL_PLACEHOLDER = "�"

/**
 * Правка форка: оставляет в тексте место под мини-превью медиа.
 *
 * Обоснование и сама метка лежат в
 * [io.element.android.libraries.eventformatter.api.LATEST_EVENT_THUMBNAIL_ID].
 */
internal fun CharSequence.withThumbnailSlot(): AnnotatedString = buildAnnotatedString {
    appendInlineContent(LATEST_EVENT_THUMBNAIL_ID, THUMBNAIL_PLACEHOLDER)
    append(" ")
    if (this@withThumbnailSlot is AnnotatedString) {
        append(this@withThumbnailSlot)
    } else {
        append(this@withThumbnailSlot.toString())
    }
}

internal fun CharSequence.prefixWith(prefix: String): AnnotatedString {
    return buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(prefix)
        }
        append(": ")
        if (this@prefixWith is AnnotatedString) {
            append(this@prefixWith)
        } else {
            append(this@prefixWith.toString())
        }
    }
}
