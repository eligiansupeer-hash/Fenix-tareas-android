package app.fenix.tareas

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import app.fenix.tareas.ai.SecureProviderStore
import app.fenix.tareas.data.TaskEntity
import app.fenix.tareas.scheduler.TaskScheduler
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var requestedDestination by mutableStateOf(0)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedDestination = if (intent.getBooleanExtra(EXTRA_OPEN_RESULTS, false)) 3 else 0
    }

    companion object {
        const val EXTRA_OPEN_RESULTS = "app.fenix.tareas.OPEN_RESULTS"
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedDestination = if (intent.getBooleanExtra(EXTRA_OPEN_RESULTS, false)) 3 else 0
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppShell(initialDestination = requestedDestination)
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
    val executions by dao.observeExecutions().collectAsState(initial = emptyList())
    var configuredProviders by remember { mutableStateOf(emptyList<app.fenix.tareas.ai.ProviderConfig>()) }
    androidx.compose.runtime.LaunchedEffect(Unit) { configuredProviders = SecureProviderStore(context).loadWithCatalog() }
    val usableProviders = configuredProviders.filter { it.enabled && it.baseUrl.isNotBlank() && it.model.isNotBlank() && (it.noKey || it.apiKeys.any { key -> key.isNotBlank() }) }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryMetric("Activas", tasks.count { (it.status in listOf("En cola", "En ejecución") || it.status.startsWith("Reintentando")) }.toString(), Modifier.weight(1f))
                SummaryMetric("Completadas", executions.count { it.status == "Completada" }.toString(), Modifier.weight(1f))
                SummaryMetric("Con error", executions.count { it.status == "Error" }.toString(), Modifier.weight(1f))
            }
            tasks.filter { it.enabled && it.scheduleAtMillis != null }
                .minByOrNull { it.scheduleAtMillis ?: Long.MAX_VALUE }?.let { next ->
                    Text("Próxima ejecución estimada: ${next.title} · ${formatScheduleTime(next.scheduleAtMillis!!)}", style = MaterialTheme.typography.bodySmall)
                }
            Text(
                "${if (usableProviders.isEmpty()) "IA: no hay proveedores listos. Entrá en «IA y API» para configurar uno." else "Proveedores configurados: ${usableProviders.joinToString { it.name }}. Las tareas los usarán al ejecutarse; los fallos se guardan en Resultados."}",
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
                            onCancel = {
                                scope.launch {
                                    TaskScheduler.cancel(context, task.id)
                                    dao.update(task.copy(enabled = false, status = "Cancelada"))
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
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun formatScheduleTime(millis: Long): String =
    SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(millis))

@Composable
private fun TaskCard(task: TaskEntity, onRun: () -> Unit, onSchedule: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            Text(task.instructions, style = MaterialTheme.typography.bodyMedium)
            Text("Estado: ${task.status}", style = MaterialTheme.typography.labelMedium)
            if (task.enabled && task.scheduleAtMillis != null) {
                Text("Próxima ejecución: ${formatScheduleTime(task.scheduleAtMillis)}", style = MaterialTheme.typography.bodySmall)
            }
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
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel) { Text("Detener / cancelar horario") }
                OutlinedButton(onClick = onDelete) { Text("Eliminar") }
            }
        }
    }
}

@Composable
private fun NewTaskDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var showTemplates by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva tarea") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { showTemplates = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Usar plantilla de tarea")
                    }
                    androidx.compose.material3.DropdownMenu(
                        expanded = showTemplates,
                        onDismissRequest = { showTemplates = false }
                    ) {
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Investigación web con fuentes") },
                            onClick = {
                                title = "Investigación: [tema]"
                                instructions = "Investigá en la web: [tema]. Priorizá fuentes primarias y recientes; contrastá al menos tres fuentes cuando sea posible, separá hechos de inferencias y citá cada afirmación importante con enlaces."
                                showTemplates = false
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Comparar opciones") },
                            onClick = {
                                title = "Comparación: [opción A] vs [opción B]"
                                instructions = "Buscá en la web y compará [opción A] y [opción B] según [criterios]. Incluí ventajas, desventajas, costos o fechas solo si las fuentes los confirman, diferencias importantes, incertidumbres y enlaces."
                                showTemplates = false
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Resumen y plan de acción") },
                            onClick = {
                                title = "Resumen y próximos pasos: [tema]"
                                instructions = "Prepará un resumen claro de [tema], identificá los puntos clave, qué falta saber, riesgos o límites, y terminá con una lista priorizada de acciones concretas. No inventes datos; indicá qué información no está disponible."
                                showTemplates = false
                            }
                        )
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("Seguimiento periódico de novedades") },
                            onClick = {
                                title = "Seguimiento: novedades sobre [tema]"
                                instructions = "Investigá en la web las novedades recientes sobre [tema]. Priorizá publicaciones nuevas y fuentes primarias; indicá la fecha de cada fuente, qué cambió desde el último informe si hay resultados previos, qué sigue sin confirmarse y agregá enlaces. No inventes una comparación si no hay informe anterior."
                                showTemplates = false
                            }
                        )
                    }
                }
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
