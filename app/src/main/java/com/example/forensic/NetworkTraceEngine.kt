package com.example.forensic

import com.example.aiforensic.AiUrlSignalAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class NetworkTraceEngine {

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun traceUrl(startUrl: String): NetworkTraceResult = withContext(Dispatchers.IO) {
        val normalized = if (startUrl.startsWith("http://") || startUrl.startsWith("https://")) {
            startUrl.trim()
        } else {
            "https://${startUrl.trim()}"
        }

        val hops = mutableListOf<RedirectHop>()
        var currentUrl = normalized
        var finalHeaders: Map<String, String> = emptyMap()
        var htmlSnippet = ""
        var errorMsg: String? = null

        try {
            for (step in 1..6) {
                val host = runCatching { URI(currentUrl).host.orEmpty() }.getOrDefault("")
                val ips = if (host.isNotBlank()) {
                    runCatching {
                        InetAddress.getAllByName(host).mapNotNull { it.hostAddress }.distinct().take(4)
                    }.getOrDefault(emptyList())
                } else {
                    emptyList()
                }

                val request = Request.Builder()
                    .url(currentUrl)
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
                    )
                    .header("Accept", "text/html,application/xhtml+xml")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val status = response.code
                    hops += RedirectHop(
                        step = step,
                        url = currentUrl,
                        statusCode = status,
                        host = host,
                        resolvedIps = ips
                    )

                    val location = response.header("Location")
                    if (status in 300..399 && !location.isNullOrBlank()) {
                        currentUrl = URI(currentUrl).resolve(location).toString()
                    } else {
                        finalHeaders = buildMap {
                            response.header("Server")?.let { put("Server", it) }
                            response.header("Via")?.let { put("Via", it) }
                            response.header("X-Cache")?.let { put("X-Cache", it) }
                            response.header("CF-RAY")?.let { put("CF-RAY", it) }
                            response.header("X-FB-Debug")?.let { put("X-FB-Debug", "Present (Edge Meta)") }
                            response.header("X-Tt-Logid")?.let { put("X-Tt-Logid", it) }
                            response.header("Date")?.let { put("HTTP-Date", it) }
                        }
                        val bodyBytes = response.body?.source()?.let { source ->
                            source.request(250_000L)
                            val toRead = minOf(source.buffer.size, 250_000L)
                            source.buffer.readUtf8(toRead)
                        } ?: ""
                        htmlSnippet = bodyBytes
                        return@withContext buildTraceResult(
                            startUrl = normalized,
                            finalUrl = currentUrl,
                            hops = hops,
                            headers = finalHeaders,
                            html = htmlSnippet,
                            errorMsg = null
                        )
                    }
                }
            }
        } catch (e: Exception) {
            errorMsg = e.localizedMessage ?: "Gagal menghubungi server target."
        }

        buildTraceResult(
            startUrl = normalized,
            finalUrl = currentUrl,
            hops = hops,
            headers = finalHeaders,
            html = htmlSnippet,
            errorMsg = errorMsg
        )
    }

    private suspend fun buildTraceResult(
        startUrl: String,
        finalUrl: String,
        hops: List<RedirectHop>,
        headers: Map<String, String>,
        html: String,
        errorMsg: String?
    ): NetworkTraceResult {
        val lastHop = hops.lastOrNull()
        val edgeIps = lastHop?.resolvedIps.orEmpty()
        val rdap = edgeIps.firstOrNull()?.let { ip -> RdapLookupEngine.lookupIp(ip) }
        val ogTitle = extractMetaContent(html, "og:title") ?: extractTitleTag(html)
        val ogDesc = extractMetaContent(html, "og:description")
            ?: extractMetaContent(html, "description")
            ?: extractMetaContent(html, "twitter:description")
        val canonical = extractLinkRelCanonical(html) ?: extractMetaContent(html, "og:url")
        val ogAuthor = extractMetaContent(html, "author")
            ?: extractJsonField(html, "ownerChannelName")
            ?: extractItemPropContent(html, "name")
            ?: extractHandleFromUrl(canonical ?: finalUrl)
        val publishedTime = extractMetaContent(html, "article:published_time")
            ?: extractMetaContent(html, "og:video:release_date")
            ?: extractJsonField(html, "uploadDate")
            ?: extractJsonField(html, "datePublished")
        val locationTag = extractMetaContent(html, "og:locality")
            ?: extractMetaContent(html, "place:location:latitude")?.let { lat ->
                val lon = extractMetaContent(html, "place:location:longitude")
                if (lon != null) "Public POI ($lat, $lon)" else null
            }
            ?: extractJsonField(html, "contentLocation")

        return NetworkTraceResult(
            targetUrl = startUrl,
            finalUrl = finalUrl,
            hops = hops,
            edgeServerHost = lastHop?.host.orEmpty(),
            edgeServerIps = edgeIps,
            rdapInfo = rdap,
            serverHeaders = headers,
            extractedOgTitle = ogTitle,
            extractedDescription = ogDesc,
            extractedCanonicalUrl = canonical,
            extractedOgAuthor = ogAuthor,
            extractedPublishedTime = publishedTime,
            extractedLocationTag = locationTag,
            aiHtmlSignals = AiUrlSignalAnalyzer.extractHtmlAiSignals(html),
            errorMessage = errorMsg
        )
    }

    private fun extractMetaContent(html: String, key: String): String? {
        if (html.isBlank()) return null
        val quotedKey = Pattern.quote(key)
        val patterns = listOf(
            Pattern.compile("""<meta[^>]+(?:property|name)=["']$quotedKey["'][^>]+content=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']$quotedKey["']""", Pattern.CASE_INSENSITIVE)
        )
        for (p in patterns) {
            val m = p.matcher(html)
            if (m.find()) return unescapeHtml(m.group(1)?.trim())
        }
        return null
    }

    private fun extractLinkRelCanonical(html: String): String? {
        if (html.isBlank()) return null
        val p = Pattern.compile("""<link[^>]+rel=["']canonical["'][^>]+href=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(html)
        return if (m.find()) unescapeHtml(m.group(1)?.trim()) else null
    }

    private fun extractItemPropContent(html: String, itemProp: String): String? {
        if (html.isBlank()) return null
        val q = Pattern.quote(itemProp)
        val p = Pattern.compile("""<(?:meta|link)[^>]+itemprop=["']$q["'][^>]+content=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(html)
        return if (m.find()) unescapeHtml(m.group(1)?.trim()) else null
    }

    private fun extractTitleTag(html: String): String? {
        if (html.isBlank()) return null
        val p = Pattern.compile("""<title[^>]*>([^<]+)</title>""", Pattern.CASE_INSENSITIVE)
        val m = p.matcher(html)
        return if (m.find()) unescapeHtml(m.group(1)?.trim()) else null
    }

    private fun extractJsonField(html: String, field: String): String? {
        if (html.isBlank()) return null
        val p = Pattern.compile(""""${Pattern.quote(field)}"\s*:\s*"([^"]+)"""")
        val m = p.matcher(html)
        return if (m.find()) m.group(1)?.trim() else null
    }

    private fun extractHandleFromUrl(url: String): String? {
        val parsed = MultiPlatformUrlParser.parse(url)
        return parsed.usernameOrHandle
    }

    private fun unescapeHtml(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .takeIf { it.isNotBlank() }
    }
}
