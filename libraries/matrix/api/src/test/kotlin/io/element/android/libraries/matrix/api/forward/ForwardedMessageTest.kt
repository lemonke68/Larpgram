/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.forward

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ForwardedMessageTest {
    @Test
    fun `hiding a profile removes all origin and avatar identifiers from encoded metadata`() {
        val source = ForwardedMessage("Alice", "@alice:hs.test", "!private:hs.test", "\$source", "mxc://hs.test/avatar")
        val hidden = source.withoutProfileLink()
        val encoded = ForwardedMessage.extraContent(hidden)
        assertThat(encoded).doesNotContain("@alice")
        assertThat(encoded).doesNotContain("!private")
        assertThat(encoded).doesNotContain("\$source")
        assertThat(encoded).doesNotContain("mxc://")
        assertThat(ForwardedMessage.fromEventJson("""{"content":$encoded}""")).isEqualTo(ForwardedMessage(name = "Alice"))
    }

    @Test
    fun `channel attribution preserves modern domainless and legacy room identifiers`() {
        listOf("!v12_AbC-def123", "!legacy:example.org").forEach { id ->
            val source = ForwardedMessage(name = "Channel", roomId = id, eventId = "\$origin", isChannel = true)
            val encoded = ForwardedMessage.extraContent(source)
            assertThat(ForwardedMessage.fromEventJson("""{"content":$encoded}""")).isEqualTo(source)
        }
    }

    @Test
    fun `untrusted event metadata does not turn external urls into profile links`() {
        val content = """{"${ForwardedMessage.FIELD}":{
            "name":"Alice","userId":"https://evil.test","roomId":"javascript:alert(1)","avatarUrl":"https://evil.test/a"
        }}"""
        assertThat(ForwardedMessage.fromEventJson("""{"content":$content}"""))
            .isEqualTo(ForwardedMessage(name = "Alice"))
        assertThat(ForwardedMessage.fromEventJson("not json")).isNull()
        assertThat(ForwardedMessage.fromEventJson("""{"content":{"${ForwardedMessage.FIELD}":[]}}""")).isNull()
        assertThat(ForwardedMessage.fromEventJson("""{"content":{"msgtype":"m.text","body":"ordinary"}}""")).isNull()
    }
}
