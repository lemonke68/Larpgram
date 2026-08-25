/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.userprofile.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * TG-style profile info card, shared by the self profile, group-member profiles and DM
 * profiles. A Matrix profile carries only the id and an optional bio, so rows are: bio
 * ("About"/"О себе") when set, then the @handle (localpart of the Matrix id) over a
 * "Username" label. Tapping the handle copies the full id to the clipboard.
 */
@Composable
fun UserProfileInfoCard(
    userId: UserId,
    about: String?,
    onHandleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val handle = userId.value.substringBefore(":")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ElementTheme.colors.bgSubtleSecondary),
    ) {
        // Bio row is shown only when set; TG order puts it above the handle.
        if (!about.isNullOrBlank()) {
            UserProfileInfoCardRow(
                value = about,
                label = stringResource(CommonStrings.larpgram_profile_about_label),
            )
        }
        UserProfileInfoCardRow(
            value = handle,
            label = stringResource(CommonStrings.common_username),
            onClick = onHandleClick,
        )
    }
}

@Composable
private fun UserProfileInfoCardRow(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = value,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textPrimary,
        )
        Text(
            text = label,
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}
