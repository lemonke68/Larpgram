/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 * Правка форка (Larpgram): Element removed this in v26.10 (split settings into media/, moderation/,
 * location/, root/, developer/appsettings/). Restored as fork-owned copy: our TgAdvancedSettingsView
 * and TG-категории (SettingsCategoryView) still need one combined state.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import io.element.android.features.preferences.impl.media.MediaOptimizationState
import io.element.android.features.preferences.impl.moderation.MediaPreviewConfigState
import io.element.android.features.preferences.impl.root.ThemeOption
import kotlinx.collections.immutable.ImmutableList

// ThemeOption → root/, MediaOptimizationState → media/, MediaPreviewConfigState → moderation/ (Element v26.10).
data class AdvancedSettingsState(
    val isDeveloperModeEnabled: Boolean,
    val isSharePresenceEnabled: Boolean,
    val mediaOptimizationState: MediaOptimizationState?,
    val theme: ThemeOption,
    val availableThemeOptions: ImmutableList<ThemeOption>,
    val mediaPreviewConfigState: MediaPreviewConfigState,
    val liveLocationMinimumDistanceUpdate: Int?,
    // Element v26.10 moved the blocked users count from the root state to ModerationAndSafetyState.
    val numberOfBlockedUsers: Int,
    val eventSink: (AdvancedSettingsEvent) -> Unit
) {
    val showBlockedUsersItem = numberOfBlockedUsers > 0
}
