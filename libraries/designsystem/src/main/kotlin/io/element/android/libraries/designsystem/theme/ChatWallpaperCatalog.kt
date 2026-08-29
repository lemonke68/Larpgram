/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Larpgram: catalog of selectable chat wallpapers, shared between the settings picker (preferences
 * module) and the timeline renderer (messages module) — neither can depend on the other, so the
 * selection model lives here in designsystem.
 *
 * A [solidColor] of null means "the themed default pattern": the messages module resolves that to
 * the tinted pattern drawable, which is theme-aware. Solids are fixed colors, independent of theme.
 * "Own photo" is a later sub-slice: it becomes another branch keyed by a stored URI, not an id here.
 */
enum class ChatWallpaperOption(val id: String, val solidColor: Color?) {
    Pattern("pattern", null),
    Graphite("graphite", Color(0xFF1E1E1E)),
    Navy("navy", Color(0xFF16232F)),
    Slate("slate", Color(0xFFC4D0DF)),
    Sand("sand", Color(0xFFE7DCCB)),
    Mint("mint", Color(0xFFCADFCF)),
    Lilac("lilac", Color(0xFFE2D3EA)),
    ;

    companion object {
        val DEFAULT = Pattern

        /**
         * Id for a user-chosen arbitrary color (the eyedropper). Not an enum entry: the actual
         * color lives in its own pref and reaches the timeline via [LocalChatWallpaperCustomColor].
         */
        const val CUSTOM_ID = "custom"

        fun fromId(id: String?): ChatWallpaperOption = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Selected wallpaper id, fed from AppPreferencesStore in ElementThemeApp and read by the timeline. */
val LocalChatWallpaperId = staticCompositionLocalOf { ChatWallpaperOption.DEFAULT.id }

/** The custom color chosen via the eyedropper; only meaningful when the selected id is [ChatWallpaperOption.CUSTOM_ID]. */
val LocalChatWallpaperCustomColor = staticCompositionLocalOf<Color?> { null }
