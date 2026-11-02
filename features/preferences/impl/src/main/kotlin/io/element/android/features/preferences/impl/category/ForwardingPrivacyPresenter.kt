/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.matrix.ui.forward.ForwardingPrivacy
import kotlinx.coroutines.launch

data class ForwardingPrivacyState(
    val allowed: Boolean?,
    val isBusy: Boolean,
    val change: (Boolean) -> Unit,
    val retry: () -> Unit,
)

@Inject
class ForwardingPrivacyPresenter(
    private val privacy: ForwardingPrivacy,
    private val snackbar: SnackbarDispatcher,
) : Presenter<ForwardingPrivacyState> {
    @Composable
    override fun present(): ForwardingPrivacyState {
        val scope = rememberCoroutineScope()
        var allowed by remember { mutableStateOf<Boolean?>(null) }
        var busy by remember { mutableStateOf(true) }
        suspend fun refresh() {
            busy = true
            privacy.ownPreference().fold(
                onSuccess = { allowed = it },
                onFailure = {
                    allowed = null
                    snackbar.post(SnackbarMessage(R.string.larpgram_forwarding_privacy_error))
                },
            )
            busy = false
        }
        LaunchedEffect(privacy) { refresh() }
        return ForwardingPrivacyState(
            allowed = allowed,
            isBusy = busy,
            change = { value ->
                if (!busy && allowed != null && value != allowed) {
                    busy = true
                    scope.launch {
                        privacy.setOwnPreference(value).fold(
                            onSuccess = { allowed = value },
                            onFailure = { snackbar.post(SnackbarMessage(R.string.larpgram_forwarding_privacy_error)) },
                        )
                        busy = false
                    }
                }
            },
            retry = {
                if (!busy) {
                    busy = true
                    scope.launch { refresh() }
                }
            },
        )
    }
}
