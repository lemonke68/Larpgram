/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: ключ восстановления при привязке устройства по QR.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.keyescrow.api.PairedRecoveryKeyHandoff
import io.element.android.libraries.matrix.api.core.SessionId
import java.util.concurrent.ConcurrentHashMap

/** Только память процесса, как [DefaultLoginPasswordHandoff]. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPairedRecoveryKeyHandoff : PairedRecoveryKeyHandoff {
    private val keys = ConcurrentHashMap<SessionId, String>()

    override fun put(sessionId: SessionId, recoveryKey: String) {
        keys[sessionId] = recoveryKey
    }

    override fun peek(sessionId: SessionId): String? = keys[sessionId]

    override fun clear(sessionId: SessionId) {
        keys.remove(sessionId)
    }
}
