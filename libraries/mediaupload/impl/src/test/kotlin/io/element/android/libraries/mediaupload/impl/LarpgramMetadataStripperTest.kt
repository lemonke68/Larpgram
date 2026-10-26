/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.mediaupload.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayOutputStream

class LarpgramMetadataStripperTest {
    // region JPEG

    private fun segment(marker: Int, payload: ByteArray): ByteArray {
        val length = payload.size + 2
        return byteArrayOf(0xFF.toByte(), marker.toByte(), (length shr 8).toByte(), length.toByte()) + payload
    }

    private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)

    private val jfif = segment(0xE0, ascii("JFIF\u0000") + byteArrayOf(1, 1, 0, 0, 1, 0, 1, 0, 0))
    private val exif = segment(0xE1, ascii("Exif\u0000\u0000") + ascii("MM Canon EOS serial 12345 GPS 55.75N"))
    private val xmp = segment(0xE1, ascii("http://ns.adobe.com/xap/1.0/\u0000<x:xmpmeta>secret</x:xmpmeta>"))
    private val icc = segment(0xE2, ascii("ICC_PROFILE\u0000") + byteArrayOf(1, 1, 9, 9, 9))
    private val mpf = segment(0xE2, ascii("MPF\u0000") + byteArrayOf(1, 2, 3))
    private val iptc = segment(0xED, ascii("Photoshop 3.0\u0000 caption=secret"))
    private val adobe = segment(0xEE, ascii("Adobe") + byteArrayOf(0, 100, 0, 0, 0, 0, 1))
    private val comment = segment(0xFE, ascii("made by secret phone"))
    private val dqt = segment(0xDB, ByteArray(65) { it.toByte() })
    private val sof = segment(0xC0, byteArrayOf(8, 0, 16, 0, 16, 1, 1, 0x11, 0))
    private val sos = segment(0xDA, byteArrayOf(1, 1, 0, 0, 63, 0))

    // Entropy data with a stuffed 0xFF00 and a restart marker inside: both must survive.
    private val scanData = byteArrayOf(0x12, 0xFF.toByte(), 0x00, 0x34, 0xFF.toByte(), 0xD0.toByte(), 0x56)
    private val soi = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
    private val eoi = byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    @Test
    fun `jpeg - drops exif, xmp, iptc, comments, mpf and trailing data, keeps decoding segments`() {
        val input = soi + jfif + exif + xmp + icc + mpf + iptc + adobe + comment + dqt + sof + sos + scanData + eoi +
            ascii("trailing secondary image with exif")
        val output = LarpgramMetadataStripper.strip(input, "image/jpeg")!!
        assertThat(output).isEqualTo(soi + jfif + icc + adobe + dqt + sof + sos + scanData + eoi)
        assertThat(String(output, Charsets.ISO_8859_1)).doesNotContain("secret")
        assertThat(String(output, Charsets.ISO_8859_1)).doesNotContain("Canon")
    }

    @Test
    fun `jpeg - progressive file with several scans keeps every scan`() {
        val dht = segment(0xC4, byteArrayOf(0, 1, 2, 3))
        val input = soi + exif + dqt + sof + sos + scanData + dht + sos + scanData + eoi
        val output = LarpgramMetadataStripper.strip(input, "image/jpeg")!!
        assertThat(output).isEqualTo(soi + dqt + sof + sos + scanData + dht + sos + scanData + eoi)
    }

    @Test
    fun `jpeg - drops jfxx thumbnail extension but keeps jfif`() {
        val jfxx = segment(0xE0, ascii("JFXX\u0000") + byteArrayOf(0x10, 1, 2, 3))
        val input = soi + jfif + jfxx + dqt + sof + sos + scanData + eoi
        val output = LarpgramMetadataStripper.strip(input, "image/jpeg")!!
        assertThat(output).isEqualTo(soi + jfif + dqt + sof + sos + scanData + eoi)
    }

    @Test
    fun `jpeg - malformed data returns null`() {
        assertThat(LarpgramMetadataStripper.strip(ascii("not a jpeg"), "image/jpeg")).isNull()
        val truncatedSegment = soi + byteArrayOf(0xFF.toByte(), 0xE1.toByte(), 0x10, 0x00, 1, 2)
        assertThat(LarpgramMetadataStripper.strip(truncatedSegment, "image/jpeg")).isNull()
    }

    // endregion

    // region PNG

    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val length = data.size
        out.write(byteArrayOf((length shr 24).toByte(), (length shr 16).toByte(), (length shr 8).toByte(), length.toByte()))
        out.write(ascii(type))
        out.write(data)
        // The CRC is not checked by the stripper, a fixed value is enough here.
        out.write(byteArrayOf(1, 2, 3, 4))
        return out.toByteArray()
    }

    @Test
    fun `png - drops text, exif and time chunks, keeps image and colour chunks`() {
        val ihdr = chunk("IHDR", ByteArray(13))
        val iccp = chunk("iCCP", ascii("icc\u0000\u0000abc"))
        val text = chunk("tEXt", ascii("Author\u0000secret"))
        val itxt = chunk("iTXt", ascii("XML:com.adobe.xmp\u0000secret"))
        val exif = chunk("eXIf", ascii("MM secret"))
        val time = chunk("tIME", ByteArray(7))
        val idat = chunk("IDAT", byteArrayOf(9, 8, 7))
        val iend = chunk("IEND", ByteArray(0))
        val input = pngSignature + ihdr + iccp + text + itxt + exif + time + idat + iend + ascii("trailing")
        val output = LarpgramMetadataStripper.strip(input, "image/png")!!
        assertThat(output).isEqualTo(pngSignature + ihdr + iccp + idat + iend)
    }

    @Test
    fun `png - without IEND returns null`() {
        val input = pngSignature + chunk("IHDR", ByteArray(13))
        assertThat(LarpgramMetadataStripper.strip(input, "image/png")).isNull()
    }

    // endregion

    // region WebP

    private fun riffChunk(fourCc: String, data: ByteArray): ByteArray {
        val size = data.size
        val header = ascii(fourCc) + byteArrayOf(size.toByte(), (size shr 8).toByte(), (size shr 16).toByte(), (size shr 24).toByte())
        return header + data + if (size % 2 == 1) byteArrayOf(0) else byteArrayOf()
    }

    private fun webp(vararg chunks: ByteArray): ByteArray {
        val body = ascii("WEBP") + chunks.fold(byteArrayOf()) { acc, c -> acc + c }
        val size = body.size
        return ascii("RIFF") + byteArrayOf(size.toByte(), (size shr 8).toByte(), (size shr 16).toByte(), (size shr 24).toByte()) + body
    }

    @Test
    fun `webp - drops exif and xmp chunks and clears their vp8x flags`() {
        val vp8xFlags = (0x20 or 0x08 or 0x04).toByte() // ICC + EXIF + XMP
        val vp8x = riffChunk("VP8X", byteArrayOf(vp8xFlags, 0, 0, 0, 9, 0, 0, 9, 0, 0))
        val iccp = riffChunk("ICCP", byteArrayOf(1, 2, 3))
        val vp8 = riffChunk("VP8 ", byteArrayOf(4, 5, 6, 7))
        val exif = riffChunk("EXIF", ascii("MM secret"))
        val xmp = riffChunk("XMP ", ascii("<x>secret</x>"))
        val output = LarpgramMetadataStripper.strip(webp(vp8x, iccp, vp8, exif, xmp), "image/webp")!!
        val expectedVp8x = riffChunk("VP8X", byteArrayOf(0x20, 0, 0, 0, 9, 0, 0, 9, 0, 0))
        assertThat(output).isEqualTo(webp(expectedVp8x, iccp, vp8))
    }

    // endregion

    @Test
    fun `unsupported formats return null`() {
        assertThat(LarpgramMetadataStripper.strip(ascii("GIF89a"), "image/gif")).isNull()
        assertThat(LarpgramMetadataStripper.isSupported("image/heic")).isFalse()
        assertThat(LarpgramMetadataStripper.isSupported("image/JPEG")).isTrue()
    }
}
