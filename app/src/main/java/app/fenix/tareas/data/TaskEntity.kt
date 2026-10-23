package app.fenix.tareas.data
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val instructions: String,
    val scheduleAtMillis: Long? = null,
    val enabled: Boolean = false,
    val status: String = "Pendiente",
    val lastRunAtMillis: Long? = null,
    val lastResult: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val repeatDays: String = "",
    val scheduleHour: Int? = null,
    val scheduleMinute: Int? = null
)
