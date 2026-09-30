/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.impl.store

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.preferences.api.store.ChatAppearanceStore
import io.element.android.libraries.preferences.api.store.DEFAULT_BUBBLE_CORNER_RADIUS_DP
import io.element.android.libraries.preferences.api.store.DEFAULT_MESSAGE_TEXT_SIZE_SP
import io.element.android.libraries.preferences.api.store.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val messageTextSizeSpKey = intPreferencesKey("larpgramMessageTextSizeSp")
private val bubbleCornerRadiusDpKey = intPreferencesKey("larpgramBubbleCornerRadiusDp")
private val chatWallpaperIdKey = stringPreferencesKey("larpgramChatWallpaperId")
private val chatWallpaperCustomColorKey = intPreferencesKey("larpgramChatWallpaperCustomColor")
private val chatBubbleColorKey = intPreferencesKey("larpgramChatBubbleColor")
private val chatAccentColorKey = intPreferencesKey("larpgramChatAccentColor")
private val chatWallpaperImageUriKey = stringPreferencesKey("larpgramChatWallpaperImageUri")
private val chatListThreeLineKey = booleanPreferencesKey("larpgramChatListThreeLine")
private val chatWallpaperGradientKey = stringPreferencesKey("larpgramChatWallpaperGradient")

/**
 * Keeps the keys in Element's "elementx_preferences" file, where they lived before the store was split out,
 * so existing settings survive the update. The factory hands out one DataStore per name, so sharing is safe.
 */
@ContributesBinding(AppScope::class)
class DefaultChatAppearanceStore(
    preferenceDataStoreFactory: PreferenceDataStoreFactory,
) : ChatAppearanceStore {
    private val store = preferenceDataStoreFactory.create("elementx_preferences")

    override suspend fun setMessageTextSizeSp(value: Int) = set(messageTextSizeSpKey, value)

    override fun getMessageTextSizeSpFlow(): Flow<Int> = get(messageTextSizeSpKey).map { it ?: DEFAULT_MESSAGE_TEXT_SIZE_SP }

    override suspend fun setBubbleCornerRadiusDp(value: Int) = set(bubbleCornerRadiusDpKey, value)

    override fun getBubbleCornerRadiusDpFlow(): Flow<Int> = get(bubbleCornerRadiusDpKey).map { it ?: DEFAULT_BUBBLE_CORNER_RADIUS_DP }

    override suspend fun setChatWallpaperId(id: String) = set(chatWallpaperIdKey, id)

    override fun getChatWallpaperIdFlow(): Flow<String?> = get(chatWallpaperIdKey)

    override suspend fun setChatWallpaperCustomColorArgb(argb: Int) = set(chatWallpaperCustomColorKey, argb)

    override fun getChatWallpaperCustomColorArgbFlow(): Flow<Int?> = get(chatWallpaperCustomColorKey)

    override suspend fun setChatBubbleColorArgb(argb: Int?) = set(chatBubbleColorKey, argb)

    override fun getChatBubbleColorArgbFlow(): Flow<Int?> = get(chatBubbleColorKey)

    override suspend fun setChatAccentColorArgb(argb: Int?) = set(chatAccentColorKey, argb)

    override fun getChatAccentColorArgbFlow(): Flow<Int?> = get(chatAccentColorKey)

    override suspend fun setChatWallpaperImageUri(uri: String?) = set(chatWallpaperImageUriKey, uri)

    override fun getChatWallpaperImageUriFlow(): Flow<String?> = get(chatWallpaperImageUriKey)

    override suspend fun setChatListThreeLine(enabled: Boolean) = set(chatListThreeLineKey, enabled)

    override fun getChatListThreeLineFlow(): Flow<Boolean> = get(chatListThreeLineKey).map { it ?: false }

    override suspend fun setChatWallpaperGradient(spec: String?) = set(chatWallpaperGradientKey, spec)

    override fun getChatWallpaperGradientFlow(): Flow<String?> = get(chatWallpaperGradientKey)

    /** A null value removes the key, which brings the setting back to its default. */
    private suspend fun <T> set(key: Preferences.Key<T>, value: T?) {
        store.edit { prefs ->
            if (value != null) {
                prefs[key] = value
            } else {
                prefs.remove(key)
            }
        }
    }

    private fun <T> get(key: Preferences.Key<T>): Flow<T?> = store.data.map { prefs -> prefs[key] }
}
