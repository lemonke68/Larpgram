/*
 * Правка форка: «Черновик:» в строке списка чатов, как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.ui.drafts

import android.content.Context
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.di.annotations.ApplicationContext
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import timber.log.Timber

/** Текст черновика и когда он сохранён — по времени TG решает, показывать ли его вместо сообщения. */
data class DraftPreview(
    val text: String,
    val savedAtMillis: Long,
)

/**
 * Черновики для списка чатов. Сами черновики SDK хранит в своей базе, но читать их можно только
 * «забрав» (`loadComposerDraft` очищает), поэтому композер при выходе из чата дублирует сюда
 * короткий текст. Хранится на устройстве, как и черновики в SDK.
 */
interface DraftPreviews {
    val drafts: StateFlow<Map<RoomId, DraftPreview>>

    /** null или пустой текст — черновика нет. */
    fun set(roomId: RoomId, text: String?)
}

class NoOpDraftPreviews : DraftPreviews {
    override val drafts: StateFlow<Map<RoomId, DraftPreview>> = MutableStateFlow(emptyMap())
    override fun set(roomId: RoomId, text: String?) = Unit
}

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultDraftPreviews(
    @ApplicationContext context: Context,
    matrixClient: MatrixClient,
) : DraftPreviews {
    // Свой файл на каждый аккаунт: у двух сессий разные комнаты и разные черновики.
    private val prefs = context.getSharedPreferences(
        "larpgram_drafts_" + matrixClient.sessionId.value.hashCode().toUInt().toString(16),
        Context.MODE_PRIVATE,
    )

    private val state = MutableStateFlow(load())
    override val drafts: StateFlow<Map<RoomId, DraftPreview>> = state.asStateFlow()

    override fun set(roomId: RoomId, text: String?) {
        val preview = text?.trim()?.take(MAX_LENGTH)?.takeIf { it.isNotEmpty() }
        if (preview == null && roomId !in state.value) return
        if (preview != null && state.value[roomId]?.text == preview) return
        state.update { current ->
            if (preview == null) current - roomId else current + (roomId to DraftPreview(preview, System.currentTimeMillis()))
        }
        val editor = prefs.edit()
        if (preview == null) {
            editor.remove(roomId.value)
        } else {
            val savedAt = state.value.getValue(roomId).savedAtMillis
            editor.putString(roomId.value, JSONObject().put(KEY_TEXT, preview).put(KEY_TIME, savedAt).toString())
        }
        editor.apply()
    }

    private fun load(): Map<RoomId, DraftPreview> = prefs.all.mapNotNull { (key, value) ->
        runCatching {
            val json = JSONObject(value as String)
            RoomId(key) to DraftPreview(json.getString(KEY_TEXT), json.getLong(KEY_TIME))
        }.onFailure { Timber.w(it, "Битый черновик в хранилище") }.getOrNull()
    }.toMap()

    private companion object {
        // В строке списка видна одна-две строки, весь текст хранить незачем.
        const val MAX_LENGTH = 200
        const val KEY_TEXT = "text"
        const val KEY_TIME = "time"
    }
}
