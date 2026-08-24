/*
 * Модуль форка: депонирование ключа восстановления (вход по коду с почты).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.keyescrow.impl

import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.keyescrow.api.KeyEscrowService
import io.element.android.libraries.keyescrow.api.RecoveryKeyAutoProvisioner
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@ContributesBinding(SessionScope::class)
class DefaultRecoveryKeyAutoProvisioner(
    private val matrixClient: MatrixClient,
    private val keyEscrowService: KeyEscrowService,
    private val dispatchers: CoroutineDispatchers,
) : RecoveryKeyAutoProvisioner {
    override suspend fun ensureProvisioned() = withContext(dispatchers.io) {
        val encryptionService = matrixClient.encryptionService
        // Дождаться, пока состояние восстановления определится (не UNKNOWN/WAITING_FOR_SYNC).
        val state = withTimeoutOrNull(RESOLVE_TIMEOUT) {
            encryptionService.recoveryStateStateFlow.first { it in RESOLVED_STATES }
        } ?: return@withContext
        // Только свежий аккаунт без восстановления. INCOMPLETE (бэкап есть, надо восстановить) и
        // ENABLED (уже настроено) не трогаем — там путь через escrow-код, а не создание нового ключа.
        if (state != RecoveryState.DISABLED) return@withContext
        // Страховка: не перезатираем уже заескроенный ключ.
        if (keyEscrowService.hasStoredKey() == true) return@withContext

        Timber.d("Auto-provisioning recovery key for a fresh account")
        encryptionService.enableRecovery(waitForBackupsToUpload = false)
            .onSuccess { recoveryKey ->
                keyEscrowService.store(recoveryKey)
                    .onFailure { Timber.w(it, "Не удалось залить авто-recovery ключ в escrow") }
            }
            .onFailure { Timber.w(it, "Авто-создание recovery ключа не удалось") }
        Unit
    }
}

private val RESOLVED_STATES = setOf(RecoveryState.DISABLED, RecoveryState.ENABLED, RecoveryState.INCOMPLETE)
private val RESOLVE_TIMEOUT = 60.seconds
