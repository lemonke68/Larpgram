/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.linkpreview.api

interface LinkPreviewService {
    /**
     * Превью для [url] или null, если его нет (сайт недоступен, нет Open Graph, ошибка сети).
     * Ответы кэшируются на время сессии, повторные вызовы не ходят в сеть.
     */
    suspend fun preview(url: String): LinkPreview?
}
