/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.featureflag.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.element.android.libraries.featureflag.ui.model.FeatureUiModel

/**
 * Правка форка: названия флагов в «Для разработчиков» по-человечески и по-русски. Сами флаги
 * (`FeatureFlags`) хранят английский текст строками в коде, а не ресурсами.
 */
private val featureStrings = mapOf(
    "feature.showBlockedUsersDetails" to
        (R.string.larpgram_feature_showBlockedUsersDetails_title to R.string.larpgram_feature_showBlockedUsersDetails_description),
    "feature.syncOnPush" to (R.string.larpgram_feature_syncOnPush_title to R.string.larpgram_feature_syncOnPush_description),
    "feature.onlySignedDeviceIsolationMode" to (R.string.larpgram_feature_onlySignedDevices_title to R.string.larpgram_feature_onlySignedDevices_description),
    "feature.print_logs_to_logcat" to (R.string.larpgram_feature_logcat_title to R.string.larpgram_feature_logcat_description),
    "feature.selectable_media_quality" to (R.string.larpgram_feature_mediaQuality_title to R.string.larpgram_feature_mediaQuality_description),
    "feature.thread_timeline" to (R.string.larpgram_feature_threads_title to R.string.larpgram_feature_threads_description),
    "feature.multi_account" to (R.string.larpgram_feature_multiAccount_title to R.string.larpgram_feature_multiAccount_description),
    "feature.qr_code_login" to (R.string.larpgram_feature_qrLogin_title to R.string.larpgram_feature_qrLogin_description),
    "feature.allow_black_theme" to (R.string.larpgram_feature_blackTheme_title to R.string.larpgram_feature_blackTheme_description),
    "feature.validate_network_when_scheduling_notification_fetching" to
        (R.string.larpgram_feature_validateNetwork_title to R.string.larpgram_feature_validateNetwork_description),
    "feature.jump_to_unread" to (R.string.larpgram_feature_jumpToUnread_title to R.string.larpgram_feature_jumpToUnread_description),
    "feature.slash_command" to (R.string.larpgram_feature_slashCommands_title to R.string.larpgram_feature_slashCommands_description),
    "feature.room_thread_list" to (R.string.larpgram_feature_threadList_title to R.string.larpgram_feature_threadList_description),
    "feature.automatic_back_pagination" to (R.string.larpgram_feature_backPagination_title to R.string.larpgram_feature_backPagination_description),
    "feature.unread_indicator_count" to (R.string.larpgram_feature_unreadCount_title to R.string.larpgram_feature_unreadCount_description),
    "feature.send_gallery_messages" to (R.string.larpgram_feature_gallery_title to R.string.larpgram_feature_gallery_description),
    "feature.knock" to (R.string.larpgram_feature_knock_title to R.string.larpgram_feature_knock_description),
    "feature.message_search" to (R.string.larpgram_feature_messageSearch_title to R.string.larpgram_feature_messageSearch_description),
)

@Composable
internal fun FeatureUiModel.localizedTitle(): String =
    featureStrings[key]?.let { stringResource(it.first) } ?: title

@Composable
internal fun FeatureUiModel.localizedDescription(): String? =
    featureStrings[key]?.let { stringResource(it.second) } ?: description
