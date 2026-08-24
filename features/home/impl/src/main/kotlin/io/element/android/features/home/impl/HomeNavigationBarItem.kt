/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import io.element.android.compound.tokens.generated.CompoundIcons

enum class HomeNavigationBarItem(
    @StringRes
    val labelRes: Int,
) {
    Chats(
        labelRes = R.string.screen_home_tab_chats
    ),
    Settings(
        labelRes = R.string.screen_home_tab_settings
    ),
    Profile(
        labelRes = R.string.screen_home_tab_profile
    );

    @Composable
    fun icon(
        isSelected: Boolean,
    ) = when (this) {
        Chats -> if (isSelected) CompoundIcons.ChatSolid() else CompoundIcons.Chat()
        Settings -> if (isSelected) CompoundIcons.SettingsSolid() else CompoundIcons.Settings()
        Profile -> if (isSelected) CompoundIcons.UserProfileSolid() else CompoundIcons.UserProfile()
    }

    companion object {
        fun from(index: Int): HomeNavigationBarItem {
            return entries.getOrElse(index) { Chats }
        }
    }
}
