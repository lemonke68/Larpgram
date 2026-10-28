/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: системное уведомление над списком чатов (редизайн V1 final) — строка, приклеенная
 * снизу к стеклянной карточке папок: плитка с иконкой, заголовок, одна строка пояснения и крестик.
 * Тап по строке — действие.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.LarpgramType
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.compound.tokens.larpgramColors
import io.element.android.features.home.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.LinearProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
internal fun TgHintBanner(
    title: String,
    message: String,
    onClick: () -> Unit,
    onDismissClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector = CompoundIcons.Info(),
    isError: Boolean = false,
    // Полоса хода внизу строки: 0..1, [INDETERMINATE_PROGRESS] — без процентов.
    progress: Float? = null,
) {
    val colors = larpgramColors
    Column(modifier = modifier.fillMaxWidth()) {
        // 1 dp divider between the folder strip (or the previous notice) and this row.
        HorizontalDivider(color = ElementTheme.colors.separatorPrimary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (isError) colors.danger else colors.accentText
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(if (isError) colors.danger.copy(alpha = 0.12f) else colors.accentSoft, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(modifier = Modifier.size(18.dp), imageVector = icon, contentDescription = null, tint = tint)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = LarpgramType.listItem.copy(fontSize = LarpgramType.folderChip.fontSize, fontWeight = FontWeight.ExtraBold),
                    color = if (isError) colors.danger else ElementTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = message,
                    style = LarpgramType.caption.copy(fontWeight = FontWeight.Medium),
                    color = ElementTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onDismissClick != null) {
                IconButton(onClick = onDismissClick, modifier = Modifier.size(34.dp)) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = CompoundIcons.Close(),
                        contentDescription = stringResource(CommonStrings.action_close),
                        tint = ElementTheme.colors.iconSecondary,
                    )
                }
            }
        }
        when {
            progress == null -> Unit
            progress < 0f -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            else -> LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }
    }
}

/** Для [TgHintBanner]: крутить полосу хода, не зная процентов. */
internal const val INDETERMINATE_PROGRESS = -1f

@PreviewsDayNight
@Composable
internal fun TgHintBannerPreview() = ElementPreview {
    TgHintBanner(
        title = stringResource(R.string.larpgram_banner_email_title),
        message = stringResource(R.string.larpgram_banner_email_message),
        icon = CompoundIcons.Email(),
        onClick = {},
        onDismissClick = {},
    )
}
