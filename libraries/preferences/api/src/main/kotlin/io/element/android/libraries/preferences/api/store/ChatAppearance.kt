/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.api.store

/** One consistent snapshot of the appearance preferences used by the application theme. */
data class ChatAppearance(
    val messageTextSizeSp: Int = DEFAULT_MESSAGE_TEXT_SIZE_SP,
    val bubbleCornerRadiusDp: Int = DEFAULT_BUBBLE_CORNER_RADIUS_DP,
    val chatWallpaperId: String? = null,
    val chatWallpaperCustomColorArgb: Int? = null,
    val chatBubbleColorArgb: Int? = null,
    val chatAccentColorArgb: Int? = null,
    val chatWallpaperImageUri: String? = null,
    val chatListThreeLine: Boolean = false,
    val chatWallpaperGradient: String? = null,
)
