/*
 * Правка форка: баннер с предложением обновить приложение.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.components

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.designsystem.components.Announcement
import io.element.android.libraries.designsystem.components.AnnouncementType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight

/**
 * Баннер «вышло обновление» для раздачи мимо магазина.
 *
 * Кнопка открывает страницу раздачи в браузере, а не качает APK сама: так не нужно право
 * REQUEST_INSTALL_PACKAGES и свой установщик, а человек ставит файл ровно тем же путём,
 * что и в первый раз. Адрес захардкожен, как homeserver: клиент всё равно только наш.
 *
 * Текст в коде, а не в ресурсах: свои строки в файлах Localazy затирает при обновлении
 * переводов, а аудитория у нас русскоязычная.
 */
@Composable
internal fun UpdateBanner(
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
    val isDark = !ElementTheme.isLightTheme
    UpdateBannerView(
        onContinueClick = {
            if (activity != null) {
                activity.openUrlInChromeCustomTab(null, darkTheme = isDark, url = DOWNLOAD_PAGE_URL)
            }
        },
        onDismissClick = onDismissClick,
        modifier = modifier,
    )
}

@Composable
private fun UpdateBannerView(
    onContinueClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Announcement(
        modifier = modifier.roomListBannerPadding(),
        title = "Вышло обновление",
        description = "Доступна свежая версия Larpgram. Откройте страницу и обновитесь, чтобы не пропустить исправления.",
        type = AnnouncementType.Actionable(
            actionText = "Обновить",
            onActionClick = onContinueClick,
            onDismissClick = onDismissClick,
        ),
    )
}

// Страница раздачи Larpgram, там же лежит манифест версии для проверки обновлений.
private const val DOWNLOAD_PAGE_URL = "https://larpgram.mango-kokos.ru"

// Превью зовёт именно UpdateBannerView: у обёртки выше внутри браузер, а имя превью по
// правилам konsist должно совпадать с тем, что оно рисует.
@PreviewsDayNight
@Composable
internal fun UpdateBannerViewPreview() = ElementPreview {
    UpdateBannerView(
        onContinueClick = {},
        onDismissClick = {},
    )
}
