/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: депонирование ключа восстановления (вход по коду с почты).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.impl

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.keyescrow.api.KeyEscrowService
import io.element.android.libraries.keyescrow.api.RecoveryKeyAutoProvisioner
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.encryption.RecoveryException
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultRecoveryKeyAutoProvisioner(
    private val matrixClient: MatrixClient,
    private val keyEscrowService: KeyEscrowService,
    private val dispatchers: CoroutineDispatchers,
) : RecoveryKeyAutoProvisioner {
    // FTUE и список чатов зовут одновременно — второй ждёт первого, а не создаёт второй ключ.
    private val mutex = Mutex()

    override suspend fun ensureProvisioned() = withContext(dispatchers.io) {
        mutex.withLock { provision() }
    }

    private suspend fun provision() {
        val encryptionService = matrixClient.encryptionService
        // Дождаться, пока состояние восстановления определится (не UNKNOWN/WAITING_FOR_SYNC).
        val state = withTimeoutOrNull(RESOLVE_TIMEOUT) {
            encryptionService.recoveryStateStateFlow.first { it in RESOLVED_STATES }
        } ?: return
        when (state) {
            // Свежий аккаунт без восстановления: создать ключ и заескроить.
            // Если в escrow уже лежит ключ, а восстановление выключено, ключ протух: его сбросили
            // в другом клиенте (Reset identity в Element). Создаём новый и перезаписываем.
            RecoveryState.DISABLED -> {
                // Сервер не ответил — не трогаем ничего, попробуем в следующий раз.
                val hasStoredKey = keyEscrowService.hasStoredKey() ?: return
                if (hasStoredKey) Timber.w("Escrowed recovery key is stale: recovery is disabled on the account")
                Timber.d("Auto-provisioning recovery key")
                encryptionService.enableRecovery(waitForBackupsToUpload = false)
                    .onSuccess { storeKey(it) }
                    .onFailure { Timber.w(it, "Авто-создание recovery ключа не удалось") }
            }
            // Новая сессия аккаунта, у которого восстановление уже есть: вход в аккаунт и есть
            // подтверждение (решение юзера 2026-09-24), поэтому ключ из escrow забираем без кода.
            // recover() разом подписывает сессию и открывает бэкап — видна вся история.
            RecoveryState.INCOMPLETE -> {
                val key = keyEscrowService.fetchSessionKey() ?: return
                Timber.d("Auto-unlocking a new session with the escrowed recovery key")
                encryptionService.recover(key)
                    .onFailure { error ->
                        Timber.w(error, "Авто-восстановление по escrow-ключу не удалось")
                        // Ключ не подошёл: его сменили в другом клиенте. Убираем протухший, чтобы
                        // после ручного подтверждения ветка ENABLED положила свежий.
                        if (error is RecoveryException.SecretStorage) {
                            Timber.w("Escrowed recovery key is stale, removing it")
                            keyEscrowService.deleteStoredKey()
                        }
                    }
            }
            // Восстановление настроено, но ключ в escrow не попал (аккаунты до escrow): выпустить
            // новый ключ и заескроить, иначе следующая сессия снова упрётся в «подтвердите».
            RecoveryState.ENABLED -> {
                if (keyEscrowService.hasStoredKey() != false) return
                Timber.d("Backfilling escrow with a fresh recovery key")
                encryptionService.resetRecoveryKey()
                    .onSuccess { storeKey(it) }
                    .onFailure { Timber.w(it, "Перевыпуск recovery ключа для escrow не удался") }
            }
            else -> Unit
        }
    }

    private suspend fun storeKey(recoveryKey: String) {
        keyEscrowService.store(recoveryKey)
            .onFailure { Timber.w(it, "Не удалось залить recovery ключ в escrow") }
    }
}

private val RESOLVED_STATES = setOf(RecoveryState.DISABLED, RecoveryState.ENABLED, RecoveryState.INCOMPLETE)
private val RESOLVE_TIMEOUT = 60.seconds
