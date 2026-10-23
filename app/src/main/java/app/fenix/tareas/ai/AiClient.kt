package app.fenix.tareas.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
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
                    val endpoint = provider.baseUrl.trimEnd('/') + "/chat/completions"
                    connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"; connectTimeout = 12000; readTimeout = 45000
                        doOutput = true; setRequestProperty("Content-Type", "application/json")
                        if (credential.isNotBlank()) setRequestProperty("Authorization", "Bearer $credential")
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
                    // Try next key/provider on rate limits, auth errors and other service failures.
                } catch (e: Exception) {
                    failures += "${provider.name}: ${e.javaClass.simpleName}"
                } finally { connection?.disconnect() }
            }
        }
        throw IllegalStateException("Ningún proveedor respondió. " + failures.joinToString("; "))
    }
}
