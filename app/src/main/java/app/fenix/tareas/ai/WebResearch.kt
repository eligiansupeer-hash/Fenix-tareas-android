package app.fenix.tareas.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.json.JSONArray
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

data class SearchHit(
    val title: String,
    val url: String,
    val snippet: String,
    val pageText: String = ""
) {
    val sourceHost: String
        get() = runCatching { URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
}

object WebResearch {
    private const val SEARCH_TIMEOUT_MS = 12_000
    private const val PAGE_TIMEOUT_MS = 8_000
    private const val MAX_PAGES_TO_READ = 3
    private const val MAX_CHARS_PER_PAGE = 4_500

    /**
     * Searches the public web, then attempts to read up to three result pages.
     * A page may refuse automated access; its search snippet and original URL are retained.
     * Page text is untrusted data and must never be treated as instructions.
     */
    suspend fun search(query: String): List<SearchHit> = withContext(Dispatchers.IO) {
        val hits = try { searchDuckDuckGo(query) } catch (_: Exception) { emptyList() }
        val results = if (hits.isNotEmpty()) hits else try { searchWikipedia(query) } catch (_: Exception) { emptyList() }
        results.take(MAX_PAGES_TO_READ).map { hit ->
            val pageText = try { readPublicPage(hit.url) } catch (_: Exception) { "" }
            hit.copy(pageText = pageText)
        }
    }

    private fun searchDuckDuckGo(query: String): List<SearchHit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val document = Jsoup.connect("https://html.duckduckgo.com/html/?q=$encoded")
            .userAgent("Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36")
            .timeout(SEARCH_TIMEOUT_MS)
            .maxBodySize(1_000_000)
            .get()
        return document.select(".result").mapNotNull { result ->
            val link = result.selectFirst(".result__a") ?: return@mapNotNull null
            val href = unwrapDuckDuckGo(link.absUrl("href").ifBlank { link.attr("href") })
            val title = link.text().trim()
            val snippet = result.selectFirst(".result__snippet")?.text()?.trim().orEmpty()
            if (title.isBlank() || !isPublicWebUrl(href)) null else SearchHit(title, href, snippet)
        }.distinctBy { it.url }.take(8)
    }

    /** Key-free fallback when the primary HTML search endpoint is unavailable or blocked. */
    private fun searchWikipedia(query: String): List<SearchHit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val body = Jsoup.connect("https://es.wikipedia.org/w/api.php?action=opensearch&search=$encoded&limit=5&namespace=0&format=json")
            .userAgent("FenixTareasAndroid/0.2 (public web research)")
            .ignoreContentType(true)
            .timeout(SEARCH_TIMEOUT_MS)
            .maxBodySize(500_000)
            .execute().body()
        val data = JSONArray(body)
        val titles = data.optJSONArray(1) ?: return emptyList()
        val descriptions = data.optJSONArray(2)
        val urls = data.optJSONArray(3)
        (0 until titles.length()).mapNotNull { index ->
            val title = titles.optString(index).trim()
            val url = urls?.optString(index).orEmpty()
            val snippet = descriptions?.optString(index).orEmpty()
            if (title.isBlank() || !isPublicWebUrl(url)) null else SearchHit(title, url, snippet)
        }
    }

    private fun readPublicPage(rawUrl: String): String {
        if (!isPublicWebUrl(rawUrl)) return ""
        val document = Jsoup.connect(rawUrl)
            .userAgent("Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36")
            .timeout(PAGE_TIMEOUT_MS)
            .maxBodySize(1_500_000)
            .followRedirects(true)
            .get()
        document.select("script,style,nav,footer,header,aside,form,button,iframe,noscript,svg,canvas").remove()
        val main = document.selectFirst("article") ?: document.selectFirst("main") ?: document.body()
        return main?.text().orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_CHARS_PER_PAGE)
    }

    private fun unwrapDuckDuckGo(rawUrl: String): String {
        return try {
            val uri = URI(rawUrl)
            if (uri.host.orEmpty().endsWith("duckduckgo.com") && uri.rawQuery != null) {
                val value = uri.rawQuery.split("&").firstOrNull { it.startsWith("uddg=") }
                    ?.substringAfter("uddg=")
                if (value != null) URLDecoder.decode(value, "UTF-8") else rawUrl
            } else rawUrl
        } catch (_: Exception) { rawUrl }
    }

    private fun isPublicWebUrl(raw: String): Boolean {
        return try {
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            val host = uri.host?.lowercase(Locale.ROOT)?.removeSuffix(".") ?: return false
            if (scheme != "http" && scheme != "https") return false
            if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) return false
            if (host == "0.0.0.0" || host == "::1" || host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.")) return false
            if (host.startsWith("169.254.")) return false
            val parts = host.split(".")
            if (parts.size == 4 && parts.all { it.toIntOrNull() != null }) {
                val octets = parts.map { it.toInt() }
                if (octets.any { it !in 0..255 }) return false
                if (octets[0] == 172 && octets[1] in 16..31) return false
            }
            true
        } catch (_: Exception) { false }
    }
}
