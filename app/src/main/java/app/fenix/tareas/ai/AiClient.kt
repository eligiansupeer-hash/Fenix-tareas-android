package app.fenix.tareas.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class ChatMessage(val role: String, val content: String)

class AiClient(private val store: SecureProviderStore) {
    private val providerHealth = ProviderHealthStore(store.appContext)

    suspend fun complete(messages: List<ChatMessage>): Pair<String, String> = withContext(Dispatchers.IO) {
        val providers = providerHealth.orderProviders(
            store.loadWithCatalog().filter { it.enabled && it.baseUrl.isNotBlank() && it.model.isNotBlank() }
        )
        if (providers.isEmpty()) throw IllegalStateException("No hay proveedores utilizables. Abrí «IA y API» y configurá/activá uno; los proveedores sin clave también requieren conexión y modelo.")
        val failures = mutableListOf<String>()
        for (provider in providers) {
            val capabilities = ProviderCapabilities.forProvider(provider)
            val credentials = if (!capabilities.requiresApiKey) listOf("") else provider.apiKeys.filter { it.isNotBlank() }
            if (credentials.isEmpty()) {
                failures += "${provider.name}: falta API key"
                continue
            }
            val providerStartedAt = System.currentTimeMillis()
            for (credential in credentials.take(10)) {
                try {
                    val response = when (capabilities.protocol) {
                        ProviderProtocol.AI_HORDE_ASYNC -> callAiHorde(provider, messages)
                        ProviderProtocol.POLLINATIONS_TEXT -> callPollinations(provider, messages)
                        ProviderProtocol.OPENAI_CHAT_COMPLETIONS ->
                            callOpenAiCompatible(provider, messages, credential.takeIf { it.isNotBlank() })
                    }
                    if (response.isNotBlank()) {
                        providerHealth.recordSuccess(provider.id, System.currentTimeMillis() - providerStartedAt)
                        return@withContext response to "${provider.name} · ${provider.model}"
                    }
                    failures += "${provider.name}: respuesta vacía"
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures += "${provider.name}: ${sanitizeProviderError(e.message ?: e.javaClass.simpleName)}"
                }
            }
            providerHealth.recordFailure(provider.id)
        }
        throw IllegalStateException("Ningún proveedor respondió. " + failures.joinToString("; "))
    }

    suspend fun testProvider(provider: ProviderConfig): String = withContext(Dispatchers.IO) {
        if (provider.baseUrl.isBlank() || provider.model.isBlank()) {
            throw IllegalArgumentException("Completá la URL base y el modelo antes de probar.")
        }
        val capabilities = ProviderCapabilities.forProvider(provider)
        val credentials = if (!capabilities.requiresApiKey) listOf("") else provider.apiKeys.filter { it.isNotBlank() }
        if (credentials.isEmpty()) throw IllegalArgumentException("Este proveedor requiere al menos una API key.")
        val startedAt = System.currentTimeMillis()
        var lastError = "respuesta vacía"
        for (credential in credentials.take(10)) {
            try {
                val response = when (capabilities.protocol) {
                    ProviderProtocol.AI_HORDE_ASYNC -> callAiHorde(provider, listOf(ChatMessage("user", "Respondé únicamente: OK")))
                    ProviderProtocol.POLLINATIONS_TEXT -> callPollinations(provider, listOf(ChatMessage("user", "Respondé únicamente: OK")))
                    ProviderProtocol.OPENAI_CHAT_COMPLETIONS ->
                        callOpenAiCompatible(provider, listOf(ChatMessage("user", "Respondé únicamente: OK")), credential.takeIf { it.isNotBlank() })
                }
                if (response.isNotBlank()) {
                    val latency = System.currentTimeMillis() - startedAt
                    providerHealth.recordSuccess(provider.id, latency)
                    return@withContext "Conexión correcta con ${provider.name} · ${latency} ms."
                }
                lastError = "el proveedor devolvió una respuesta vacía"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = sanitizeProviderError(e.message ?: e.javaClass.simpleName)
            }
        }
        providerHealth.recordFailure(provider.id)
        throw IllegalStateException("No respondió ${provider.name}: $lastError")
    }

    private fun sanitizeProviderError(value: String): String = value
        .replace(Regex("(?i)(https?://)[^/@\\s]+@"), "$1[REDACTED]@")
        .replace(Regex("(?i)([?&](?:api[_-]?key|key|token|access_token|password)=)[^&\\s]+"), "$1[REDACTED]")
        .take(500)

    private fun readBounded(input: java.io.InputStream, maxChars: Int): String {
        val output = StringBuilder(minOf(maxChars, 8192))
        input.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(8192)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (output.length + count > maxChars) {
                    throw IllegalStateException("La respuesta del proveedor supera el límite de $maxChars caracteres.")
                }
                output.append(buffer, 0, count)
            }
        }
        return output.toString()
    }

    private companion object {
        const val MAX_TEXT_RESPONSE_CHARS = 100_000
        const val MAX_JSON_RESPONSE_CHARS = 500_000
    }

    private fun callPollinations(provider: ProviderConfig, messages: List<ChatMessage>): String {
        val prompt = messages.joinToString("\n\n") { "${it.role}: ${it.content}" }
        val endpoint = provider.baseUrl.trimEnd('/') + "/" + URLEncoder.encode(prompt, "UTF-8") +
            "?model=" + URLEncoder.encode(provider.model, "UTF-8")
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 12000; readTimeout = 45000
            setRequestProperty("Accept", "text/plain")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return readBounded(connection.inputStream, MAX_TEXT_RESPONSE_CHARS).trim()
        } finally { connection.disconnect() }
    }

    private fun callOpenAiCompatible(provider: ProviderConfig, messages: List<ChatMessage>, credential: String?): String {
        val base = provider.baseUrl.trimEnd('/')
        val endpoint = if (base.endsWith("/v1") || base.endsWith("/openai")) {
            "$base/chat/completions"
        } else {
            "$base/v1/chat/completions"
        }
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 12000; readTimeout = 45000
            doOutput = true; setRequestProperty("Content-Type", "application/json")
            if (!credential.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $credential")
        }
        try {
            val body = JSONObject().put("model", provider.model)
                .put("messages", JSONArray().apply { messages.forEach { put(JSONObject().put("role", it.role).put("content", it.content)) } })
                .put("temperature", 0.4)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val response = readBounded(connection.inputStream, MAX_JSON_RESPONSE_CHARS)
            return JSONObject(response).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
        } finally { connection.disconnect() }
    }

    private suspend fun callAiHorde(provider: ProviderConfig, messages: List<ChatMessage>): String {
        val prompt = messages.joinToString("\n\n") { "${it.role}: ${it.content}" } + "\n\nassistant:"
        val base = provider.baseUrl.trimEnd('/')
        val request = JSONObject().put("prompt", prompt)
            .put("params", JSONObject().put("max_length", 400).put("max_context_length", 2048))
            .put("models", JSONArray().put(provider.model))
            .put("trusted_workers", false)
        val start = (URL("$base/generate/async").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 12000; readTimeout = 20000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", "0000000000")
            setRequestProperty("Client-Agent", "Fenix-Tareas:0.2.0:android")
        }
        val id: String
        try {
            start.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
            val code = start.responseCode
            if (code !in 200..299) throw IllegalStateException("AI Horde HTTP $code")
            id = JSONObject(readBounded(start.inputStream, MAX_JSON_RESPONSE_CHARS)).optString("id")
            if (id.isBlank()) throw IllegalStateException("AI Horde no devolvió ID de generación")
        } finally { start.disconnect() }

        repeat(24) {
            delay(1500)
            val poll = (URL("$base/generate/status/$id").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 8000; readTimeout = 10000
                setRequestProperty("apikey", "0000000000")
                setRequestProperty("Client-Agent", "Fenix-Tareas:0.2.0:android")
            }
            try {
                val code = poll.responseCode
                if (code !in 200..299) throw IllegalStateException("AI Horde status HTTP $code")
                val json = JSONObject(readBounded(poll.inputStream, MAX_JSON_RESPONSE_CHARS))
                if (json.optBoolean("faulted", false)) throw IllegalStateException("AI Horde falló la generación")
                if (json.optBoolean("done", false)) {
                    val generations = json.optJSONArray("generations")
                    if (generations != null && generations.length() > 0) return generations.getJSONObject(0).optString("text").trim()
                    throw IllegalStateException("AI Horde terminó sin texto")
                }
            } finally { poll.disconnect() }
        }
        throw IllegalStateException("AI Horde está en cola; se agotó la espera. Probá nuevamente.")
    }
}
