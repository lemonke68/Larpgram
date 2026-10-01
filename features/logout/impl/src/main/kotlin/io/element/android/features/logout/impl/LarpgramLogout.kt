/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.logout.impl

import io.element.android.libraries.accountapi.api.AccountSessionApi
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.CancellationException

/**
 * Выход, который работает и для сессии, полученной по QR-коду. Такую сессию выдаёт сервис account
 * (токен MAS «personal session»), и штатный Matrix-запрос `/logout` сервер для неё не принимает.
 * Поэтому, если обычный выход не удался, просим сервис завершить сеанс этого устройства и после
 * этого чистим локальные данные.
 *
 * Обычные сессии (вход по паролю) выходят первым же запросом, для них ничего не меняется.
 */
internal suspend fun MatrixClient.logoutOrEndSession(accountSessionApi: AccountSessionApi, ignoreSdkError: Boolean) {
    try {
        logout(userInitiated = true, ignoreSdkError = ignoreSdkError)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        if (!accountSessionApi.endSession(deviceId.value)) throw failure
        // Сеанс на сервере завершён: ошибка SDK при повторном запросе уже ничего не значит.
        logout(userInitiated = true, ignoreSdkError = true)
    }
}
