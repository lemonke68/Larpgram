/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import io.element.android.compound.theme.Theme
import io.element.android.compound.theme.mapToTheme
import io.element.android.libraries.architecture.Presenter
import androidx.compose.ui.graphics.toArgb
import io.element.android.libraries.designsystem.theme.ChatThemeOption
import io.element.android.libraries.designsystem.theme.ChatWallpaperOption
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.preferences.api.store.AppPreferencesStore
import io.element.android.libraries.preferences.api.store.DEFAULT_BUBBLE_CORNER_RADIUS_DP
import io.element.android.libraries.preferences.api.store.DEFAULT_MESSAGE_TEXT_SIZE_SP
import io.element.android.libraries.preferences.api.store.SessionPreferencesStore
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Inject
class AdvancedSettingsPresenter(
    private val appPreferencesStore: AppPreferencesStore,
    private val sessionPreferencesStore: SessionPreferencesStore,
    private val mediaPreviewConfigStateStore: MediaPreviewConfigStateStore,
    @SessionCoroutineScope
    private val sessionCoroutineScope: CoroutineScope,
    private val featureFlagService: FeatureFlagService,
) : Presenter<AdvancedSettingsState> {
    @Composable
    override fun present(): AdvancedSettingsState {
        val isDeveloperModeEnabled by remember {
            appPreferencesStore.isDeveloperModeEnabledFlow()
        }.collectAsState(initial = false)
        val isSharePresenceEnabled by remember {
            sessionPreferencesStore.isSharePresenceEnabled()
        }.collectAsState(initial = true)
        val isBlackThemeAllowed by remember {
            featureFlagService.isFeatureEnabledFlow(FeatureFlags.AllowBlackTheme)
        }.collectAsState(initial = false)
        val theme = remember(isBlackThemeAllowed) {
            appPreferencesStore.getThemeFlow().mapToTheme(isBlackThemeAllowed)
        }.collectAsState(initial = Theme.System)

        val liveLocationMinimumDistanceUpdate by produceState<Int?>(null) {
            appPreferencesStore.getLiveLocationMinimumDistanceInMetersUpdateFlow().collect { value = it }
        }

        // Larpgram: chat appearance customization.
        val messageTextSizeSp by remember {
            appPreferencesStore.getMessageTextSizeSpFlow()
        }.collectAsState(initial = DEFAULT_MESSAGE_TEXT_SIZE_SP)
        val bubbleCornerRadiusDp by remember {
            appPreferencesStore.getBubbleCornerRadiusDpFlow()
        }.collectAsState(initial = DEFAULT_BUBBLE_CORNER_RADIUS_DP)
        val chatWallpaperId by remember {
            appPreferencesStore.getChatWallpaperIdFlow()
        }.collectAsState(initial = null)
        val chatWallpaperCustomColorArgb by remember {
            appPreferencesStore.getChatWallpaperCustomColorArgbFlow()
        }.collectAsState(initial = null)
        val chatBubbleColorArgb by remember {
            appPreferencesStore.getChatBubbleColorArgbFlow()
        }.collectAsState(initial = null)
        val chatAccentColorArgb by remember {
            appPreferencesStore.getChatAccentColorArgbFlow()
        }.collectAsState(initial = null)
        val chatWallpaperImageUri by remember {
            appPreferencesStore.getChatWallpaperImageUriFlow()
        }.collectAsState(initial = null)
        val chatListThreeLine by remember {
            appPreferencesStore.getChatListThreeLineFlow()
        }.collectAsState(initial = false)
        val chatWallpaperGradientSpec by remember {
            appPreferencesStore.getChatWallpaperGradientFlow()
        }.collectAsState(initial = null)

        val mediaPreviewConfigState = mediaPreviewConfigStateStore.state()

        val themeOption by remember {
            derivedStateOf {
                when (theme.value) {
                    Theme.System -> ThemeOption.System
                    Theme.Dark -> ThemeOption.Dark
                    Theme.Black -> ThemeOption.Black
                    Theme.Light -> ThemeOption.Light
                }
            }
        }

        val hasSplitMediaQualityOptions by produceState<Boolean?>(null) {
            value = featureFlagService.isFeatureEnabled(FeatureFlags.SelectableMediaQuality)
        }

        val availableThemeOptions = remember(isBlackThemeAllowed) {
            if (isBlackThemeAllowed) {
                ThemeOption.entries
            } else {
                ThemeOption.entries.filterNot { it == ThemeOption.Black }
            }.toImmutableList()
        }

        val mediaOptimizationState by produceState<MediaOptimizationState?>(null) {
            val hasSplitMediaQualityOptionsFlow = featureFlagService.isFeatureEnabledFlow(FeatureFlags.SelectableMediaQuality)
            combine(
                hasSplitMediaQualityOptionsFlow,
                sessionPreferencesStore.doesOptimizeImages(),
                sessionPreferencesStore.getVideoCompressionPreset()
            ) { hasSplitOptions, compressImages, videoPreset ->
                if (hasSplitMediaQualityOptions == true) {
                    value = MediaOptimizationState.Split(
                        compressImages = compressImages,
                        videoPreset = videoPreset,
                    )
                } else if (hasSplitMediaQualityOptions == false) {
                    value = MediaOptimizationState.AllMedia(isEnabled = compressImages)
                }
            }.collect()
        }

        fun handleEvent(event: AdvancedSettingsEvents) {
            when (event) {
                is AdvancedSettingsEvents.SetDeveloperModeEnabled -> sessionCoroutineScope.launch {
                    appPreferencesStore.setDeveloperModeEnabled(event.enabled)
                }
                is AdvancedSettingsEvents.SetSharePresenceEnabled -> sessionCoroutineScope.launch {
                    sessionPreferencesStore.setSharePresence(event.enabled)
                }
                is AdvancedSettingsEvents.SetCompressMedia -> sessionCoroutineScope.launch {
                    sessionPreferencesStore.setOptimizeImages(event.compress)
                }
                is AdvancedSettingsEvents.SetTheme -> sessionCoroutineScope.launch {
                    when (event.theme) {
                        ThemeOption.System -> appPreferencesStore.setTheme(Theme.System.name)
                        ThemeOption.Dark -> appPreferencesStore.setTheme(Theme.Dark.name)
                        ThemeOption.Black -> appPreferencesStore.setTheme(Theme.Black.name)
                        ThemeOption.Light -> appPreferencesStore.setTheme(Theme.Light.name)
                    }
                }
                is AdvancedSettingsEvents.SetHideInviteAvatars -> mediaPreviewConfigStateStore.setHideInviteAvatars(event.value)
                is AdvancedSettingsEvents.SetTimelineMediaPreviewValue -> mediaPreviewConfigStateStore.setTimelineMediaPreviewValue(event.value)
                is AdvancedSettingsEvents.SetLiveLocationMinimumDistanceUpdate -> sessionCoroutineScope.launch {
                    appPreferencesStore.setLiveLocationMinimumDistanceInMetersUpdate(event.value)
                }
                is AdvancedSettingsEvents.SetMessageTextSize -> sessionCoroutineScope.launch {
                    appPreferencesStore.setMessageTextSizeSp(event.sizeSp)
                }
                is AdvancedSettingsEvents.SetBubbleCornerRadius -> sessionCoroutineScope.launch {
                    appPreferencesStore.setBubbleCornerRadiusDp(event.radiusDp)
                }
                is AdvancedSettingsEvents.SetChatWallpaper -> sessionCoroutineScope.launch {
                    appPreferencesStore.setChatWallpaperId(event.id)
                }
                is AdvancedSettingsEvents.SetChatWallpaperCustomColor -> sessionCoroutineScope.launch {
                    // Сохраняем цвет и переключаем выбор на кастомный маркер-id.
                    appPreferencesStore.setChatWallpaperCustomColorArgb(event.argb)
                    appPreferencesStore.setChatWallpaperId(ChatWallpaperOption.CUSTOM_ID)
                }
                is AdvancedSettingsEvents.SetChatBubbleColor -> sessionCoroutineScope.launch {
                    appPreferencesStore.setChatBubbleColorArgb(event.argb)
                }
                is AdvancedSettingsEvents.SetChatAccentColor -> sessionCoroutineScope.launch {
                    appPreferencesStore.setChatAccentColorArgb(event.argb)
                }
                is AdvancedSettingsEvents.SetChatListThreeLine -> sessionCoroutineScope.launch {
                    appPreferencesStore.setChatListThreeLine(event.enabled)
                }
                is AdvancedSettingsEvents.SetChatWallpaperGradient -> sessionCoroutineScope.launch {
                    // Градиент задан: сохраняем спеку и переводим id обоев на «градиент». Сброс (null)
                    // — очищаем и возвращаем паттерн.
                    appPreferencesStore.setChatWallpaperGradient(event.spec)
                    appPreferencesStore.setChatWallpaperId(
                        if (event.spec != null) ChatWallpaperOption.CUSTOM_GRADIENT_ID else ChatWallpaperOption.DEFAULT.id
                    )
                }
                is AdvancedSettingsEvents.SetChatWallpaperImage -> sessionCoroutineScope.launch {
                    // Фото выбрано: сохраняем URI и переводим маркер обоев на «фото». Сброс (null) —
                    // очищаем URI и возвращаем обои к дефолтному паттерну.
                    appPreferencesStore.setChatWallpaperImageUri(event.uri)
                    appPreferencesStore.setChatWallpaperId(
                        if (event.uri != null) ChatWallpaperOption.CUSTOM_IMAGE_ID else ChatWallpaperOption.DEFAULT.id
                    )
                }
                is AdvancedSettingsEvents.ApplyChatTheme -> sessionCoroutineScope.launch {
                    // Пресет = связка: ставим обои, цвет пузыря и акцент разом, палитра согласована.
                    val theme = ChatThemeOption.entries.first { it.id == event.themeId }
                    appPreferencesStore.setChatWallpaperId(theme.wallpaper.id)
                    appPreferencesStore.setChatBubbleColorArgb(theme.bubbleColor?.toArgb())
                    appPreferencesStore.setChatAccentColorArgb(theme.accentColor?.toArgb())
                }
                is AdvancedSettingsEvents.SetCompressImages -> sessionCoroutineScope.launch {
                    sessionPreferencesStore.setOptimizeImages(event.compress)
                }
                is AdvancedSettingsEvents.SetVideoUploadQuality -> sessionCoroutineScope.launch {
                    sessionPreferencesStore.setVideoCompressionPreset(event.videoPreset)
                }
            }
        }

        return AdvancedSettingsState(
            isDeveloperModeEnabled = isDeveloperModeEnabled,
            isSharePresenceEnabled = isSharePresenceEnabled,
            mediaOptimizationState = mediaOptimizationState,
            theme = themeOption,
            availableThemeOptions = availableThemeOptions,
            mediaPreviewConfigState = mediaPreviewConfigState,
            liveLocationMinimumDistanceUpdate = liveLocationMinimumDistanceUpdate,
            messageTextSizeSp = messageTextSizeSp,
            bubbleCornerRadiusDp = bubbleCornerRadiusDp,
            chatWallpaperId = chatWallpaperId ?: ChatWallpaperOption.DEFAULT.id,
            chatWallpaperCustomColorArgb = chatWallpaperCustomColorArgb,
            chatBubbleColorArgb = chatBubbleColorArgb,
            chatAccentColorArgb = chatAccentColorArgb,
            chatWallpaperImageUri = chatWallpaperImageUri,
            chatWallpaperGradientSpec = chatWallpaperGradientSpec,
            chatListThreeLine = chatListThreeLine,
            eventSink = ::handleEvent,
        )
    }
}
