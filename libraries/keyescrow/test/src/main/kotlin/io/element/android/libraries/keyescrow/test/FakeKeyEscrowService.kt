/*
 * Модуль форка: депонирование ключа восстановления (вход по коду с почты).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.keyescrow.test

import io.element.android.libraries.keyescrow.api.KeyEscrowService
import io.element.android.libraries.keyescrow.api.RedeemResult
import io.element.android.libraries.keyescrow.api.RequestCodeResult

class FakeKeyEscrowService(
    private val hasStoredKeyResult: Boolean? = false,
    private val storeLambda: (String) -> Result<Unit> = { Result.success(Unit) },
    private val requestCodeLambda: () -> RequestCodeResult = { RequestCodeResult.NetworkError },
    private val redeemCodeLambda: (String) -> RedeemResult = { RedeemResult.NetworkError },
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
}
