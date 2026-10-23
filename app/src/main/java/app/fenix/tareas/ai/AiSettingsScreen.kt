package app.fenix.tareas.ai

import androidx.compose.foundation.layout.*
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { providers = store.loadWithCatalog() }
    val current = providers.getOrNull(selected)
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Proveedores de IA", style = MaterialTheme.typography.headlineMedium)
        Text("Las claves se cifran localmente con Android Keystore. No se suben a Fénix. El catálogo nuevo se incorpora sin borrar tus ajustes.", style = MaterialTheme.typography.bodySmall)
        Text("Se permiten hasta 10 credenciales por proveedor y se intenta el siguiente proveedor/clave cuando una llamada falla.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(providers) { index, provider ->
                OutlinedButton(onClick = { selected = index }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (index == selected) "● " else "") + provider.name + if (provider.noKey) " · sin clave (experimental)" else " · ${provider.apiKeys.count { it.isNotBlank() }} claves")
                }
            }
        }
        current?.let { provider ->
            Text(provider.name, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = provider.name, onValueChange = { v -> providers = providers.toMutableList().also { it[selected] = provider.copy(name = v) } }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = provider.baseUrl, onValueChange = { v -> providers = providers.toMutableList().also { it[selected] = provider.copy(baseUrl = v) } }, label = { Text("URL base compatible con OpenAI") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = provider.model, onValueChange = { v -> providers = providers.toMutableList().also { it[selected] = provider.copy(model = v) } }, label = { Text("Modelo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Las URL y los modelos se pueden editar; se requiere un endpoint compatible con /chat/completions.")
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
                OutlinedButton(onClick = {
                    if (providers.size < 20) {
                        providers = providers + ProviderConfig("custom-${System.currentTimeMillis()}", "Proveedor personalizado", "", "", emptyList(), true, false)
                        selected = providers.lastIndex
                    }
                }) { Text("Agregar proveedor") }
            }
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
