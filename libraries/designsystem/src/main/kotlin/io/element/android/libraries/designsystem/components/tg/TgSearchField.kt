/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.tg

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun TgSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        singleLine = true,
        textStyle = ElementTheme.typography.fontBodyMdRegular.copy(color = ElementTheme.colors.textPrimary),
        cursorBrush = SolidColor(ElementTheme.colors.textActionAccent),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = CompoundIcons.Search(),
                    contentDescription = null,
                    tint = ElementTheme.colors.iconSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Box(Modifier.weight(1f).padding(start = 8.dp)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        imageVector = CompoundIcons.Close(),
                        contentDescription = stringResource(CommonStrings.action_clear),
                        tint = ElementTheme.colors.iconSecondary,
                        modifier = Modifier.size(24.dp).clickable { onValueChange("") },
                    )
                }
            }
        },
    )
}
