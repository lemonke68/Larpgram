/*
 * Модуль форка: депонирование ключа восстановления (вход по коду с почты).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.keyescrow.test

import io.element.android.libraries.keyescrow.api.RecoveryKeyAutoProvisioner

class FakeRecoveryKeyAutoProvisioner(
    private val ensureProvisionedLambda: () -> Unit = {},
) : RecoveryKeyAutoProvisioner {
    override suspend fun ensureProvisioned() {
        ensureProvisionedLambda()
    }
}
