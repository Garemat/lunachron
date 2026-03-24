package io.github.garemat.lunachron

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Troupe::class, Campaign::class, GameResult::class],
    version = 1,
    exportSchema = true  // required for MigrationTestHelper in future migration tests
)
@TypeConverters(Converters::class)
abstract class UserDatabase : RoomDatabase() {

    abstract val dao: UserDataDAO

    companion object {
        @Volatile
        private var INSTANCE: UserDatabase? = null

        fun getDatabase(context: Context): UserDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UserDatabase::class.java,
                    "moonstone_user_db"
                )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
