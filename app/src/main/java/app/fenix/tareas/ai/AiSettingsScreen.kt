package app.fenix.tareas.ai

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AiSettingsScreen() {
    val context = LocalContext.current
    val store = remember { SecureProviderStore(context) }
    var providers by remember { mutableStateOf(store.load()) }
    var selected by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        providers = store.loadWithCatalog().map { provider ->
            if (!provider.noKey && provider.apiKeys.isEmpty()) provider.copy(apiKeys = listOf("")) else provider
        }
        selected = selected.coerceIn(0, (providers.size - 1).coerceAtLeast(0))
    }

    val current = providers.getOrNull(selected)
    fun saveProviders() {
        store.save(providers)
        status = "Configuración guardada y cifrada en este dispositivo."
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Proveedores de IA", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Editá el nombre, la URL y la API key de cada proveedor. Las URLs vienen precargadas como valores iniciales, no están bloqueadas. Las claves se cifran localmente con Android Keystore.",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Las API de OpenAI (ChatGPT) y Anthropic (Claude) pueden requerir facturación o saldo propio; tener una URL guardada no implica acceso gratuito.",
            style = MaterialTheme.typography.bodySmall
        )

        Text("Elegí un proveedor", style = MaterialTheme.typography.titleMedium)
        providers.forEachIndexed { index, provider ->
            OutlinedButton(
                onClick = { selected = index; status = null },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text((if (index == selected) "● " else "") + provider.name)
            }
        }

        current?.let { provider ->
            HorizontalDivider()
            Text("Editar proveedor seleccionado", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { saveProviders() }, modifier = Modifier.fillMaxWidth()) {
                Text("Guardar ${provider.name.ifBlank { "proveedor" }}")
            }

            OutlinedTextField(
                value = provider.name,
                onValueChange = { v -> providers = providers.toMutableList().also { it[selected] = provider.copy(name = v) } },
                label = { Text("Nombre del proveedor") },
                placeholder = { Text("Ej.: Mi proveedor") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = provider.baseUrl,
                onValueChange = { v -> providers = providers.toMutableList().also { it[selected] = provider.copy(baseUrl = v) } },
                label = { Text("URL base de la API (editable)") },
                placeholder = { Text("https://api.ejemplo.com/v1") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            val visibleKeys = if (provider.apiKeys.isEmpty() && !provider.noKey) listOf("") else provider.apiKeys
            if (!provider.noKey) {
                visibleKeys.forEachIndexed { keyIndex, key ->
                    OutlinedTextField(
                        value = key,
                        onValueChange = { value ->
                            providers = providers.toMutableList().also { list ->
                                val keys = visibleKeys.toMutableList()
                                keys[keyIndex] = value
                                list[selected] = provider.copy(apiKeys = keys)
                            }
                        },
                        label = { Text(if (visibleKeys.size == 1) "API key" else "API key ${keyIndex + 1}") },
                        placeholder = { Text("Pegá aquí la clave del proveedor") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (visibleKeys.size < 10) {
                    OutlinedButton(
                        onClick = {
                            providers = providers.toMutableList().also {
                                it[selected] = provider.copy(apiKeys = visibleKeys + "")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Agregar otra API key") }
                }
            } else {
                Text("Este proveedor no requiere API key según su configuración actual. Su disponibilidad puede variar.", style = MaterialTheme.typography.bodySmall)
            }

            OutlinedTextField(
                value = provider.model,
                onValueChange = { v -> providers = providers.toMutableList().also { it[selected] = provider.copy(model = v) } },
                label = { Text("Modelo") },
                placeholder = { Text("Identificador del modelo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = provider.apiKeyUrl,
                onValueChange = { v -> providers = providers.toMutableList().also { it[selected] = provider.copy(apiKeyUrl = v) } },
                label = { Text("URL para obtener la API key (editable)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            if (provider.apiKeyUrl.startsWith("https://")) {
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.apiKeyUrl)))
                        } catch (_: Exception) {
                            status = "No se pudo abrir el enlace. Copiá la URL y abrila en el navegador."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Abrir portal del proveedor / obtener API key") }
            }

            Text(ProviderCapabilities.forProvider(provider).explanation, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { saveProviders() }, modifier = Modifier.weight(1f)) {
                    Text("Guardar")
                }
                OutlinedButton(
                    enabled = !testing && provider.baseUrl.isNotBlank() && provider.model.isNotBlank(),
                    onClick = {
                        scope.launch {
                            testing = true
                            status = try {
                                AiClient(store).testProvider(provider.copy(apiKeys = visibleKeys))
                            } catch (e: Exception) {
                                "Prueba fallida: ${e.message ?: e.javaClass.simpleName}"
                            } finally {
                                testing = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text(if (testing) "Probando…" else "Probar conexión") }
            }
        }

        OutlinedButton(
            onClick = {
                val newProvider = ProviderConfig(
                    id = "custom-${System.currentTimeMillis()}",
                    name = "Otro proveedor",
                    baseUrl = "",
                    model = "",
                    apiKeys = listOf(""),
                    enabled = true,
                    noKey = false,
                    apiKeyUrl = ""
                )
                providers = providers + newProvider
                selected = providers.lastIndex
                status = "Proveedor personalizado agregado. Completá nombre, URL y API key, y después tocá Guardar."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("+ Agregar otro proveedor personalizado") }

        status?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
