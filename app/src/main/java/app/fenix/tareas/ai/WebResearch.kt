package app.fenix.tareas.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class SearchHit(val title: String, val url: String, val snippet: String)

object WebResearch {
    suspend fun search(query: String): List<SearchHit> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val connection = (URL("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1").openConnection() as HttpURLConnection)
        try {
            connection.connectTimeout = 10000; connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "FenixTareasAndroid/0.2")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val hits = mutableListOf<SearchHit>()
            fun collect(o: JSONObject) {
                val text = o.optString("Text")
                val first = o.optString("FirstURL")
                if (text.isNotBlank() && first.startsWith("http")) hits += SearchHit(text.substringBefore(" - "), first, text)
                val topics = o.optJSONArray("Topics") ?: return
                for (i in 0 until topics.length()) {
                    val item = topics.optJSONObject(i) ?: continue
                    if (item.has("Topics")) collect(item) else {
                        val t = item.optString("Text"); val u = item.optString("FirstURL")
                        if (t.isNotBlank() && u.startsWith("http")) hits += SearchHit(t.substringBefore(" - "), u, t)
                    }
                }
            }
            collect(json)
            hits.distinctBy { it.url }.take(8)
        } finally { connection.disconnect() }
    }
}
