/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.element.android.features.home.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight

/**
 * Escrow вариант B: ключ к истории не заперт паролем аккаунта, а пароля у приложения нет (вошли по
 * QR, аккаунт до варианта B). Тап — экран «Защита истории» в настройках, там вводят пароль.
 */
@Composable
internal fun ProtectHistoryBanner(
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalTgAccountNavigator.current
    ProtectHistoryBannerView(
        onClick = { navigator?.openHistoryProtection() },
        onDismissClick = onDismissClick,
        modifier = modifier,
    )
}

@Composable
private fun ProtectHistoryBannerView(
    onClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TgHintBanner(
        modifier = modifier,
        title = stringResource(R.string.larpgram_banner_history_title),
        message = stringResource(R.string.larpgram_banner_history_message),
        onClick = onClick,
        onDismissClick = onDismissClick,
    )
}

// Превью зовёт ProtectHistoryBannerView: у обёртки внутри навигация (см. ConnectEmailBanner).
@PreviewsDayNight
@Composable
internal fun ProtectHistoryBannerViewPreview() = ElementPreview {
    ProtectHistoryBannerView(
        onClick = {},
        onDismissClick = {},
    )
}
