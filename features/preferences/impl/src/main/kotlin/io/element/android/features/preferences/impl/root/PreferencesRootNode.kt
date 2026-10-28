/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.emoji.api.picker.EmojiPickerRenderer
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.permalink.PermalinkBuilder
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import io.element.android.libraries.matrix.ui.profile.PublicBio
import io.element.android.libraries.matrix.ui.saved.SavedMessages
import io.element.android.libraries.musicplayer.api.MusicPlayerUi
import kotlinx.coroutines.launch

@ContributesNode(SessionScope::class)
@AssistedInject
class PreferencesRootNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: PreferencesRootPresenter,
    private val emojiPickerRenderer: EmojiPickerRenderer,
    private val savedMessages: SavedMessages,
    // Правка форка (Larpgram V1 final): вкладка «Профиль» = профиль + настройки.
    private val matrixClient: MatrixClient,
    private val publicBio: PublicBio,
    private val musicPlayerUi: MusicPlayerUi,
    private val permalinkBuilder: PermalinkBuilder,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        fun navigateToCategory(category: SettingsCategory)
        fun navigateToAddAccount()

        // Правка форка (Larpgram): Element v26.10 switches account via navigation.
        fun navigateToSession(sessionId: SessionId)
        fun navigateToBugReport()
        fun navigateToAbout()
        fun navigateToDeveloperSettings()
        fun navigateToLabs()
        fun navigateToAdvancedSettings()
        fun navigateToUserProfile(matrixUser: MatrixUser)
        fun navigateToRoom(roomId: RoomId)

        // Правка форка (V1 final): строка «Шифрование и ключи» и предупреждение о неподтверждённой сессии.
        fun navigateToSecureBackup() = Unit

        // Правка форка: во вкладке «Настройки» стрелки назад нет.
        val showBackButton: Boolean get() = true
    }

    private val callback: Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        val coroutineScope = rememberCoroutineScope()
        val about by produceState<String?>(null) { value = publicBio.ownBio() }
        val recoveryState by matrixClient.encryptionService.recoveryStateStateFlow.collectAsState()
        val verifiedStatus by matrixClient.sessionVerificationService.sessionVerifiedStatus.collectAsState()
        val profileLink = remember { permalinkBuilder.permalinkForUser(matrixClient.sessionId).getOrNull() }
        val extras = TgProfileExtras(
            about = about,
            isKeyBackupMissing = recoveryState == RecoveryState.DISABLED,
            isSessionUnverified = verifiedStatus == SessionVerifiedStatus.NotVerified,
            profileLink = profileLink,
            profileMusic = { musicModifier -> musicPlayerUi.ProfileMusicRow(matrixClient.sessionId, state.myUser.displayName, musicModifier) },
        )
        // Правка форка: корень настроек Telegram, элементовский PreferencesRootView не трогаем (C-009).
        TgPreferencesRootView(
            state = state,
            emojiPickerRenderer = emojiPickerRenderer,
            modifier = modifier,
            onBackClick = if (callback.showBackButton) this::navigateUp else null,
            onAddAccountClick = callback::navigateToAddAccount,
            onSwitchToSession = callback::navigateToSession,
            onOpenCategory = callback::navigateToCategory,
            onOpenUserProfile = callback::navigateToUserProfile,
            onOpenAbout = callback::navigateToAbout,
            onOpenRageShake = callback::navigateToBugReport,
            onOpenLabs = callback::navigateToLabs,
            onOpenDeveloperSettings = callback::navigateToDeveloperSettings,
            onOpenAdvancedSettings = callback::navigateToAdvancedSettings,
            onOpenEncryption = callback::navigateToSecureBackup,
            extras = extras,
            // Правка форка: «Избранное» создаётся при первом открытии.
            onOpenSavedMessages = {
                coroutineScope.launch {
                    savedMessages.getOrCreate().onSuccess(callback::navigateToRoom)
                }
            },
        )
    }
}
