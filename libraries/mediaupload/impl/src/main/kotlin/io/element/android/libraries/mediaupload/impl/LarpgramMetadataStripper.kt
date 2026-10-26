/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaupload.impl

import java.io.ByteArrayOutputStream

/**
 * Removes metadata from images sent without compression, like Telegram does: EXIF (camera model,
 * serial numbers, dates, GPS, maker notes, embedded thumbnail), XMP, IPTC/Photoshop blocks, comments
 * and text chunks. Pixels are not touched: the file is rewritten segment by segment.
 *
 * Kept, because decoding needs them: JFIF header, ICC colour profile, Adobe APP14 (CMYK/colour
 * transform), quantisation/Huffman tables and the image data itself. EXIF orientation is dropped
 * together with the rest; the caller writes it back afterwards (see [AndroidMediaPreProcessor]).
 *
 * Works on raw bytes, so it is plain JVM code and unit-testable without Android.
 */
object LarpgramMetadataStripper {
    /** Returns the stripped bytes, or `null` when the format is not supported or the data is malformed. */
    fun strip(bytes: ByteArray, mimeType: String): ByteArray? = runCatching {
        when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg", "image/pjpeg" -> stripJpeg(bytes)
            "image/png" -> stripPng(bytes)
            "image/webp" -> stripWebp(bytes)
            else -> null
        }
    }.getOrNull()

    fun isSupported(mimeType: String): Boolean = mimeType.lowercase() in SUPPORTED

    private val SUPPORTED = setOf("image/jpeg", "image/jpg", "image/pjpeg", "image/png", "image/webp")

    // region JPEG

    private const val SOI = 0xD8
    private const val EOI = 0xD9
    private const val SOS = 0xDA
    private const val APP0 = 0xE0
    private const val APP2 = 0xE2
    private const val APP14 = 0xEE
    private const val APP15 = 0xEF
    private const val COM = 0xFE

    private fun stripJpeg(bytes: ByteArray): ByteArray? {
        if (bytes.size < 4 || bytes.u8(0) != 0xFF || bytes.u8(1) != SOI) return null
        val out = ByteArrayOutputStream(bytes.size)
        out.write(0xFF)
        out.write(SOI)
        var pos = 2
        while (pos < bytes.size) {
            if (bytes.u8(pos) != 0xFF) return null
            // Fill bytes: any number of 0xFF may precede a marker.
            while (pos < bytes.size && bytes.u8(pos) == 0xFF) pos++
            if (pos >= bytes.size) return null
            val marker = bytes.u8(pos)
            pos++
            when {
                marker == EOI -> {
                    out.write(0xFF)
                    out.write(EOI)
                    // Anything after EOI (MPF secondary images, trailers) is dropped.
                    return out.toByteArray()
                }
                marker in 0xD0..0xD7 || marker == 0x01 -> {
                    out.write(0xFF)
                    out.write(marker)
                }
                else -> {
                    if (pos + 2 > bytes.size) return null
                    val length = bytes.u16be(pos)
                    if (length < 2 || pos + length > bytes.size) return null
                    val segmentStart = pos - 2
                    val segmentEnd = pos + length
                    if (keepJpegSegment(marker, bytes, pos + 2, length - 2)) {
                        out.write(bytes, segmentStart, segmentEnd - segmentStart)
                    }
                    pos = segmentEnd
                    if (marker == SOS) {
                        // Entropy-coded data runs until the next real marker: 0xFF followed by a
                        // byte that is neither a stuffed 0x00 nor a restart marker.
                        val dataStart = pos
                        while (pos < bytes.size) {
                            if (bytes.u8(pos) == 0xFF && pos + 1 < bytes.size) {
                                val next = bytes.u8(pos + 1)
                                if (next != 0x00 && next !in 0xD0..0xD7 && next != 0xFF) break
                            }
                            pos++
                        }
                        out.write(bytes, dataStart, pos - dataStart)
                    }
                }
            }
        }
        // No EOI: truncated file, still return what was parsed.
        return out.toByteArray()
    }

    private fun keepJpegSegment(marker: Int, bytes: ByteArray, dataStart: Int, dataLength: Int): Boolean = when (marker) {
        // JFIF is needed; a JFXX extension only carries a thumbnail.
        APP0 -> !bytes.startsWithAscii(dataStart, dataLength, "JFXX")
        APP2 -> bytes.startsWithAscii(dataStart, dataLength, "ICC_PROFILE")
        APP14 -> bytes.startsWithAscii(dataStart, dataLength, "Adobe")
        // APP1 (EXIF, XMP), APP3..APP13 (IPTC/Photoshop, vendor data), APP15, comments.
        in APP0..APP15 -> false
        COM -> false
        else -> true
    }

    // endregion

    // region PNG

    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val PNG_DROPPED = setOf("eXIf", "tEXt", "zTXt", "iTXt", "tIME")

    private fun stripPng(bytes: ByteArray): ByteArray? {
        if (bytes.size < PNG_SIGNATURE.size || !PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }) return null
        val out = ByteArrayOutputStream(bytes.size)
        out.write(PNG_SIGNATURE)
        var pos = PNG_SIGNATURE.size
        while (pos + 8 <= bytes.size) {
            val length = bytes.u32be(pos)
            val type = String(bytes, pos + 4, 4, Charsets.US_ASCII)
            val chunkEnd = pos + 12L + length
            if (length < 0 || chunkEnd > bytes.size) return null
            if (type !in PNG_DROPPED) out.write(bytes, pos, (chunkEnd - pos).toInt())
            pos = chunkEnd.toInt()
            if (type == "IEND") return out.toByteArray()
        }
        return null
    }

    // endregion

    // region WebP

    private const val VP8X_FLAG_XMP = 0x04
    private const val VP8X_FLAG_EXIF = 0x08

    private fun stripWebp(bytes: ByteArray): ByteArray? {
        if (bytes.size < 12 || !bytes.startsWithAscii(0, 4, "RIFF") || !bytes.startsWithAscii(8, 4, "WEBP")) return null
        val body = ByteArrayOutputStream(bytes.size)
        body.write(bytes, 8, 4)
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val fourCc = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = bytes.u32le(pos + 4)
            val padded = size + (size and 1)
            val chunkEnd = pos + 8L + padded
            if (size < 0 || chunkEnd > bytes.size) return null
            when (fourCc) {
                "EXIF", "XMP " -> Unit
                "VP8X" -> {
                    val chunk = bytes.copyOfRange(pos, chunkEnd.toInt())
                    if (chunk.size > 8) {
                        chunk[8] = (chunk.u8(8) and (VP8X_FLAG_XMP or VP8X_FLAG_EXIF).inv()).toByte()
                    }
                    body.write(chunk)
                }
                else -> body.write(bytes, pos, (chunkEnd - pos).toInt())
            }
            pos = chunkEnd.toInt()
        }
        val bodyBytes = body.toByteArray()
        val out = ByteArrayOutputStream(bodyBytes.size + 8)
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        val riffSize = bodyBytes.size
        out.write(riffSize and 0xFF)
        out.write((riffSize shr 8) and 0xFF)
        out.write((riffSize shr 16) and 0xFF)
        out.write((riffSize shr 24) and 0xFF)
        out.write(bodyBytes)
        return out.toByteArray()
    }

    // endregion

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF
    private fun ByteArray.u16be(index: Int): Int = (u8(index) shl 8) or u8(index + 1)
    private fun ByteArray.u32be(index: Int): Int = (u8(index) shl 24) or (u8(index + 1) shl 16) or (u8(index + 2) shl 8) or u8(index + 3)
    private fun ByteArray.u32le(index: Int): Int = u8(index) or (u8(index + 1) shl 8) or (u8(index + 2) shl 16) or (u8(index + 3) shl 24)

    private fun ByteArray.startsWithAscii(start: Int, available: Int, prefix: String): Boolean {
        if (available < prefix.length || start + prefix.length > size) return false
        return prefix.indices.all { this[start + it] == prefix[it].code.toByte() }
    }
}
