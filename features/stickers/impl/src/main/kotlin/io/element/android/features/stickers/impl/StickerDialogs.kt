/*
 * Модуль форка: диалоги стикеров — ошибка отправки и добавление пака из Telegram.
 * Раньше жили в StickerPickerView.kt, чей сам пикер заменила TgStickerPanel.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.stickers.impl

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.components.dialogs.ListDialog
import io.element.android.libraries.designsystem.theme.components.TextField

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

/** Окно добавления пака: ввод имени, ход импорта и результат. */
@Composable
internal fun ImportDialog(state: StickerPickerState) {
    val importState = state.importState
    if (importState is ImportState.Hidden) return

    var packName by rememberSaveable { mutableStateOf("") }
    val isFinished = importState is ImportState.Done || importState is ImportState.Error
    val subtitle = when (importState) {
        is ImportState.InProgress -> stringResource(R.string.larpgram_stickers_import_progress)
        // Про пропущенные говорим прямо, иначе человек решит, что пак приехал битым.
        is ImportState.Done -> if (importState.skipped > 0) {
            stringResource(R.string.larpgram_stickers_import_done_skipped, importState.packName, importState.skipped)
        } else {
            stringResource(R.string.larpgram_stickers_import_done, importState.packName)
        }
        is ImportState.Error -> stringResource(
            when (importState.reason) {
                ImportErrorReason.NotFound -> R.string.larpgram_stickers_import_not_found
                ImportErrorReason.EmptyPack -> R.string.larpgram_stickers_import_empty
                ImportErrorReason.Failed -> R.string.larpgram_stickers_import_failed
            }
        )
        else -> stringResource(R.string.larpgram_stickers_import_hint)
    }

    // ListDialog, а не ConfirmationDialog: у последнего нет слота под содержимое,
    // и поле ввода уезжает в слот иконки, то есть выше заголовка.
    ListDialog(
        title = stringResource(R.string.larpgram_stickers_import_title),
        subtitle = subtitle,
        submitText = stringResource(if (isFinished) R.string.larpgram_stickers_ok else R.string.larpgram_stickers_import_add),
        enabled = isFinished || (importState is ImportState.Asking && packName.isNotBlank()),
        onSubmit = {
            if (isFinished) {
                state.eventSink(StickerPickerEvents.DismissImport)
            } else {
                state.eventSink(StickerPickerEvents.ImportPack(packName))
            }
        },
        onDismissRequest = { state.eventSink(StickerPickerEvents.DismissImport) },
    ) {
        if (importState is ImportState.Asking) {
            item {
                TextField(
                    value = packName,
                    onValueChange = { packName = it },
                    placeholder = stringResource(R.string.larpgram_stickers_import_placeholder),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
