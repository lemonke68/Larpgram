/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.textcomposer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.glass.TgGlassDefaults
import io.element.android.libraries.designsystem.components.glass.tgGlass
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.ui.media.contentvalidation.collectOverallState
import io.element.android.libraries.matrix.ui.media.contentvalidation.rememberEventContentValidationState
import io.element.android.libraries.matrix.ui.messages.reply.InReplyToDetails
import io.element.android.libraries.matrix.ui.messages.reply.InReplyToView
import io.element.android.libraries.matrix.ui.messages.reply.eventId
import io.element.android.libraries.textcomposer.components.VoiceMessageDeleteButtonIcon
import io.element.android.libraries.textcomposer.model.MessageComposerMode
import io.element.android.libraries.textcomposer.model.VoiceMessageRecorderEvent
import io.element.android.libraries.textcomposer.model.VoiceMessageState
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Правка форка: полоса ввода как в Telegram 12 (`ChatActivityEnterView`).
 *
 * Стеклянная пилюля 44dp со скруглением 22dp: слева кнопка эмодзи/стикеров, поле ввода, справа
 * скрепка (уезжает, когда в поле есть текст). Рядом с пилюлей отдельный круг цвета акцента с
 * белой иконкой: запись голосового или кружочка, а при наборе — «Отправить». Плашка ответа или
 * редактирования — верхней строкой внутри пилюли. Отступы от краёв 7dp, снизу 9dp.
 *
 * Используется для обычного режима и ответа/редактирования. Подпись к вложению (экран
 * предпросмотра медиа) остаётся на апстримовской раскладке [StandardLayout].
 */
@Composable
internal fun TgStandardLayout(
    composerMode: MessageComposerMode,
    voiceMessageState: VoiceMessageState,
    isTextEmpty: Boolean,
    textInput: @Composable () -> Unit,
    voiceRecording: @Composable () -> Unit,
    endButtonParams: EndButtonParams,
    onAddAttachment: () -> Unit,
    onStickerClick: (() -> Unit)?,
    isMediaPanelOpen: Boolean,
    circleRecordGestures: CircleRecordGestures?,
    showRecordModeButton: Boolean,
    onVoiceHoldStop: () -> Unit,
    onVoiceHoldCancel: () -> Unit,
    onVoiceHoldLock: () -> Unit,
    onDeleteVoiceMessage: () -> Unit,
    onVoiceRecorderEvent: (VoiceMessageRecorderEvent) -> Unit,
    onResetComposerMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val iconTint = TgGlassDefaults.iconColor()
    Row(
        modifier = modifier.padding(
            start = TgGlassDefaults.inputSideMargin,
            end = TgGlassDefaults.inputSideMargin,
            top = 6.dp,
            bottom = TgGlassDefaults.inputBottomMargin,
        ),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .tgGlass(RoundedCornerShape(TgGlassDefaults.inputRadius)),
        ) {
            if (composerMode is MessageComposerMode.Special) {
                TgComposerModeView(
                    composerMode = composerMode,
                    onResetComposerMode = onResetComposerMode,
                    iconTint = iconTint,
                )
            }
            Row(
                modifier = Modifier.heightIn(min = TgGlassDefaults.inputHeight),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Слева: эмодзи/стикеры, а во время записи голосового — корзина.
                // To avoid loosing keyboard focus, the IconButton has to be always enabled.
                if (voiceMessageState is VoiceMessageState.Idle) {
                    TgComposerIconButton(
                        // Панель открыта — кнопка возвращает клавиатуру, как в Telegram.
                        imageVector = if (isMediaPanelOpen) CompoundIcons.Keyboard() else CompoundIcons.Reaction(),
                        contentDescription = if (isMediaPanelOpen) "Клавиатура" else "Эмодзи и стикеры",
                        tint = iconTint,
                        onClick = { onStickerClick?.invoke() },
                        modifier = Modifier.padding(start = 2.dp),
                    )
                } else {
                    IconButton(
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(TgGlassDefaults.inputHeight),
                        onClick = {
                            when (voiceMessageState) {
                                is VoiceMessageState.Preview -> if (!voiceMessageState.isSending) {
                                    onDeleteVoiceMessage()
                                }
                                is VoiceMessageState.Recording ->
                                    onVoiceRecorderEvent(VoiceMessageRecorderEvent.Cancel)
                                VoiceMessageState.Idle -> Unit
                            }
                        },
                    ) {
                        VoiceMessageDeleteButtonIcon(
                            enabled = voiceMessageState !is VoiceMessageState.Preview || !voiceMessageState.isSending,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = TgGlassDefaults.inputHeight),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    val movableVoiceRecording = remember { movableContentOf { voiceRecording() } }
                    if (voiceMessageState is VoiceMessageState.Idle) {
                        textInput()
                    } else {
                        Box(modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                            movableVoiceRecording()
                        }
                    }
                }
                // Справа в пилюле: скрепка. В TG уезжает, как только в поле появился текст
                // (`ChatActivityEnterView.checkSendButton`, 100–150 мс).
                AnimatedVisibility(
                    visible = isTextEmpty && voiceMessageState is VoiceMessageState.Idle,
                    enter = fadeIn(tween(ATTACH_ANIMATION_MS)) + scaleIn(tween(ATTACH_ANIMATION_MS)) +
                        expandHorizontally(tween(ATTACH_ANIMATION_MS), expandFrom = Alignment.Start),
                    exit = fadeOut(tween(ATTACH_ANIMATION_MS)) + scaleOut(tween(ATTACH_ANIMATION_MS)) +
                        shrinkHorizontally(tween(ATTACH_ANIMATION_MS), shrinkTowards = Alignment.Start),
                ) {
                    TgComposerIconButton(
                        imageVector = CompoundIcons.Attachment(),
                        contentDescription = stringResource(R.string.rich_text_editor_a11y_add_attachment),
                        tint = iconTint,
                        onClick = onAddAttachment,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        // Круг цвета акцента справа: запись (удержание) или отправка.
        Box(
            modifier = Modifier.size(TgGlassDefaults.inputHeight),
            contentAlignment = Alignment.Center,
        ) {
            if (showRecordModeButton && circleRecordGestures != null) {
                LarpgramRecordModeButton(
                    circleGestures = circleRecordGestures,
                    onVoiceStart = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onVoiceRecorderEvent(VoiceMessageRecorderEvent.Start)
                    },
                    onVoiceStop = onVoiceHoldStop,
                    onVoiceCancel = onVoiceHoldCancel,
                    onVoiceLock = onVoiceHoldLock,
                    tgStyle = true,
                )
                if (voiceMessageState is VoiceMessageState.Recording) {
                    VoiceLockHint()
                }
            } else {
                val endButtonContentDescription = stringResource(endButtonParams.endButtonContentDescriptionResId)
                // To avoid loosing keyboard focus, the IconButton has to be always enabled.
                IconButton(
                    modifier = Modifier
                        .size(TgGlassDefaults.inputHeight)
                        .clearAndSetSemantics {
                            contentDescription = endButtonContentDescription
                            onClick(null, null)
                        },
                    onClick = endButtonParams.endButtonClick,
                    content = endButtonParams.endButtonContent,
                )
            }
        }
    }
}

@Composable
private fun TgComposerIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        modifier = modifier.size(TgGlassDefaults.inputHeight),
        onClick = onClick,
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

/**
 * Плашка ответа или редактирования, верхняя строка пилюли, как в Telegram: значок действия
 * цвета акцента на месте кнопки эмодзи, вертикальная черта, имя/заголовок и одна строка текста,
 * крестик справа.
 */
@Composable
private fun TgComposerModeView(
    composerMode: MessageComposerMode.Special,
    onResetComposerMode: () -> Unit,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    val accent = ElementTheme.colors.iconAccentPrimary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 2.dp)
                .size(TgGlassDefaults.inputHeight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(22.dp),
                imageVector = if (composerMode is MessageComposerMode.Reply) CompoundIcons.Reply() else CompoundIcons.Edit(),
                contentDescription = null,
                tint = accent,
            )
        }
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(accent)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp),
        ) {
            when (composerMode) {
                is MessageComposerMode.Reply -> {
                    val details = composerMode.replyToDetails
                    val validationState = rememberEventContentValidationState(
                        details.eventId(),
                        (details as? InReplyToDetails.Ready)?.eventContent,
                    )
                    val currentValidationState by validationState.collectOverallState()
                    InReplyToView(
                        inReplyTo = details,
                        hideImage = composerMode.hideImage,
                        contentValidationValue = currentValidationState,
                        maxLines = 1,
                        containerColor = Color.Transparent,
                    )
                }
                is MessageComposerMode.Edit -> TgModeTitleAndText(
                    title = stringResource(CommonStrings.common_editing),
                    text = composerMode.content,
                    accent = accent,
                )
                is MessageComposerMode.EditCaption -> TgModeTitleAndText(
                    title = stringResource(
                        if (composerMode.content.isEmpty()) CommonStrings.common_adding_caption else CommonStrings.common_editing_caption
                    ),
                    text = composerMode.content,
                    accent = accent,
                )
            }
        }
        IconButton(
            modifier = Modifier.size(TgGlassDefaults.inputHeight),
            onClick = onResetComposerMode,
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = CompoundIcons.Close(),
                contentDescription = stringResource(CommonStrings.action_close),
                tint = iconTint,
            )
        }
    }
}

@Composable
private fun TgModeTitleAndText(
    title: String,
    text: String,
    accent: Color,
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Text(
            text = title,
            style = ElementTheme.typography.fontBodyMdMedium,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (text.isNotEmpty()) {
            Text(
                text = text,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Подсказка «свайп вверх — зафиксировать» над кнопкой записи. Через Popup: он в отдельном слое
 * окна, поэтому плавает над кнопкой, не входит в поток (высота полосы не скачет) и не обрезается.
 */
@Composable
private fun VoiceLockHint() {
    val density = LocalDensity.current
    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, with(density) { (-52).dp.roundToPx() }),
        properties = PopupProperties(focusable = false, clippingEnabled = false),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = CompoundIcons.LockSolid(),
                contentDescription = "Закрепить запись",
                tint = ElementTheme.colors.iconAccentPrimary,
            )
        }
    }
}

private const val ATTACH_ANIMATION_MS = 150
