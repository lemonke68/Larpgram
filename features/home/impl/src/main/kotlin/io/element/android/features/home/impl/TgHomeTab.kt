/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Home tabs of the V1 final redesign: «Чаты» and «Профиль». There is no Settings tab: the Profile
 * tab hosts the screen that merges the profile and the settings. Search is a separate round
 * button next to the tabs. Replaces Element's [HomeNavigationBarItem] (Chats/Spaces), which is
 * kept as upstream (аудит C-009); the selection lives in [TgHomeView].
 */
enum class TgHomeTab(
    @StringRes
    val labelRes: Int,
) {
    Chats(
        labelRes = R.string.screen_home_tab_chats
    ),
    Profile(
        labelRes = R.string.screen_home_tab_profile
    ),
}

/**
 * The Telegram-like speech bubble of the Chats tab (`tokens.json` → `icons.chatsTab`): 24 dp grid,
 * 1.9 stroke, round caps. Tinted by the caller.
 */
val TgChatsTabIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "TgChatsTab",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = addPathNodes(
            "M12 3.6c-4.9 0-8.8 3.4-8.8 7.6 0 2.4 1.2 4.5 3.2 5.9-.2 1.3-.8 2.5-1.8 3.3 2 .1 3.8-.6 5-1.6.8.2 1.6.3 2.4.3 " +
                "4.9 0 8.8-3.4 8.8-7.9S16.9 3.6 12 3.6z"
        ),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.9f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
}
