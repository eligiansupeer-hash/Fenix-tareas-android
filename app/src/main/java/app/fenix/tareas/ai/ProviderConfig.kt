package app.fenix.tareas.ai

import org.json.JSONObject

data class ProviderConfig(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val apiKeys: List<String> = emptyList(),
    val enabled: Boolean = true,
    val noKey: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", name)
        .put("baseUrl", baseUrl).put("model", model).put("apiKeys", org.json.JSONArray(apiKeys))
        .put("enabled", enabled).put("noKey", noKey)

    companion object {
        fun fromJson(o: JSONObject): ProviderConfig {
            val keys = o.optJSONArray("apiKeys")
            return ProviderConfig(
                o.optString("id"), o.optString("name"), o.optString("baseUrl"),
                o.optString("model"), (0 until (keys?.length() ?: 0)).map { keys!!.optString(it) },
                o.optBoolean("enabled", true), o.optBoolean("noKey", false)
            )
        }
    }
}
