/*
 * Правка форка: баннер с предложением обновить приложение.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.home.impl.roomlist.UpdateBannerState
import io.element.android.libraries.appupdate.api.UpdateInstallState
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight

/**
 * Баннер «вышло обновление» для раздачи мимо магазина. Тап качает APK прямо в приложении и
 * открывает системное окно «Обновить?» (см. `UpdateInstaller`); пока идёт загрузка, баннер
 * показывает проценты и не закрывается.
 *
 * Текст в коде, а не в ресурсах: свои строки в файлах Localazy затирает при обновлении
 * переводов, а аудитория у нас русскоязычная.
 */
@Composable
internal fun UpdateBanner(
    state: UpdateBannerState,
    onClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val install = state.installState
    val (title, message) = when (install) {
        UpdateInstallState.Idle -> "Вышло обновление Larpgram ${state.versionName}" to "Нажмите, чтобы обновить."
        is UpdateInstallState.Downloading -> {
            val percent = install.progress?.let { " ${(it * 100).toInt()}%" }.orEmpty()
            "Загружаем обновление…$percent" to "После установки приложение откроется заново."
        }
        UpdateInstallState.WaitingForConfirmation -> "Подтвердите установку" to "Нажмите «Обновить» в окне Android."
        UpdateInstallState.NeedsPermission -> "Разрешите установку обновлений" to "Включите «Разрешить из этого источника», вернитесь и нажмите сюда."
        UpdateInstallState.Failed -> "Не удалось обновиться" to "Проверьте интернет и нажмите, чтобы попробовать снова."
    }
    val isBusy = install is UpdateInstallState.Downloading
    TgHintBanner(
        modifier = modifier,
        title = title,
        message = message,
        onClick = { if (!isBusy) onClick() },
        // Загрузку не прячем: крестик вернётся, когда она закончится.
        onDismissClick = onDismissClick.takeIf { !isBusy },
        isError = install == UpdateInstallState.Failed,
        progress = (install as? UpdateInstallState.Downloading)?.let { it.progress ?: INDETERMINATE_PROGRESS },
    )
}

@PreviewsDayNight
@Composable
internal fun UpdateBannerPreview(@PreviewParameter(UpdateBannerStateProvider::class) state: UpdateBannerState) = ElementPreview {
    UpdateBanner(
        state = state,
        onClick = {},
        onDismissClick = {},
    )
}

internal class UpdateBannerStateProvider : PreviewParameterProvider<UpdateBannerState> {
    override val values = sequenceOf(
        UpdateInstallState.Idle,
        UpdateInstallState.Downloading(progress = 0.42f),
        UpdateInstallState.WaitingForConfirmation,
        UpdateInstallState.Failed,
    ).map { UpdateBannerState(versionName = "0.3.1", installState = it) }
}
