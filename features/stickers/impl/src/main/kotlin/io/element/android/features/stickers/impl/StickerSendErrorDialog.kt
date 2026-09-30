/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: диалог ошибки отправки стикера.
 * Раньше жили в StickerPickerView.kt, чей сам пикер заменила TgStickerPanel.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.stickers.impl

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog

/**
 * Диалог «стикер не отправился».
 *
 * Вызывается СНАРУЖИ шторки с пикером, а не внутри: шторка закрывается сразу по нажатию
 * на стикер, и диалог внутри неё никто бы не успел увидеть.
 */
@Composable
fun StickerSendErrorDialog(state: StickerPickerState) {
    val error = state.sendError ?: return
    val dismiss = { state.eventSink(StickerPickerEvents.DismissSendError) }

    // ErrorDialog, а не ListDialog: у последнего всегда есть кнопка отмены, а отменять
    // тут нечего, сообщение чисто информационное.
    ErrorDialog(
        title = stringResource(R.string.larpgram_stickers_send_error_title),
        content = when (error) {
            // Ведём к уборке сессий, а не к кнопке «отправить всё равно». Она бы тоже
            // разблокировала отправку, но означает «шли ключи на устройство, которое я
            // не проверял», то есть обход предупреждения, а не его устранение.
            StickerSendError.UnverifiedSession -> stringResource(R.string.larpgram_stickers_send_error_unverified)
            StickerSendError.Other -> stringResource(R.string.larpgram_stickers_send_error_other)
        },
        submitText = stringResource(R.string.larpgram_stickers_ok),
        onSubmit = dismiss,
    )
}
