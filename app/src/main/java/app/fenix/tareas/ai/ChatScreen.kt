package app.fenix.tareas.ai

import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class UiMessage(val role: String, val text: String, val provider: String? = null)

@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val store = remember { SecureProviderStore(context) }
    val client = remember { AiClient(store) }
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<UiMessage>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { store.loadWithCatalog() }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Fénix IA", style = MaterialTheme.typography.headlineMedium)
        Text("Conversación, investigación web y tareas. La fecha y hora se leen del reloj real del dispositivo.", style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (messages.isEmpty()) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("¿Qué necesitás investigar o resolver?", style = MaterialTheme.typography.titleMedium)
                        Text("Probá: “Investigá las últimas novedades sobre…” o preguntá directamente.")
                    }
                }
            }
            items(messages) { message ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = if (message.role == "user") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (message.role == "user") "Vos" else "Fénix IA", style = MaterialTheme.typography.labelMedium)
                        Text(message.text)
                        message.provider?.let { Text("Proveedor: $it", style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                label = { Text("Escribí un mensaje") }, maxLines = 4, enabled = !busy)
            Button(enabled = input.isNotBlank() && !busy, onClick = {
                val prompt = input.trim()
                input = ""; error = null
                messages.add(UiMessage("user", prompt)); busy = true
                scope.launch {
                    try {
                        store.loadWithCatalog()
                        val lower = prompt.lowercase()
                        var answer: Pair<String, String> = "" to ""
                        if (lower.contains("alarma") || lower.contains("despertador")) {
                            val match = Regex("""(?:a las|para las|las)\s+(\d{1,2})(?::(\d{2}))?""").find(lower)
                            if (match == null) {
                                answer = "Decime la hora, por ejemplo: “poné una alarma a las 7:30”. La hora actual del teléfono es ${DeviceClock.currentTime()}." to "Skill de alarmas"
                            } else {
                                val hour = match.groupValues[1].toIntOrNull() ?: -1
                                val minute = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: 0
                                if (hour !in 0..23 || minute !in 0..59) {
                                    answer = "La hora no es válida. Usá formato de 24 horas, por ejemplo 19:30. La hora actual del teléfono es ${DeviceClock.currentTime()}." to "Skill de alarmas"
                                } else {
                                    try {
                                        val alarm = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                                            putExtra(AlarmClock.EXTRA_HOUR, hour)
                                            putExtra(AlarmClock.EXTRA_MINUTES, minute)
                                            putExtra(AlarmClock.EXTRA_MESSAGE, prompt)
                                            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                                        }
                                        context.startActivity(alarm)
                                        answer = "Hora actual del teléfono: ${DeviceClock.currentTime()}. Solicité al reloj del sistema una alarma para %02d:%02d; revisá esa hora en la pantalla que se abrió y confirmala. Fénix no cambia la hora pedida ni confirma la alarma por vos.".format(hour, minute) to "Skill de alarmas"
                                    } catch (_: Exception) {
                                        answer = "No encontré una aplicación de reloj compatible para crear la alarma." to "Skill de alarmas"
                                    }
                                }
                            }
                        } else if (listOf("investig", "buscá", "busca en la web", "fuentes", "noticias").any { lower.contains(it) }) {
                            val hits = WebResearch.search(prompt)
                            if (hits.isEmpty()) {
                                val history = messages.map { ChatMessage(it.role, it.text) }
                                answer = client.complete(listOf(ChatMessage("system", DeviceClock.contextLine())) + history)
                                    .let { "No encontré resultados web en esta búsqueda. Respuesta del modelo: ${it.first}" to it.second }
                            } else {
                                val sourceText = hits.joinToString("\n\n") { "- ${it.title}\n${it.snippet}\nURL: ${it.url}" }
                                val researchPrompt = "Respondé en español usando únicamente estos resultados de búsqueda. Aclarà que son resultados preliminares, distinguí hechos de inferencias y conservá los enlaces como fuentes.\nConsulta: $prompt\n\nResultados:\n$sourceText"
                                answer = client.complete(listOf(ChatMessage("system", DeviceClock.contextLine()), ChatMessage("user", researchPrompt)))
                                    .let { "${it.first}\n\nFuentes consultadas:\n" + hits.joinToString("\n") { hit -> "• ${hit.title}: ${hit.url}" } to it.second }
                            }
                        } else {
                            val history = messages.map { ChatMessage(it.role, it.text) }
                            answer = client.complete(listOf(ChatMessage("system", DeviceClock.contextLine())) + history)
                        }
                        messages.add(UiMessage("assistant", answer.first, answer.second))
                    } catch (e: Exception) { error = e.message ?: "No se pudo completar la solicitud." }
                    finally { busy = false }
                }
            }) { Text("Enviar") }
        }
    }
}
