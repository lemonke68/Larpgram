/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.api.store

import kotlinx.coroutines.flow.Flow

/** Baseline message text size, mapping to text scale 1.0 in the timeline. */
const val DEFAULT_MESSAGE_TEXT_SIZE_SP = 16

/** Baseline message bubble corner radius, matching [TelegramBubbleShape] defaults. */
const val DEFAULT_BUBBLE_CORNER_RADIUS_DP = 20

/**
 * Larpgram chat appearance, stored on the device only.
 *
 * Lives beside Element's [AppPreferencesStore] (same file, own keys) so Element's store stays upstream (аудит C-009).
 */
interface ChatAppearanceStore {
    /** Message text size in sp. Default [DEFAULT_MESSAGE_TEXT_SIZE_SP]. */
    suspend fun setMessageTextSizeSp(value: Int)
    fun getMessageTextSizeSpFlow(): Flow<Int>

    /** Message bubble corner radius in dp. Default [DEFAULT_BUBBLE_CORNER_RADIUS_DP]. */
    suspend fun setBubbleCornerRadiusDp(value: Int)
    fun getBubbleCornerRadiusDpFlow(): Flow<Int>

    /** Selected chat wallpaper id (see ChatWallpaperOption). Null flow value means the default. */
    suspend fun setChatWallpaperId(id: String)
    fun getChatWallpaperIdFlow(): Flow<String?>

    /** ARGB color chosen with the wallpaper eyedropper. Used only when the id is the custom one. */
    suspend fun setChatWallpaperCustomColorArgb(argb: Int)
    fun getChatWallpaperCustomColorArgbFlow(): Flow<Int?>

    /** Outgoing ("Мои сообщения") bubble color, ARGB. Null clears it back to the themed default. */
    suspend fun setChatBubbleColorArgb(argb: Int?)
    fun getChatBubbleColorArgbFlow(): Flow<Int?>

    /** App accent color, ARGB. Null keeps the default brand accent. */
    suspend fun setChatAccentColorArgb(argb: Int?)
    fun getChatAccentColorArgbFlow(): Flow<Int?>

    /** User-picked chat wallpaper photo, as a persistable content URI string. Null = no photo. */
    suspend fun setChatWallpaperImageUri(uri: String?)
    fun getChatWallpaperImageUriFlow(): Flow<String?>

    /** Chat list row density: true = three-line (two preview lines), false = two-line (one). */
    suspend fun setChatListThreeLine(enabled: Boolean)
    fun getChatListThreeLineFlow(): Flow<Boolean>

    /** Two-colour gradient wallpaper spec ("start:end:angle"). Null = no gradient set. */
    suspend fun setChatWallpaperGradient(spec: String?)
    fun getChatWallpaperGradientFlow(): Flow<String?>
}
