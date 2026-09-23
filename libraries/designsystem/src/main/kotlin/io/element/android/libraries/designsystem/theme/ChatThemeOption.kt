/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Larpgram: catalog of ready-made chat themes, à la Telegram's "Настройки темы" grid. A theme is a
 * *coherent bundle* — a wallpaper paired with an outgoing-bubble color — so one tap sets both and
 * avoids the mismatched palette you get when only the wallpaper is tunable.
 *
 * A theme maps onto the two existing device-local prefs (chat wallpaper id + outgoing bubble color
 * argb); there is no separate "selected theme" pref. The picker highlights whichever theme matches
 * the current pair (see [matching]); tweaking either pref by hand simply stops matching → "custom".
 *
 * [bubbleColor] `null` means the themed default outgoing bubble (`messageFromMeBackground`).
 */
enum class ChatThemeOption(
    val id: String,
    val wallpaper: ChatWallpaperOption,
    val bubbleColor: Color?,
    val accentColor: Color?,
) {
    // Themed pattern + default blue bubble + brand accent — the out-of-the-box look.
    Default("default", ChatWallpaperOption.Pattern, null, null),
    Ocean("ocean", ChatWallpaperOption.Navy, Color(0xFF2E5C99), Color(0xFF3D82D6)),
    Grape("grape", ChatWallpaperOption.Lilac, Color(0xFF7B54C4), Color(0xFF7B54C4)),
    Sunset("sunset", ChatWallpaperOption.Sand, Color(0xFFC4703C), Color(0xFFC4703C)),
    Meadow("meadow", ChatWallpaperOption.Mint, Color(0xFF3B8F6B), Color(0xFF3B8F6B)),
    Steel("steel", ChatWallpaperOption.Slate, Color(0xFF3D6DB5), Color(0xFF3D6DB5)),
    Charcoal("charcoal", ChatWallpaperOption.Graphite, Color(0xFF6949A8), Color(0xFF9072CA)),
    Pine("pine", ChatWallpaperOption.Graphite, Color(0xFF2E7D5B), Color(0xFF43A375)),
    ;

    companion object {
        /** The theme whose (wallpaper, bubble, accent) triple matches the current selection, or null. */
        fun matching(wallpaperId: String?, bubbleColorArgb: Int?, accentColorArgb: Int?): ChatThemeOption? {
            val wp = wallpaperId ?: ChatWallpaperOption.DEFAULT.id
            return entries.firstOrNull {
                it.wallpaper.id == wp &&
                    it.bubbleColor?.toArgb() == bubbleColorArgb &&
                    it.accentColor?.toArgb() == accentColorArgb
            }
        }
    }
}
