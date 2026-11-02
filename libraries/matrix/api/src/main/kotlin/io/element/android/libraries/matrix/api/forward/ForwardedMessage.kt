/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.forward

import io.element.android.libraries.matrix.api.core.MatrixPatterns
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/** Attribution supplied by the forwarding client; this is not proof of authorship. */
@Serializable
data class ForwardedMessage(
    val name: String? = null,
    val userId: String? = null,
    val roomId: String? = null,
    val eventId: String? = null,
    val avatarUrl: String? = null,
    val isChannel: Boolean = false,
) {
    /** A hidden profile must not remain recoverable from avatar or origin metadata. */
    fun withoutProfileLink(): ForwardedMessage = copy(userId = null, roomId = null, eventId = null, avatarUrl = null)

    companion object {
        const val FIELD = "ru.mangokokos.larpgram.forwarded"
        private val json = Json { ignoreUnknownKeys = true }

        fun fromEventJson(raw: String?): ForwardedMessage? {
            if (raw == null || !raw.contains(FIELD)) return null
            return runCatching {
                val value = json.parseToJsonElement(raw).jsonObject["content"]?.jsonObject?.get(FIELD) ?: return null
                json.decodeFromJsonElement<ForwardedMessage>(value).let {
                    it.copy(
                        name = it.name?.take(MAX_NAME_LENGTH),
                        userId = it.userId?.takeIf { id -> id.startsWith("@") && ':' in id && id.length <= MAX_ID_LENGTH },
                        roomId = it.roomId?.takeIf { id -> id.length <= MAX_ID_LENGTH && MatrixPatterns.isRoomId(id) },
                        eventId = it.eventId?.takeIf { id -> id.startsWith("$") && id.length <= MAX_ID_LENGTH },
                        avatarUrl = it.avatarUrl?.takeIf { url -> url.startsWith("mxc://") && url.length <= MAX_ID_LENGTH },
                    )
                }
            }.getOrNull()
        }

        fun extraContent(value: ForwardedMessage): String = json.encodeToString(
            JsonObject(mapOf(FIELD to json.parseToJsonElement(json.encodeToString(value))))
        )

        private const val MAX_NAME_LENGTH = 256
        private const val MAX_ID_LENGTH = 1024
    }
}

/** A media copy omits both attribution and the original caption. */
data class ForwardOptions(
    val hideAuthor: Boolean = false,
    val mediaOnly: Boolean = false,
    val caption: String? = null,
)
