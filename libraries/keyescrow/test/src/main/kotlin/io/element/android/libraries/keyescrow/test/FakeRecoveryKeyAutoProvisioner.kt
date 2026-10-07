/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: депонирование ключа восстановления (вход по коду с почты).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.test

import io.element.android.libraries.keyescrow.api.RecoveryKeyAutoProvisioner
import kotlinx.coroutines.flow.MutableStateFlow

class FakeRecoveryKeyAutoProvisioner(
    private val ensureProvisionedLambda: suspend () -> Unit = {},
    private val lockWithPasswordLambda: suspend (String) -> Result<Unit> = { Result.success(Unit) },
    private val onPasswordChangedLambda: suspend (String) -> Unit = {},
    private val onRecoveryKeyCreatedLambda: suspend (String) -> Unit = {},
) : RecoveryKeyAutoProvisioner {
    override val needsPassword = MutableStateFlow(false)

    override suspend fun ensureProvisioned() {
        ensureProvisionedLambda()
    }

    override suspend fun lockWithPassword(password: String): Result<Unit> = lockWithPasswordLambda(password)

    override suspend fun onPasswordChanged(newPassword: String) = onPasswordChangedLambda(newPassword)

    override suspend fun onRecoveryKeyCreated(recoveryKey: String) = onRecoveryKeyCreatedLambda(recoveryKey)
}
