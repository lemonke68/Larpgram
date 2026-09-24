/*
 * Модуль форка: депонирование ключа восстановления (вход по коду с почты).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.keyescrow.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.keyescrow.test.FakeKeyEscrowService
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.encryption.FakeEncryptionService
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultRecoveryKeyAutoProvisionerTest {
    @Test
    fun `fresh account gets a recovery key that goes to escrow`() = runTest {
        val encryptionService = FakeEncryptionService(enableRecoveryLambda = { _, _ -> Result.success("NEW KEY") })
        encryptionService.recoveryStateStateFlow.value = RecoveryState.DISABLED
        val escrow = FakeKeyEscrowService(hasStoredKeyResult = false)

        createProvisioner(encryptionService, escrow).ensureProvisioned()

        assertThat(escrow.storedKey).isEqualTo("NEW KEY")
    }

    @Test
    fun `new session of an account with recovery is unlocked with the escrowed key without a code`() = runTest {
        val encryptionService = FakeEncryptionService()
        encryptionService.recoveryStateStateFlow.value = RecoveryState.INCOMPLETE
        val escrow = FakeKeyEscrowService(fetchSessionKeyLambda = { "ESCROWED KEY" })

        createProvisioner(encryptionService, escrow).ensureProvisioned()

        assertThat(encryptionService.lastRecoveryKey).isEqualTo("ESCROWED KEY")
        assertThat(escrow.storedKey).isNull()
    }

    @Test
    fun `account with recovery but nothing in escrow gets a new key escrowed`() = runTest {
        val encryptionService = FakeEncryptionService(resetRecoveryKeyLambda = { Result.success("RESET KEY") })
        encryptionService.recoveryStateStateFlow.value = RecoveryState.ENABLED
        val escrow = FakeKeyEscrowService(hasStoredKeyResult = false)

        createProvisioner(encryptionService, escrow).ensureProvisioned()

        assertThat(escrow.storedKey).isEqualTo("RESET KEY")
    }

    @Test
    fun `nothing is reset when escrow is unreachable`() = runTest {
        val encryptionService = FakeEncryptionService(resetRecoveryKeyLambda = { error("must not reset") })
        encryptionService.recoveryStateStateFlow.value = RecoveryState.ENABLED
        val escrow = FakeKeyEscrowService(hasStoredKeyResult = null)

        createProvisioner(encryptionService, escrow).ensureProvisioned()

        assertThat(escrow.storedKey).isNull()
    }

    private fun TestScope.createProvisioner(
        encryptionService: FakeEncryptionService,
        escrow: FakeKeyEscrowService,
    ) = DefaultRecoveryKeyAutoProvisioner(
        matrixClient = FakeMatrixClient(encryptionService = encryptionService),
        keyEscrowService = escrow,
        dispatchers = testCoroutineDispatchers(),
    )
}
