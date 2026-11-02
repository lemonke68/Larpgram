/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.features.viewfolder.api

import androidx.compose.runtime.staticCompositionLocalOf

/** A room viewer can disable line copying and supply an authoritative check before export. */
val LocalTextFileCopyAllowed = staticCompositionLocalOf { true }
val LocalTextFileCopyGate = staticCompositionLocalOf<suspend () -> Boolean> { { true } }
