/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.compound.tokens

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.element.android.compound.R

/**
 * Larpgram fonts (redesign V1 final, `design/redesign/handoff/tokens.json` → `fonts`).
 *
 * Both are OFL (licenses in `libraries/compound/fonts-licenses/`). The files are static instances
 * of the Google Fonts variable fonts, subset to Latin + Cyrillic.
 */
object LarpgramFonts {
    /** Manrope: every text in the app. */
    val text: FontFamily = FontFamily(
        Font(R.font.manrope_regular, FontWeight.Normal),
        Font(R.font.manrope_medium, FontWeight.Medium),
        Font(R.font.manrope_semibold, FontWeight.SemiBold),
        Font(R.font.manrope_bold, FontWeight.Bold),
        Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
    )

    /** Unbounded: the `larpgram` wordmark, screen titles and the own name in the profile. */
    val display: FontFamily = FontFamily(
        Font(R.font.unbounded_semibold, FontWeight.SemiBold),
        Font(R.font.unbounded_bold, FontWeight.Bold),
    )
}

/** Text styles from `tokens.json` → `type` that have no Compound equivalent. */
object LarpgramType {
    val wordmark = TextStyle(fontFamily = LarpgramFonts.display, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.6 / 22).em)
    val screenTitle = TextStyle(fontFamily = LarpgramFonts.display, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, letterSpacing = (-0.4 / 23).em)
    val profileName = TextStyle(fontFamily = LarpgramFonts.display, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, letterSpacing = (-0.4 / 21).em)
    val chatRowName = TextStyle(fontFamily = LarpgramFonts.text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 21.sp)
    val chatRowPreview = TextStyle(fontFamily = LarpgramFonts.text, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp)
    val chatRowTime = TextStyle(fontFamily = LarpgramFonts.text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    val headerTitle = TextStyle(fontFamily = LarpgramFonts.text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, lineHeight = 20.sp)
    val headerSubtitle = TextStyle(fontFamily = LarpgramFonts.text, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 15.sp)
    val message = TextStyle(fontFamily = LarpgramFonts.text, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp)
    val messageSender = TextStyle(fontFamily = LarpgramFonts.text, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    val messageTime = TextStyle(fontFamily = LarpgramFonts.text, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    val tabLabel = TextStyle(fontFamily = LarpgramFonts.text, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, lineHeight = 14.sp)
    val folderChip = TextStyle(fontFamily = LarpgramFonts.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    val listItem = TextStyle(fontFamily = LarpgramFonts.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    val caption = TextStyle(fontFamily = LarpgramFonts.text, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    val badge = TextStyle(fontFamily = LarpgramFonts.text, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
    val field = TextStyle(fontFamily = LarpgramFonts.text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    val fieldLabel = TextStyle(fontFamily = LarpgramFonts.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
}
