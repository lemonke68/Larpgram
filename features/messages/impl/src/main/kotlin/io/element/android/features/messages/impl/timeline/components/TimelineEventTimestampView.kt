/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.TimelineEvent
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.isEdited
import io.element.android.features.messages.impl.timeline.model.event.isRedacted
import io.element.android.libraries.core.bool.orFalse
import io.element.android.libraries.designsystem.components.messages.MessageDeliveryState
import io.element.android.libraries.designsystem.components.messages.MessageDeliveryTicks
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun TimelineEventTimestampView(
    event: TimelineItem.Event,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
    modifier: Modifier = Modifier,
    isLayoutDirectionMismatched: Boolean = false,
    // Larpgram: на постах канала вместо тиков доставки показываем «просмотры» (по числу read
    // receipts) со значком глаза — как в Telegram. Тики в вещательном канале смысла не имеют:
    // пост не «прочитан» одним адресатом.
    showViewCount: Boolean = false,
    // Larpgram: цвет содержимого плашки. Для оверлея поверх медиа задаём белый — фон плашки там
    // полупрозрачный тёмный, а тема-зависимый textSecondary на ярком фото читался бы плохо.
    contentColor: Color? = null,
) {
    val formattedTime = event.sentTime
    val hasError = event.failedToSend
    val hasEncryptionCritical = event.messageShield?.isCritical.orFalse()
    val isMessageEdited = event.content.isEdited()
    val isMessageRedacted = event.content.isRedacted()
    val tint = if (hasError || hasEncryptionCritical && !isMessageRedacted) {
        ElementTheme.colors.textCriticalPrimary
    } else {
        contentColor ?: ElementTheme.colors.textSecondary
    }

    val shield = event.messageShield
    val isVerifiedUserSendFailure = event.localSendState is LocalEventSendState.Failed.VerifiedUser
    val onClickLabel = when {
        shield != null -> stringResource(CommonStrings.a11y_view_details)
        hasError && isVerifiedUserSendFailure -> stringResource(CommonStrings.action_open_context_menu)
        else -> null
    }
    val clickableModifier = remember(shield, hasError) {
        when {
            shield != null -> {
                Modifier.clickable(
                    onClickLabel = onClickLabel,
                ) {
                    eventSink(TimelineEvent.ShowShieldDialog(shield))
                }
            }
            hasError -> Modifier
                .clickable(
                    enabled = isVerifiedUserSendFailure,
                    onClickLabel = onClickLabel,
                ) {
                    eventSink(TimelineEvent.ComputeVerifiedUserSendFailure(event))
                }
            else -> Modifier
        }
    }

    val padding = if (!isLayoutDirectionMismatched) {
        PaddingValues(start = TimelineEventTimestampViewDefaults.spacing)
    } else {
        PaddingValues(end = TimelineEventTimestampViewDefaults.spacing)
    }

    Row(
        modifier = Modifier
            .padding(padding)
            // For a better click target, make the corners rounded
            .clip(RoundedCornerShape(8.dp))
            .then(clickableModifier)
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isMessageEdited) {
            Text(
                stringResource(CommonStrings.common_edited_suffix),
                style = ElementTheme.typography.fontBodyXsRegular,
                color = tint,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        // Larpgram: «просмотры» канала — значок глаза + число прочитавших (read receipts), перед
        // временем, как в Telegram. Показываем вместо тиков.
        if (showViewCount) {
            Icon(
                imageVector = CompoundIcons.VisibilityOn(),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                // read receipts исключают самого зрителя, поэтому +1 = собственный просмотр.
                // Приближение: receipt отмечает лишь ПОСЛЕДНИЙ прочитанный юзером пост, поэтому на
                // старых постах число оседает к 1 по мере чтения новых — это не истинный кумулятив.
                text = (event.readReceiptState.receipts.size + 1).toString(),
                style = ElementTheme.typography.fontBodyXsRegular,
                color = tint,
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            formattedTime,
            style = ElementTheme.typography.fontBodyXsRegular,
            color = tint,
        )
        // Telegram's delivery ticks, on our own messages only. One tick means the server took it,
        // two mean somebody has read it: Matrix spells those as the local send state and read
        // receipts respectively.
        if (event.isMine && !hasError && !isMessageRedacted && !showViewCount) {
            Spacer(modifier = Modifier.width(3.dp))
            MessageDeliveryTicks(
                state = when {
                    event.localSendState is LocalEventSendState.Sending -> MessageDeliveryState.Sending
                    event.readReceiptState.receipts.isNotEmpty() -> MessageDeliveryState.Read
                    event.isRemote -> MessageDeliveryState.Sent
                    else -> MessageDeliveryState.Sending
                }
            )
        }
        if (hasError) {
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = CompoundIcons.ErrorSolid(),
                contentDescription = stringResource(id = CommonStrings.common_sending_failed),
                tint = tint,
                modifier = Modifier.size(15.dp, 18.dp),
            )
        }

        if (!isMessageRedacted) {
            shield?.let { shield ->
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = shield.toIcon(),
                    contentDescription = stringResource(id = CommonStrings.a11y_encryption_details),
                    modifier = Modifier.size(15.dp),
                    tint = shield.toIconColor(),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineEventTimestampViewPreview(@PreviewParameter(TimelineItemEventForTimestampViewProvider::class) event: TimelineItem.Event) = ElementPreview {
    TimelineEventTimestampView(
        event = event,
        eventSink = {},
    )
}

object TimelineEventTimestampViewDefaults {
    val spacing = 16.dp
}
