/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: депонирование ключа восстановления (вход по коду с почты).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.keyescrow.test.FakeKeyEscrowService
import io.element.android.libraries.keyescrow.test.FakeLoginPasswordHandoff
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.encryption.FakeEncryptionService
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultRecoveryKeyAutoProvisionerTest {
    private val lock = RecoveryKeyLock(RecoveryKeyLock.Params(memoryKib = 16 * 1024, iterations = 2, parallelism = 1))
    private val escrow = FakeKeyEscrowService()
    private val localStore = InMemoryLocalRecoveryKeyStore()
    private val handoff = FakeLoginPasswordHandoff()

    private fun FakeKeyEscrowService.opened(password: String): String? = lockedKey?.let { lock.open(it, password, A_SESSION_ID.value) }

    @Test
    fun `fresh account gets a key locked with the login password, the server keeps no key`() = runTest {
        val encryption = FakeEncryptionService(enableRecoveryLambda = { _, _ -> Result.success(NEW_KEY) })
        encryption.recoveryStateStateFlow.value = RecoveryState.DISABLED
        handoff.put(A_SESSION_ID, PASSWORD)

        val provisioner = createProvisioner(encryption)
        provisioner.ensureProvisioned()

        assertThat(escrow.opened(PASSWORD)).isEqualTo(NEW_KEY)
        assertThat(escrow.serverKey).isNull()
        assertThat(localStore.keys[A_SESSION_ID]).isEqualTo(NEW_KEY)
        assertThat(handoff.peek(A_SESSION_ID)).isNull()
        assertThat(provisioner.needsPassword.value).isFalse()
    }

    @Test
    fun `fresh key without a password drops the stale blob and asks for the password`() = runTest {
        val encryption = FakeEncryptionService(enableRecoveryLambda = { _, _ -> Result.success(NEW_KEY) })
        encryption.recoveryStateStateFlow.value = RecoveryState.DISABLED
        escrow.lockedKey = lock.lock(OLD_KEY, PASSWORD, A_SESSION_ID.value)

        val provisioner = createProvisioner(encryption)
        provisioner.ensureProvisioned()

        assertThat(escrow.lockedKey).isNull()
        assertThat(localStore.keys[A_SESSION_ID]).isEqualTo(NEW_KEY)
        assertThat(provisioner.needsPassword.value).isTrue()

        encryption.recoveryStateStateFlow.value = RecoveryState.ENABLED
        assertThat(provisioner.lockWithPassword(PASSWORD).isSuccess).isTrue()
        assertThat(escrow.opened(PASSWORD)).isEqualTo(NEW_KEY)
        assertThat(provisioner.needsPassword.value).isFalse()
    }

    @Test
    fun `new session opens the blob with the login password, no server key involved`() = runTest {
        val encryption = FakeEncryptionService().apply { acceptedRecoveryKey = OLD_KEY }
        encryption.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE
        escrow.lockedKey = lock.lock(OLD_KEY, PASSWORD, A_SESSION_ID.value)
        handoff.put(A_SESSION_ID, PASSWORD)

        createProvisioner(encryption).ensureProvisioned()

        assertThat(encryption.lastRecoveryKey).isEqualTo(OLD_KEY)
        assertThat(localStore.keys[A_SESSION_ID]).isEqualTo(OLD_KEY)
        assertThat(escrow.lockedKeyStores).isEqualTo(0)
        assertThat(handoff.peek(A_SESSION_ID)).isNull()
    }

    @Test
    fun `a password reset elsewhere leaves the new session for manual verification`() = runTest {
        val encryption = FakeEncryptionService().apply { acceptedRecoveryKey = OLD_KEY }
        encryption.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE
        escrow.lockedKey = lock.lock(OLD_KEY, "old password", A_SESSION_ID.value)
        handoff.put(A_SESSION_ID, PASSWORD)

        createProvisioner(encryption).ensureProvisioned()

        assertThat(encryption.lastRecoveryKey).isNull()
        assertThat(escrow.lockedKey).isNotNull()
        // Пароль нужен после ручного подтверждения: ветка ENABLED им перезапрёт ключ.
        assertThat(handoff.peek(A_SESSION_ID)).isEqualTo(PASSWORD)
    }

    @Test
    fun `after manual verification the session reissues the key and locks it with the new password`() = runTest {
        val encryption = FakeEncryptionService(resetRecoveryKeyLambda = { Result.success(NEW_KEY) }).apply { acceptedRecoveryKey = OLD_KEY }
        encryption.recoveryStateStateFlow.value = RecoveryState.ENABLED
        escrow.lockedKey = lock.lock(OLD_KEY, "old password", A_SESSION_ID.value)
        handoff.put(A_SESSION_ID, PASSWORD)

        createProvisioner(encryption).ensureProvisioned()

        assertThat(escrow.opened(PASSWORD)).isEqualTo(NEW_KEY)
        assertThat(localStore.keys[A_SESSION_ID]).isEqualTo(NEW_KEY)
    }

    @Test
    fun `old account migrates - the v1 server key unlocks, then is replaced by a blob`() = runTest {
        val encryption = FakeEncryptionService().apply { acceptedRecoveryKey = OLD_KEY }
        encryption.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE
        escrow.serverKey = OLD_KEY
        handoff.put(A_SESSION_ID, PASSWORD)

        createProvisioner(encryption).ensureProvisioned()

        assertThat(encryption.lastRecoveryKey).isEqualTo(OLD_KEY)
        assertThat(escrow.opened(PASSWORD)).isEqualTo(OLD_KEY)
        assertThat(escrow.serverKey).isNull()
    }

    @Test
    fun `with server recovery switched on the server key is kept next to the blob`() = runTest {
        val encryption = FakeEncryptionService().apply { acceptedRecoveryKey = OLD_KEY }
        encryption.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE
        escrow.serverKey = OLD_KEY
        escrow.serverKeyOptIn = true
        handoff.put(A_SESSION_ID, PASSWORD)

        createProvisioner(encryption).ensureProvisioned()

        assertThat(escrow.opened(PASSWORD)).isEqualTo(OLD_KEY)
        assertThat(escrow.serverKey).isEqualTo(OLD_KEY)
        assertThat(escrow.serverKeyOptIn).isTrue()
    }

    @Test
    fun `QR login of a migrated account gets nothing from the server and does not nag`() = runTest {
        val encryption = FakeEncryptionService().apply { acceptedRecoveryKey = OLD_KEY }
        encryption.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE
        escrow.lockedKey = lock.lock(OLD_KEY, PASSWORD, A_SESSION_ID.value)

        val provisioner = createProvisioner(encryption)
        provisioner.ensureProvisioned()

        assertThat(encryption.lastRecoveryKey).isNull()
        assertThat(provisioner.needsPassword.value).isFalse()
    }

    @Test
    fun `a stale blob that opens but does not unlock is removed`() = runTest {
        val encryption = FakeEncryptionService().apply { acceptedRecoveryKey = NEW_KEY }
        encryption.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE
        escrow.lockedKey = lock.lock(OLD_KEY, PASSWORD, A_SESSION_ID.value)
        handoff.put(A_SESSION_ID, PASSWORD)

        createProvisioner(encryption).ensureProvisioned()

        assertThat(escrow.lockedKey).isNull()
    }

    @Test
    fun `pre-B session without a password asks for it when there is no blob`() = runTest {
        val encryption = FakeEncryptionService(resetRecoveryKeyLambda = { error("must not reset") })
        encryption.recoveryStateStateFlow.value = RecoveryState.ENABLED
        escrow.serverKey = OLD_KEY

        val provisioner = createProvisioner(encryption)
        provisioner.ensureProvisioned()

        assertThat(provisioner.needsPassword.value).isTrue()
        assertThat(escrow.serverKey).isEqualTo(OLD_KEY)
    }

    @Test
    fun `a session with a valid blob and the login password does nothing heavy`() = runTest {
        val encryption = FakeEncryptionService(resetRecoveryKeyLambda = { error("must not reset") }).apply { acceptedRecoveryKey = OLD_KEY }
        encryption.recoveryStateStateFlow.value = RecoveryState.ENABLED
        escrow.lockedKey = lock.lock(OLD_KEY, PASSWORD, A_SESSION_ID.value)
        handoff.put(A_SESSION_ID, PASSWORD)

        createProvisioner(encryption).ensureProvisioned()

        assertThat(escrow.lockedKeyStores).isEqualTo(0)
        assertThat(localStore.keys[A_SESSION_ID]).isEqualTo(OLD_KEY)
    }

    @Test
    fun `password change in the app relocks the local key with the new password`() = runTest {
        val encryption = FakeEncryptionService(resetRecoveryKeyLambda = { error("must not reset") }).apply { acceptedRecoveryKey = OLD_KEY }
        encryption.recoveryStateStateFlow.value = RecoveryState.ENABLED
        escrow.lockedKey = lock.lock(OLD_KEY, PASSWORD, A_SESSION_ID.value)
        localStore.keys[A_SESSION_ID] = OLD_KEY

        createProvisioner(encryption).onPasswordChanged("new password")

        assertThat(escrow.opened("new password")).isEqualTo(OLD_KEY)
        assertThat(escrow.opened(PASSWORD)).isNull()
        assertThat(handoff.peek(A_SESSION_ID)).isNull()
    }

    @Test
    fun `a stale local copy is never uploaded`() = runTest {
        val encryption = FakeEncryptionService(resetRecoveryKeyLambda = { Result.success(NEW_KEY) }).apply { acceptedRecoveryKey = NEW_KEY }
        encryption.recoveryStateStateFlow.value = RecoveryState.ENABLED
        localStore.keys[A_SESSION_ID] = OLD_KEY

        createProvisioner(encryption).onPasswordChanged(PASSWORD)

        assertThat(escrow.opened(PASSWORD)).isEqualTo(NEW_KEY)
    }

    @Test
    fun `nothing changes while escrow is unreachable`() = runTest {
        val encryption = FakeEncryptionService(
            enableRecoveryLambda = { _, _ -> error("must not enable") },
            resetRecoveryKeyLambda = { error("must not reset") },
        )
        encryption.recoveryStateStateFlow.value = RecoveryState.DISABLED
        escrow.unavailable = true
        handoff.put(A_SESSION_ID, PASSWORD)

        createProvisioner(encryption).ensureProvisioned()

        assertThat(handoff.peek(A_SESSION_ID)).isEqualTo(PASSWORD)
        assertThat(localStore.keys).isEmpty()
    }

    @Test
    fun `a key created on Element's screens is kept and locked`() = runTest {
        val encryption = FakeEncryptionService()
        encryption.recoveryStateStateFlow.value = RecoveryState.ENABLED
        handoff.put(A_SESSION_ID, PASSWORD)

        createProvisioner(encryption).onRecoveryKeyCreated(NEW_KEY)

        assertThat(escrow.opened(PASSWORD)).isEqualTo(NEW_KEY)
        assertThat(localStore.keys[A_SESSION_ID]).isEqualTo(NEW_KEY)
    }

    private fun TestScope.createProvisioner(encryptionService: FakeEncryptionService) = DefaultRecoveryKeyAutoProvisioner(
        matrixClient = FakeMatrixClient(sessionId = A_SESSION_ID, encryptionService = encryptionService),
        keyEscrowService = escrow,
        localKeyStore = localStore,
        passwordHandoff = handoff,
        recoveryKeyLock = lock,
        dispatchers = testCoroutineDispatchers(),
    )

    private companion object {
        const val PASSWORD = "correct horse"
        const val OLD_KEY = "EsTc 5rr1 4fJY BvG1 x8Ci ZcYa 3PdS Xa6A PdKx zEsK q7Ap yupT"
        const val NEW_KEY = "EsTd 5rr1 4fJY BvG1 x8Ci ZcYa 3PdS Xa6A PdKx zEsK q7Ap yupT"
    }
}

private class InMemoryLocalRecoveryKeyStore : LocalRecoveryKeyStore {
    val keys = mutableMapOf<SessionId, String>()

    override suspend fun get(sessionId: SessionId): String? = keys[sessionId]

    override suspend fun put(sessionId: SessionId, recoveryKey: String) {
        keys[sessionId] = recoveryKey
    }

    override suspend fun remove(sessionId: SessionId) {
        keys.remove(sessionId)
    }
}
