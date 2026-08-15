/*
 * Модуль форка: почта аккаунта (проверка привязки и баннер-напоминание).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.accountemail.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.accountemail.api.AccountEmailStatus
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/**
 * Читает привязанные адреса через CS API и помнит, когда баннер закрывали.
 *
 * Почему `/account/3pid`, а не MAS: подтверждённый адрес MAS сам прокидывает в Synapse
 * при провижининге, проверено живьём на свежерегнутом аккаунте. GraphQL у MAS для этого
 * не годится — он отвечает 401 на токены, которыми ходит клиент.
 *
 * Своего запроса тут не избежать: в Rust SDK работы с 3pid нет вообще, как и чтения
 * состояния комнаты (та же история, что у паков комнат в imagepacks).
 */
@ContributesBinding(SessionScope::class)
class DefaultAccountEmailStatus(
    private val matrixClient: MatrixClient,
    private val okHttpClient: OkHttpClient,
    private val coroutineDispatchers: CoroutineDispatchers,
) : AccountEmailStatus {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun hasEmail(): Boolean? {
        val token = matrixClient.getAccessToken().getOrNull() ?: return null
        val url = matrixClient.homeserverUrl.trimEnd('/') + THREE_PID_PATH
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        val body = withContext(coroutineDispatchers.io) {
            runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
            }.getOrElse {
                Timber.w(it, "не удалось спросить сервер про почту аккаунта")
                null
            }
        } ?: return null
        return runCatching {
            json.decodeFromString<ThreePidsResponse>(body).threepids.any { it.medium == MEDIUM_EMAIL }
        }.getOrElse {
            Timber.w(it, "сервер ответил не тем, что ожидалось, на запрос про почту")
            null
        }
    }

    override suspend fun isBannerHidden(): Boolean {
        val raw = matrixClient.getAccountData(BANNER_EVENT_TYPE).getOrNull() ?: return false
        val hiddenUntil = runCatching {
            json.decodeFromString<BannerStateJson>(raw).hiddenUntil
        }.getOrElse {
            // Мусор в account data не должен намертво прятать баннер.
            Timber.w(it, "не разобрал состояние баннера почты")
            0L
        }
        return System.currentTimeMillis() < hiddenUntil
    }

    override suspend fun hideBanner() {
        val hiddenUntil = System.currentTimeMillis() + HIDE_DURATION_MILLIS
        val payload = json.encodeToString(BannerStateJson(hiddenUntil = hiddenUntil))
        matrixClient.setAccountData(BANNER_EVENT_TYPE, payload)
    }

    private companion object {
        const val THREE_PID_PATH = "/_matrix/client/v3/account/3pid"
        const val MEDIUM_EMAIL = "email"

        /**
         * Состояние баннера лежит в account data, а не на устройстве: закрыл на телефоне —
         * молчит и на планшете. Список наших своих типов событий тот же, что у паков.
         */
        const val BANNER_EVENT_TYPE = "ru.mangokokos.larpgram.email_banner"

        /**
         * Две недели молчания вместо «закрыл навсегда»: потеря доступа к аккаунту это
         * дорого, а напоминание раз в две недели ещё не назойливость. Насовсем баннер
         * пропадает только когда почта появилась.
         */
        const val HIDE_DURATION_MILLIS = 14L * 24 * 60 * 60 * 1000
    }
}

@Serializable
private data class ThreePidsResponse(
    val threepids: List<ThreePidJson> = emptyList(),
)

@Serializable
private data class ThreePidJson(
    val medium: String = "",
    val address: String = "",
)

@Serializable
private data class BannerStateJson(
    @SerialName("hidden_until") val hiddenUntil: Long = 0L,
)
