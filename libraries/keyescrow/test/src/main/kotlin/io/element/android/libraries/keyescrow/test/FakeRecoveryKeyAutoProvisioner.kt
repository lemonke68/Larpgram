/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: депонирование ключа восстановления (вход по коду с почты).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.test

import io.element.android.libraries.keyescrow.api.RecoveryKeyAutoProvisioner

class FakeRecoveryKeyAutoProvisioner(
    private val ensureProvisionedLambda: suspend () -> Unit = {},
) : RecoveryKeyAutoProvisioner {
    override suspend fun ensureProvisioned() {
        ensureProvisionedLambda()
    }
}
