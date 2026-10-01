/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl

import android.os.Parcelable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.pop
import com.bumble.appyx.navmodel.backstack.operation.push
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.login.api.LoginEntryPoint
import io.element.android.features.login.impl.screens.tg.TgAuthNode
import io.element.android.features.preferences.api.PreferencesEntryPoint
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import kotlinx.parcelize.Parcelize

// Правка форка: вход Larpgram — один свой экран с шагами (приветствие → вход → регистрация), без
// браузера. Экраны входа Element (проверка Classic, онбординг, выбор сервера, пароль, QR, вкладка
// MAS) из форка удалены 2026-10-01; при синке их файлы остаются удалёнными (sync-upstream.sh).
@ContributesNode(AppScope::class)
@AssistedInject
class LoginFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val preferencesEntryPoint: PreferencesEntryPoint,
) : BaseFlowNode<LoginFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = NavTarget.TgAuth,
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
) {
    // Параметры ссылки входа Element (сервер, подсказка логина). Сервер у Larpgram один, поэтому
    // они не используются; тип остался, потому что его передаёт точка входа.
    data class Params(
        val accountProvider: String?,
        val loginHint: String?,
    ) : NodeInputs

    private val callback: LoginEntryPoint.Callback = callback()

    sealed interface NavTarget : Parcelable {
        @Parcelize
        data object TgAuth : NavTarget

        @Parcelize
        data object AppDeveloperSettings : NavTarget
    }

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            NavTarget.TgAuth -> {
                val callback = object : TgAuthNode.Callback {
                    override fun navigateToBugReport() {
                        callback.navigateToBugReport()
                    }

                    override fun navigateToDeveloperSettings() {
                        backstack.push(NavTarget.AppDeveloperSettings)
                    }

                    override fun onDone() {
                        callback.onDone()
                    }
                }
                createNode<TgAuthNode>(buildContext, listOf(callback))
            }
            NavTarget.AppDeveloperSettings -> {
                val callback = object : PreferencesEntryPoint.DeveloperSettingsCallback {
                    override fun onDone() {
                        backstack.pop()
                    }
                }
                preferencesEntryPoint.createAppDeveloperSettingsNode(
                    parentNode = this,
                    buildContext = buildContext,
                    callback = callback,
                )
            }
        }
    }

    @Composable
    override fun View(modifier: Modifier) {
        BackstackView()
    }
}
