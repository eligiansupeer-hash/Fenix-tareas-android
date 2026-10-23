package app.fenix.tareas.scheduler
import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
object TaskScheduler {
    fun enqueue(context: Context, taskId: Long, runAtMillis: Long? = null) {
        val delay = ((runAtMillis ?: System.currentTimeMillis()) - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<TaskWorker>()
            .setInputData(workDataOf(TaskWorker.KEY_TASK_ID to taskId))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS).addTag("fenix-task-$taskId").build()
        WorkManager.getInstance(context).enqueue(request)
    }
    fun cancel(context: Context, taskId: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag("fenix-task-$taskId")
    }
}
