package app.fenix.tareas.ai

import android.content.Context

/**
 * Local, non-secret provider health hints used to choose a sensible fallback order.
 * A cooling-down provider is still tried if all alternatives fail.
 */
class ProviderHealthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("fenix_provider_health", Context.MODE_PRIVATE)

    fun orderProviders(providers: List<ProviderConfig>): List<ProviderConfig> {
        val now = System.currentTimeMillis()
        return providers.sortedWith(
            compareBy<ProviderConfig> { isCoolingDown(it.id, now) }
                .thenByDescending { prefs.getLong(successKey(it.id), 0L) }
        )
    }

    fun recordSuccess(providerId: String, latencyMillis: Long) {
        prefs.edit()
            .putLong(successKey(providerId), System.currentTimeMillis())
            .putLong(latencyKey(providerId), latencyMillis.coerceAtLeast(0L))
            .putInt(failureKey(providerId), 0)
            .remove(failureTimeKey(providerId))
            .apply()
    }

    fun recordFailure(providerId: String) {
        prefs.edit()
            .putInt(failureKey(providerId), (prefs.getInt(failureKey(providerId), 0) + 1).coerceAtMost(20))
            .putLong(failureTimeKey(providerId), System.currentTimeMillis())
            .apply()
    }

    private fun isCoolingDown(providerId: String, now: Long): Boolean {
        val failures = prefs.getInt(failureKey(providerId), 0)
        val lastFailure = prefs.getLong(failureTimeKey(providerId), 0L)
        return failures >= 2 && now - lastFailure in 0 until COOLDOWN_MS
    }

    private fun successKey(id: String) = "success:$id"
    private fun latencyKey(id: String) = "latency:$id"
    private fun failureKey(id: String) = "failures:$id"
    private fun failureTimeKey(id: String) = "last_failure:$id"

    companion object {
        private const val COOLDOWN_MS = 5 * 60 * 1000L
    }
}
