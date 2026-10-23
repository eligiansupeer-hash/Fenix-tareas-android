package app.fenix.tareas

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.fenix.tareas.data.TaskDatabase
import app.fenix.tareas.data.TaskExecutionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen() {
    val context = LocalContext.current
    val dao = remember { TaskDatabase.get(context).taskDao() }
    val scope = rememberCoroutineScope()
    val executions by dao.observeExecutions().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("Todas") }
    var confirmClear by remember { mutableStateOf(false) }
    var showRetentionMenu by remember { mutableStateOf(false) }
    val preferences = remember { context.getSharedPreferences("fenix_results_preferences", android.content.Context.MODE_PRIVATE) }
    var retentionDays by remember { mutableIntStateOf(preferences.getInt("retention_days", 0)) }

    LaunchedEffect(retentionDays) {
        if (retentionDays > 0) {
            dao.pruneHistoryBefore(System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L)
        }
    }
    val statuses = listOf("Todas", "Completada", "Error", "Reintentando", "Cancelada", "En ejecución")
    val filtered = executions.filter { execution ->
        val matchesQuery = query.isBlank() ||
            execution.taskTitle.contains(query, ignoreCase = true) ||
            execution.result.orEmpty().contains(query, ignoreCase = true) ||
            execution.error.orEmpty().contains(query, ignoreCase = true)
        val matchesStatus = statusFilter == "Todas" ||
            (statusFilter == "En ejecución" && execution.status in listOf("En ejecución", "Pendiente", "En cola")) ||
            execution.status == statusFilter
        matchesQuery && matchesStatus
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Historial de ejecuciones", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { confirmClear = true }, enabled = executions.isNotEmpty()) { Text("Borrar todo") }
        }
        Text("Cada ejecución conserva su propio estado, duración, intentos y resultado en este dispositivo.", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Conservar historial:")
            Box {
                OutlinedButton(onClick = { showRetentionMenu = true }) {
                    Text(when (retentionDays) { 30 -> "30 días"; 90 -> "90 días"; else -> "Sin límite" })
                }
                DropdownMenu(expanded = showRetentionMenu, onDismissRequest = { showRetentionMenu = false }) {
                    DropdownMenuItem(text = { Text("Sin límite") }, onClick = {
                        retentionDays = 0
                        preferences.edit().putInt("retention_days", 0).apply()
                        showRetentionMenu = false
                    })
                    DropdownMenuItem(text = { Text("30 días") }, onClick = {
                        retentionDays = 30
                        preferences.edit().putInt("retention_days", 30).apply()
                        showRetentionMenu = false
                    })
                    DropdownMenuItem(text = { Text("90 días") }, onClick = {
                        retentionDays = 90
                        preferences.edit().putInt("retention_days", 90).apply()
                        showRetentionMenu = false
                    })
                }
            }
            Text("Los registros antiguos se borran automáticamente según esta elección.", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar en tareas e informes") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            statuses.take(3).forEach { status ->
                FilterChip(selected = statusFilter == status, onClick = { statusFilter = status }, label = { Text(status) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            statuses.drop(3).forEach { status ->
                FilterChip(selected = statusFilter == status, onClick = { statusFilter = status }, label = { Text(status) })
            }
        }
        if (filtered.isEmpty()) {
            Text(if (executions.isEmpty()) "Todavía no hay ejecuciones registradas. Ejecutá una tarea desde Tareas." else "No hay ejecuciones que coincidan con el filtro.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.executionId }) { execution ->
                    ExecutionCard(
                        execution = execution,
                        onShare = { shareExecution(context, execution) },
                        onDelete = { scope.launch { dao.deleteExecutionAndClearLatestResult(execution) } }
                    )
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Borrar todo el historial") },
            text = { Text("Se eliminarán los registros de ejecución y sus informes guardados. Las definiciones de tareas no se borrarán.") },
            confirmButton = {
                Button(onClick = {
                    scope.launch { dao.clearHistoryAndTaskResults() }
                    confirmClear = false
                }) { Text("Borrar historial") }
            },
            dismissButton = { OutlinedButton(onClick = { confirmClear = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ExecutionCard(
    execution: TaskExecutionEntity,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(execution.taskTitle, style = MaterialTheme.typography.titleMedium)
            Text("Estado: ${execution.status}", style = MaterialTheme.typography.labelMedium)
            Text(
                "Inicio: ${formatDate(execution.startedAtMillis)}" +
                    (execution.finishedAtMillis?.let { " · Fin: ${formatDate(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall
            )
            execution.durationMillis?.let {
                Text("Duración: ${formatDuration(it)} · Intentos: ${execution.attemptCount}", style = MaterialTheme.typography.bodySmall)
            } ?: Text("Intento actual: ${execution.attemptCount}", style = MaterialTheme.typography.bodySmall)
            execution.provider?.let { Text("Proveedor: $it", style = MaterialTheme.typography.bodySmall) }
            if (execution.sourceCount > 0) Text("Fuentes recuperadas: ${execution.sourceCount}", style = MaterialTheme.typography.bodySmall)
            execution.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            execution.result?.let { Text(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onShare, enabled = !execution.result.isNullOrBlank() || !execution.error.isNullOrBlank()) {
                    Text("Compartir")
                }
                OutlinedButton(onClick = onDelete) { Text("Eliminar registro") }
            }
        }
    }
}

private fun shareExecution(context: android.content.Context, execution: TaskExecutionEntity) {
    val content = buildString {
        appendLine("Fénix Tareas — ${execution.taskTitle}")
        appendLine("Estado: ${execution.status}")
        appendLine("Inicio: ${formatDate(execution.startedAtMillis)}")
        execution.durationMillis?.let { appendLine("Duración: ${formatDuration(it)}") }
        execution.provider?.let { appendLine("Proveedor: $it") }
        execution.error?.let { appendLine("\nError: $it") }
        execution.result?.let { appendLine("\n$it") }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Fénix Tareas: ${execution.taskTitle}")
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir resultado"))
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(millis))

private fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0L)
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}
