package app.fenix.tareas.scheduler
import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.fenix.tareas.data.TaskEntity
import java.util.Calendar
import java.util.concurrent.TimeUnit
object TaskScheduler {
    fun enqueue(context: Context, taskId: Long, runAtMillis: Long? = null) {
        val delay = ((runAtMillis ?: System.currentTimeMillis()) - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<TaskWorker>()
            .setInputData(workDataOf(TaskWorker.KEY_TASK_ID to taskId))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS).addTag("fenix-task-$taskId").build()
        WorkManager.getInstance(context).enqueueUniqueWork("fenix-task-$taskId", androidx.work.ExistingWorkPolicy.REPLACE, request)
    }
    fun schedule(context: Context, task: TaskEntity) {
        val time = task.scheduleAtMillis ?: return
        enqueue(context, task.id, if (task.repeatDays.isBlank()) time else nextWeeklyRun(task, System.currentTimeMillis()))
    }
    fun nextWeeklyRun(task: TaskEntity, afterMillis: Long): Long {
        val hour = task.scheduleHour ?: 9
        val minute = task.scheduleMinute ?: 0
        val days = task.repeatDays.split(",").mapNotNull { it.toIntOrNull() }.toSet()
        val base = Calendar.getInstance().apply { timeInMillis = afterMillis }
        for (offset in 0..7) {
            val candidate = (base.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val day = candidate.get(Calendar.DAY_OF_WEEK)
            if (day in days && candidate.timeInMillis > afterMillis) return candidate.timeInMillis
        }
        return afterMillis + TimeUnit.DAYS.toMillis(7)
    }
    fun cancel(context: Context, taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork("fenix-task-$taskId")
        WorkManager.getInstance(context).cancelAllWorkByTag("fenix-task-$taskId")
    }
}
