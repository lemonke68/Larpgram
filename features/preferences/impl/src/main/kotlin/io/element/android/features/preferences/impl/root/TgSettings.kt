/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.atomic.atoms.RoundedIconAtom
import io.element.android.libraries.designsystem.atomic.atoms.RoundedIconAtomSize
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.matrix.ui.model.getBestName
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * TG-стиль настроек (форк Larpgram): цветные скруглённые иконки, подзаголовки,
 * строки сгруппированы в скруглённые карточки на приглушённом фоне.
 *
 * Ф1 — только хром: пункты и навигация остаются элементовскими, меняется подача.
 * Задел под реструктуризацию в TG-категории — отдельным эпиком.
 */
object TgSettingsColors {
    val Blue = Color(0xFF3478F6)
    val Cyan = Color(0xFF29B6D8)
    val Teal = Color(0xFF37AEA0)
    val Green = Color(0xFF4CB050)
    val Orange = Color(0xFFF3A33B)
    val Red = Color(0xFFEB5545)
    val Purple = Color(0xFF8E64E8)
    val Gray = Color(0xFF8A8A90)
}

/**
 * Скруглённая карточка-группа: соседние [TgSettingsItem] лежат внутри одной карточки,
 * между карточками — вертикальный зазор. Аналог секций в TG-настройках.
 */
@Composable
fun ColumnScope.TgSettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
            .padding(vertical = 4.dp),
        content = content,
    )
}

/**
 * Строка настроек в TG-стиле: цветная скруглённая иконка + заголовок + необязательный подзаголовок.
 * Контейнер [ListItem] прозрачный, поэтому сквозь него виден фон карточки.
 */
@Composable
fun TgSettingsItem(
    title: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconVector: ImageVector? = null,
    iconRes: Int? = null,
    subtitle: String? = null,
    trailingContent: ListItemContent? = null,
    style: ListItemStyle = ListItemStyle.Default,
) {
    ListItem(
        modifier = modifier,
        content = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = ListItemContent.Custom {
            RoundedIconAtom(
                size = RoundedIconAtomSize.Medium,
                imageVector = iconVector,
                resourceId = iconRes,
                tint = Color.White,
                backgroundTint = color,
            )
        },
        trailingContent = trailingContent,
        style = style,
        onClick = onClick,
    )
}

/**
 * Шапка корня настроек как в TG (`SettingsActivity`): крупный аватар по центру с кнопкой камеры,
 * имя и @имя пользователя. Тап — в редактирование профиля.
 */
@Composable
fun TgSettingsProfileHeader(
    matrixUser: MatrixUser,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(AvatarSize.UserHeader.dp)) {
            Avatar(
                avatarData = matrixUser.getAvatarData(size = AvatarSize.UserHeader),
                avatarType = AvatarType.User,
                contentDescription = stringResource(CommonStrings.a11y_user_avatar),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(ElementTheme.colors.bgCanvasDefault)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(ElementTheme.colors.iconSecondary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(18.dp),
                    imageVector = CompoundIcons.TakePhotoSolid(),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = matrixUser.getBestName(),
            style = ElementTheme.typography.fontHeadingSmMedium.copy(fontWeight = FontWeight.SemiBold),
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = matrixUser.userId.value.substringBefore(":"),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}
