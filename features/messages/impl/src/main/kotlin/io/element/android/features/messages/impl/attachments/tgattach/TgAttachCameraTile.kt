/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.attachments.tgattach

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.utils.OnLifecycleEvent
import timber.log.Timber

/**
 * Правка форка: первая плитка меню вложений — живой кадр задней камеры (`ChatAttachAlertPhotoLayout`
 * `cameraView`). Тап — снять фото, долгое нажатие — видео; оба через системную камеру, как раньше
 * пункты «Снять фото / видео» Element. Без разрешения на камеру — тёмная плитка со значком, тап
 * спросит разрешение.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TgAttachCameraTile(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    fun hasCameraPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(hasCameraPermission()) }
    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) hasPermission = hasCameraPermission()
    }
    Box(
        modifier = modifier
            // TextureView превью иначе выглядывает за край плитки.
            .clipToBounds()
            .background(Color.Black)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        if (hasPermission && !LocalInspectionMode.current) {
            CameraPreview(Modifier.fillMaxSize())
        }
        Icon(
            modifier = Modifier
                .align(if (hasPermission) Alignment.TopEnd else Alignment.Center)
                .padding(if (hasPermission) 8.dp else 0.dp)
                .size(if (hasPermission) 26.dp else 32.dp),
            imageVector = CompoundIcons.TakePhotoSolid(),
            contentDescription = null,
            tint = Color.White,
        )
    }
}

@Composable
private fun CameraPreview(modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            // TextureView: превью обрезается скруглением плитки и живёт внутри прокручиваемой сетки.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    DisposableEffect(lifecycleOwner) {
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        providerFuture.addListener(
            {
                if (disposed) return@addListener
                runCatching {
                    provider = providerFuture.get().also {
                        it.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview)
                    }
                }.onFailure { Timber.w(it, "Attach camera preview failed") }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            disposed = true
            provider?.unbind(preview)
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { previewView },
    )
}
