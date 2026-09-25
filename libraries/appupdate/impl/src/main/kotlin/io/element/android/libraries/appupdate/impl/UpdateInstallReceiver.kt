/*
 * Модуль форка: установка обновления из приложения.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.appupdate.impl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import timber.log.Timber

/**
 * Ответы PackageInstaller по сессии обновления. На STATUS_PENDING_USER_ACTION открывает
 * системное окно подтверждения, остальное пересылает в [DefaultUpdateInstaller].
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
            if (confirm != null) {
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { Timber.w(it, "Не открылось окно подтверждения установки") }
            }
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            Timber.w("Установка обновления: статус $status, ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
        onResult?.invoke(status)
    }

    companion object {
        /** Ставит [DefaultUpdateInstaller]; ресивер создаёт система, DI до него не дотянуть. */
        @Volatile
        internal var onResult: ((Int) -> Unit)? = null
    }
}
