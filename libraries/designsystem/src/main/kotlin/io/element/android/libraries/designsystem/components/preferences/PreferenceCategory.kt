/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementThemedPreview
import io.element.android.libraries.designsystem.preview.PreviewGroup
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * Правка форка: секция настроек — карточка TG 12 (скруглённый блок на приглушённом фоне,
 * заголовок цветом акцента внутри карточки), как `TgSettingsGroup` на корне настроек.
 * [showTopDivider] больше не рисует разделитель: секции разделяет зазор между карточками.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun PreferenceCategory(
    modifier: Modifier = Modifier,
    title: String? = null,
    showTopDivider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(vertical = 4.dp)
    ) {
        if (title != null) {
            Text(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 2.dp),
                text = title,
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textActionAccent,
            )
        }
        content()
    }
}

@Preview(group = PreviewGroup.Preferences)
@Composable
internal fun PreferenceCategoryPreview() = ElementThemedPreview {
    PreferenceCategory(
        title = "Category title",
    ) {
        PreferenceSwitch(
            title = "Switch",
            icon = CompoundIcons.Threads(),
            isChecked = true,
            onCheckedChange = {},
        )
        PreferenceSlide(
            title = "Slide",
            summary = "Summary",
            value = 0.75F,
            showIconAreaIfNoIcon = true,
            onValueChange = {},
        )
    }
}
