package app.fenix.tareas

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.util.Calendar
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
import androidx.compose.material3.Checkbox
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
                    AppShell()
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TaskHome() {
    val context = LocalContext.current
    val dao = remember { TaskDatabase.get(context).taskDao() }
    val tasks by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var taskToSchedule by remember { mutableStateOf<TaskEntity?>(null) }

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
                            onSchedule = { taskToSchedule = task },
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

    taskToSchedule?.let { task ->
        ScheduleTaskDialog(
            task = task,
            onDismiss = { taskToSchedule = null },
            onSave = { runAt, hour, minute, days ->
                scope.launch {
                    val updated = task.copy(
                        enabled = true,
                        scheduleAtMillis = runAt,
                        scheduleHour = hour,
                        scheduleMinute = minute,
                        repeatDays = days.joinToString(","),
                        status = if (days.isEmpty()) "Programada para fecha y hora elegidas" else "Programada semanalmente"
                    )
                    dao.update(updated)
                    TaskScheduler.cancel(context, task.id)
                    TaskScheduler.schedule(context, updated)
                    taskToSchedule = null
                }
            }
        )
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
                    Text("Programar")
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


@Composable
private fun ScheduleTaskDialog(
    task: TaskEntity,
    onDismiss: () -> Unit,
    onSave: (Long, Int, Int, Set<Int>) -> Unit
) {
    val context = LocalContext.current
    val initial = Calendar.getInstance().apply {
        timeInMillis = task.scheduleAtMillis ?: (System.currentTimeMillis() + 60 * 60 * 1000L)
    }
    var selectedDate by remember { mutableStateOf(initial.timeInMillis) }
    var selectedHour by remember { mutableStateOf(task.scheduleHour ?: initial.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(task.scheduleMinute ?: initial.get(Calendar.MINUTE)) }
    var selectedDays by remember {
        mutableStateOf(task.repeatDays.split(",").mapNotNull { it.toIntOrNull() }.toSet())
    }
    val dayOptions = listOf(
        Calendar.MONDAY to "Lun",
        Calendar.TUESDAY to "Mar",
        Calendar.WEDNESDAY to "Mié",
        Calendar.THURSDAY to "Jue",
        Calendar.FRIDAY to "Vie",
        Calendar.SATURDAY to "Sáb",
        Calendar.SUNDAY to "Dom"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Programar: " + task.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val c = Calendar.getInstance().apply { timeInMillis = selectedDate }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            val updated = Calendar.getInstance().apply {
                                timeInMillis = selectedDate
                                set(Calendar.YEAR, year)
                                set(Calendar.MONTH, month)
                                set(Calendar.DAY_OF_MONTH, day)
                            }
                            selectedDate = updated.timeInMillis
                        },
                        c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Elegir fecha: " + java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(selectedDate)))
                }
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        selectedHour = h
                        selectedMinute = m
                    }, selectedHour, selectedMinute, true).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Hora: " + "%02d:%02d".format(selectedHour, selectedMinute))
                }
                Text("Si elegís días, se repetirá semanalmente a esa hora:", style = MaterialTheme.typography.bodyMedium)
                dayOptions.forEach { (day, label) ->
                    Row {
                        Checkbox(
                            checked = day in selectedDays,
                            onCheckedChange = { checked ->
                                selectedDays = if (checked) selectedDays + day else selectedDays - day
                            }
                        )
                        Text(label, modifier = Modifier.padding(top = 12.dp))
                    }
                }
                Text(
                    if (selectedDays.isEmpty()) "Una sola ejecución en la fecha y hora elegidas." else "Repetición semanal: " + dayOptions.filter { it.first in selectedDays }.joinToString { it.second },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val date = Calendar.getInstance().apply {
                    timeInMillis = selectedDate
                    set(Calendar.HOUR_OF_DAY, selectedHour)
                    set(Calendar.MINUTE, selectedMinute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val runAt = if (selectedDays.isEmpty()) date else TaskScheduler.nextWeeklyRun(
                    task.copy(scheduleHour = selectedHour, scheduleMinute = selectedMinute, repeatDays = selectedDays.joinToString(",")),
                    System.currentTimeMillis()
                )
                onSave(runAt, selectedHour, selectedMinute, selectedDays)
            }) { Text("Guardar horario") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
