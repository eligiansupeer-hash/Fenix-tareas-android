package app.fenix.tareas.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class ChatMessage(val role: String, val content: String)

class AiClient(private val store: SecureProviderStore) {
    suspend fun complete(messages: List<ChatMessage>): Pair<String, String> = withContext(Dispatchers.IO) {
        val providers = store.load().filter { it.enabled && it.baseUrl.isNotBlank() && it.model.isNotBlank() }
        if (providers.isEmpty()) throw IllegalStateException("Configurá un proveedor en Ajustes de IA antes de enviar mensajes.")
        val failures = mutableListOf<String>()
        for (provider in providers) {
            val credentials = if (provider.noKey) listOf("") else provider.apiKeys.filter { it.isNotBlank() }
            if (credentials.isEmpty()) { failures += "${provider.name}: falta API key"; continue }
            for (credential in credentials.take(10)) {
                var connection: HttpURLConnection? = null
                try {
                    if (provider.noKey) {
                        // Generic no-key text endpoint mode. The endpoint and model are editable in the catalog/settings.
                        val prompt = messages.joinToString("\n") { "${it.role}: ${it.content}" }
                        val encodedPrompt = URLEncoder.encode(prompt, "UTF-8")
                        val encodedModel = URLEncoder.encode(provider.model, "UTF-8")
                        val endpoint = provider.baseUrl.trimEnd('/') + "/" + encodedPrompt + "?model=" + encodedModel
                        connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"; connectTimeout = 12000; readTimeout = 45000
                            setRequestProperty("Accept", "text/plain")
                        }
                        val code = connection.responseCode
                        if (code in 200..299) {
                            val response = connection.inputStream.bufferedReader().use { it.readText() }.trim()
                            if (response.isNotBlank()) return@withContext response to provider.name
                        }
                        failures += "${provider.name}: HTTP $code"
                    } else {
                        val endpoint = provider.baseUrl.trimEnd('/') + "/chat/completions"
                        connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                            requestMethod = "POST"; connectTimeout = 12000; readTimeout = 45000
                            doOutput = true; setRequestProperty("Content-Type", "application/json")
                            setRequestProperty("Authorization", "Bearer $credential")
                        }
                        val body = JSONObject().put("model", provider.model)
                            .put("messages", JSONArray().apply { messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) } })
                            .put("temperature", 0.4)
                        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                        val code = connection.responseCode
                        if (code in 200..299) {
                            val response = connection.inputStream.bufferedReader().use { it.readText() }
                            val text = JSONObject(response).getJSONArray("choices").getJSONObject(0)
                                .getJSONObject("message").getString("content")
                            return@withContext text to provider.name
                        }
                        failures += "${provider.name}: HTTP $code"
                    }
                    // 429, authentication failures, quota errors and other service errors fall through to the next credential/provider.
                } catch (e: Exception) {
                    failures += "${provider.name}: ${e.javaClass.simpleName}"
                } finally { connection?.disconnect() }
            }
        }
        throw IllegalStateException("Ningún proveedor respondió. " + failures.joinToString("; "))
    }
}
