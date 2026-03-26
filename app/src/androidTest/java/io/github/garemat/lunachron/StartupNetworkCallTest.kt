package io.github.garemat.lunachron

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies that the app makes zero outbound network requests on startup when
 * all auto-fetch settings are at their defaults (off).
 *
 * A [MockEngine] is injected into both [CharacterViewModel] (news) and
 * [DataUpdateRepository] (data/app updates). Any HTTP request increments a
 * counter; the primary test asserts that counter remains zero after startup.
 *
 * Companion tests confirm the inverse: enabling each setting triggers at least
 * one request, proving the [MockEngine] is wired correctly and that the gating
 * logic runs in both directions.
 *
 * Note: [CountDownLatch] is used for the inverse tests because [CharacterViewModel]
 * launches coroutines on [androidx.lifecycle.viewModelScope] (real background
 * threads), which are not controlled by the test's coroutine scheduler.
 */
@RunWith(AndroidJUnit4::class)
class StartupNetworkCallTest {

    private lateinit var app: Application
    private lateinit var gameDb: GameDatabase
    private lateinit var userDb: UserDatabase
    private lateinit var requestCount: AtomicInteger
    private lateinit var mockClient: HttpClient

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()

        // Clear prefs so every test starts from defaults (all auto-* = false)
        app.getSharedPreferences("lunachron_prefs", Context.MODE_PRIVATE).edit { clear() }

        gameDb = Room.inMemoryDatabaseBuilder(app, GameDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userDb = Room.inMemoryDatabaseBuilder(app, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        requestCount = AtomicInteger(0)
        mockClient = buildClient(onRequest = null)
    }

    @After
    fun tearDown() {
        gameDb.close()
        userDb.close()
        mockClient.close()
    }

    private fun buildClient(onRequest: (() -> Unit)?): HttpClient {
        val engine = MockEngine { _ ->
            requestCount.incrementAndGet()
            onRequest?.invoke()
            respond("", HttpStatusCode.OK)
        }
        return HttpClient(engine)
    }

    private fun buildViewModel(): CharacterViewModel {
        val repo = LocalCharacterRepository(gameDb.dao, userDb.dao)
        val dataUpdateRepo = DataUpdateRepository(app, repo, mockClient)
        return CharacterViewModel(app, repo, dataUpdateRepo, newsClient = mockClient)
    }

    @Test
    fun noNetworkCallsOnStartupWithDefaultSettings() {
        buildViewModel()
        // Sleep briefly so any potential startup coroutines have time to fire
        Thread.sleep(1000)
        assertEquals(
            "Expected 0 network calls on startup with all auto-fetch settings off, got ${requestCount.get()}",
            0,
            requestCount.get()
        )
    }

    @Test
    fun networkCallMadeWhenAutoFetchNewsEnabled() {
        app.getSharedPreferences("lunachron_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("auto_fetch_news", true)
        }
        val latch = CountDownLatch(1)
        mockClient = buildClient(onRequest = { latch.countDown() })

        buildViewModel()

        assertTrue(
            "Expected a network call within 10s when auto_fetch_news=true",
            latch.await(10, TimeUnit.SECONDS)
        )
    }

    @Test
    fun networkCallMadeWhenAutoCheckDataUpdatesEnabled() {
        app.getSharedPreferences("lunachron_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("auto_check_data_updates", true)
        }
        val latch = CountDownLatch(1)
        mockClient = buildClient(onRequest = { latch.countDown() })

        buildViewModel()

        assertTrue(
            "Expected a network call within 10s when auto_check_data_updates=true",
            latch.await(10, TimeUnit.SECONDS)
        )
    }
}
