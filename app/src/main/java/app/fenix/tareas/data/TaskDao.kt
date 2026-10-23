package app.fenix.tareas.data
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
@Dao
interface TaskDao {
    @Query("SELECT * FROM task_executions ORDER BY startedAtMillis DESC")
    fun observeExecutions(): Flow<List<TaskExecutionEntity>>

    @Query("SELECT * FROM task_executions WHERE executionId = :executionId LIMIT 1")
    suspend fun findExecution(executionId: String): TaskExecutionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertExecution(execution: TaskExecutionEntity)

    @Query("SELECT * FROM tasks ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): TaskEntity?
    @Insert suspend fun insert(task: TaskEntity): Long
    @Update suspend fun update(task: TaskEntity)
    @Delete suspend fun delete(task: TaskEntity)
}
