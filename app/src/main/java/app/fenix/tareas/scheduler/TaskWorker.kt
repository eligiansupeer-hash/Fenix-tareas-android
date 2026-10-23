package app.fenix.tareas.scheduler
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.fenix.tareas.data.TaskDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class TaskWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val taskId = inputData.getLong(KEY_TASK_ID, -1L)
        if (taskId <= 0L) return@withContext Result.failure()
        val dao = TaskDatabase.get(applicationContext).taskDao()
        val task = dao.findById(taskId) ?: return@withContext Result.failure()
        val now = System.currentTimeMillis()
        val resultText = "Ejecución local registrada. La integración web e IA aún no está configurada."
        dao.update(task.copy(status = "Completada", lastRunAtMillis = now, lastResult = resultText))
        showNotification(task.title, "La tarea terminó. Revisá el resultado en Fénix Tareas.")
        Result.success()
    }
    private fun showNotification(title: String, body: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Tareas de Fénix", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(applicationContext).notify(title.hashCode(), notification) }
        catch (_: SecurityException) { /* Notification permission can be denied. */ }
    }
    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val CHANNEL_ID = "fenix_task_notifications"
    }
}
