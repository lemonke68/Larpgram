/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components.avatar.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.libraries.designsystem.colors.TgAvatarPalette
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewGroup
import io.element.android.libraries.designsystem.text.toSp
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * Правка форка: заглушка аватара как в Telegram (`AvatarDrawable`): вертикальный градиент одной из
 * семи пар цветов по id и белые инициалы (до двух букв) среднего начертания.
 *
 * Размер букв — доля размера аватара: в TG 18dp на аватаре списка чатов (≈52dp) и 12dp на мелких
 * (≈36dp), то есть около 0.34.
 */
@Composable
internal fun TgGradientAvatar(
    id: String,
    text: String,
    size: Dp,
    avatarShape: Shape,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val gradient = TgAvatarPalette.gradient(id)
    Box(
        modifier
            .size(size)
            .clip(avatarShape)
            .background(Brush.verticalGradient(listOf(gradient.top, gradient.bottom)))
    ) {
        val fontSize = (size * TEXT_SIZE_RATIO).toSp()
        Text(
            modifier = Modifier
                .clearAndSetSemantics {
                    contentDescription?.let {
                        this.contentDescription = it
                    }
                }
                .align(Alignment.Center),
            text = text,
            style = TextStyle(
                fontSize = fontSize,
                lineHeight = fontSize,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
            color = Color.White,
            maxLines = 1,
        )
    }
}

private const val TEXT_SIZE_RATIO = 0.34f

@Preview(group = PreviewGroup.Avatars)
@Composable
internal fun TgGradientAvatarPreview() = ElementPreview {
    Row(
        modifier = Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("Иван Петров", "lin", "server-alerts", "test-channel — comments", "Новости", "farting", "Pavel Durov")
            .forEachIndexed { index, name ->
                val data = AvatarData(id = "$index", name = name, size = AvatarSize.RoomListItem)
                TgGradientAvatar(
                    id = data.id,
                    text = data.initials,
                    size = 52.dp,
                    avatarShape = CircleShape,
                    contentDescription = null,
                )
            }
    }
}
