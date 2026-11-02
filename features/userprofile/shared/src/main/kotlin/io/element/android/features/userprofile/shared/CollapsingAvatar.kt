/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: аватар профиля, раскрывающийся тягой вниз (как в TG).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.userprofile.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Аватар шапки профиля.
 *
 * При [expandFraction] = 0 это обычный круг [AvatarSize.UserHeader]. Когда шапку тянут вниз,
 * fraction растёт к 1, и круг превращается в квадрат во всю ширину экрана (только сверху, не на
 * весь экран). Раскрываем только если есть картинка: квадрат из инициалов смысла не имеет,
 * поэтому без url показываем обычный кружок и игнорируем fraction.
 */
@Composable
fun CollapsingAvatar(
    avatarData: AvatarData,
    userName: String?,
    expandFraction: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatarType: AvatarType = AvatarType.User,
) {
    val hasImage = !avatarData.url.isNullOrBlank()
    if (!hasImage) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Avatar(
                avatarData = avatarData.copy(size = AvatarSize.UserHeader),
                avatarType = avatarType,
                contentDescription = stringResource(CommonStrings.a11y_user_avatar),
            )
        }
        return
    }

    val collapsedSize = AvatarSize.UserHeader.dp
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val fraction = expandFraction.coerceIn(0f, 1f)
    val size = lerp(collapsedSize, screenWidth, fraction)
    // Круг -> квадрат: радиус угла от половины стороны к нулю.
    val corner = lerp(collapsedSize / 2, 0.dp, fraction)
    // Из центра (bias 0) к левому краю (bias -1) по мере раскрытия.
    val alignment = BiasAlignment(horizontalBias = -fraction, verticalBias = 0f)

    Box(modifier.fillMaxWidth(), contentAlignment = alignment) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .clickable(enabled = fraction < 0.5f, onClick = onClick),
        ) {
            SubcomposeAsyncImage(
                model = avatarData.copy(size = AvatarSize.UserHeaderExpanded),
                contentDescription = stringResource(CommonStrings.a11y_user_avatar),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            ) {
                val painterState by painter.state.collectAsState()
                when (painterState) {
                    is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
                    else -> Avatar(
                        avatarData = avatarData.copy(size = AvatarSize.UserHeader),
                        avatarType = avatarType,
                        contentDescription = null,
                    )
                }
            }
            if (fraction > 0.5f && !userName.isNullOrBlank()) {
                val opacity = ((fraction - 0.5f) * 2f).coerceIn(0f, 1f)
                Text(
                    text = userName,
                    style = ElementTheme.typography.fontHeadingSmMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = opacity),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f * opacity))))
                        .padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 18.dp)
                        .clearAndSetSemantics { },
                )
            }
        }
    }
}
