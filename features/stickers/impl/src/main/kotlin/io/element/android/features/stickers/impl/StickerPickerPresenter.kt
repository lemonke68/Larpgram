/*
 * Модуль форка: пикер стикеров.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.stickers.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.features.stickers.impl.import.ImportResult
import io.element.android.features.stickers.impl.import.StickerPackImporter
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.channelcomments.ChannelPostMirror
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.imagepacks.api.ImagePack
import io.element.android.libraries.imagepacks.api.ImagePackEventTypes
import io.element.android.libraries.imagepacks.api.ImagePackId
import io.element.android.libraries.imagepacks.api.ImagePackSource
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import timber.log.Timber

@Inject
@ContributesBinding(RoomScope::class)
class StickerPickerPresenter(
    private val imagePackSource: ImagePackSource,
    private val room: JoinedRoom,
    private val matrixClient: MatrixClient,
    private val importer: StickerPackImporter,
) : Presenter<StickerPickerState> {
    @Composable
    override fun present(): StickerPickerState {
        val coroutineScope = rememberCoroutineScope()
        var packs by remember { mutableStateOf<ImmutableList<ImagePack>>(persistentListOf()) }
        var selectedPackIndex by remember { mutableIntStateOf(0) }
        var isLoading by remember { mutableStateOf(true) }
        var importState by remember { mutableStateOf<ImportState>(ImportState.Hidden) }
        var sendError by remember { mutableStateOf<StickerSendError?>(null) }

        suspend fun load() {
            isLoading = true
            // Паки без стикеров в пикере не нужны: они могут состоять из одних эмодзи.
            packs = imagePackSource.getAllPacks()
                .filter { it.stickers.isNotEmpty() }
                .toImmutableList()
            // Пак мог исчезнуть, пока пикер был открыт, поэтому вкладку держим в границах.
            selectedPackIndex = selectedPackIndex.coerceIn(0, (packs.size - 1).coerceAtLeast(0))
            isLoading = false
        }

        LaunchedEffect(Unit) {
            load()
        }

        fun handleEvent(event: StickerPickerEvents) {
            when (event) {
                is StickerPickerEvents.SelectPack -> {
                    if (event.index in packs.indices) {
                        selectedPackIndex = event.index
                    }
                }
                is StickerPickerEvents.SendSticker -> coroutineScope.launch {
                    val image = event.image
                    // Larpgram: запомним свои стикеры до отправки, чтобы опознать только что
                    // отправленный и зеркалить его в дискуссию канала (комменты под стикером).
                    val preIds = ChannelPostMirror.myPostIds(room) { ChannelPostMirror.isStickerEvent(it) }
                    // Larpgram: вшиваем дескриптор пака в событие (только у сохранённых паков),
                    // чтобы по тапу на стикер показать его пак и предложить добавить/удалить.
                    val pack = packs.getOrNull(selectedPackIndex)?.takeIf { it.id is ImagePackId.Saved }
                    val extraContent = pack?.let {
                        "{\"" + ImagePackEventTypes.STICKER_PACK_FIELD + "\":" +
                            imagePackSource.serializePackDescriptor(it) + "}"
                    }
                    room.sendSticker(
                        url = image.url,
                        body = image.bestDescription,
                        mimeType = image.mimeType,
                        width = image.width,
                        height = image.height,
                        size = image.size,
                        extraContent = extraContent,
                    ).onSuccess {
                        runCatchingExceptions {
                            ChannelPostMirror.mirrorLastPost(
                                room = room,
                                matrixClient = matrixClient,
                                preIds = preIds,
                                eventType = ChannelPostMirror.STICKER_EVENT_TYPE,
                            ) { ChannelPostMirror.isStickerEvent(it) }
                        }.onFailure { Timber.w(it, "не удалось зеркалить стикер в дискуссию") }
                    }.onFailure { error ->
                        // Молча ронять отправку нельзя: именно из-за этого неотправка
                        // стикеров в шифрованных комнатах не оставляла ни следа ни в
                        // логе, ни на экране.
                        Timber.e(error, "не удалось отправить стикер")
                        sendError = error.toStickerSendError()
                    }
                }
                StickerPickerEvents.ShowImport -> importState = ImportState.Asking
                StickerPickerEvents.DismissImport -> importState = ImportState.Hidden
                is StickerPickerEvents.ImportPack -> coroutineScope.launch {
                    importState = ImportState.InProgress
                    importState = when (val result = importer.import(event.packName)) {
                        is ImportResult.Success -> {
                            // Пак записан в account data, но getAccountData сразу после setAccountData
                            // может ещё отдавать старый кэш — из-за этого load() не видел свежий пак до
                            // следующего импорта. Поэтому добавляем его в список оптимистично (дедуп по
                            // id) и показываем; на следующем открытии пикера load() всё сверит с сервером.
                            val pack = result.pack
                            if (pack.stickers.isNotEmpty() && packs.none { it.id == pack.id }) {
                                packs = (packs + pack).toImmutableList()
                            }
                            selectedPackIndex = packs.indexOfFirst { it.id == pack.id }.coerceAtLeast(0)
                            ImportState.Done(
                                packName = pack.displayName.orEmpty(),
                                skipped = result.skipped,
                            )
                        }
                        ImportResult.NotFound -> ImportState.Error("Такого пака нет. Проверь название.")
                        is ImportResult.EmptyPack -> ImportState.Error(
                            "В этом паке нет стикеров."
                        )
                        ImportResult.Failed -> ImportState.Error("Не получилось. Попробуй ещё раз.")
                    }
                }
                StickerPickerEvents.Refresh -> coroutineScope.launch { load() }
                StickerPickerEvents.DismissSendError -> sendError = null
            }
        }

        return StickerPickerState(
            packs = packs,
            selectedPackIndex = selectedPackIndex,
            isLoading = isLoading,
            importState = importState,
            sendError = sendError,
            eventSink = ::handleEvent,
        )
    }
}

/**
 * Опознаём отказ шифрования из-за неподписанной сессии.
 *
 * Типа исключения под это в SDK нет, наружу приходит `ClientException.Generic`, поэтому
 * смотрим на текст в `details`. Хрупко: строка живёт в Rust SDK и может поменяться при
 * обновлении. Если поменяется, человек увидит общее сообщение вместо точного, но ничего
 * не сломается, поэтому так и оставлено.
 */
private fun Throwable.toStickerSendError(): StickerSendError {
    val details = generateSequence(this) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
    return if (details.contains("VerifiedUserHasUnsignedDevice") ||
        details.contains("SessionRecipientCollectionError")
    ) {
        StickerSendError.UnverifiedSession
    } else {
        StickerSendError.Other
    }
}
