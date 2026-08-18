/*
 * Модуль форка: проверка обновлений приложения (раздача APK мимо магазина).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.appupdate.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.appupdate.api.UpdateChecker
import io.element.android.libraries.appupdate.api.UpdateStatus
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.meta.BuildMeta
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
 * Тянет манифест версии с сайта раздачи и сравнивает с установленной сборкой.
 *
 * Манифест это маленький JSON на официальной странице Larpgram: последняя версия и её
 * versionCode. Сам APK там же, но качать его отсюда не надо — баннер обновления открывает
 * страницу в браузере, где человек скачивает файл ровно так же, как ставил в первый раз.
 * Так не нужно право REQUEST_INSTALL_PACKAGES и свой установщик, а UX установки уже
 * знаком по странице раздачи.
 *
 * Сравниваем по versionCode, а не по имени: имя косметическое, а монотонно растёт именно
 * код (см. `applicationVariants` в app/build.gradle.kts).
 *
 * «Отклонить» помним в account data, а не на устройстве: отмахнулся на телефоне — молчит
 * и на планшете. Список наших своих типов событий тот же, что у паков и баннера почты.
 */
@ContributesBinding(SessionScope::class)
class DefaultUpdateChecker(
    private val matrixClient: MatrixClient,
    private val okHttpClient: OkHttpClient,
    private val buildMeta: BuildMeta,
    private val coroutineDispatchers: CoroutineDispatchers,
) : UpdateChecker {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun check(): UpdateStatus {
        val manifest = fetchManifest() ?: return UpdateStatus.Unknown

        // Установлена свежая или свежее (например тестовая сборка): показывать нечего.
        if (manifest.versionCode <= buildMeta.versionCode) {
            return UpdateStatus.UpToDate
        }

        // Эту версию (или ещё более новую) уже отклоняли — молчим до следующей.
        if (manifest.versionCode <= dismissedVersionCode()) {
            return UpdateStatus.UpToDate
        }

        return UpdateStatus.Available(
            versionName = manifest.versionName,
            versionCode = manifest.versionCode,
        )
    }

    override suspend fun dismiss(versionCode: Long) {
        val payload = json.encodeToString(DismissedStateJson(versionCode = versionCode))
        matrixClient.setAccountData(DISMISSED_EVENT_TYPE, payload)
    }

    private suspend fun fetchManifest(): ManifestJson? {
        val request = Request.Builder().url(MANIFEST_URL).build()
        val body = withContext(coroutineDispatchers.io) {
            runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
            }.getOrElse {
                Timber.w(it, "не удалось скачать манифест обновления")
                null
            }
        } ?: return null
        return runCatching {
            json.decodeFromString<ManifestJson>(body)
        }.getOrElse {
            Timber.w(it, "манифест обновления не разобрался")
            null
        }
    }

    private suspend fun dismissedVersionCode(): Long {
        val raw = matrixClient.getAccountData(DISMISSED_EVENT_TYPE).getOrNull() ?: return 0L
        return runCatching {
            json.decodeFromString<DismissedStateJson>(raw).versionCode
        }.getOrElse {
            // Мусор в account data не должен намертво прятать обновления.
            Timber.w(it, "не разобрал отклонённую версию обновления")
            0L
        }
    }

    private companion object {
        /** Манифест на официальной странице Larpgram. Домен захардкожен, как и homeserver. */
        const val MANIFEST_URL = "https://larpgram.mango-kokos.ru/latest.json"

        /** Тип события в account data. Тот же неймспейс, что у паков и баннера почты. */
        const val DISMISSED_EVENT_TYPE = "ru.mangokokos.larpgram.update_dismissed"
    }
}

@Serializable
private data class ManifestJson(
    @SerialName("versionCode") val versionCode: Long = 0L,
    @SerialName("versionName") val versionName: String = "",
)

@Serializable
private data class DismissedStateJson(
    @SerialName("version_code") val versionCode: Long = 0L,
)
