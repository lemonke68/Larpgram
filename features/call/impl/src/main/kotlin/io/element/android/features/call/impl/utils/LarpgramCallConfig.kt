/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.utils

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import org.json.JSONArray
import org.json.JSONObject

/** Правка форка: возможности камер передаются через конфигурацию встроенного Element Call. */
internal fun larpgramCallConfig(context: Context, original: String): String {
    val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    val cameras = JSONArray()
    val ids = runCatching { manager?.cameraIdList.orEmpty() }.getOrDefault(emptyArray())
    for (id in ids) {
        runCatching {
            val characteristics = manager?.getCameraCharacteristics(id) ?: return@runCatching
            val side = when (characteristics[CameraCharacteristics.LENS_FACING]) {
                CameraCharacteristics.LENS_FACING_FRONT -> "front"
                CameraCharacteristics.LENS_FACING_BACK -> "rear"
                else -> return@runCatching
            }
            val width = characteristics[CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE]?.width ?: return@runCatching
            val focal = characteristics[CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]?.minOrNull() ?: return@runCatching
            if (width > 0f && focal > 0f) {
                cameras.put(JSONObject().put("cameraId", id).put("side", side).put("relativeFocalLength", focal / width))
            }
        }
    }
    return JSONObject(original).put("larpgram_camera_metadata", cameras).toString()
}
