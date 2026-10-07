/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: депонирование ключа восстановления (вход по коду с почты).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.test

import io.element.android.libraries.keyescrow.api.EscrowRemoteState
import io.element.android.libraries.keyescrow.api.KeyEscrowService
import io.element.android.libraries.keyescrow.api.LockedKeyFetch
import io.element.android.libraries.keyescrow.api.LockedRecoveryKey
import io.element.android.libraries.keyescrow.api.RedeemResult
import io.element.android.libraries.keyescrow.api.RequestCodeResult
import io.element.android.libraries.matrix.api.core.RoomId

class FakeKeyEscrowService(
    private val hasStoredKeyResult: Boolean? = false,
    private val storeLambda: (String) -> Result<Unit> = { Result.success(Unit) },
    private val requestCodeLambda: () -> RequestCodeResult = { RequestCodeResult.NetworkError },
    private val redeemCodeLambda: (String) -> RedeemResult = { RedeemResult.NetworkError },
    private val deleteDmForBothLambda: (RoomId) -> Boolean = { true },
    /** `null` — отдавать ключ сервера из [serverKey], как настоящий сервер с тумблером. */
    private val fetchSessionKeyLambda: (() -> String?)? = null,
    private val deleteStoredKeyLambda: () -> Boolean = { true },
) : KeyEscrowService {
    var storedKey: String? = null
        private set

    override suspend fun hasStoredKey(): Boolean? = hasStoredKeyResult

    override suspend fun store(recoveryKey: String): Result<Unit> {
        storedKey = recoveryKey
        return storeLambda(recoveryKey)
    }

    override suspend fun requestCode(): RequestCodeResult = requestCodeLambda()

    override suspend fun redeemCode(code: String): RedeemResult = redeemCodeLambda(code)

    override suspend fun fetchSessionKey(): String? = fetchSessionKeyLambda?.invoke() ?: serverKey

    // Вариант B: сервер в памяти.

    /** Сервер недоступен: [remoteState] отвечает `null`, запись не проходит. */
    var unavailable = false
    var lockedKey: LockedRecoveryKey? = null
    var serverKey: String? = null
    var serverKeyOptIn = false
    var lockedKeyStores = 0
        private set

    override suspend fun remoteState(): EscrowRemoteState? =
        if (unavailable) null else EscrowRemoteState(lockedKey != null, serverKey != null, serverKey != null && serverKeyOptIn)

    override suspend fun fetchLockedKey(): LockedKeyFetch = when {
        unavailable -> LockedKeyFetch.NetworkError
        else -> lockedKey?.let { LockedKeyFetch.Found(it) } ?: LockedKeyFetch.NotFound
    }

    override suspend fun storeLockedKey(locked: LockedRecoveryKey): Boolean {
        if (unavailable) return false
        lockedKey = locked
        lockedKeyStores++
        return true
    }

    override suspend fun deleteLockedKey(): Boolean {
        if (unavailable) return false
        lockedKey = null
        return true
    }

    override suspend fun enableServerKey(recoveryKey: String): Boolean {
        if (unavailable) return false
        serverKey = recoveryKey
        serverKeyOptIn = true
        return true
    }

    override suspend fun disableServerKey(): Boolean {
        if (unavailable) return false
        serverKey = null
        serverKeyOptIn = false
        return true
    }

    override suspend fun deleteDmForBoth(roomId: RoomId): Boolean = deleteDmForBothLambda(roomId)

    override suspend fun deleteStoredKey(): Boolean = deleteStoredKeyLambda()
}
