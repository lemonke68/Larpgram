/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: ключ восстановления при привязке устройства по QR.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.api

import io.element.android.libraries.matrix.api.core.SessionId

/**
 * Ключ восстановления, пришедший со старого устройства через QR, для [RecoveryKeyAutoProvisioner]:
 * экран входа живёт до сессии, провижинер — в ней. Только память процесса, до первого применения.
 */
interface PairedRecoveryKeyHandoff {
    fun put(sessionId: SessionId, recoveryKey: String)

    fun peek(sessionId: SessionId): String?

    fun clear(sessionId: SessionId)
}
