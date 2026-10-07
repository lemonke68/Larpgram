/*
 * Copyright (c) 2026 Larpgram.
 * Модуль форка: ключ восстановления, запертый паролем (escrow вариант B).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.keyescrow.api

/**
 * Ключ восстановления, запертый паролем аккаунта: KEK = Argon2id(пароль, [salt], [memoryKib],
 * [iterations], [parallelism]), шифр AES-256-GCM с user id в AAD. Сервер хранит это как есть и
 * открыть не может. Параметры лежат рядом с шифротекстом, поэтому их можно поднимать, не ломая
 * старые блобы. Байтовые поля — base64 без переносов.
 */
data class LockedRecoveryKey(
    val version: Int,
    val kdf: String,
    val memoryKib: Int,
    val iterations: Int,
    val parallelism: Int,
    val salt: String,
    val nonce: String,
    val ciphertext: String,
)
