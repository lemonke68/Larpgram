/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: ключ восстановления, запертый паролем (escrow вариант B).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.api

import io.element.android.libraries.matrix.api.core.SessionId

/**
 * Пароль, которым только что вошли, для [RecoveryKeyAutoProvisioner]: экран входа живёт до
 * сессии, провижинер — в ней. Пароль лежит только в памяти процесса и только до тех пор, пока
 * провижинер не запер или не отпер им ключ восстановления.
 */
interface LoginPasswordHandoff {
    fun put(sessionId: SessionId, password: String)

    fun peek(sessionId: SessionId): String?

    fun clear(sessionId: SessionId)
}
