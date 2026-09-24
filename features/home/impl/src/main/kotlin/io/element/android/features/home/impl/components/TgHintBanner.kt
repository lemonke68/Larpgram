/*
 * Правка форка: подсказка над списком чатов как `DialogsHintCell` в Telegram — строка во всю
 * ширину с жирным заголовком 14, серым текстом 13 и крестиком справа; тап по строке — действие.
 * Вместо элементовской карточки-объявления с большой кнопкой на треть экрана.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
internal fun TgHintBanner(
    title: String,
    message: String,
    onClick: () -> Unit,
    onDismissClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, end = 4.dp, top = 9.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = ElementTheme.typography.fontBodyMdMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                    color = if (isError) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textPrimary,
                )
                Text(
                    text = message,
                    style = ElementTheme.typography.fontBodySmRegular.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = ElementTheme.colors.textSecondary,
                )
            }
            if (onDismissClick != null) {
                IconButton(onClick = onDismissClick) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = CompoundIcons.Close(),
                        contentDescription = stringResource(CommonStrings.action_close),
                        tint = ElementTheme.colors.iconSecondary,
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@PreviewsDayNight
@Composable
internal fun TgHintBannerPreview() = ElementPreview {
    TgHintBanner(
        title = "Привяжите почту",
        message = "Без неё не восстановить доступ, если забудете пароль.",
        onClick = {},
        onDismissClick = {},
    )
}
