/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: ключ восстановления, запертый паролем (escrow вариант B).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.test

import io.element.android.libraries.keyescrow.api.LoginPasswordHandoff
import io.element.android.libraries.matrix.api.core.SessionId

class FakeLoginPasswordHandoff : LoginPasswordHandoff {
    val passwords = mutableMapOf<SessionId, String>()

    override fun put(sessionId: SessionId, password: String) {
        passwords[sessionId] = password
    }

    override fun peek(sessionId: SessionId): String? = passwords[sessionId]

    override fun clear(sessionId: SessionId) {
        passwords.remove(sessionId)
    }
}
