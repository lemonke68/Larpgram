/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: диалог добавления пака из Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
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
import io.element.android.libraries.designsystem.components.dialogs.ListDialog
import io.element.android.libraries.designsystem.theme.components.TextField

/**
 * Окно добавления пака: ввод имени, ход импорта и результат.
 *
 * Тоже вызывается СНАРУЖИ панели стикеров: панель стоит на месте клавиатуры и сжимается, когда
 * та поднимается, так что диалог внутри неё исчезал при первом же тапе в поле ввода.
 */
@Composable
fun StickerImportDialog(state: StickerPickerState) {
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
