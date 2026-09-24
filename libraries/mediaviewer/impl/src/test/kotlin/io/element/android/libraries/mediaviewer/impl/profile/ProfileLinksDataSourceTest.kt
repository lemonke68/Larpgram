/*
 * Правка форка: разбор ссылок для вкладки «Ссылки» профиля.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.libraries.mediaviewer.impl.profile

import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class ProfileLinksDataSourceTest : RobolectricTest() {
    @Test
    fun `extractUrls finds links and adds a scheme`() {
        val urls = ProfileLinksDataSource.extractUrls("Качать тут: https://larpgram.mango-kokos.ru и t.me/foo")
        assertThat(urls).containsExactly("https://larpgram.mango-kokos.ru", "https://t.me/foo").inOrder()
    }

    @Test
    fun `extractUrls skips e-mails and plain text`() {
        assertThat(ProfileLinksDataSource.extractUrls("пиши на a@mango-kokos.ru, т.е. сегодня")).isEmpty()
    }

    @Test
    fun `siteName takes the second-level label`() {
        assertThat(ProfileLinksDataSource.siteName("https://larpgram.mango-kokos.ru/app")).isEqualTo("Mango-kokos")
        assertThat(ProfileLinksDataSource.siteName("https://t.me/x")).isEqualTo("T")
        assertThat(ProfileLinksDataSource.siteName("https://www.net4.su")).isEqualTo("Net4")
    }

    @Test
    fun `textWithoutUrls drops links and keeps the rest`() {
        assertThat(ProfileLinksDataSource.textWithoutUrls("Все новые апк качать отсюда:\nhttps://larpgram.mango-kokos.ru"))
            .isEqualTo("Все новые апк качать отсюда:")
        assertThat(ProfileLinksDataSource.textWithoutUrls("https://larpgram.mango-kokos.ru")).isNull()
    }
}
