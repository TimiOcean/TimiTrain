package de.timitrain.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.timitrain.domain.InitialNumbers

@Database(
    entities = [
        ExerciseEntity::class,
        ExerciseDefaultSetEntity::class,
        WorkoutEntity::class,
        WorkoutSlotEntity::class,
        SlotAlternativeEntity::class,
        PlannedSetEntity::class,
        WeekPlanEntity::class,
        SessionEntity::class,
        SessionSetEntity::class,
        SessionPlanSnapshotEntity::class,
        SessionRotationSnapshotEntity::class,
        SettingsEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class TimiTrainDatabase : RoomDatabase() {
    abstract fun exercises(): ExerciseDao
    abstract fun workouts(): WorkoutDao
    abstract fun slots(): WorkoutSlotDao
    abstract fun alternatives(): SlotAlternativeDao
    abstract fun plannedSets(): PlannedSetDao
    abstract fun weekPlan(): WeekPlanDao
    abstract fun sessions(): SessionDao
    abstract fun settings(): SettingsDao

    companion object {
        fun create(context: Context): TimiTrainDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                TimiTrainDatabase::class.java,
                "timitrain.db",
            ).addCallback(SeedCallback).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        }

        internal fun createInMemory(context: Context): TimiTrainDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                TimiTrainDatabase::class.java,
            ).addCallback(SeedCallback).allowMainThreadQueries().build()
        }
    }
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `session_plan_snapshots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `alternativeId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `reps` INTEGER, `weightKg` REAL, `durationSec` INTEGER, `durationMin` INTEGER, `distanceKm` REAL, FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_plan_snapshots_sessionId` ON `session_plan_snapshots` (`sessionId`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `session_rotation_snapshots` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `sessionId` INTEGER NOT NULL, `slotId` INTEGER NOT NULL, `rotationIndex` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_rotation_snapshots_sessionId` ON `session_rotation_snapshots` (`sessionId`)",
        )
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exercise_default_sets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `exerciseId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `reps` INTEGER, `weightKg` REAL, `durationSec` INTEGER, `durationMin` INTEGER, `distanceKm` REAL, FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_exercise_default_sets_exerciseId` ON `exercise_default_sets` (`exerciseId`)",
        )
    }
}

private object SeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        for (weekday in 1..7) {
            db.execSQL("INSERT INTO week_plan (weekday, workoutId) VALUES ($weekday, NULL)")
        }
        db.execSQL(
            "INSERT INTO settings (id, defaultRestSeconds) VALUES (1, ${InitialNumbers.DEFAULT_REST_SECONDS})",
        )
    }
}
