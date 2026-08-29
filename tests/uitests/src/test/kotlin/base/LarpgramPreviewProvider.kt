/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:Suppress("DEPRECATION")

package base

import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import sergio.sastre.composable.preview.scanner.android.AndroidPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview

/**
 * The previews worth looking at while reworking the design.
 *
 * Rendering all 3150 snapshots takes minutes; this shortlist takes seconds, which is what makes
 * design iteration without a phone bearable. Add a name here when a new screen is being reworked.
 */
private val DESIGN_PREVIEWS = setOf(
    "ColorAliasesPreview",
    "TelegramBubbleShapePreview",
    "MessageDeliveryTicksPreview",
    "TimelineEventTimestampViewPreview",
    "MessageEventBubblePreview",
    "TimelineItemEventRowPreview",
    "RoomSummaryRowPreview",
    "SwipeableRoomListRowRevealedPreview",
    "UnreadIndicatorAtomPreview",
    "CounterAtomPreview",
    "TextComposerSimplePreview",
    "RoomListContentViewPreview",
    "TimelineItemDaySeparatorViewPreview",
    "TimelineViewPreview",
    // Chats-list chrome redesign (2026-08-20): folder pills + bottom nav.
    "HomeViewPreview",
    "HomeTopBarPreview",
    "SpaceFolderPillsViewPreview",
    // Self-profile TG redesign (2026-08-25): 3-button row + info card + bio.
    "UserProfileViewPreview",
    "EditUserProfileViewPreview",
    // Chat customization (2026-08-29): message text size + bubble corner radius, wallpaper, color picker.
    "ChatAppearanceSectionPreview",
    "ChatWallpaperColorPickerDialogPreview",
)

object LarpgramPreviewProvider : TestParameterValuesProvider() {
    override fun provideValues(context: Context): List<ComposablePreview<AndroidPreviewInfo>> =
        ComposablePreviewProvider.values
            .map { it.value }
            .filter { it.methodName in DESIGN_PREVIEWS }
}
