/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import im.vector.app.features.analytics.plan.Interaction
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.designsystem.components.dialogs.ListDialog
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.components.preferences.PreferenceCategory
import io.element.android.libraries.designsystem.components.preferences.PreferenceDropdown
import io.element.android.libraries.designsystem.components.preferences.PreferenceSwitch
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListSectionHeader
import io.element.android.libraries.designsystem.theme.components.ListSupportingText
import io.element.android.libraries.designsystem.theme.components.ListSupportingTextDefaults
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.media.MediaPreviewValue
import io.element.android.libraries.preferences.api.store.VideoCompressionPreset
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.services.analytics.compose.LocalAnalyticsService
import io.element.android.services.analyticsproviders.api.trackers.captureInteraction

/**
 * Секции экрана "Дополнительно", вынесенные для переиспользования: Ф2 раскладывает их по
 * TG-категориям (тема — в «Настройки чатов», presence и модерация — в «Конфиденциальность»,
 * загрузка медиа — в «Данные и память»), а сам экран Advanced собирает их все вместе.
 */

@Composable
fun ColumnScope.AppearanceThemeItem(state: AdvancedSettingsState) {
    PreferenceDropdown(
        title = stringResource(id = CommonStrings.common_appearance),
        selectedOption = state.theme,
        options = state.availableThemeOptions,
        onSelectOption = { themeOption ->
            state.eventSink(AdvancedSettingsEvents.SetTheme(themeOption))
        }
    )
}

@Composable
fun ColumnScope.SharePresenceItem(state: AdvancedSettingsState) {
    ListItem(
        headlineContent = {
            Text(text = stringResource(id = R.string.screen_advanced_settings_share_presence))
        },
        supportingContent = {
            Text(text = stringResource(id = R.string.screen_advanced_settings_share_presence_description))
        },
        trailingContent = ListItemContent.Switch(
            checked = state.isSharePresenceEnabled,
        ),
        onClick = { state.eventSink(AdvancedSettingsEvents.SetSharePresenceEnabled(!state.isSharePresenceEnabled)) }
    )
}

@Composable
fun ColumnScope.MediaUploadSection(state: AdvancedSettingsState) {
    val analyticsService = LocalAnalyticsService.current
    val compressImages = state.mediaOptimizationState?.shouldCompressImages

    when (state.mediaOptimizationState) {
        null -> Unit
        is MediaOptimizationState.AllMedia -> {
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.screen_advanced_settings_media_compression_title))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.screen_advanced_settings_media_compression_description))
                },
                trailingContent = ListItemContent.Switch(
                    checked = compressImages ?: false,
                ),
                onClick = {
                    val newValue = !(compressImages ?: false)
                    analyticsService.captureInteraction(
                        if (newValue) {
                            Interaction.Name.MobileSettingsOptimizeMediaUploadsEnabled
                        } else {
                            Interaction.Name.MobileSettingsOptimizeMediaUploadsDisabled
                        }
                    )
                    state.eventSink(AdvancedSettingsEvents.SetCompressMedia(newValue))
                }
            )
        }
        is MediaOptimizationState.Split -> {
            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.screen_advanced_settings_optimise_image_upload_quality_title))
                },
                supportingContent = {
                    Text(text = stringResource(id = R.string.screen_advanced_settings_optimise_image_upload_quality_description))
                },
                trailingContent = ListItemContent.Switch(
                    checked = compressImages ?: false,
                ),
                onClick = {
                    val newValue = !(compressImages ?: false)
                    analyticsService.captureInteraction(
                        if (newValue) {
                            Interaction.Name.MobileSettingsOptimizeMediaUploadsEnabled
                        } else {
                            Interaction.Name.MobileSettingsOptimizeMediaUploadsDisabled
                        }
                    )
                    state.eventSink(AdvancedSettingsEvents.SetCompressMedia(newValue))
                }
            )

            var displaySelectorDialog by remember { mutableStateOf(false) }

            ListItem(
                headlineContent = {
                    Text(text = stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_title))
                },
                supportingContent = {
                    val description = stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_description)
                    val quality = when (state.mediaOptimizationState.videoPreset) {
                        VideoCompressionPreset.LOW -> stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_low)
                        VideoCompressionPreset.STANDARD -> stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_standard)
                        VideoCompressionPreset.HIGH -> stringResource(id = R.string.screen_advanced_settings_optimise_video_upload_quality_high)
                    }
                    val descriptionWithValue = remember(quality) {
                        String.format(description, quality)
                    }
                    Text(text = descriptionWithValue)
                },
                onClick = { displaySelectorDialog = true },
            )

            if (displaySelectorDialog) {
                VideoQualitySelectorDialog(
                    selectedPreset = state.mediaOptimizationState.videoPreset,
                    onSubmit = { preset ->
                        state.eventSink(AdvancedSettingsEvents.SetVideoUploadQuality(preset))
                        displaySelectorDialog = false
                    },
                    onDismiss = { displaySelectorDialog = false },
                )
            }
        }
    }
}

@Composable
internal fun VideoQualitySelectorDialog(
    selectedPreset: VideoCompressionPreset,
    onSubmit: (VideoCompressionPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val videoPresets = VideoCompressionPreset.entries
    var localSelectedPreset by remember { mutableStateOf(selectedPreset) }
    ListDialog(
        title = stringResource(CommonStrings.dialog_video_quality_selector_title),
        subtitle = stringResource(CommonStrings.dialog_default_video_quality_selector_subtitle),
        onSubmit = { onSubmit(localSelectedPreset) },
        onDismissRequest = onDismiss,
        applyPaddingToContents = false,
    ) {
        for (preset in videoPresets) {
            val isSelected = preset == localSelectedPreset
            item(
                key = preset,
                contentType = preset,
            ) {
                val title = when (preset) {
                    VideoCompressionPreset.LOW -> stringResource(R.string.screen_advanced_settings_optimise_video_upload_quality_low)
                    VideoCompressionPreset.STANDARD -> stringResource(R.string.screen_advanced_settings_optimise_video_upload_quality_standard)
                    VideoCompressionPreset.HIGH -> stringResource(R.string.screen_advanced_settings_optimise_video_upload_quality_high)
                }
                val subtitle = when (preset) {
                    VideoCompressionPreset.LOW -> stringResource(CommonStrings.common_video_quality_low_description)
                    VideoCompressionPreset.STANDARD -> stringResource(CommonStrings.common_video_quality_standard_description)
                    VideoCompressionPreset.HIGH -> stringResource(CommonStrings.common_video_quality_high_description)
                }
                ListItem(
                    headlineContent = {
                        Text(
                            text = title,
                            style = ElementTheme.typography.fontBodyLgMedium,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = subtitle,
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                    },
                    leadingContent = ListItemContent.RadioButton(
                        selected = isSelected,
                    ),
                    onClick = {
                        localSelectedPreset = preset
                    },
                )
            }
        }
    }
}

@Composable
fun ModerationAndSafetySection(
    state: AdvancedSettingsState,
) {
    PreferenceCategory(
        title = stringResource(R.string.screen_advanced_settings_moderation_and_safety_section_title),
        showTopDivider = true
    ) {
        PreferenceSwitch(
            title = stringResource(R.string.screen_advanced_settings_hide_invite_avatars_toggle_title),
            isChecked = state.mediaPreviewConfigState.hideInviteAvatars,
            onCheckedChange = {
                state.eventSink(AdvancedSettingsEvents.SetHideInviteAvatars(it))
            },
            enabled = !state.mediaPreviewConfigState.setHideInviteAvatarsAction.isLoading()
        )
        ListSectionHeader(
            title = stringResource(R.string.screen_advanced_settings_show_media_timeline_title),
            hasDivider = false,
            description = {
                ListSupportingText(
                    text = stringResource(R.string.screen_advanced_settings_show_media_timeline_subtitle),
                    contentPadding = ListSupportingTextDefaults.Padding.None,
                )
            }
        )
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.screen_advanced_settings_show_media_timeline_always_hide)) },
            leadingContent = ListItemContent.RadioButton(
                selected = state.mediaPreviewConfigState.timelineMediaPreviewValue == MediaPreviewValue.Off,
                compact = true
            ),
            onClick = {
                state.eventSink(AdvancedSettingsEvents.SetTimelineMediaPreviewValue(MediaPreviewValue.Off))
            },
            enabled = !state.mediaPreviewConfigState.setTimelineMediaPreviewAction.isLoading()
        )
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.screen_advanced_settings_show_media_timeline_private_rooms)) },
            leadingContent = ListItemContent.RadioButton(
                selected = state.mediaPreviewConfigState.timelineMediaPreviewValue == MediaPreviewValue.Private,
                compact = true
            ),
            onClick = {
                state.eventSink(AdvancedSettingsEvents.SetTimelineMediaPreviewValue(MediaPreviewValue.Private))
            },
            enabled = !state.mediaPreviewConfigState.setTimelineMediaPreviewAction.isLoading()
        )
        ListItem(
            headlineContent = { Text(text = stringResource(R.string.screen_advanced_settings_show_media_timeline_always_show)) },
            leadingContent = ListItemContent.RadioButton(
                selected = state.mediaPreviewConfigState.timelineMediaPreviewValue == MediaPreviewValue.On,
                compact = true
            ),
            onClick = {
                state.eventSink(AdvancedSettingsEvents.SetTimelineMediaPreviewValue(MediaPreviewValue.On))
            },
            enabled = !state.mediaPreviewConfigState.setTimelineMediaPreviewAction.isLoading()
        )
    }
}
