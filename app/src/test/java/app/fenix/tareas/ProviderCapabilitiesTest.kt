package app.fenix.tareas

import app.fenix.tareas.ai.ProviderCapabilities
import app.fenix.tareas.ai.ProviderProtocol
import app.fenix.tareas.ai.ProviderConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCapabilitiesTest {
    @Test
    fun pollinationsUsesItsSpecificTextAdapter() {
        val config = ProviderConfig("pollinations", "Pollinations", "https://text.pollinations.ai", "openai", noKey = true)
        val capabilities = ProviderCapabilities.forProvider(config)
        assertEquals(ProviderProtocol.POLLINATIONS_TEXT, capabilities.protocol)
        assertFalse(capabilities.requiresApiKey)
    }

    @Test
    fun aiHordeUsesItsAsyncProtocol() {
        val config = ProviderConfig("ai-horde", "AI Horde", "https://aihorde.net/api/v2", "model", noKey = true)
        assertEquals(ProviderProtocol.AI_HORDE_ASYNC, ProviderCapabilities.forProvider(config).protocol)
    }

    @Test
    fun customNoKeyProviderUsesOpenAiCompatibleProtocol() {
        val config = ProviderConfig("custom", "Custom", "https://example.org/v1", "model", noKey = true)
        val capabilities = ProviderCapabilities.forProvider(config)
        assertEquals(ProviderProtocol.OPENAI_CHAT_COMPLETIONS, capabilities.protocol)
        assertFalse(capabilities.requiresApiKey)
    }

    @Test
    fun clientDoesNotClaimUnimplementedToolOrJsonSupport() {
        val config = ProviderConfig("custom", "Custom", "https://example.org/v1", "model", apiKeys = listOf("secret"))
        val capabilities = ProviderCapabilities.forProvider(config)
        assertTrue(capabilities.requiresApiKey)
        assertFalse(capabilities.supportsToolCalls)
        assertFalse(capabilities.supportsStructuredJson)
    }
}
