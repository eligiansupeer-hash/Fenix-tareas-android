package app.fenix.tareas.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Composable
fun AiSettingsScreen() {
    val context = LocalContext.current
    val store = remember { SecureProviderStore(context) }
    var providers by remember { mutableStateOf(store.load()) }
    var selected by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { providers = store.loadWithCatalog() }
    val current = providers.getOrNull(selected)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Proveedores de IA", style = MaterialTheme.typography.headlineMedium)
        Text("Las claves se cifran localmente con Android Keystore. No se suben a Fénix. El catálogo nuevo se incorpora sin borrar tus ajustes. Las URLs base y los enlaces para obtener claves se pueden editar.", style = MaterialTheme.typography.bodySmall)
        Text("Se permiten hasta 10 credenciales por proveedor y se intenta el siguiente proveedor/clave cuando una llamada falla.")
        LazyColumn(Modifier.fillMaxWidth().height(160.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(providers) { index, provider ->
                OutlinedButton(onClick = { selected = index }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (index == selected) "● " else "") + provider.name + if (provider.noKey) " · sin clave (experimental)" else " · ${provider.apiKeys.count { it.isNotBlank() }} claves")
                }
            }
        }
        current?.let { provider ->
            Text(provider.name, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = provider.name, onValueChange = { v -> providers = providers.toMutableList().also { it[selected] = provider.copy(name = v) } }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = provider.baseUrl, onValueChange = { v -> providers = providers.toMutableList().also { it[selected] = provider.copy(baseUrl = v) } }, label = { Text("URL base de la API (editable)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = provider.apiKeyUrl, onValueChange = { v -> providers = providers.toMutableList().also { it[selected] = provider.copy(apiKeyUrl = v) } }, label = { Text("Enlace para obtener la API key (editable)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (provider.apiKeyUrl.startsWith("https://")) {
                OutlinedButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.apiKeyUrl)))
                    } catch (_: Exception) {
                        status = "No se pudo abrir el enlace. Copiá la URL y abrila en el navegador."
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text(if (provider.noKey) "Abrir portal oficial del proveedor" else "Obtener API key / abrir portal oficial") }
            }
            OutlinedTextField(value = provider.model, onValueChange = { v -> providers = providers.toMutableList().also { it[selected] = provider.copy(model = v) } }, label = { Text("Modelo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text(ProviderCapabilities.forProvider(provider).explanation, style = MaterialTheme.typography.bodySmall)
            Text("También podés pedir en el chat: “cambiá la URL de Groq a https://…”. Fénix aplicará únicamente cambios de URL explícitos que indiques.", style = MaterialTheme.typography.bodySmall)
            Text("El test envía un mensaje breve al proveedor seleccionado y puede consumir una pequeña cantidad de cuota.")
            if (!provider.noKey) {
                provider.apiKeys.forEachIndexed { keyIndex, key ->
                    OutlinedTextField(value = key, onValueChange = { v ->
                        providers = providers.toMutableList().also { list ->
                            val keys = provider.apiKeys.toMutableList(); keys[keyIndex] = v
                            list[selected] = provider.copy(apiKeys = keys)
                        }
                    }, label = { Text("API key ${keyIndex + 1}") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                if (provider.apiKeys.size < 10) OutlinedButton(onClick = {
                    providers = providers.toMutableList().also { it[selected] = provider.copy(apiKeys = provider.apiKeys + "") }
                }, modifier = Modifier.fillMaxWidth()) { Text("Agregar API key") }
            } else {
                Text("Sin clave no significa garantía de disponibilidad: Pollinations es experimental, AI Horde usa una cola pública y Ollama requiere un servidor accesible por red.")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    store.save(providers)
                    status = "Configuración guardada cifrada en este dispositivo."
                }) { Text("Guardar") }
                OutlinedButton(
                    enabled = !testing && provider.baseUrl.isNotBlank() && provider.model.isNotBlank(),
                    onClick = {
                        scope.launch {
                            testing = true
                            status = try {
                                AiClient(store).testProvider(provider)
                            } catch (e: Exception) {
                                "Prueba fallida: ${e.message ?: e.javaClass.simpleName}"
                            } finally {
                                testing = false
                            }
                        }
                    }
                ) { Text(if (testing) "Probando…" else "Probar conexión") }
                OutlinedButton(onClick = {
                    if (providers.size < 20) {
                        providers = providers + ProviderConfig("custom-${System.currentTimeMillis()}", "Proveedor personalizado", "", "", emptyList(), true, false)
                        selected = providers.lastIndex
                    }
                }) { Text("Agregar") }
            }
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
