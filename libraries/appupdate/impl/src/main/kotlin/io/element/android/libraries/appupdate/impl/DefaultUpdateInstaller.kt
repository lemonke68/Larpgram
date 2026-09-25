/*
 * Модуль форка: установка обновления из приложения.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.appupdate.impl

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.appupdate.api.UpdateInstallState
import io.element.android.libraries.appupdate.api.UpdateInstaller
import io.element.android.libraries.appupdate.api.UpdateStatus
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.di.annotations.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

/**
 * Качает APK в кэш, сверяет sha256 из манифеста и коммитит сессию PackageInstaller. Системное
 * окно «Обновить?» поднимает [UpdateInstallReceiver]; после установки процесс убивает система,
 * а [UpdateAppliedReceiver] открывает приложение заново.
 *
 * Живёт в AppScope: загрузка не должна обрываться, если человек ушёл со списка чатов.
 * Когда приложение само поставило обновление, оно становится «установщиком» пакета — на
 * Android 12+ это то, что позволило бы ставить молча, но сейчас сознательно спрашиваем.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DefaultUpdateInstaller(
    @ApplicationContext private val context: Context,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: CoroutineDispatchers,
) : UpdateInstaller {
    // Свой клиент без интерсепторов: debug-логгер с уровнем BODY буферизует ответ целиком,
    // а APK весит больше 100 МБ.
    private val httpClient by lazy { OkHttpClient() }

    private val _state = MutableStateFlow<UpdateInstallState>(UpdateInstallState.Idle)
    override val state: StateFlow<UpdateInstallState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        UpdateInstallReceiver.onResult = ::onInstallResult
        // APK прошлых обновлений больше не нужны.
        appCoroutineScope.launch(dispatchers.io) { updatesDir().deleteRecursively() }
    }

    override fun install(update: UpdateStatus.Available) {
        if (job?.isActive == true) return
        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
                .onFailure { Timber.w(it, "Не открылись настройки установки из неизвестных источников") }
            _state.value = UpdateInstallState.NeedsPermission
            return
        }
        job = appCoroutineScope.launch(dispatchers.io) {
            _state.value = UpdateInstallState.Downloading(progress = 0f)
            runCatching {
                val apk = download(update)
                commitSession(apk)
            }.onSuccess {
                _state.value = UpdateInstallState.WaitingForConfirmation
            }.onFailure {
                Timber.w(it, "Обновление до ${update.versionName} не удалось")
                _state.value = UpdateInstallState.Failed
            }
        }
    }

    private fun download(update: UpdateStatus.Available): File {
        val target = File(updatesDir().apply { mkdirs() }, "larpgram-${update.versionCode}.apk")
        val request = Request.Builder().url(update.apkUrl).build()
        val digest = MessageDigest.getInstance("SHA-256")
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 }
            var done = 0L
            var lastPercent = -1
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        // Размер неизвестен — один раз показываем «без процентов».
                        val percent = total?.let { (done * 100 / it).toInt() } ?: UNKNOWN_PERCENT
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.value = UpdateInstallState.Downloading(progress = percent.takeIf { it >= 0 }?.let { it / 100f })
                        }
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (update.sha256 != null && update.sha256 != actual) {
            target.delete()
            error("sha256 не совпал: ждали ${update.sha256}, получили $actual")
        }
        return target
    }

    private fun commitSession(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("larpgram.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { it.copyTo(output) }
                session.fsync(output)
            }
            val callback = Intent(context, UpdateInstallReceiver::class.java).setPackage(context.packageName)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                // PackageInstaller дописывает в интент статус — нужен изменяемый PendingIntent.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pendingIntent = PendingIntent.getBroadcast(context, sessionId, callback, flags)
            session.commit(pendingIntent.intentSender)
        }
    }

    private fun onInstallResult(status: Int) {
        _state.value = when (status) {
            // Окно показано — ждём человека.
            PackageInstaller.STATUS_PENDING_USER_ACTION -> UpdateInstallState.WaitingForConfirmation
            // Отказался в системном окне — вернуть баннер как был, можно нажать снова.
            PackageInstaller.STATUS_FAILURE_ABORTED -> UpdateInstallState.Idle
            // Успех: процесс сейчас убьют, состояние уже не важно.
            PackageInstaller.STATUS_SUCCESS -> UpdateInstallState.WaitingForConfirmation
            else -> UpdateInstallState.Failed
        }
    }

    private fun updatesDir() = File(context.cacheDir, "updates")
}

private const val UNKNOWN_PERCENT = -2
