package app.fenix.tareas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.fenix.tareas.data.TaskDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ResultsScreen() {
    val context = LocalContext.current
    val dao = remember { TaskDatabase.get(context).taskDao() }
    val tasks by dao.observeAll().collectAsState(initial = emptyList())
    val completed = tasks.filter { !it.lastResult.isNullOrBlank() }.sortedByDescending { it.lastRunAtMillis ?: 0L }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Resultados de tareas", style = MaterialTheme.typography.headlineMedium)
        Text("Las respuestas y errores de cada ejecución se guardan en el dispositivo.", style = MaterialTheme.typography.bodySmall)
        if (completed.isEmpty()) {
            Text("Todavía no hay resultados. Ejecutá una tarea desde la sección Tareas.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(completed, key = { it.id }) { task ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium)
                            Text("Estado: ${task.status}", style = MaterialTheme.typography.labelMedium)
                            task.lastRunAtMillis?.let { Text(SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it)), style = MaterialTheme.typography.bodySmall) }
                            Text(task.lastResult ?: "")
                        }
                    }
                }
            }
        }
    }
}
