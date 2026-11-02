/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.libraries.matrix.impl.room

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.impl.fixtures.factories.aRustImageMessageContent
import org.junit.Test
import org.matrix.rustcomponents.sdk.FormattedBody
import org.matrix.rustcomponents.sdk.ImageInfo
import org.matrix.rustcomponents.sdk.MediaSource
import org.matrix.rustcomponents.sdk.Mentions
import org.matrix.rustcomponents.sdk.MessageContent
import org.matrix.rustcomponents.sdk.MessageFormat
import org.matrix.rustcomponents.sdk.MessageType
import org.matrix.rustcomponents.sdk.NoHandle

class ForwardedMediaCaptionTest {
    @Test
    fun `media-only copy replaces captions and keeps encryption and technical info`() {
        val encryptedSource = object : MediaSource(NoHandle) {
            override fun url() = "mxc://example.org/encrypted"
            override fun toJson() = """{"file":{"url":"mxc://example.org/encrypted","key":{"k":"test-only-key"}}}"""
        }
        val info = ImageInfo(720uL, 1280uL, "image/png", 8192uL, null, null, "test-blurhash", false)
        val message = MessageContent(
            msgType = MessageType.Image(aRustImageMessageContent(
                caption = "Original caption",
                formattedCaption = FormattedBody(MessageFormat.Html, "<b>Original caption</b>"),
                source = encryptedSource,
                info = info,
            )),
            body = "Original caption",
            isEdited = true,
            mentions = Mentions(listOf("@someone:example.org"), true),
        )
        val copy = message.withMediaCaption("New caption")
        val image = (copy.msgType as MessageType.Image).content
        assertThat(image.caption).isEqualTo("New caption")
        assertThat(image.formattedCaption).isNull()
        assertThat(image.source).isSameInstanceAs(encryptedSource)
        assertThat(image.source.toJson()).contains("test-only-key")
        assertThat(image.info).isSameInstanceAs(info)
        assertThat(copy.mentions).isNull()
        assertThat(((message.withMediaCaption(null).msgType as MessageType.Image).content).caption).isNull()
        assertThat((message.msgType as MessageType.Image).content.caption).isEqualTo("Original caption")
    }
}
