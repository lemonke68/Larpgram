/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.test

import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.matrix.api.tracing.LogLevel
import io.element.android.libraries.matrix.api.tracing.TraceLogPack
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.DEFAULT_BUBBLE_CORNER_RADIUS_DP
import io.element.android.libraries.preferences.api.store.DEFAULT_MESSAGE_TEXT_SIZE_SP
import io.element.android.libraries.preferences.api.store.NotificationSound
import io.element.android.libraries.preferences.api.store.NotificationSoundChannelConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

class InMemoryAppPreferencesStore(
    isDeveloperModeEnabled: Boolean = false,
    customElementCallBaseUrl: String? = null,
    hideInviteAvatars: Boolean? = null,
    timelineMediaPreviewValue: MediaPreviewValue? = null,
    theme: String? = null,
    liveLocationMinimumDistanceUpdate: Int = 10,
    messageTextSizeSp: Int = DEFAULT_MESSAGE_TEXT_SIZE_SP,
    bubbleCornerRadiusDp: Int = DEFAULT_BUBBLE_CORNER_RADIUS_DP,
    chatWallpaperId: String? = null,
    chatWallpaperCustomColorArgb: Int? = null,
    chatBubbleColorArgb: Int? = null,
    chatAccentColorArgb: Int? = null,
    chatWallpaperImageUri: String? = null,
    chatListThreeLine: Boolean = false,
    logLevel: LogLevel = LogLevel.INFO,
    traceLogPacks: Set<TraceLogPack> = emptySet(),
    messageSound: NotificationSound = NotificationSound.SystemDefault,
    messageSoundChannelVersion: Int = 0,
    messageSoundDisplayName: String? = null,
    callRingtone: NotificationSound = NotificationSound.SystemDefault,
    callRingtoneChannelVersion: Int = 0,
    callRingtoneDisplayName: String? = null,
) : AppPreferencesStore {
    private val isDeveloperModeEnabled = MutableStateFlow(isDeveloperModeEnabled)
    private val customElementCallBaseUrl = MutableStateFlow(customElementCallBaseUrl)
    private val theme = MutableStateFlow(theme)
    private val liveLocationMinimumDistanceUpdate = MutableStateFlow(liveLocationMinimumDistanceUpdate)
    private val messageTextSizeSp = MutableStateFlow(messageTextSizeSp)
    private val bubbleCornerRadiusDp = MutableStateFlow(bubbleCornerRadiusDp)
    private val chatWallpaperId = MutableStateFlow(chatWallpaperId)
    private val chatWallpaperCustomColorArgb = MutableStateFlow(chatWallpaperCustomColorArgb)
    private val chatBubbleColorArgb = MutableStateFlow(chatBubbleColorArgb)
    private val chatAccentColorArgb = MutableStateFlow(chatAccentColorArgb)
    private val chatWallpaperImageUri = MutableStateFlow(chatWallpaperImageUri)
    private val chatListThreeLine = MutableStateFlow(chatListThreeLine)
    private val logLevel = MutableStateFlow(logLevel)
    private val tracingLogPacks = MutableStateFlow(traceLogPacks)
    private val hideInviteAvatars = MutableStateFlow(hideInviteAvatars)
    private val timelineMediaPreviewValue = MutableStateFlow(timelineMediaPreviewValue)
    private val messageSound = MutableStateFlow(messageSound)
    private val messageSoundChannelVersion = MutableStateFlow(messageSoundChannelVersion)
    private val messageSoundDisplayName = MutableStateFlow(messageSoundDisplayName)
    private val callRingtone = MutableStateFlow(callRingtone)
    private val callRingtoneChannelVersion = MutableStateFlow(callRingtoneChannelVersion)
    private val callRingtoneDisplayName = MutableStateFlow(callRingtoneDisplayName)

    override suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        isDeveloperModeEnabled.value = enabled
    }

    override fun isDeveloperModeEnabledFlow(): Flow<Boolean> {
        return isDeveloperModeEnabled
    }

    override suspend fun setCustomElementCallBaseUrl(string: String?) {
        customElementCallBaseUrl.tryEmit(string)
    }

    override fun getCustomElementCallBaseUrlFlow(): Flow<String?> {
        return customElementCallBaseUrl
    }

    override suspend fun setTheme(theme: String) {
        this.theme.value = theme
    }

    override fun getThemeFlow(): Flow<String?> {
        return theme
    }

    override suspend fun setLiveLocationMinimumDistanceInMetersUpdate(value: Int) {
        liveLocationMinimumDistanceUpdate.value = value
    }

    override fun getLiveLocationMinimumDistanceInMetersUpdateFlow(): Flow<Int> {
        return liveLocationMinimumDistanceUpdate
    }

    override suspend fun setMessageTextSizeSp(value: Int) {
        messageTextSizeSp.value = value
    }

    override fun getMessageTextSizeSpFlow(): Flow<Int> {
        return messageTextSizeSp
    }

    override suspend fun setBubbleCornerRadiusDp(value: Int) {
        bubbleCornerRadiusDp.value = value
    }

    override fun getBubbleCornerRadiusDpFlow(): Flow<Int> {
        return bubbleCornerRadiusDp
    }

    override suspend fun setChatWallpaperId(id: String) {
        chatWallpaperId.value = id
    }

    override fun getChatWallpaperIdFlow(): Flow<String?> {
        return chatWallpaperId
    }

    override suspend fun setChatWallpaperCustomColorArgb(argb: Int) {
        chatWallpaperCustomColorArgb.value = argb
    }

    override fun getChatWallpaperCustomColorArgbFlow(): Flow<Int?> {
        return chatWallpaperCustomColorArgb
    }

    override suspend fun setChatBubbleColorArgb(argb: Int?) {
        chatBubbleColorArgb.value = argb
    }

    override fun getChatBubbleColorArgbFlow(): Flow<Int?> {
        return chatBubbleColorArgb
    }

    override suspend fun setChatAccentColorArgb(argb: Int?) {
        chatAccentColorArgb.value = argb
    }

    override fun getChatAccentColorArgbFlow(): Flow<Int?> {
        return chatAccentColorArgb
    }

    override suspend fun setChatWallpaperImageUri(uri: String?) {
        chatWallpaperImageUri.value = uri
    }

    override fun getChatWallpaperImageUriFlow(): Flow<String?> {
        return chatWallpaperImageUri
    }

    override suspend fun setChatListThreeLine(enabled: Boolean) {
        chatListThreeLine.value = enabled
    }

    override fun getChatListThreeLineFlow(): Flow<Boolean> {
        return chatListThreeLine
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override fun getHideInviteAvatarsFlow(): Flow<Boolean?> {
        return hideInviteAvatars
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override fun getTimelineMediaPreviewValueFlow(): Flow<MediaPreviewValue?> {
        return timelineMediaPreviewValue
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override suspend fun setHideInviteAvatars(hide: Boolean?) {
        hideInviteAvatars.value = hide
    }

    @Deprecated("Use MediaPreviewService instead. Kept only for migration.")
    override suspend fun setTimelineMediaPreviewValue(mediaPreviewValue: MediaPreviewValue?) {
        timelineMediaPreviewValue.value = mediaPreviewValue
    }

    override suspend fun setTracingLogLevel(logLevel: LogLevel) {
        this.logLevel.value = logLevel
    }

    override fun getTracingLogLevelFlow(): Flow<LogLevel> {
        return logLevel
    }

    override suspend fun setTracingLogPacks(targets: Set<TraceLogPack>) {
        tracingLogPacks.value = targets
    }

    override fun getTracingLogPacksFlow(): Flow<Set<TraceLogPack>> {
        return tracingLogPacks
    }

    override fun getMessageSoundFlow(): Flow<NotificationSound> {
        return messageSound
    }

    override suspend fun setMessageSoundAndIncrementVersion(sound: NotificationSound, title: String?): Int {
        messageSound.value = sound
        messageSoundDisplayName.value = if (sound is NotificationSound.Custom && !title.isNullOrBlank()) title else null
        return messageSoundChannelVersion.updateAndGet { it + 1 }
    }

    override fun getMessageSoundDisplayNameFlow(): Flow<String?> {
        return messageSoundDisplayName
    }

    override fun getCallRingtoneFlow(): Flow<NotificationSound> {
        return callRingtone
    }

    override suspend fun setCallRingtoneAndIncrementVersion(sound: NotificationSound, title: String?): Int {
        callRingtone.value = sound
        callRingtoneDisplayName.value = if (sound is NotificationSound.Custom && !title.isNullOrBlank()) title else null
        return callRingtoneChannelVersion.updateAndGet { it + 1 }
    }

    override fun getCallRingtoneDisplayNameFlow(): Flow<String?> {
        return callRingtoneDisplayName
    }

    override suspend fun getNotificationSoundChannelConfig(): NotificationSoundChannelConfig {
        return NotificationSoundChannelConfig(
            messageSound = messageSound.value,
            messageSoundVersion = messageSoundChannelVersion.value,
            messageSoundDisplayName = messageSoundDisplayName.value,
            callRingtone = callRingtone.value,
            callRingtoneVersion = callRingtoneChannelVersion.value,
            callRingtoneDisplayName = callRingtoneDisplayName.value,
        )
    }

    override suspend fun reset() {
        // No op
    }
}
