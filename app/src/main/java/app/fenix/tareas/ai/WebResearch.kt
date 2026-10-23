package app.fenix.tareas.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.json.JSONArray
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

data class SearchHit(
    val title: String,
    val url: String,
    val snippet: String,
    val pageText: String = "",
    val publishedAt: String? = null,
    val retrievedAtMillis: Long = System.currentTimeMillis()
) {
    val sourceHost: String
        get() = runCatching { URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")
}

object WebResearch {
    private data class PageContent(val finalUrl: String, val text: String, val publishedAt: String?)
    private const val SEARCH_TIMEOUT_MS = 12_000
    private const val PAGE_TIMEOUT_MS = 8_000
    private const val MAX_PAGES_TO_READ = 3
    private const val MAX_CHARS_PER_PAGE = 4_500
    private const val MAX_REDIRECTS = 5
    private const val MAX_PAGE_BYTES = 1_500_000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36"

    /**
     * Searches the public web, then attempts to read up to three result pages.
     * A page may refuse automated access; its search snippet and original URL are retained.
     * Page text is untrusted data and must never be treated as instructions.
     */
    suspend fun search(query: String): List<SearchHit> = withContext(Dispatchers.IO) {
        val hits = try { searchDuckDuckGo(query) } catch (_: Exception) { emptyList() }
        val results = if (hits.isNotEmpty()) hits else try { searchWikipedia(query) } catch (_: Exception) { emptyList() }
        results.take(MAX_PAGES_TO_READ).map { hit ->
            val page = try { readPublicPage(hit.url) } catch (_: Exception) { null }
            if (page == null) hit else hit.copy(
                url = page.finalUrl,
                pageText = page.text,
                publishedAt = page.publishedAt
            )
        }
    }

    private fun searchDuckDuckGo(query: String): List<SearchHit> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val document = Jsoup.connect("https://html.duckduckgo.com/html/?q=$encoded")
            .userAgent(USER_AGENT)
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
        return (0 until titles.length()).mapNotNull { index ->
            val title = titles.optString(index).trim()
            val url = urls?.optString(index).orEmpty()
            val snippet = descriptions?.optString(index).orEmpty()
            if (title.isBlank() || !isPublicWebUrl(url)) null else SearchHit(title, url, snippet)
        }
    }

    /**
     * Manually follows a bounded number of redirects and revalidates every destination.
     * DNS results are checked before each request to reject hosts resolving to local/private
     * addresses. This is defense-in-depth; it is not a substitute for network-layer egress rules.
     */
    private fun readPublicPage(rawUrl: String): PageContent {
        var currentUrl = rawUrl
        var redirects = 0
        var finalResponse: Connection.Response? = null
        while (true) {
            if (!isPublicWebUrl(currentUrl, resolveDns = true)) {
                throw IOException("Se rechazó una URL no pública o un destino de redirección inseguro.")
            }
            val response = Jsoup.connect(currentUrl)
                .userAgent(USER_AGENT)
                .timeout(PAGE_TIMEOUT_MS)
                .maxBodySize(MAX_PAGE_BYTES)
                .followRedirects(false)
                .ignoreHttpErrors(true)
                .execute()
            val status = response.statusCode()
            if (status in 300..399) {
                if (redirects >= MAX_REDIRECTS) throw IOException("Se excedió el límite de redirecciones.")
                val location = response.header("Location")?.trim().orEmpty()
                if (location.isBlank()) throw IOException("Redirección sin destino.")
                currentUrl = URI(currentUrl).resolve(location).toString()
                redirects += 1
                continue
            }
            if (status !in 200..299) throw IOException("La página respondió HTTP $status.")
            finalResponse = response
            break
        }
        val response = finalResponse ?: throw IOException("No se pudo leer la respuesta de la página.")
        val document = response.parse()
        val publishedAt = listOf(
            document.selectFirst("meta[property=article:published_time]")?.attr("content"),
            document.selectFirst("meta[name=date]")?.attr("content"),
            document.selectFirst("meta[name=pubdate]")?.attr("content"),
            document.selectFirst("time[datetime]")?.attr("datetime")
        ).firstOrNull { !it.isNullOrBlank() }?.trim()
        document.select("script,style,nav,footer,header,aside,form,button,iframe,noscript,svg,canvas").remove()
        val main = document.selectFirst("article") ?: document.selectFirst("main") ?: document.body()
        val text = main?.text().orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_CHARS_PER_PAGE)
        val finalUrl = response.url().toString()
        if (!isPublicWebUrl(finalUrl)) throw IOException("La URL final de la página no es pública.")
        return PageContent(finalUrl = finalUrl, text = text, publishedAt = publishedAt)
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

    /** Internal for deterministic unit tests; DNS resolution is enabled only for actual fetches. */
    internal fun isPublicWebUrl(raw: String, resolveDns: Boolean = false): Boolean {
        return try {
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("[")?.removeSuffix("]")?.removeSuffix(".")
                ?: return false
            if (scheme != "http" && scheme != "https") return false
            if (uri.rawUserInfo != null) return false
            if (host.isBlank() || host == "localhost" || host.endsWith(".localhost") ||
                host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".test") ||
                host.endsWith(".invalid" ) || host.endsWith(".example")) return false
            if (host.equals("metadata.google.internal", ignoreCase = true)) return false

            val numericHost = host.matches(Regex("[0-9.]+"))
            val literalAddress = parseIpLiteral(host)
            if (numericHost && literalAddress == null) return false
            if (literalAddress != null && !isGloballyRoutable(literalAddress)) return false
            if (resolveDns && literalAddress == null) {
                val addresses = InetAddress.getAllByName(host)
                if (addresses.isEmpty() || addresses.any { !isGloballyRoutable(it) }) return false
            }
            true
        } catch (_: Exception) { false }
    }

    private fun parseIpLiteral(host: String): InetAddress? {
        val looksLikeIpv4 = host.matches(Regex("[0-9.]+"))
        val looksLikeIpv6 = host.contains(":")
        if (!looksLikeIpv4 && !looksLikeIpv6) return null
        return try {
            if (looksLikeIpv4) {
                val parts = host.split(".")
                if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) null
                else InetAddress.getByName(host)
            } else InetAddress.getByName(host)
        } catch (_: Exception) { null }
    }

    private fun isGloballyRoutable(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress) return false
        if (address.address.size == 16) {
            val bytes = address.address.map { it.toInt() and 0xff }
            if ((bytes[0] and 0xfe) == 0xfc) return false // IPv6 unique-local (fc00::/7)
            if (bytes[0] !in 0x20..0x3f) return false // Reject non-global-unicast IPv6 ranges
            if (bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == 0xb8) return false // Documentation range
        }
        if (address is Inet4Address) {
            val bytes = address.address.map { it.toInt() and 0xff }
            val a = bytes[0]
            val b = bytes[1]
            val c = bytes[2]
            if (a == 0 || a == 10 || a == 127 || a >= 224) return false
            if (a == 100 && b in 64..127) return false // shared address space (RFC 6598)
            if (a == 169 && b == 254) return false
            if (a == 172 && b in 16..31) return false
            if (a == 192 && (b == 168 || b == 0 && (c == 0 || c == 2) || b == 88 && c == 99)) return false
            if (a == 198 && (b == 18 || b == 19 || b == 51 && c == 100)) return false
            if (a == 203 && b == 0 && c == 113) return false
        }
        return true
    }
}
