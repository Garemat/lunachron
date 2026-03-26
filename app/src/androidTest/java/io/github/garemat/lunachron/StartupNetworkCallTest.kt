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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * Verifies that the app makes zero outbound network requests on startup when
 * all auto-fetch settings are at their defaults (off).
 *
 * A [MockEngine] is injected into both [CharacterViewModel] (news) and
 * [DataUpdateRepository] (data/app updates). Any HTTP request increments a
 * counter; the test asserts that counter remains zero after the ViewModel
 * initialises and all startup coroutines complete.
 *
 * Companion tests confirm the inverse: that enabling each setting does trigger
 * at least one request, proving the MockEngine is wired correctly.
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

        // Clear prefs so every test starts from the default (all auto-* = false)
        app.getSharedPreferences("lunachron_prefs", Context.MODE_PRIVATE).edit { clear() }

        gameDb = Room.inMemoryDatabaseBuilder(app, GameDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userDb = Room.inMemoryDatabaseBuilder(app, UserDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        requestCount = AtomicInteger(0)
        val engine = MockEngine { _ ->
            requestCount.incrementAndGet()
            respond("", HttpStatusCode.OK)
        }
        mockClient = HttpClient(engine)
    }

    @After
    fun tearDown() {
        gameDb.close()
        userDb.close()
        mockClient.close()
    }

    private fun buildViewModel(): CharacterViewModel {
        val repo = LocalCharacterRepository(gameDb.dao, userDb.dao)
        val dataUpdateRepo = DataUpdateRepository(app, repo, mockClient)
        return CharacterViewModel(app, repo, dataUpdateRepo, newsClient = mockClient)
    }

    @Test
    fun noNetworkCallsOnStartupWithDefaultSettings() = runTest {
        buildViewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(
            "Expected 0 network calls on startup with all auto-fetch settings off, got ${requestCount.get()}",
            0,
            requestCount.get()
        )
    }

    @Test
    fun networkCallMadeWhenAutoFetchNewsEnabled() = runTest {
        app.getSharedPreferences("lunachron_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("auto_fetch_news", true)
        }

        buildViewModel()
        testScheduler.advanceUntilIdle()

        assert(requestCount.get() >= 1) {
            "Expected at least 1 network call when auto_fetch_news = true, got 0"
        }
    }

    @Test
    fun networkCallMadeWhenAutoCheckDataUpdatesEnabled() = runTest {
        app.getSharedPreferences("lunachron_prefs", Context.MODE_PRIVATE).edit {
            putBoolean("auto_check_data_updates", true)
        }

        buildViewModel()
        testScheduler.advanceUntilIdle()

        assert(requestCount.get() >= 1) {
            "Expected at least 1 network call when auto_check_data_updates = true, got 0"
        }
    }
}
