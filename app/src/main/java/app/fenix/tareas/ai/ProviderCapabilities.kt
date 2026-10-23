package app.fenix.tareas.ai

enum class ProviderProtocol {
    OPENAI_CHAT_COMPLETIONS,
    POLLINATIONS_TEXT,
    AI_HORDE_ASYNC
}

/**
 * Describes only capabilities implemented and relied on by this client.
 * It deliberately does not infer tool-calling or JSON-mode support from an API-compatible URL.
 */
data class ProviderCapabilities(
    val protocol: ProviderProtocol,
    val requiresApiKey: Boolean,
    val supportsToolCalls: Boolean = false,
    val supportsStructuredJson: Boolean = false,
    val explanation: String
) {
    companion object {
        fun forProvider(provider: ProviderConfig): ProviderCapabilities {
            return when (provider.id.lowercase()) {
                "pollinations" -> ProviderCapabilities(
                    protocol = ProviderProtocol.POLLINATIONS_TEXT,
                    requiresApiKey = false,
                    explanation = "Adaptador de texto de Pollinations; el acceso público puede cambiar o limitarse."
                )
                "ai-horde" -> ProviderCapabilities(
                    protocol = ProviderProtocol.AI_HORDE_ASYNC,
                    requiresApiKey = false,
                    explanation = "Generación asíncrona en cola pública; disponibilidad y demora variables."
                )
                "ollama" -> ProviderCapabilities(
                    protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                    requiresApiKey = false,
                    explanation = "API compatible con OpenAI; requiere un servidor Ollama accesible."
                )
                else -> ProviderCapabilities(
                    protocol = ProviderProtocol.OPENAI_CHAT_COMPLETIONS,
                    requiresApiKey = !provider.noKey,
                    explanation = "API compatible con chat/completions. El cliente actual no envía llamadas a herramientas ni exige salida JSON."
                )
            }
        }
    }
}
