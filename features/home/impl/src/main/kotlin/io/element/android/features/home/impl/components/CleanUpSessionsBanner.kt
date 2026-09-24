/*
 * Правка форка: баннер с предложением почистить старые сессии.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.home.impl.components

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight

/**
 * Баннер про очистку старых сессий.
 *
 * Появляется, когда у аккаунта есть другие сессии (`isLastDevice == false`). Обычно это
 * брошенная старая сессия после переустановки без разлогина: с ONLY_TRUSTED_DEVICES она молча
 * не получает новые сообщения, а висит зря. Кнопка ведёт на страницу управления сессиями MAS
 * в браузере, как и баннер про почту — своего экрана списка сессий в приложении нет.
 */
@Composable
internal fun CleanUpSessionsBanner(
    manageSessionsUrl: String?,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
    val isDark = !ElementTheme.isLightTheme
    CleanUpSessionsBannerView(
        onContinueClick = {
            if (activity != null && manageSessionsUrl != null) {
                activity.openUrlInChromeCustomTab(null, darkTheme = isDark, url = manageSessionsUrl)
            }
        },
        onDismissClick = onDismissClick,
        modifier = modifier,
    )
}

@Composable
private fun CleanUpSessionsBannerView(
    onContinueClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Правка форка: компактная подсказка TG вместо карточки с кнопкой.
    TgHintBanner(
        modifier = modifier,
        title = "Проверьте свои сессии",
        message = "Есть и другие входы в аккаунт. Нажмите, чтобы завершить лишние.",
        onClick = onContinueClick,
        onDismissClick = onDismissClick,
    )
}

// Превью зовёт именно CleanUpSessionsBannerView: у обёртки выше внутри браузер, а имя
// превью по правилам konsist должно совпадать с тем, что оно рисует.
@PreviewsDayNight
@Composable
internal fun CleanUpSessionsBannerViewPreview() = ElementPreview {
    CleanUpSessionsBannerView(
        onContinueClick = {},
        onDismissClick = {},
    )
}
