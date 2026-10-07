/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: ключ восстановления, запертый паролем (escrow вариант B).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.impl

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.androidutils.hash.hash
import io.element.android.libraries.cryptography.api.EncryptionDecryptionService
import io.element.android.libraries.cryptography.api.EncryptionResult
import io.element.android.libraries.cryptography.api.SecretKeyRepository
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.preferences.api.store.PreferenceDataStoreFactory
import io.element.android.libraries.sessionstorage.api.observer.SessionListener
import io.element.android.libraries.sessionstorage.api.observer.SessionObserver
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Копия ключа восстановления на устройстве: ею вошедшее устройство перезапирает ключ новым
 * паролем и показывает его в настройках. Лежит в DataStore зашифрованной ключом Android Keystore
 * (он не покидает устройство), удаляется при выходе из аккаунта.
 */
interface LocalRecoveryKeyStore {
    suspend fun get(sessionId: SessionId): String?

    suspend fun put(sessionId: SessionId, recoveryKey: String)

    suspend fun remove(sessionId: SessionId)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultLocalRecoveryKeyStore(
    preferenceDataStoreFactory: PreferenceDataStoreFactory,
    private val secretKeyRepository: SecretKeyRepository,
    private val encryptionDecryptionService: EncryptionDecryptionService,
    sessionObserver: SessionObserver,
) : LocalRecoveryKeyStore {
    private val store = preferenceDataStoreFactory.create("larpgram_recovery_keys")

    init {
        sessionObserver.addListener(object : SessionListener {
            override suspend fun onSessionDeleted(userId: String, wasLastSession: Boolean) {
                remove(SessionId(userId))
            }
        })
    }

    override suspend fun get(sessionId: SessionId): String? {
        val encrypted = store.data.first()[keyOf(sessionId)] ?: return null
        return runCatching {
            val secretKey = secretKeyRepository.getOrCreateKey(KEYSTORE_ALIAS, false)
            encryptionDecryptionService.decrypt(secretKey, EncryptionResult.fromBase64(encrypted)).toString(Charsets.UTF_8)
        }.getOrElse {
            // Ключ Keystore пропал (восстановление из бэкапа Android, сброс блокировки экрана):
            // копия бесполезна, без неё провижинер перевыпустит ключ, когда будет пароль.
            Timber.w(it, "Локальная копия ключа восстановления не расшифровалась")
            remove(sessionId)
            null
        }
    }

    override suspend fun put(sessionId: SessionId, recoveryKey: String) {
        val secretKey = secretKeyRepository.getOrCreateKey(KEYSTORE_ALIAS, false)
        val encrypted = encryptionDecryptionService.encrypt(secretKey, recoveryKey.toByteArray(Charsets.UTF_8)).toBase64()
        store.edit { it[keyOf(sessionId)] = encrypted }
    }

    override suspend fun remove(sessionId: SessionId) {
        store.edit { it.remove(keyOf(sessionId)) }
    }

    private fun keyOf(sessionId: SessionId) = stringPreferencesKey("rk_${sessionId.value.hash().take(32)}")

    private companion object {
        const val KEYSTORE_ALIAS = "larpgram.RECOVERY_KEY"
    }
}
