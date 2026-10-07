/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: ключ восстановления, запертый паролем (escrow вариант B).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.keyescrow.api.LoginPasswordHandoff
import io.element.android.libraries.matrix.api.core.SessionId
import java.util.concurrent.ConcurrentHashMap

/** Только память процесса: на диск пароль не попадает, после убийства процесса его нет. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultLoginPasswordHandoff : LoginPasswordHandoff {
    private val passwords = ConcurrentHashMap<SessionId, String>()

    override fun put(sessionId: SessionId, password: String) {
        passwords[sessionId] = password
    }

    override fun peek(sessionId: SessionId): String? = passwords[sessionId]

    override fun clear(sessionId: SessionId) {
        passwords.remove(sessionId)
    }
}
