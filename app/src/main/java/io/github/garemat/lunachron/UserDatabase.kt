package io.github.garemat.lunachron

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Troupe::class, Campaign::class, GameResult::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class UserDatabase : RoomDatabase() {

    abstract val dao: UserDataDAO

    companion object {
        @Volatile
        private var INSTANCE: UserDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Campaign ADD COLUMN totalRounds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Campaign ADD COLUMN gameSize INTEGER NOT NULL DEFAULT 6")
                db.execSQL("ALTER TABLE Campaign ADD COLUMN startingCharacters INTEGER NOT NULL DEFAULT 6")
                db.execSQL("ALTER TABLE Campaign ADD COLUMN characterGrowthEvery INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE Campaign ADD COLUMN upgradeGrowthEvery INTEGER NOT NULL DEFAULT 3")
            }
        }

        fun getDatabase(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UserDatabase::class.java,
                    "moonstone_user_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
