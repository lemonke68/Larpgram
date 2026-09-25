/*
 * Модуль форка: установка обновления из приложения.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.appupdate.impl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * Приложение обновилось поверх себя: система убила старый процесс, открываем новую версию,
 * чтобы после «Обновить» человек сразу оказался в приложении, а не на рабочем столе.
 */
class UpdateAppliedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { Timber.w(it, "Не удалось открыть приложение после обновления") }
    }
}
