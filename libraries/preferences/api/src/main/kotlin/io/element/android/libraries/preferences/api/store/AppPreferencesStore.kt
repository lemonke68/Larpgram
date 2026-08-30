/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.api.store

import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.matrix.api.tracing.LogLevel
import io.element.android.libraries.matrix.api.tracing.TraceLogPack
import kotlinx.coroutines.flow.Flow

/** Larpgram: baseline message text size, mapping to text scale 1.0 in the timeline. */
const val DEFAULT_MESSAGE_TEXT_SIZE_SP = 16

/** Larpgram: baseline message bubble corner radius, matching [TelegramBubbleShape] defaults. */
const val DEFAULT_BUBBLE_CORNER_RADIUS_DP = 20

interface AppPreferencesStore {
    suspend fun setDeveloperModeEnabled(enabled: Boolean)
    fun isDeveloperModeEnabledFlow(): Flow<Boolean>

    suspend fun setCustomElementCallBaseUrl(string: String?)
    fun getCustomElementCallBaseUrlFlow(): Flow<String?>

    suspend fun setTheme(theme: String)
    fun getThemeFlow(): Flow<String?>

    suspend fun setLiveLocationMinimumDistanceInMetersUpdate(value: Int)
    fun getLiveLocationMinimumDistanceInMetersUpdateFlow(): Flow<Int>

    /** Larpgram: message text size in sp (device-local). Default [DEFAULT_MESSAGE_TEXT_SIZE_SP]. */
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

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    suspend fun setHideInviteAvatars(hide: Boolean?)
    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    fun getHideInviteAvatarsFlow(): Flow<Boolean?>
    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    suspend fun setTimelineMediaPreviewValue(mediaPreviewValue: MediaPreviewValue?)
    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    fun getTimelineMediaPreviewValueFlow(): Flow<MediaPreviewValue?>

    suspend fun setTracingLogLevel(logLevel: LogLevel)
    fun getTracingLogLevelFlow(): Flow<LogLevel>

    suspend fun setTracingLogPacks(targets: Set<TraceLogPack>)
    fun getTracingLogPacksFlow(): Flow<Set<TraceLogPack>>

    fun getMessageSoundFlow(): Flow<NotificationSound>

    /**
     * Atomically persists [sound] (with copy-time [title] for Custom; cleared otherwise) and
     * bumps the channel version. Single transaction so process death can't desync URI and version.
     */
    suspend fun setMessageSoundAndIncrementVersion(sound: NotificationSound, title: String?): Int

    /** Title captured at copy time. Null for SystemDefault / Silent or pre-title persisted data. */
    fun getMessageSoundDisplayNameFlow(): Flow<String?>

    fun getCallRingtoneFlow(): Flow<NotificationSound>

    /** See [setMessageSoundAndIncrementVersion]. */
    suspend fun setCallRingtoneAndIncrementVersion(sound: NotificationSound, title: String?): Int

    /** See [getMessageSoundDisplayNameFlow]. */
    fun getCallRingtoneDisplayNameFlow(): Flow<String?>

    /** Single-snapshot read of all sound prefs; used at boot to seed channels without N reads. */
    suspend fun getNotificationSoundChannelConfig(): NotificationSoundChannelConfig

    suspend fun reset()
}
