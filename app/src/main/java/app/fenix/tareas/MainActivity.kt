package app.fenix.tareas

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.fenix.tareas.data.TaskDatabase
import app.fenix.tareas.data.TaskEntity
import app.fenix.tareas.scheduler.TaskScheduler
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TaskHome()
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TaskHome() {
    val context = LocalContext.current
    val dao = remember { TaskDatabase.get(context).taskDao() }
    val tasks by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Fénix Tareas") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Crear tarea")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Organizá, ejecutá y programá tus tareas.", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Versión inicial: la ejecución se registra localmente. Las conexiones web e IA todavía no están activas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (tasks.isEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Todavía no hay tareas. Tocá + para crear la primera.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onRun = {
                                scope.launch {
                                    dao.update(task.copy(status = "En cola"))
                                    TaskScheduler.enqueue(context, task.id)
                                }
                            },
                            onSchedule = {
                                scope.launch {
                                    val runAt = System.currentTimeMillis() + 15 * 60 * 1000L
                                    dao.update(task.copy(
                                        enabled = true,
                                        scheduleAtMillis = runAt,
                                        status = "Programada para dentro de 15 minutos"
                                    ))
                                    TaskScheduler.enqueue(context, task.id, runAt)
                                }
                            },
                            onDelete = {
                                scope.launch {
                                    TaskScheduler.cancel(context, task.id)
                                    dao.delete(task)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        NewTaskDialog(
            onDismiss = { showDialog = false },
            onCreate = { title, instructions ->
                scope.launch {
                    dao.insert(TaskEntity(title = title, instructions = instructions))
                    showDialog = false
                }
            }
        )
    }
}

@Composable
private fun TaskCard(task: TaskEntity, onRun: () -> Unit, onSchedule: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            Text(task.instructions, style = MaterialTheme.typography.bodyMedium)
            Text("Estado: ${task.status}", style = MaterialTheme.typography.labelMedium)
            task.lastResult?.let { Text("Último resultado: $it", style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRun) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text(" Ejecutar")
                }
                OutlinedButton(onClick = onSchedule) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Text(" +15 min")
                }
                OutlinedButton(onClick = onDelete) { Text("Eliminar") }
            }
        }
    }
}

@Composable
private fun NewTaskDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva tarea") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Qué debe hacer") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title.trim(), instructions.trim()) },
                enabled = title.isNotBlank() && instructions.isNotBlank()
            ) { Text("Crear") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
