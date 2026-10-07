/*
 * Copyright (c) 2026 Larpgram.
 * Правка форка: баннеры списка чатов — почта, старые сессии, обновление — и тихая заводка ключа
 * восстановления. Вынесено из `RoomListPresenter` (аудит C-009).
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.accountemail.api.AccountEmailStatus
import io.element.android.libraries.appupdate.api.UpdateChecker
import io.element.android.libraries.appupdate.api.UpdateInstallState
import io.element.android.libraries.appupdate.api.UpdateInstaller
import io.element.android.libraries.appupdate.api.UpdateStatus
import io.element.android.libraries.keyescrow.api.RecoveryKeyAutoProvisioner
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.encryption.RecoveryState
import io.element.android.libraries.matrix.api.oauth.AccountManagementAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

// Почта аккаунта: повторы при неудачном запросе и переспрос, пока баннер на экране.
private const val EMAIL_RETRY_MS = 20_000L
private const val EMAIL_MAX_RETRIES = 5
private const val EMAIL_RECHECK_MS = 15_000L
private const val EMAIL_MAX_RECHECKS = 40

@Inject
class RoomListForkBanners(
    private val client: MatrixClient,
    private val accountEmailStatus: AccountEmailStatus,
    private val updateChecker: UpdateChecker,
    private val updateInstaller: UpdateInstaller,
    private val recoveryKeyAutoProvisioner: RecoveryKeyAutoProvisioner,
) {
    data class State(
        val showConnectEmailBanner: Boolean,
        val accountManagementUrl: String?,
        val showCleanUpSessionsBanner: Boolean,
        val manageSessionsUrl: String?,
        val updateBanner: UpdateBannerState?,
        /** Escrow B: попросить пароль, чтобы запереть им ключ к истории. */
        val showProtectHistoryBanner: Boolean,
        /** Закрыть баннер почты, сессий или обновления, поставить обновление. */
        val eventSink: (RoomListEvent) -> Unit,
    )

    @Composable
    fun present(): State {
        val coroutineScope = rememberCoroutineScope()

        // Молча заводим ключ восстановления + escrow (идемпотентно): новые и сброшенные сессии
        // открывают историю паролем со входа и не ловят UTD. Заново — на каждой смене состояния
        // восстановления: после ручного подтверждения сессии ключ надо запереть паролем (escrow B).
        LaunchedEffect(Unit) {
            client.encryptionService.recoveryStateStateFlow
                .filter { it == RecoveryState.ENABLED || it == RecoveryState.DISABLED || it == RecoveryState.INCOMPLETE }
                .collect { recoveryKeyAutoProvisioner.ensureProvisioned() }
        }

        // Баннер про почту. hasEmail() == false, а не != true: null означает «не дозвонились до
        // сервера», и на нём баннер показывать нельзя, иначе он выскочит в самолётном режиме у того,
        // у кого почта давно привязана. Поэтому на null спрашиваем ещё несколько раз, а не молчим до
        // следующего запуска. Пока баннер висит, переспрашиваем изредка: почту привязывают в
        // настройках приложения, и после возврата на список баннер должен уйти сам.
        var connectEmailBannerDismissed by rememberSaveable { mutableStateOf(false) }
        val needsPassword by recoveryKeyAutoProvisioner.needsPassword.collectAsState()
        var protectHistoryBannerDismissed by rememberSaveable { mutableStateOf(false) }
        val accountNeedsEmail by produceState(false) {
            if (accountEmailStatus.isBannerHidden()) return@produceState
            var failures = 0
            var rechecks = 0
            while (true) {
                when (accountEmailStatus.hasEmail()) {
                    true -> {
                        value = false
                        return@produceState
                    }
                    false -> {
                        value = true
                        if (++rechecks > EMAIL_MAX_RECHECKS) return@produceState
                        delay(EMAIL_RECHECK_MS)
                    }
                    null -> {
                        if (++failures > EMAIL_MAX_RETRIES) return@produceState
                        delay(EMAIL_RETRY_MS)
                    }
                }
            }
        }
        // Адрес спрашиваем только когда баннер и правда нужен: у тех, кто почту уже привязал, это
        // лишний поход в сеть на каждом открытии списка чатов.
        val accountManagementUrl by produceState<String?>(null, accountNeedsEmail) {
            value = if (accountNeedsEmail) {
                client.getAccountManagementUrl(AccountManagementAction.Profile).getOrNull()
            } else {
                null
            }
        }

        // Баннер про очистку старых сессий. isLastDevice == false означает, что у аккаунта есть
        // другие сессии — обычно брошенная старая после переустановки без разлогина. Реальное
        // значение приходит быстрее сетевого запроса URL ниже, поэтому ложного мелькания на одном
        // устройстве нет.
        var cleanUpSessionsBannerDismissed by rememberSaveable { mutableStateOf(false) }
        val isLastDevice by client.encryptionService.isLastDevice.collectAsState()
        val hasOtherSessions = !isLastDevice
        val manageSessionsUrl by produceState<String?>(null, hasOtherSessions, cleanUpSessionsBannerDismissed) {
            value = if (hasOtherSessions && !cleanUpSessionsBannerDismissed) {
                client.getAccountManagementUrl(AccountManagementAction.DevicesList).getOrNull()
            } else {
                null
            }
        }

        // Баннер обновления. Спрашиваем манифест один раз за показ экрана, как и почту. check() уже
        // отсекает старые и отклонённые версии, поэтому здесь достаточно проверить, что вернулось
        // Available. Ход загрузки и установки — из UpdateInstaller.
        var updateBannerDismissed by rememberSaveable { mutableStateOf(false) }
        val updateStatus by produceState<UpdateStatus>(UpdateStatus.Unknown) {
            value = updateChecker.check()
        }
        val updateInstallState by updateInstaller.state.collectAsState()
        val updateBanner = (updateStatus as? UpdateStatus.Available)
            // Пока идёт установка, баннер не прячем, даже если его закрыли раньше.
            ?.takeIf { !updateBannerDismissed || updateInstallState != UpdateInstallState.Idle }
            ?.let { UpdateBannerState(versionName = it.versionName, installState = updateInstallState) }

        fun handleEvent(event: RoomListEvent) {
            when (event) {
                RoomListEvent.DismissConnectEmailBanner -> {
                    connectEmailBannerDismissed = true
                    coroutineScope.launch { accountEmailStatus.hideBanner() }
                }
                RoomListEvent.DismissCleanUpSessionsBanner -> cleanUpSessionsBannerDismissed = true
                RoomListEvent.DismissProtectHistoryBanner -> protectHistoryBannerDismissed = true
                RoomListEvent.DismissUpdateBanner -> {
                    updateBannerDismissed = true
                    // Запоминаем именно эту версию, чтобы следующая, ещё более свежая, снова
                    // показала баннер. Если статус ещё не подъехал — прячем только на сессию.
                    (updateStatus as? UpdateStatus.Available)?.let { available ->
                        coroutineScope.launch { updateChecker.dismiss(available.versionCode) }
                    }
                }
                RoomListEvent.InstallUpdate -> (updateStatus as? UpdateStatus.Available)?.let(updateInstaller::install)
                else -> Unit
            }
        }

        return State(
            // Оба баннера ведут на свои экраны настроек («Почта», «Устройства»), поэтому адрес
            // страницы MAS им больше не нужен: он остаётся запасным путём, если экран не открыть.
            showConnectEmailBanner = accountNeedsEmail && !connectEmailBannerDismissed,
            accountManagementUrl = accountManagementUrl,
            showCleanUpSessionsBanner = hasOtherSessions && !cleanUpSessionsBannerDismissed,
            manageSessionsUrl = manageSessionsUrl,
            updateBanner = updateBanner,
            showProtectHistoryBanner = needsPassword && !protectHistoryBannerDismissed,
            eventSink = ::handleEvent,
        )
    }
}
