/*
 * Модуль форка: кружочки, круглые видеосообщения как в Telegram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.circles.impl

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.ExperimentalPersistentRecording
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import timber.log.Timber
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Сколько длится самый длинный кружочек. Столько же у Telegram. */
const val CIRCLE_MAX_DURATION_MS = 60_000L

/**
 * Запись кружочка через CameraX.
 *
 * Квадрат получается не обрезкой после записи, а `ViewPort` с соотношением 1:1: CameraX
 * применяет его к записи, и на выходе сразу квадратное видео. Своего перекодировщика у
 * нас нет, а тащить его ради обрезки было бы несоразмерно.
 *
 * Осторожно: соблюдение `ViewPort` для записи зависит от устройства, на эмуляторе
 * проверяется только то, что файл вообще квадратный. На живом телефоне это первое, что
 * нужно перепроверить.
 */
class CircleRecorder(
    private val context: Context,
    private val cacheDir: File,
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var previewUseCase: Preview? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    /**
     * Подключает камеру к экрану. Возвращает use case превью, который вью отдаёт своему
     * `PreviewView`.
     *
     * Use case создаются один раз и переиспользуются при каждом переподключении. Это не
     * экономия: идущая запись привязана к конкретному `VideoCapture`, и если пересоздать
     * его при смене камеры, запись оборвётся.
     */
    suspend fun bind(lifecycleOwner: LifecycleOwner, frontCamera: Boolean): Preview {
        val provider = cameraProvider ?: context.awaitCameraProvider().also { cameraProvider = it }
        // Превью маленькое: на экране это круг ~300 dp, а полное разрешение камеры на
        // слабом железе долго стартует и лагает при записи (Kirin 710, 2026-08-15).
        val preview = previewUseCase ?: Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            android.util.Size(720, 720),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        )
                    )
                    .build()
            )
            .build()
            .also { previewUseCase = it }
        val capture = videoCapture ?: VideoCapture.Builder(
            Recorder.Builder()
                // SD хватает: кружочек рисуется 200 dp, гнаться за качеством незачем, а
                // вес и время загрузки растут быстро.
                .setQualitySelector(
                    // Откат ВНИЗ, а не вверх: с higherQualityOrLowerThan устройство без
                    // ровного SD выдавало 720p, и запись начинала лагать. Кружочек
                    // рисуется 200 dp, лишние пиксели там не видит никто.
                    QualitySelector.from(Quality.SD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
                )
                .build()
        )
            // Зеркалим запись с фронталки, как это делает Telegram. По умолчанию CameraX
            // пишет «как видят другие», а превью при записи зеркальное, и человек получает
            // кружок, не совпадающий с тем, что он видел, — юзер заметил это сразу
            // (2026-08-14). Задняя камера не зеркалится, поэтому ON_FRONT_ONLY.
            .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
            .build()
            .also { videoCapture = it }

        val useCaseGroup = UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(capture)
            // Квадрат задаётся здесь, а не постобработкой.
            .setViewPort(
                ViewPort.Builder(android.util.Rational(1, 1), preview.targetRotation)
                    .setScaleType(ViewPort.FILL_CENTER)
                    .build()
            )
            .build()

        val selector = CameraSelector.Builder()
            .requireLensFacing(
                // Кружочки почти всегда снимают себя, поэтому по умолчанию фронталка.
                if (frontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            )
            .build()

        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, useCaseGroup)
        return preview
    }

    /**
     * Начинает запись в файл. Имя файла и есть пометка кружочка, см.
     * `LARPGRAM_CIRCLE_FILENAME_PREFIX`: своего поля в content отправка видео не принимает.
     */
    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalPersistentRecording::class)
    fun start(onFinished: (Result<File>) -> Unit): Boolean {
        val capture = videoCapture ?: return false
        val target = File(cacheDir, "larpgram-circle-${UUID.randomUUID()}.mp4")
        val options = FileOutputOptions.Builder(target).build()
        recording = capture.output
            .prepareRecording(context, options)
            // Кружочек без звука бессмысленен.
            .withAudioEnabled()
            // Без этого запись обрывается, как только камера переподключается, то есть
            // при первой же смене фронталки на основную посреди записи.
            .asPersistentRecording()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    recording = null
                    if (event.hasError()) {
                        Timber.e("запись кружочка не удалась: код %d", event.error)
                        target.delete()
                        onFinished(Result.failure(IllegalStateException("код ${event.error}")))
                    } else {
                        onFinished(Result.success(target))
                    }
                }
            }
        return true
    }

    /** Останавливает запись; файл придёт в колбэк из [start]. */
    fun stop() {
        recording?.stop()
        recording = null
    }

    /**
     * Отменяет запись. Файл всё равно дописывается и приходит в колбэк, поэтому вызвавший
     * должен сам понимать, что результат нужно выбросить.
     */
    fun cancel() {
        recording?.stop()
        recording = null
    }

    fun release() {
        recording?.stop()
        recording = null
        cameraProvider?.unbindAll()
        previewUseCase = null
        videoCapture = null
    }
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { future ->
        future.addListener({ continuation.resume(future.get()) }, ContextCompat.getMainExecutor(this))
    }
}
