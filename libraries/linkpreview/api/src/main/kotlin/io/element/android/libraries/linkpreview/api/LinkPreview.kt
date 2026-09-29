/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.linkpreview.api

/**
 * Карточка ссылки из Open Graph страницы. Картинка — уже в медиа сервера ([imageMxc]), её грузим
 * как обычное медиа Matrix. Всё, кроме [url], может отсутствовать.
 */
data class LinkPreview(
    val url: String,
    val siteName: String?,
    val title: String?,
    val description: String?,
    val imageMxc: String?,
    val imageWidth: Int?,
    val imageHeight: Int?,
) {
    /** Пустая карточка (сайт ничего о себе не сказал) не рисуется. */
    val isEmpty: Boolean = siteName == null && title == null && description == null && imageMxc == null
}
