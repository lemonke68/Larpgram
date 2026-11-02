/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package io.element.android.libraries.matrix.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.core.data.bytes
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.paths.SessionPaths
import io.element.android.libraries.matrix.impl.fixtures.factories.aRustUserProfile
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiClient
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiSyncService
import io.element.android.libraries.matrix.impl.room.FakeTimelineEventFilterFactory
import io.element.android.libraries.matrix.impl.search.RustSearchBackfillService
import io.element.android.libraries.matrix.impl.search.workmanager.SearchBackfillRequestBuilder
import io.element.android.libraries.matrix.test.AN_AVATAR_URL
import io.element.android.libraries.matrix.test.A_DEVICE_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.A_USER_ID_2
import io.element.android.libraries.matrix.test.A_USER_NAME
import io.element.android.libraries.matrix.test.scanner.FakeContentScanner
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.libraries.workmanager.api.WorkManagerRequestWrapper
import io.element.android.libraries.workmanager.test.FakeWorkManagerScheduler
import io.element.android.services.analytics.test.FakeAnalyticsService
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.lambda.any
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.CreateRoomParameters
import org.matrix.rustcomponents.sdk.IgnoredUsersListener
import org.matrix.rustcomponents.sdk.MediaSource
import org.matrix.rustcomponents.sdk.RoomHistoryVisibility
import org.matrix.rustcomponents.sdk.StoreSizes
import java.io.File

private const val AN_ACCOUNT_DATA_EVENT_TYPE = "org.example.custom"
private const val AN_ACCOUNT_DATA_CONTENT = """{"key":"value"}"""

class RustMatrixClientTest {
    @Test
    fun `ensure that sessionId and deviceId can be retrieved from the client`() = runTest {
        createRustMatrixClient().run {
            assertThat(sessionId).isEqualTo(A_USER_ID)
            assertThat(deviceId).isEqualTo(A_DEVICE_ID)
            destroy()
        }
    }

    @Test
    fun `clear cache invokes the method clearCaches from the client and close it`() = runTest {
        val clearCachesResult = lambdaRecorder<Unit> { }
        val closeResult = lambdaRecorder<Unit> { }
        val client = createRustMatrixClient(
            client = FakeFfiClient(
                clearCachesResult = clearCachesResult,
                closeResult = closeResult,
            )
        )
        client.clearCache()
        clearCachesResult.assertions().isCalledOnce()
        closeResult.assertions().isCalledOnce()
        client.destroy()
    }

    @Test
    fun `retrieving the UserProfile updates the database`() = runTest {
        val profilePersisted = CompletableDeferred<Unit>()
        val updateUserProfileResult = lambdaRecorder<String, String?, String?, String?, Unit> { _, _, _, _ -> profilePersisted.complete(Unit) }
        val getMediaThumbnailResult = lambdaRecorder<MediaSource, ULong, ULong, ByteArray> { _, _, _ -> byteArrayOf(1, 2, 3) }
        val sessionStore = InMemorySessionStore(
            initialList = listOf(
                aSessionData(
                    sessionId = A_USER_ID.value,
                    userDisplayName = null,
                    userAvatarUrl = null,
                )
            ),
            updateUserProfileResult = updateUserProfileResult,
        )
        val client = createRustMatrixClient(
            client = FakeFfiClient(
                getProfileResult = { userId -> aRustUserProfile(userId = userId, displayName = A_USER_NAME, avatarUrl = AN_AVATAR_URL) },
                getMediaThumbnailResult = getMediaThumbnailResult,
            ),
            sessionStore = sessionStore,
        )
        profilePersisted.await()
        getMediaThumbnailResult.assertions().isCalledOnce()
            .with(any(), value(240uL), value(240uL))
        updateUserProfileResult.assertions().isCalledOnce()
            .with(
                value(A_USER_ID.value),
                value(A_USER_NAME),
                value(AN_AVATAR_URL),
                value("AQID"),
            )
        client.destroy()
    }

    @Test
    fun `retrieving the UserProfile does not download the avatar again if it has not changed`() = runTest {
        val profilePersisted = CompletableDeferred<Unit>()
        val updateUserProfileResult = lambdaRecorder<String, String?, String?, String?, Unit> { _, _, _, _ -> profilePersisted.complete(Unit) }
        val getMediaThumbnailResult = lambdaRecorder<MediaSource, ULong, ULong, ByteArray> { _, _, _ -> byteArrayOf(1, 2, 3) }
        val sessionStore = InMemorySessionStore(
            initialList = listOf(
                aSessionData(
                    sessionId = A_USER_ID.value,
                    userDisplayName = A_USER_NAME,
                    userAvatarUrl = AN_AVATAR_URL,
                    userAvatarData = "storedData",
                )
            ),
            updateUserProfileResult = updateUserProfileResult,
        )
        val client = createRustMatrixClient(
            client = FakeFfiClient(
                getProfileResult = { userId -> aRustUserProfile(userId = userId, displayName = A_USER_NAME, avatarUrl = AN_AVATAR_URL) },
                getMediaThumbnailResult = getMediaThumbnailResult,
            ),
            sessionStore = sessionStore,
        )
        profilePersisted.await()
        getMediaThumbnailResult.assertions().isNeverCalled()
        updateUserProfileResult.assertions().isCalledOnce()
            .with(
                value(A_USER_ID.value),
                value(A_USER_NAME),
                value(AN_AVATAR_URL),
                value("storedData"),
            )
        client.destroy()
    }

    @Test
    fun `retrieving the UserProfile downloads the avatar again if the previous download failed`() = runTest {
        val profilePersisted = CompletableDeferred<Unit>()
        val updateUserProfileResult = lambdaRecorder<String, String?, String?, String?, Unit> { _, _, _, _ -> profilePersisted.complete(Unit) }
        val getMediaThumbnailResult = lambdaRecorder<MediaSource, ULong, ULong, ByteArray> { _, _, _ -> byteArrayOf(1, 2, 3) }
        val sessionStore = InMemorySessionStore(
            initialList = listOf(
                aSessionData(
                    sessionId = A_USER_ID.value,
                    userDisplayName = A_USER_NAME,
                    userAvatarUrl = AN_AVATAR_URL,
                    userAvatarData = null,
                )
            ),
            updateUserProfileResult = updateUserProfileResult,
        )
        val client = createRustMatrixClient(
            client = FakeFfiClient(
                getProfileResult = { userId -> aRustUserProfile(userId = userId, displayName = A_USER_NAME, avatarUrl = AN_AVATAR_URL) },
                getMediaThumbnailResult = getMediaThumbnailResult,
            ),
            sessionStore = sessionStore,
        )
        profilePersisted.await()
        getMediaThumbnailResult.assertions().isCalledOnce()
        updateUserProfileResult.assertions().isCalledOnce()
            .with(
                value(A_USER_ID.value),
                value(A_USER_NAME),
                value(AN_AVATAR_URL),
                value("AQID"),
            )
        client.destroy()
    }

    @Test
    fun `retrieving the UserProfile clears the avatar data if the avatar download fails`() = runTest {
        val profilePersisted = CompletableDeferred<Unit>()
        val updateUserProfileResult = lambdaRecorder<String, String?, String?, String?, Unit> { _, _, _, _ -> profilePersisted.complete(Unit) }
        val getMediaThumbnailResult = lambdaRecorder<MediaSource, ULong, ULong, ByteArray> { _, _, _ -> error("Download failed") }
        val sessionStore = InMemorySessionStore(
            initialList = listOf(
                aSessionData(
                    sessionId = A_USER_ID.value,
                    userDisplayName = A_USER_NAME,
                    userAvatarUrl = "mxc://server.org/previousAvatar",
                    userAvatarData = "previousData",
                )
            ),
            updateUserProfileResult = updateUserProfileResult,
        )
        val client = createRustMatrixClient(
            client = FakeFfiClient(
                getProfileResult = { userId -> aRustUserProfile(userId = userId, displayName = A_USER_NAME, avatarUrl = AN_AVATAR_URL) },
                getMediaThumbnailResult = getMediaThumbnailResult,
            ),
            sessionStore = sessionStore,
        )
        profilePersisted.await()
        getMediaThumbnailResult.assertions().isCalledOnce()
        updateUserProfileResult.assertions().isCalledOnce()
            .with(
                value(A_USER_ID.value),
                value(A_USER_NAME),
                value(AN_AVATAR_URL),
                value(null),
            )
        client.destroy()
    }

    @Test
    fun `retrieving the UserProfile without avatar clears the avatar data`() = runTest {
        val profilePersisted = CompletableDeferred<Unit>()
        val updateUserProfileResult = lambdaRecorder<String, String?, String?, String?, Unit> { _, _, _, _ -> profilePersisted.complete(Unit) }
        val getMediaThumbnailResult = lambdaRecorder<MediaSource, ULong, ULong, ByteArray> { _, _, _ -> byteArrayOf(1, 2, 3) }
        val sessionStore = InMemorySessionStore(
            initialList = listOf(
                aSessionData(
                    sessionId = A_USER_ID.value,
                    userDisplayName = A_USER_NAME,
                    userAvatarUrl = AN_AVATAR_URL,
                    userAvatarData = "storedData",
                )
            ),
            updateUserProfileResult = updateUserProfileResult,
        )
        val client = createRustMatrixClient(
            client = FakeFfiClient(
                getProfileResult = { userId -> aRustUserProfile(userId = userId, displayName = A_USER_NAME, avatarUrl = null) },
                getMediaThumbnailResult = getMediaThumbnailResult,
            ),
            sessionStore = sessionStore,
        )
        profilePersisted.await()
        getMediaThumbnailResult.assertions().isNeverCalled()
        updateUserProfileResult.assertions().isCalledOnce()
            .with(
                value(A_USER_ID.value),
                value(A_USER_NAME),
                value(null),
                value(null),
            )
        client.destroy()
    }

    @Test
    fun `getDatabaseSizes returns the database sizes`() = runTest {
        val client = createRustMatrixClient(
            client = FakeFfiClient(getStoreSizesResult = {
                StoreSizes(
                    cryptoStore = null,
                    stateStore = 10uL,
                    eventCacheStore = 11uL,
                    mediaStore = 12uL,
                    searchIndex = 13uL,
                )
            })
        )

        client.getDatabaseSizes().getOrThrow().run {
            assertThat(cryptoStore).isNull()
            assertThat(stateStore).isEqualTo(10.bytes)
            assertThat(eventCacheStore).isEqualTo(11.bytes)
            assertThat(mediaStore).isEqualTo(12.bytes)
            assertThat(searchIndex).isEqualTo(13.bytes)
        }
    }

    @Test
    fun `media cache usage keeps removable protected and database bytes separate`() = runTest {
        val client = createRustMatrixClient(
            client = FakeFfiClient(mediaCacheUsageResult = {
                org.matrix.rustcomponents.sdk.MediaCacheUsage(
                    images = 10uL,
                    videos = 20uL,
                    audio = 30uL,
                    other = 40uL,
                    thumbnails = 5uL,
                    `protected` = 50uL,
                    databaseBytes = 200uL,
                )
            })
        )
        val usage = client.getMediaCacheUsage().getOrThrow()
        assertThat(usage.removableBytes).isEqualTo(105)
        assertThat(usage.protectedBytes).isEqualTo(50)
        assertThat(usage.databaseBytes).isEqualTo(200)
        assertThat(usage.databaseOverheadBytes).isEqualTo(45)
        client.destroy()
    }

    @Test
    fun `clear media cache does not reset SDK session or destroy client`() = runTest {
        var cleared = 0
        var closed = false
        val client = createRustMatrixClient(
            client = FakeFfiClient(
                clearMediaCacheResult = {
                    cleared++
                    123uL
                },
                clearCachesResult = { error("Full cache reset must not be called") },
                closeResult = { closed = true },
            )
        )
        assertThat(client.clearMediaCache().isSuccess).isTrue()
        assertThat(cleared).isEqualTo(1)
        assertThat(closed).isFalse()
        assertThat(client.isShuttingDown).isFalse()
        assertThat(client.sessionId).isEqualTo(A_USER_ID)
        client.destroy()
    }

    @Test
    fun `media cache errors remain failures without a destructive fallback`() = runTest {
        val error = IllegalStateException("store unavailable")
        val client = createRustMatrixClient(
            client = FakeFfiClient(
                mediaCacheUsageResult = { throw error },
                clearMediaCacheResult = { throw error },
                clearCachesResult = { throw AssertionError("Full cache reset must not be called") },
            )
        )
        listOf(client.getMediaCacheUsage(), client.clearMediaCache()).forEach { result ->
            assertThat(result.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
            assertThat(result.exceptionOrNull()).hasMessageThat().isEqualTo("store unavailable")
        }
        assertThat(client.isShuttingDown).isFalse()
        client.destroy()
    }

    @Test
    fun `createDM overrides room history visibility to invited`() = runTest {
        var createParameters: CreateRoomParameters? = null
        val createRoomLambda = lambdaRecorder<CreateRoomParameters, String> {
            createParameters = it
            A_ROOM_ID.value
        }
        val client = createRustMatrixClient(
            client = FakeFfiClient(createRoomResult = createRoomLambda)
        )

        client.createDM(userId = A_USER_ID, isEncrypted = true)

        createRoomLambda.assertions().isCalledOnce()
        assertThat(createParameters?.historyVisibilityOverride).isEqualTo(RoomHistoryVisibility.Invited)
    }

    @Test
    fun `getAccountData returns the raw content provided by the client`() = runTest {
        val accountDataResult = lambdaRecorder<String, String?> { AN_ACCOUNT_DATA_CONTENT }
        val client = createRustMatrixClient(
            client = FakeFfiClient(accountDataResult = accountDataResult)
        )

        assertThat(client.getAccountData(AN_ACCOUNT_DATA_EVENT_TYPE).getOrThrow()).isEqualTo(AN_ACCOUNT_DATA_CONTENT)
        accountDataResult.assertions().isCalledOnce().with(value(AN_ACCOUNT_DATA_EVENT_TYPE))
        client.destroy()
    }

    @Test
    fun `setAccountData forwards the event type and the raw content to the client`() = runTest {
        val setAccountDataResult = lambdaRecorder<String, String, Unit> { _, _ -> }
        val client = createRustMatrixClient(
            client = FakeFfiClient(setAccountDataResult = setAccountDataResult)
        )

        assertThat(client.setAccountData(AN_ACCOUNT_DATA_EVENT_TYPE, AN_ACCOUNT_DATA_CONTENT).isSuccess).isTrue()
        setAccountDataResult.assertions().isCalledOnce().with(value(AN_ACCOUNT_DATA_EVENT_TYPE), value(AN_ACCOUNT_DATA_CONTENT))
        client.destroy()
    }

    @Test
    fun `ignoredUsersFlow uses the value from the server even if the SDK first emits an empty list`() = runTest {
        val client = createRustMatrixClient(
            client = FakeFfiClient(
                ignoredUsersResult = { listOf(A_USER_ID.value, A_USER_ID_2.value) },
                // The SDK emits its in-memory value immediately, which is empty until the account data is received by the sync
                subscribeToIgnoredUsersResult = { listener -> listener.call(emptyList()) },
            )
        )
        runInitialIgnoredUsersFetch()
        assertThat(client.ignoredUsersFlow.value).containsExactly(A_USER_ID, A_USER_ID_2).inOrder()
        client.destroy()
    }

    @Test
    fun `ignoredUsersFlow emits the values received from the SDK listener`() = runTest {
        var ignoredUsersListener: IgnoredUsersListener? = null
        val client = createRustMatrixClient(
            client = FakeFfiClient(
                subscribeToIgnoredUsersResult = { listener -> ignoredUsersListener = listener },
            )
        )
        runInitialIgnoredUsersFetch()
        assertThat(client.ignoredUsersFlow.value).isEmpty()
        ignoredUsersListener!!.call(listOf(A_USER_ID.value))
        assertThat(client.ignoredUsersFlow.value).containsExactly(A_USER_ID)
        client.destroy()
    }

    @Test
    fun `ignoredUsersFlow still emits the values received from the SDK listener if the initial fetch fails`() = runTest {
        var ignoredUsersListener: IgnoredUsersListener? = null
        val client = createRustMatrixClient(
            client = FakeFfiClient(
                ignoredUsersResult = { error("Network error") },
                subscribeToIgnoredUsersResult = { listener -> ignoredUsersListener = listener },
            )
        )
        runInitialIgnoredUsersFetch()
        assertThat(client.ignoredUsersFlow.value).isEmpty()
        ignoredUsersListener!!.call(listOf(A_USER_ID.value))
        assertThat(client.ignoredUsersFlow.value).containsExactly(A_USER_ID)
        client.destroy()
    }

    // The initial fetch of the ignored users runs in the background scope, with a simulated delay
    private fun TestScope.runInitialIgnoredUsersFetch() {
        advanceTimeBy(1)
        runCurrent()
    }

    @Test
    fun `copy restrictions are read only for private channels and not for public channels or groups`() = runTest {
        val cases = listOf(Triple(true, 50L, false), Triple(false, 0L, false), Triple(false, 50L, true))
        cases.forEach { (isPublic, eventsDefault, shouldReadState) ->
            var reads = 0
            val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
                reads++
                okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                    .body("{\"enabled\":true}".toResponseBody("application/json".toMediaType())).build()
            }.build()
            val info = io.element.android.libraries.matrix.impl.fixtures.factories.aRustRoomInfo(
                isPublic = isPublic,
                roomPowerLevels = io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiRoomPowerLevels(
                    values = io.element.android.libraries.matrix.impl.fixtures.fakes.defaultFfiRoomPowerLevelValues().copy(eventsDefault = eventsDefault)
                ),
            )
            val rooms = io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiRoomListService {
                io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiRoom(roomInfo = info)
            }
            val client =
                createRustMatrixClient(
                    client = FakeFfiClient(homeserver = "https://hs.test"),
                    roomListService = rooms,
                    sharingApi = io.element.android.libraries.matrix.impl.forward.LarpgramSharingApi(http)
                )
            assertThat(client.getRoomCopyProtection(A_ROOM_ID).getOrThrow()).isEqualTo(shouldReadState)
            assertThat(reads).isEqualTo(if (shouldReadState) 1 else 0)
        }
    }

    @Test
    fun `copy policy fails closed when private room permissions are unavailable`() = runTest {
        val info = io.element.android.libraries.matrix.impl.fixtures.factories.aRustRoomInfo().copy(powerLevels = null)
        val rooms = io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiRoomListService {
            io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiRoom(roomInfo = info)
        }
        val client = createRustMatrixClient(roomListService = rooms)
        assertThat(client.getRoomCopyProtection(A_ROOM_ID).isFailure).isTrue()
    }

    private fun TestScope.createRustMatrixClient(
        client: Client = FakeFfiClient(),
        roomListService: org.matrix.rustcomponents.sdk.RoomListService = io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiRoomListService(),
        sharingApi: io.element.android.libraries.matrix.impl.forward.LarpgramSharingApi = io.element.android.libraries.matrix.impl.forward.LarpgramSharingApi(
            okhttp3.OkHttpClient()
        ),
        sessionStore: SessionStore = InMemorySessionStore(
            updateUserProfileResult = { _, _, _, _ -> },
        ),
    ) = RustMatrixClient(
        sharingApi = sharingApi,
        innerClient = client,
        sessionPaths = SessionPaths(fileDirectory = File("files"), cacheDirectory = File("cache")),
        sessionStore = sessionStore,
        appCoroutineScope = backgroundScope,
        sessionDelegate = aRustClientSessionDelegate(
            sessionStore = sessionStore,
        ),
        innerSyncService = FakeFfiSyncService(roomListService),
        dispatchers = testCoroutineDispatchers(),
        baseCacheDirectory = File(""),
        clock = FakeSystemClock(),
        timelineEventFilterFactory = FakeTimelineEventFilterFactory(),
        featureFlagService = FakeFeatureFlagService(),
        analyticsService = FakeAnalyticsService(),
        workManagerScheduler = FakeWorkManagerScheduler(submitLambda = {}, cancelLambda = { _, _ -> }),
        contentScanner = FakeContentScanner(),
        isMessageSearchAvailable = false,
        searchBackfillServiceFactory = object : RustSearchBackfillService.Factory {
            override fun create(innerClient: Client) = RustSearchBackfillService(
                innerClient = innerClient,
                searchBackfillRequestBuilderFactory = {
                    object : SearchBackfillRequestBuilder {
                        override suspend fun build(): Result<List<WorkManagerRequestWrapper>> = Result.success(emptyList())
                    }
                },
                workManagerScheduler = FakeWorkManagerScheduler(submitLambda = {}, cancelLambda = { _, _ -> }),
            )
        },
    )
}
