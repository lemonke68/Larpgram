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
import io.element.android.libraries.matrix.api.oauth.AccountManagementAction
import kotlinx.coroutines.launch

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
        /** Закрыть баннер почты, сессий или обновления, поставить обновление. */
        val eventSink: (RoomListEvent) -> Unit,
    )

    @Composable
    fun present(): State {
        val coroutineScope = rememberCoroutineScope()

        // Молча заводим ключ восстановления + escrow, если у аккаунта его ещё нет (идемпотентно,
        // гейт внутри по RecoveryState.DISABLED): новые и сброшенные сессии восстанавливаются
        // кодом с почты и не ловят UTD.
        LaunchedEffect(Unit) {
            recoveryKeyAutoProvisioner.ensureProvisioned()
        }

        // Баннер про почту. Спрашиваем один раз за показ экрана, а не подпиской: адрес сам собой не
        // появляется и не исчезает, а привязка происходит в браузере, после чего человек всё равно
        // возвращается в приложение заново.
        //
        // hasEmail() == false, а не != true: null означает «не дозвонились до сервера», и на нём
        // баннер показывать нельзя, иначе он выскочит в самолётном режиме у того, у кого почта давно
        // привязана.
        var connectEmailBannerDismissed by rememberSaveable { mutableStateOf(false) }
        val accountNeedsEmail by produceState(false) {
            value = !accountEmailStatus.isBannerHidden() && accountEmailStatus.hasEmail() == false
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
            // Баннер про почту показываем, только если знаем, куда вести человека.
            showConnectEmailBanner = accountNeedsEmail && !connectEmailBannerDismissed && accountManagementUrl != null,
            accountManagementUrl = accountManagementUrl,
            // То же и с сессиями: без адреса страницы управления вести некуда.
            showCleanUpSessionsBanner = hasOtherSessions && !cleanUpSessionsBannerDismissed && manageSessionsUrl != null,
            manageSessionsUrl = manageSessionsUrl,
            updateBanner = updateBanner,
            eventSink = ::handleEvent,
        )
    }
}
