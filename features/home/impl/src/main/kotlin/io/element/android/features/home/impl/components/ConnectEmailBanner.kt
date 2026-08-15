/*
 * Правка форка: напоминание привязать почту к аккаунту.
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
 * Баннер для тех, кто зарегистрировался до того, как почта стала обязательной.
 *
 * Привязка живёт на странице аккаунта MAS, поэтому кнопка открывает браузер, как это уже
 * делает «Управление аккаунтом» в настройках. Своего экрана для этого не завести: почтой
 * владеет MAS, а не Synapse, и в CS API её добавления нет.
 *
 * Текст написан прямо в коде, а не в ресурсах: свои строки в файлах Localazy затирает
 * при обновлении переводов, а аудитория у нас русскоязычная.
 *
 * Слово «двухфакторка» намеренно не используется: MAS по почте восстанавливает пароль,
 * вторым фактором при входе она не становится. Обещать в баннере то, чего нет, нельзя.
 */
@Composable
internal fun ConnectEmailBanner(
    accountManagementUrl: String?,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
    val isDark = !ElementTheme.isLightTheme
    ConnectEmailBannerView(
        onContinueClick = {
            if (activity != null && accountManagementUrl != null) {
                activity.openUrlInChromeCustomTab(null, darkTheme = isDark, url = accountManagementUrl)
            }
        },
        onDismissClick = onDismissClick,
        modifier = modifier,
    )
}

@Composable
private fun ConnectEmailBannerView(
    onContinueClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Announcement(
        modifier = modifier.roomListBannerPadding(),
        title = "Привяжите почту",
        description = "Без неё восстановить доступ к аккаунту нечем: забытый пароль сбросить не получится.",
        type = AnnouncementType.Actionable(
            actionText = "Привязать",
            onActionClick = onContinueClick,
            onDismissClick = onDismissClick,
        ),
    )
}

// Превью зовёт именно ConnectEmailBannerView: у обёртки выше внутри браузер, а имя
// превью по правилам konsist должно совпадать с тем, что оно рисует.
@PreviewsDayNight
@Composable
internal fun ConnectEmailBannerViewPreview() = ElementPreview {
    ConnectEmailBannerView(
        onContinueClick = {},
        onDismissClick = {},
    )
}
