package io.github.garemat.lunachron

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Verifies that [UserDatabase] migrations preserve existing user data.
 *
 * Each test opens the database at the old schema version, writes a row,
 * runs the migration, then reads back the row and asserts nothing was lost.
 * Add a new test here every time UserDatabase.version is incremented.
 *
 * Requires exportSchema = true and the schema JSON files in app/schemas/.
 */
@RunWith(AndroidJUnit4::class)
class UserDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        UserDatabase::class.java
    )

    /**
     * MIGRATION_1_2 adds five columns to the Campaign table.
     * A Campaign row written at v1 must be readable at v2 with the new
     * columns carrying their migration defaults (totalRounds=0, gameSize=6,
     * startingCharacters=6, characterGrowthEvery=1, upgradeGrowthEvery=3).
     */
    @Test
    @Throws(IOException::class)
    fun migrate1To2_campaignRowPreserved() {
        // Write a Campaign row at schema v1.
        helper.createDatabase("test_migration", 1).use { db ->
            db.execSQL(
                """INSERT INTO Campaign
                   (id, name, description, players, currentRound, rounds,
                    attacksEnabled, machinationPhaseActive, machinationDraft)
                   VALUES (1, 'Test Campaign', 'desc', '[]', 1, '[]', 0, 0, NULL)"""
            )
        }

        // Run the migration and validate the schema.
        helper.runMigrationsAndValidate("test_migration", 2, true, UserDatabase.MIGRATION_1_2).use { db ->
            val cursor = db.query("SELECT * FROM Campaign WHERE id = 1")
            cursor.moveToFirst()

            assertEquals("Test Campaign", cursor.getString(cursor.getColumnIndexOrThrow("name")))

            // New columns must carry the defaults from the migration SQL.
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("totalRounds")))
            assertEquals(6, cursor.getInt(cursor.getColumnIndexOrThrow("gameSize")))
            assertEquals(6, cursor.getInt(cursor.getColumnIndexOrThrow("startingCharacters")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("characterGrowthEvery")))
            assertEquals(3, cursor.getInt(cursor.getColumnIndexOrThrow("upgradeGrowthEvery")))

            cursor.close()
        }
    }
}
