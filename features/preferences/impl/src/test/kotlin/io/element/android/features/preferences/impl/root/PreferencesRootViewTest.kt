/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.preferences.impl.root

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.emoji.api.picker.NoOpEmojiPickerRenderer
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.test.A_USER_ID_2
import io.element.android.libraries.matrix.ui.components.aMatrixUser
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.EnsureNeverCalled
import io.element.android.tests.testutils.EnsureNeverCalledWithParam
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.ensureCalledOnce
import io.element.android.tests.testutils.ensureCalledOnceWithParam
import io.element.android.tests.testutils.pressBack
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class PreferencesRootViewTest : RobolectricTest() {
    @Test
    fun `clicking on back invokes back callback`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
        ensureCalledOnce { callback ->
            setView(
                aPreferencesRootState(
                    eventSink = eventsRecorder
                ),
                onBackClick = callback,
            )
            pressBack()
        }
    }

    @Test
    fun `click on User profile invokes the expected callback`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
        val user = aMatrixUser()
        ensureCalledOnceWithParam(user) { callback ->
            setView(
                aPreferencesRootState(
                    myUser = user,
                    eventSink = eventsRecorder,
                ),
                onOpenUserProfile = callback,
            )
            onNodeWithText("Alice").performClick()
        }
    }

    @Test
    fun `clicking on other session sends a SwitchToSession`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<PreferencesRootEvent>()
        setView(
            aPreferencesRootState(
                isMultiAccountEnabled = true,
                otherSessions = listOf(
                    aMatrixUser(
                        id = A_USER_ID_2.value,
                        displayName = "Bob",
                    )
                ),
                eventSink = eventsRecorder,
            ),
        )
        onNodeWithText("Bob").performClick()
        eventsRecorder.assertSingle(PreferencesRootEvent.SwitchToSession(A_USER_ID_2))
    }

    @Test
    fun `all category rows are shown`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
        setView(
            aPreferencesRootState(
                eventSink = eventsRecorder,
            ),
        )
        SettingsCategory.entries.forEach { category ->
            // performScrollTo() throws if the node does not exist.
            onNodeWithText(category.title).performScrollTo()
        }
    }

    @Test
    fun `clicking a category row invokes onOpenCategory with that category`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
        ensureCalledOnceWithParam(SettingsCategory.Privacy) { callback ->
            setView(
                aPreferencesRootState(
                    eventSink = eventsRecorder,
                ),
                onOpenCategory = callback,
            )
            clickOnCategory(SettingsCategory.Privacy)
        }
    }

    @Test
    fun `click on About invokes the expected callback`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
        ensureCalledOnce { callback ->
            setView(
                aPreferencesRootState(
                    eventSink = eventsRecorder,
                ),
                onOpenAbout = callback,
            )
            clickOn(CommonStrings.common_about)
        }
    }

    @Test
    fun `click on Report a problem invokes the expected callback`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
        ensureCalledOnce { callback ->
            setView(
                aPreferencesRootState(
                    canReportBug = true,
                    eventSink = eventsRecorder,
                ),
                onOpenRageShake = callback,
            )
            val text = activity!!.getString(CommonStrings.common_report_a_problem)
            onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
        }
    }

    @Test
    fun `when canReportBug is false, Report a problem is not shown`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
        setView(
            aPreferencesRootState(
                canReportBug = false,
                eventSink = eventsRecorder,
            ),
        )
        onNodeWithText(activity!!.getString(CommonStrings.common_report_a_problem)).assertDoesNotExist()
    }

    @Test
    fun `click on Developer settings invokes the expected callback`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
        ensureCalledOnce { callback ->
            setView(
                aPreferencesRootState(
                    showDeveloperSettings = true,
                    eventSink = eventsRecorder,
                ),
                onOpenDeveloperSettings = callback,
            )
            val text = activity!!.getString(CommonStrings.common_developer_options)
            onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
        }
    }

    @Test
    fun `when showDeveloperSettings is false, item is not shown`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
        setView(
            aPreferencesRootState(
                showDeveloperSettings = false,
                eventSink = eventsRecorder,
            ),
        )
        onNodeWithText(activity!!.getString(CommonStrings.common_developer_options)).assertDoesNotExist()
    }

    @Test
    fun `click on Labs invokes the expected callback`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
        ensureCalledOnce { callback ->
            setView(
                aPreferencesRootState(
                    showLabsItem = true,
                    eventSink = eventsRecorder,
                ),
                onOpenLabs = callback,
            )
            val text = activity!!.getString(R.string.screen_labs_title)
            onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
        }
    }

    @Test
    fun `when showLabsItem is false, item is not shown`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
        setView(
            aPreferencesRootState(
                showLabsItem = false,
                eventSink = eventsRecorder,
            ),
        )
        onNodeWithText(activity!!.getString(R.string.screen_labs_title)).assertDoesNotExist()
    }

    @Test
    fun `click on Advanced settings invokes the expected callback`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<PreferencesRootEvent>(expectEvents = false)
        ensureCalledOnce { callback ->
            setView(
                aPreferencesRootState(
                    eventSink = eventsRecorder,
                ),
                onOpenAdvancedSettings = callback,
            )
            clickOn(CommonStrings.common_advanced_settings)
        }
    }

    @Test
    fun `clicking on version sends a PreferencesRootEvents`() = runAndroidComposeUiTest {
        val version = "VERSION"
        val eventsRecorder = EventsRecorder<PreferencesRootEvent>()
        setView(
            aPreferencesRootState(
                version = version,
                eventSink = eventsRecorder,
            ),
        )
        onNodeWithText(version).performScrollTo().performClick()
        eventsRecorder.assertSingle(PreferencesRootEvent.OnVersionInfoClick)
    }
}

private fun AndroidComposeUiTest<ComponentActivity>.clickOnCategory(category: SettingsCategory) {
    onNodeWithText(category.title).performScrollTo().performClick()
}

private fun AndroidComposeUiTest<ComponentActivity>.clickOn(resId: Int) {
    onNodeWithText(activity!!.getString(resId)).performScrollTo().performClick()
}

private fun AndroidComposeUiTest<ComponentActivity>.setView(
    state: PreferencesRootState,
    onBackClick: () -> Unit = EnsureNeverCalled(),
    onAddAccountClick: () -> Unit = EnsureNeverCalled(),
    onOpenCategory: (SettingsCategory) -> Unit = EnsureNeverCalledWithParam(),
    onOpenUserProfile: (MatrixUser) -> Unit = EnsureNeverCalledWithParam(),
    onOpenAbout: () -> Unit = EnsureNeverCalled(),
    onOpenRageShake: () -> Unit = EnsureNeverCalled(),
    onOpenLabs: () -> Unit = EnsureNeverCalled(),
    onOpenDeveloperSettings: () -> Unit = EnsureNeverCalled(),
    onOpenAdvancedSettings: () -> Unit = EnsureNeverCalled(),
) {
    setContent {
        PreferencesRootView(
            state = state,
            emojiPickerRenderer = NoOpEmojiPickerRenderer,
            onBackClick = onBackClick,
            onAddAccountClick = onAddAccountClick,
            onOpenCategory = onOpenCategory,
            onOpenUserProfile = onOpenUserProfile,
            onOpenAbout = onOpenAbout,
            onOpenRageShake = onOpenRageShake,
            onOpenLabs = onOpenLabs,
            onOpenDeveloperSettings = onOpenDeveloperSettings,
            onOpenAdvancedSettings = onOpenAdvancedSettings,
        )
    }
}
