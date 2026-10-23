package app.fenix.tareas.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

data class SearchHit(val title: String, val url: String, val snippet: String)

object WebResearch {
    /** Public HTML search endpoint; results may be rate-limited and must be treated as untrusted content. */
    suspend fun search(query: String): List<SearchHit> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val document = Jsoup.connect("https://html.duckduckgo.com/html/?q=$encoded")
            .userAgent("Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36")
            .timeout(15000).get()
        document.select(".result").mapNotNull { result ->
            val link = result.selectFirst(".result__a") ?: return@mapNotNull null
            val href = link.absUrl("href").ifBlank { link.attr("href") }
            val title = link.text().trim()
            val snippet = result.selectFirst(".result__snippet")?.text()?.trim().orEmpty()
            if (title.isBlank() || !href.startsWith("http")) null else SearchHit(title, href, snippet)
        }.distinctBy { it.url }.take(8)
    }
}
