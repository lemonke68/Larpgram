/*
 * Модуль форка: пользовательское био («О себе») для профиля.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.matrix.api.user

import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Био хранится в глобальной account data, а не в профиле Matrix: rust SDK отдаёт из профиля
 * только displayName и avatarUrl, а поля MSC4133 (extended profile) на уровне FFI не читаются.
 * Отсюда следствие: био приватно и видно только самому пользователю на своём профиле.
 */
const val LARPGRAM_PROFILE_ACCOUNT_DATA_TYPE = "ru.mangokokos.larpgram.profile"

@Serializable
data class LarpgramProfileContent(
    val about: String? = null,
)

private val larpgramProfileJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

/**
 * Читает био текущего пользователя. Возвращает null, если account data нет или поле пустое.
 */
suspend fun MatrixClient.getLarpgramBio(): String? {
    val raw = getAccountData(LARPGRAM_PROFILE_ACCOUNT_DATA_TYPE).getOrNull() ?: return null
    val about = runCatching { larpgramProfileJson.decodeFromString<LarpgramProfileContent>(raw).about }
        .getOrNull()
    return about?.trim()?.ifEmpty { null }
}

/**
 * Пишет био текущего пользователя. Пустая строка стирает поле.
 */
suspend fun MatrixClient.setLarpgramBio(about: String?): Result<Unit> {
    val content = LarpgramProfileContent(about = about?.trim()?.ifEmpty { null })
    return setAccountData(LARPGRAM_PROFILE_ACCOUNT_DATA_TYPE, larpgramProfileJson.encodeToString(content))
}
