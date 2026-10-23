package app.fenix.tareas.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.fenix.tareas.data.TaskDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RETRY_TASK) return
        val taskId = intent.getLongExtra(TaskWorker.KEY_TASK_ID, -1L)
        if (taskId <= 0L) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = TaskDatabase.get(appContext).taskDao()
                val task = dao.findById(taskId) ?: return@launch
                dao.update(task.copy(status = "En cola", lastResult = task.lastResult))
                TaskScheduler.enqueue(appContext, taskId)
            } catch (_: Exception) {
                // A notification action must not crash the process if the database or scheduler is unavailable.
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_RETRY_TASK = "app.fenix.tareas.action.RETRY_TASK"
    }
}
