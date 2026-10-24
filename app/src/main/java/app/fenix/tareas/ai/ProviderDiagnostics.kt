package app.fenix.tareas.ai

/**
 * Produces a local, secret-free explanation of common provider configuration failures.
 * It never sends credentials anywhere and never mutates saved configuration.
 */
internal object ProviderDiagnostics {
    fun explain(error: String, providers: List<ProviderConfig>): String {
        val safe = error
            .replace(Regex("(?i)(sk-ant-api03-|sk-proj-|sk-|AIza)[A-Za-z0-9_\\-]{8,}"), "[API KEY OCULTA]")
            .replace(Regex("(?i)(authorization|x-api-key|api[_-]?key)\\s*[:=]\\s*[^,;\\s]+"), "$1: [OCULTO]")
        val lower = safe.lowercase()
        val clues = mutableListOf<String>()
        if (lower.contains("http 401") || lower.contains("http 403") || lower.contains("unauthorized") || lower.contains("invalid api key")) {
            clues += "El servidor rechazó la credencial (401/403). Revisá que la clave pertenezca a este proveedor, que esté activa y que tenga permisos o saldo."
        }
        if (lower.contains("http 404") || lower.contains("model not found") || lower.contains("not_found")) {
            clues += "Un 404 suele indicar una URL base, ruta API o identificador de modelo incorrectos."
        }
        if (lower.contains("http 429") || lower.contains("rate limit") || lower.contains("quota")) {
            clues += "El proveedor indicó límite de uso o cuota agotada; no necesariamente es una URL incorrecta."
        }
        if (lower.contains("timeout") || lower.contains("unknownhost") || lower.contains("unable to resolve") || lower.contains("connectexception")) {
            clues += "Parece un problema de red, DNS o disponibilidad del servidor."
        }

        val configured = providers.filter { it.baseUrl.isNotBlank() && it.model.isNotBlank() }
        val keyPairs = configured.flatMap { p -> p.apiKeys.filter { it.isNotBlank() }.map { p to it } }
        val candidate = keyPairs.firstOrNull { (_, key) ->
            (key.startsWith("sk-ant-") && configured.any { it.id.equals("anthropic", true) || it.name.contains("Claude", true) }) ||
            (key.startsWith("AIza") && configured.any { it.id.equals("gemini", true) || it.name.contains("Gemini", true) }) ||
            ((key.startsWith("sk-") || key.startsWith("sk-proj-")) && configured.any { it.id.equals("openai", true) || it.name.contains("OpenAI", true) })
        }?.first
        if (candidate != null) {
            clues += "Por el formato de la clave, podría corresponder a «${candidate.name}». El formato no confirma que sea válida; compará el proveedor seleccionado antes de cambiar nada."
        }
        if (clues.isEmpty()) clues += "No alcanza la información del error para identificar una causa con seguridad. Verificá proveedor, URL base, modelo y conexión."
        return "Diagnóstico local (no se envió la API key):\n" + clues.distinct().joinToString("\n") +
            "\n\nError técnico depurado: " + safe.take(400) +
            "\nNo cambié tu configuración. Si querés corregir URL o proveedor, indicá cuál y te pediré confirmación antes de guardarlo."
    }
}
