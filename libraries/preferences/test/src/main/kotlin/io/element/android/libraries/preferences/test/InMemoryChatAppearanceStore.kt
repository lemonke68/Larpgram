/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.test

import io.element.android.libraries.preferences.api.store.ChatAppearanceStore
import io.element.android.libraries.preferences.api.store.DEFAULT_BUBBLE_CORNER_RADIUS_DP
import io.element.android.libraries.preferences.api.store.DEFAULT_MESSAGE_TEXT_SIZE_SP
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class InMemoryChatAppearanceStore(
    messageTextSizeSp: Int = DEFAULT_MESSAGE_TEXT_SIZE_SP,
    bubbleCornerRadiusDp: Int = DEFAULT_BUBBLE_CORNER_RADIUS_DP,
    chatWallpaperId: String? = null,
    chatWallpaperCustomColorArgb: Int? = null,
    chatBubbleColorArgb: Int? = null,
    chatAccentColorArgb: Int? = null,
    chatWallpaperImageUri: String? = null,
    chatListThreeLine: Boolean = false,
    chatWallpaperGradient: String? = null,
) : ChatAppearanceStore {
    private val messageTextSizeSp = MutableStateFlow(messageTextSizeSp)
    private val bubbleCornerRadiusDp = MutableStateFlow(bubbleCornerRadiusDp)
    private val chatWallpaperId = MutableStateFlow(chatWallpaperId)
    private val chatWallpaperCustomColorArgb = MutableStateFlow(chatWallpaperCustomColorArgb)
    private val chatBubbleColorArgb = MutableStateFlow(chatBubbleColorArgb)
    private val chatAccentColorArgb = MutableStateFlow(chatAccentColorArgb)
    private val chatWallpaperImageUri = MutableStateFlow(chatWallpaperImageUri)
    private val chatListThreeLine = MutableStateFlow(chatListThreeLine)
    private val chatWallpaperGradient = MutableStateFlow(chatWallpaperGradient)

    override suspend fun setMessageTextSizeSp(value: Int) {
        messageTextSizeSp.value = value
    }

    override fun getMessageTextSizeSpFlow(): Flow<Int> = messageTextSizeSp

    override suspend fun setBubbleCornerRadiusDp(value: Int) {
        bubbleCornerRadiusDp.value = value
    }

    override fun getBubbleCornerRadiusDpFlow(): Flow<Int> = bubbleCornerRadiusDp

    override suspend fun setChatWallpaperId(id: String) {
        chatWallpaperId.value = id
    }

    override fun getChatWallpaperIdFlow(): Flow<String?> = chatWallpaperId

    override suspend fun setChatWallpaperCustomColorArgb(argb: Int) {
        chatWallpaperCustomColorArgb.value = argb
    }

    override fun getChatWallpaperCustomColorArgbFlow(): Flow<Int?> = chatWallpaperCustomColorArgb

    override suspend fun setChatBubbleColorArgb(argb: Int?) {
        chatBubbleColorArgb.value = argb
    }

    override fun getChatBubbleColorArgbFlow(): Flow<Int?> = chatBubbleColorArgb

    override suspend fun setChatAccentColorArgb(argb: Int?) {
        chatAccentColorArgb.value = argb
    }

    override fun getChatAccentColorArgbFlow(): Flow<Int?> = chatAccentColorArgb

    override suspend fun setChatWallpaperImageUri(uri: String?) {
        chatWallpaperImageUri.value = uri
    }

    override fun getChatWallpaperImageUriFlow(): Flow<String?> = chatWallpaperImageUri

    override suspend fun setChatListThreeLine(enabled: Boolean) {
        chatListThreeLine.value = enabled
    }

    override fun getChatListThreeLineFlow(): Flow<Boolean> = chatListThreeLine

    override suspend fun setChatWallpaperGradient(spec: String?) {
        chatWallpaperGradient.value = spec
    }

    override fun getChatWallpaperGradientFlow(): Flow<String?> = chatWallpaperGradient
}
