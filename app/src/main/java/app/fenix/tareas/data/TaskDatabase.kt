package app.fenix.tareas.data
import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
@Database(entities = [TaskEntity::class], version = 2, exportSchema = false)
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
        @Volatile private var instance: TaskDatabase? = null
        fun get(context: Context): TaskDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TaskDatabase::class.java, "fenix-tareas.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
