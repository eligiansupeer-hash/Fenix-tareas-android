package app.fenix.tareas.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TaskEntity::class, TaskExecutionEntity::class], version = 4, exportSchema = false)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN repeatDays TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tasks ADD COLUMN scheduleHour INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN scheduleMinute INTEGER")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS task_executions (
                        executionId TEXT NOT NULL,
                        taskId INTEGER NOT NULL,
                        taskTitle TEXT NOT NULL,
                        status TEXT NOT NULL,
                        startedAtMillis INTEGER NOT NULL,
                        finishedAtMillis INTEGER,
                        durationMillis INTEGER,
                        attemptCount INTEGER NOT NULL,
                        provider TEXT,
                        result TEXT,
                        error TEXT,
                        sourceCount INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(executionId)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_task_executions_taskId_startedAtMillis " +
                        "ON task_executions(taskId, startedAtMillis)"
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN workflowSteps TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile private var instance: TaskDatabase? = null

        fun get(context: Context): TaskDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TaskDatabase::class.java,
                "fenix-tareas.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}
