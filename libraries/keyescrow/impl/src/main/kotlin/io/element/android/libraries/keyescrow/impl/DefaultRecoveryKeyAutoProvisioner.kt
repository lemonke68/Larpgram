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
import io.element.android.libraries.keyescrow.api.EscrowRemoteState
import io.element.android.libraries.keyescrow.api.KeyEscrowService
import io.element.android.libraries.keyescrow.api.LockedKeyFetch
import io.element.android.libraries.keyescrow.api.LoginPasswordHandoff
import io.element.android.libraries.keyescrow.api.RecoveryKeyAutoProvisioner
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.encryption.RecoveryException
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * Escrow вариант B, см. [RecoveryKeyAutoProvisioner]. Источники ключа восстановления по порядку:
 * копия на устройстве, блоб на сервере (отпирается паролем со входа), ключ сервера (только с
 * тумблером или старый, до конца срока перехода). Ключ, который пойдёт в блоб, сначала проверяется
 * через `recover()`: протухшая копия (ключ сбросили в другом клиенте) не должна затереть живой блоб.
 */
@ContributesBinding(SessionScope::class)
@SingleIn(SessionScope::class)
class DefaultRecoveryKeyAutoProvisioner(
    private val matrixClient: MatrixClient,
    private val keyEscrowService: KeyEscrowService,
    private val localKeyStore: LocalRecoveryKeyStore,
    private val passwordHandoff: LoginPasswordHandoff,
    private val recoveryKeyLock: RecoveryKeyLock,
    private val dispatchers: CoroutineDispatchers,
) : RecoveryKeyAutoProvisioner {
    // FTUE и список чатов зовут одновременно — второй ждёт первого, а не создаёт второй ключ.
    private val mutex = Mutex()
    private val sessionId = matrixClient.sessionId
    private val userId = sessionId.value
    private val encryptionService = matrixClient.encryptionService

    private val needsPasswordFlow = MutableStateFlow(false)
    override val needsPassword: StateFlow<Boolean> = needsPasswordFlow.asStateFlow()

    override suspend fun ensureProvisioned() = withContext(dispatchers.io) {
        mutex.withLock { provision() }
    }

    override suspend fun lockWithPassword(password: String): Result<Unit> = withContext(dispatchers.io) {
        mutex.withLock {
            val remote = keyEscrowService.remoteState()
                ?: return@withLock Result.failure(IllegalStateException("escrow недоступен"))
            val key = currentKey() ?: reissueKey()
                ?: return@withLock Result.failure(IllegalStateException("нет ключа восстановления"))
            if (lockAndUpload(key, password, remote)) {
                Result.success(Unit)
            } else {
                Result.failure(IllegalStateException("escrow не принял запертый ключ"))
            }
        }
    }

    override suspend fun onPasswordChanged(newPassword: String) = withContext(dispatchers.io) {
        mutex.withLock {
            // Не вышло сейчас — пароль остаётся в памяти, следующий провижининг перезапрёт.
            passwordHandoff.put(sessionId, newPassword)
            val remote = keyEscrowService.remoteState() ?: return@withLock
            val key = currentKey() ?: reissueKey() ?: return@withLock
            lockAndUpload(key, newPassword, remote)
            Unit
        }
    }

    override suspend fun onRecoveryKeyCreated(recoveryKey: String) = withContext(dispatchers.io) {
        mutex.withLock {
            localKeyStore.put(sessionId, recoveryKey)
            val remote = keyEscrowService.remoteState() ?: return@withLock
            val password = passwordHandoff.peek(sessionId)
            if (password != null) {
                lockAndUpload(recoveryKey, password, remote)
            } else {
                forgetOldKeyAndAskPassword(recoveryKey, remote)
            }
            Unit
        }
    }

    private suspend fun provision() {
        // Дождаться, пока состояние восстановления определится (не UNKNOWN/WAITING_FOR_SYNC).
        val state = withTimeoutOrNull(RESOLVE_TIMEOUT) {
            encryptionService.recoveryStateStateFlow.first { it in RESOLVED_STATES }
        } ?: return
        // Сервер не ответил — не трогаем ничего, попробуем в следующий раз.
        val remote = keyEscrowService.remoteState() ?: return
        val password = passwordHandoff.peek(sessionId)
        when (state) {
            RecoveryState.DISABLED -> provisionDisabled(remote, password)
            RecoveryState.INCOMPLETE -> provisionIncomplete(remote, password)
            RecoveryState.ENABLED -> provisionEnabled(remote, password)
            else -> Unit
        }
    }

    /**
     * Восстановления у аккаунта нет: свежий аккаунт или его сбросили (Reset identity). Создаём ключ,
     * кладём копию и запираем паролем. Старый блоб, если был, открывает уже не тот ключ.
     */
    private suspend fun provisionDisabled(remote: EscrowRemoteState, password: String?) {
        Timber.d("Auto-provisioning recovery key")
        val key = encryptionService.enableRecovery(waitForBackupsToUpload = false)
            .onFailure { Timber.w(it, "Авто-создание recovery ключа не удалось") }
            .getOrNull() ?: return
        localKeyStore.put(sessionId, key)
        if (password != null) {
            lockAndUpload(key, password, remote)
        } else {
            forgetOldKeyAndAskPassword(key, remote)
        }
    }

    /**
     * Новая сессия аккаунта, у которого восстановление уже есть: вход в аккаунт и есть подтверждение
     * (решение юзера 2026-09-24). Отпираем блоб паролем со входа, без пароля или блоба — ключ
     * сервера, если он его отдаёт. Нет ни того, ни другого — подтверждать вручную со второго
     * устройства, после этого сработает ветка ENABLED.
     */
    private suspend fun provisionIncomplete(remote: EscrowRemoteState, password: String?) {
        val fromBlob = if (password != null && remote.hasLockedKey) openRemoteBlob(password) else null
        val key = fromBlob ?: keyEscrowService.fetchSessionKey() ?: return
        Timber.d("Auto-unlocking a new session with the escrowed recovery key")
        encryptionService.recover(key)
            .onSuccess {
                localKeyStore.put(sessionId, key)
                when {
                    fromBlob != null -> passwordHandoff.clear(sessionId)
                    // Ключ пришёл от сервера, а пароль есть: запираем, чтобы следующим сервер был не нужен.
                    password != null -> lockAndUpload(key, password, remote)
                    else -> if (!remote.hasLockedKey) needsPasswordFlow.value = true
                }
            }
            .onFailure { error ->
                Timber.w(error, "Авто-восстановление по escrow-ключу не удалось")
                // Ключ не подошёл: его сменили в другом клиенте. Убираем протухший источник, чтобы
                // после ручного подтверждения ветка ENABLED положила свежий.
                if (error is RecoveryException.SecretStorage) {
                    Timber.w("Escrowed recovery key is stale, removing it")
                    if (fromBlob != null) keyEscrowService.deleteLockedKey() else keyEscrowService.deleteStoredKey()
                }
            }
    }

    /**
     * Сессия подписана. Убедиться, что ключ заперт текущим паролем: у сессий до варианта B и у
     * подтверждённых вручную нет копии ключа. Без пароля под рукой и без блоба — попросить пароль.
     */
    private suspend fun provisionEnabled(remote: EscrowRemoteState, password: String?) {
        if (password == null) {
            if (!remote.hasLockedKey) needsPasswordFlow.value = true
            return
        }
        // Блоб открывается этим паролем и ключ в нём живой — всё уже как надо.
        val fromBlob = if (remote.hasLockedKey) openRemoteBlob(password) else null
        if (fromBlob != null && isCurrent(fromBlob)) {
            localKeyStore.put(sessionId, fromBlob)
            if (remote.hasServerKey && !remote.serverKeyOptIn) keyEscrowService.disableServerKey()
            passwordHandoff.clear(sessionId)
            return
        }
        val key = currentKey() ?: reissueKey() ?: return
        lockAndUpload(key, password, remote)
    }

    /**
     * Запереть ключ паролем и положить на сервер. После этого ключ сервера без тумблера (остаток v1)
     * больше не нужен, а с тумблером — обновляется тем же ключом.
     */
    private suspend fun lockAndUpload(key: String, password: String, remote: EscrowRemoteState): Boolean {
        val locked = withContext(dispatchers.computation) { recoveryKeyLock.lock(key, password, userId) }
        if (!keyEscrowService.storeLockedKey(locked)) {
            Timber.w("Не удалось положить запертый ключ в escrow")
            return false
        }
        localKeyStore.put(sessionId, key)
        if (remote.serverKeyOptIn) {
            keyEscrowService.enableServerKey(key)
        } else if (remote.hasServerKey) {
            keyEscrowService.disableServerKey()
        }
        passwordHandoff.clear(sessionId)
        needsPasswordFlow.value = false
        return true
    }

    /** Новый ключ, а пароля нет: прежний блоб открывает уже не тот ключ, убираем его и просим пароль. */
    private suspend fun forgetOldKeyAndAskPassword(key: String, remote: EscrowRemoteState) {
        if (remote.hasLockedKey) keyEscrowService.deleteLockedKey()
        if (remote.serverKeyOptIn) {
            keyEscrowService.enableServerKey(key)
        } else if (remote.hasServerKey) {
            keyEscrowService.disableServerKey()
        }
        needsPasswordFlow.value = true
    }

    private suspend fun openRemoteBlob(password: String): String? {
        val locked = when (val fetch = keyEscrowService.fetchLockedKey()) {
            is LockedKeyFetch.Found -> fetch.locked
            LockedKeyFetch.NotFound, LockedKeyFetch.NetworkError -> return null
        }
        return withContext(dispatchers.computation) { recoveryKeyLock.open(locked, password, userId) }
            .also { if (it == null) Timber.w("Запертый ключ не открылся паролем со входа") }
    }

    /** Копия ключа на устройстве, если она ещё открывает хранилище секретов аккаунта. */
    private suspend fun currentKey(): String? {
        val local = localKeyStore.get(sessionId) ?: return null
        if (isCurrent(local)) return local
        Timber.w("Local recovery key copy is stale, dropping it")
        localKeyStore.remove(sessionId)
        return null
    }

    // recover() на подписанной сессии ничего не ломает: заново достаёт те же секреты.
    private suspend fun isCurrent(key: String): Boolean = encryptionService.recover(key).isSuccess

    /** Копии ключа нет, а сессия подписана: выпустить новый (прежний блоб станет бесполезен). */
    private suspend fun reissueKey(): String? {
        if (encryptionService.recoveryStateStateFlow.value != RecoveryState.ENABLED) return null
        Timber.d("Reissuing the recovery key to lock it with the password")
        return encryptionService.resetRecoveryKey()
            .onFailure { Timber.w(it, "Перевыпуск recovery ключа не удался") }
            .getOrNull()
            ?.also { localKeyStore.put(sessionId, it) }
    }
}

private val RESOLVED_STATES = setOf(RecoveryState.DISABLED, RecoveryState.ENABLED, RecoveryState.INCOMPLETE)
private val RESOLVE_TIMEOUT = 60.seconds
