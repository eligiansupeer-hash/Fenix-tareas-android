package app.fenix.tareas.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "task_executions",
    indices = [Index(value = ["taskId", "startedAtMillis"])]
)
data class TaskExecutionEntity(
    @PrimaryKey val executionId: String,
    val taskId: Long,
    val taskTitle: String,
    val status: String,
    val startedAtMillis: Long,
    val finishedAtMillis: Long? = null,
    val durationMillis: Long? = null,
    val attemptCount: Int = 1,
    val provider: String? = null,
    val result: String? = null,
    val error: String? = null,
    @ColumnInfo(defaultValue = "0") val sourceCount: Int = 0
)
