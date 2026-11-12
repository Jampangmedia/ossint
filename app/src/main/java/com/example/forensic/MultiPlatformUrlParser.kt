package com.example.forensic

import java.net.URI
import java.net.URLDecoder
import java.util.Locale
import java.util.UUID

object MultiPlatformUrlParser {

    private val trackerDescriptions = mapOf(
        "_t" to "Token timestamp/session share TikTok",
        "_r" to "Flag referral tautan TikTok",
        "sender_device" to "Tipe perangkat pengirim tautan (mobile/pc)",
        "share_app_id" to "ID aplikasi asal share (mis. 1233=TikTok Global, 1180=Asia)",
        "u_code" to "Kode unik sesi pengirim share TikTok",
        "fbclid" to "Facebook Click Identifier (jejak rujukan outbound/share)",
        "mibextid" to "Meta Mobile Share Context Identifier",
        "rdid" to "Redirect attribution ID Meta",
        "igsh" to "Instagram Share Attribution Token (Base64)",
        "igshid" to "Instagram Share Session Identifier",
        "si" to "YouTube Share Session Identifier",
        "xmt" to "Threads Cross-App Share Tracking Token",
        "ref" to "Parameter referral internal platform",
        "is_from_webapp" to "Indikator asal pembuat tautan dari browser web"
    )

    fun parse(rawInput: String): ParsedSocialUrl {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ParsedSocialUrl(
                originalUrl = trimmed,
                canonicalUrl = "",
                platform = SocialPlatform.UNKNOWN,
                urlKind = UrlKind.UNKNOWN,
                metadataNotes = listOf("URL kosong")
            )
        }

        val normalizedInput = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val uri = runCatching { URI(normalizedInput) }.getOrNull()
        val host = uri?.host?.lowercase(Locale.US)?.removePrefix("www.") ?: ""
        val path = uri?.path ?: ""
        val segments = path.split('/').filter { it.isNotBlank() }
        val queryMap = parseQuery(uri?.rawQuery)

        val platform = detectPlatform(host)
        val trackers = extractTrackers(queryMap)
        val cleanQueryPairs = queryMap.filterKeys { !trackerDescriptions.containsKey(it) }
            .filterKeys { !it.startsWith("utm_") }

        val notes = mutableListOf<String>()
        var urlKind = UrlKind.UNKNOWN
        var username: String? = null
        var authorId: String? = null
        var contentId: String? = null
        var shortcode: String? = null
        var snowflake: SnowflakeBreakdown? = null

        when (platform) {
            SocialPlatform.TIKTOK -> {
                if (host.startsWith("vt.") || host.startsWith("vm.") ||
                    (segments.size == 1 && segments[0].length in 6..12 && !segments[0].startsWith("@"))
                ) {
                    urlKind = UrlKind.SHORTLINK
                    shortcode = segments.firstOrNull()
                    notes += "Shortlink TikTok terdeteksi; jalankan Lacak Redirect untuk resolusi ID 64-bit."
                } else {
                    val userSeg = segments.firstOrNull { it.startsWith("@") }
                    if (userSeg != null) {
                        username = userSeg.removePrefix("@")
                    }
                    val videoIdx = segments.indexOfFirst { it == "video" || it == "photo" || it == "v" }
                    if (videoIdx >= 0 && videoIdx + 1 < segments.size) {
                        val candidateId = segments[videoIdx + 1].removeSuffix(".html")
                        contentId = candidateId
                        urlKind = UrlKind.POST
                        snowflake = SocialIdDecoders.decodeTikTokSnowflake(candidateId)
                        if (snowflake != null) {
                            notes += "Timestamp unggah berhasil didekode dari 32-bit atas ID TikTok."
                        }
                    } else if (username != null) {
                        urlKind = UrlKind.PROFILE
                        notes += "URL profil TikTok teridentifikasi (@$username)."
                    }
                }
            }

            SocialPlatform.FACEBOOK -> {
                if (host == "fb.watch" || segments.firstOrNull() == "share") {
                    urlKind = UrlKind.SHORTLINK
                    shortcode = segments.lastOrNull()
                    notes += "Tautan pendek/share Facebook; memerlukan resolusi redirect untuk ID kanonik."
                } else {
                    val profileId = queryMap["id"]
                    val storyFbid = queryMap["story_fbid"]
                    val watchV = queryMap["v"]
                    when {
                        path.contains("/posts/") || path.contains("/videos/") ||
                            path.contains("/reel/") || path.contains("/permalink/") -> {
                            urlKind = UrlKind.POST
                            val slug = segments.firstOrNull()
                            if (slug != null && slug !in setOf("reel", "watch", "groups", "permalink.php", "photo")) {
                                username = slug
                            }
                            contentId = segments.lastOrNull()
                            if (contentId?.startsWith("pfbid") == true) {
                                notes += "Menggunakan format Facebook pfbid terenkripsi; waktu akurat bergantung pada metadata publik."
                            }
                        }
                        storyFbid != null -> {
                            urlKind = UrlKind.POST
                            contentId = storyFbid
                            authorId = profileId
                        }
                        watchV != null -> {
                            urlKind = UrlKind.POST
                            contentId = watchV
                        }
                        path.contains("profile.php") && profileId != null -> {
                            urlKind = UrlKind.PROFILE
                            authorId = profileId
                            username = "id:$profileId"
                        }
                        segments.isNotEmpty() -> {
                            urlKind = UrlKind.PROFILE
                            username = segments[0]
                        }
                    }
                }
            }

            SocialPlatform.INSTAGRAM -> {
                val first = segments.firstOrNull()
                if (first in setOf("p", "reel", "reels", "tv") && segments.size >= 2) {
                    urlKind = UrlKind.POST
                    shortcode = segments[1]
                    snowflake = SocialIdDecoders.decodeMetaShortcode(shortcode, SocialPlatform.INSTAGRAM)
                    contentId = snowflake?.rawId ?: shortcode
                    if (snowflake != null) {
                        notes += "Shortcode Instagram didekode menjadi Media ID 64-bit & waktu publikasi."
                    }
                } else if (first == "share" && segments.size >= 2) {
                    urlKind = UrlKind.SHORTLINK
                    shortcode = segments.lastOrNull()
                    notes += "Tautan share Instagram terdeteksi; resolusi redirect diperlukan."
                } else if (first != null && first !in setOf("explore", "accounts", "stories")) {
                    urlKind = UrlKind.PROFILE
                    username = first.removePrefix("@")
                }
            }

            SocialPlatform.THREADS -> {
                val userSeg = segments.firstOrNull { it.startsWith("@") }
                username = userSeg?.removePrefix("@")
                val postIdx = segments.indexOf("post")
                if (postIdx >= 0 && postIdx + 1 < segments.size) {
                    urlKind = UrlKind.POST
                    shortcode = segments[postIdx + 1]
                    snowflake = SocialIdDecoders.decodeMetaShortcode(shortcode, SocialPlatform.THREADS)
                    contentId = snowflake?.rawId ?: shortcode
                    if (snowflake != null) {
                        notes += "Shortcode Threads didekode menggunakan skema Meta 64-bit Media ID."
                    }
                } else if (username != null) {
                    urlKind = UrlKind.PROFILE
                }
            }

            SocialPlatform.YOUTUBE -> {
                when {
                    host == "youtu.be" && segments.isNotEmpty() -> {
                        urlKind = UrlKind.POST
                        contentId = segments[0]
                        notes += "YouTube menggunakan ID hash acak 64-bit; waktu unggah diambil dari metadata publik."
                    }
                    segments.firstOrNull() == "shorts" && segments.size >= 2 -> {
                        urlKind = UrlKind.POST
                        contentId = segments[1]
                    }
                    queryMap["v"] != null -> {
                        urlKind = UrlKind.POST
                        contentId = queryMap["v"]
                        notes += "Video YouTube teridentifikasi (v=${contentId})."
                    }
                    segments.firstOrNull()?.startsWith("@") == true -> {
                        urlKind = UrlKind.PROFILE
                        username = segments[0].removePrefix("@")
                    }
                    segments.firstOrNull() == "channel" && segments.size >= 2 -> {
                        urlKind = UrlKind.PROFILE
                        authorId = segments[1]
                        username = segments[1]
                    }
                }
            }

            SocialPlatform.UNKNOWN -> {
                notes += "Domain tidak termasuk 5 platform target (TikTok, Facebook, Instagram, Threads, YouTube)."
            }
        }

        val canonicalQuery = if (cleanQueryPairs.isEmpty()) "" else {
            cleanQueryPairs.entries.joinToString("&", prefix = "?") { "${it.key}=${it.value}" }
        }
        val cleanPath = if (path.isBlank()) "" else path.trimEnd('/')
        val canonicalUrl = if (host.isNotBlank()) "https://$host$cleanPath$canonicalQuery" else normalizedInput

        return ParsedSocialUrl(
            originalUrl = trimmed,
            canonicalUrl = canonicalUrl,
            platform = platform,
            urlKind = urlKind,
            usernameOrHandle = username,
            authorId = authorId,
            contentId = contentId,
            shortcode = shortcode,
            strippedTrackers = trackers,
            snowflakeBreakdown = snowflake,
            publicPublishedTime = snowflake?.wibFormatted,
            metadataNotes = notes
        )
    }

    fun correlate(
        profileInput: String,
        postInput: String,
        networkTrace: NetworkTraceResult? = null
    ): CorrelationReport {
        val profile = parse(profileInput)
        var post = parse(postInput)

        // Enrich post with canonical redirect or OG author if available from network trace
        if (networkTrace != null) {
            val resolvedUrl = networkTrace.extractedCanonicalUrl ?: networkTrace.finalUrl
            if (resolvedUrl.isNotBlank() && resolvedUrl != post.originalUrl) {
                val reparsed = parse(resolvedUrl)
                post = post.copy(
                    canonicalUrl = reparsed.canonicalUrl.ifBlank { post.canonicalUrl },
                    usernameOrHandle = post.usernameOrHandle ?: reparsed.usernameOrHandle ?: networkTrace.extractedOgAuthor,
                    authorId = post.authorId ?: reparsed.authorId,
                    contentId = post.contentId ?: reparsed.contentId,
                    shortcode = post.shortcode ?: reparsed.shortcode,
                    snowflakeBreakdown = post.snowflakeBreakdown ?: reparsed.snowflakeBreakdown,
                    publicPublishedTime = networkTrace.extractedPublishedTime
                        ?: post.publicPublishedTime
                        ?: reparsed.publicPublishedTime,
                    publicDeclaredLocation = networkTrace.extractedLocationTag
                )
            } else {
                post = post.copy(
                    usernameOrHandle = post.usernameOrHandle ?: networkTrace.extractedOgAuthor,
                    publicPublishedTime = networkTrace.extractedPublishedTime ?: post.publicPublishedTime,
                    publicDeclaredLocation = networkTrace.extractedLocationTag
                )
            }
        }

        val evidences = mutableListOf<CorrelationEvidence>()
        var score = 0

        // 1. Platform consistency (25 pts)
        val samePlatform = profile.platform != SocialPlatform.UNKNOWN && profile.platform == post.platform
        if (samePlatform) score += 25
        evidences += CorrelationEvidence(
            title = "Konsistensi Platform",
            matched = samePlatform,
            weight = 25,
            detail = if (samePlatform) {
                "Keduanya berada di platform ${profile.platform.displayName}."
            } else {
                "Platform berbeda atau tidak dikenali (${profile.platform.displayName} vs ${post.platform.displayName})."
            }
        )

        // 2. Handle / Username match (40 pts)
        val pHandle = profile.usernameOrHandle?.lowercase(Locale.US)?.trim()
        val postHandle = post.usernameOrHandle?.lowercase(Locale.US)?.trim()
        val handleMatched = !pHandle.isNullOrBlank() && pHandle == postHandle
        if (handleMatched) score += 40
        evidences += CorrelationEvidence(
            title = "Kecocokan Handle / Slug Profil",
            matched = handleMatched,
            weight = 40,
            detail = when {
                handleMatched -> "Handle '@$pHandle' pada URL Profil identik dengan pembuat pada URL Postingan."
                !pHandle.isNullOrBlank() && !postHandle.isNullOrBlank() ->
                    "Ketidakcocokan handle: profil '@$pHandle' vs postingan '@$postHandle'."
                else -> "Handle pembuat tidak eksplisit di URL postingan (mis. shortlink/Instagram/YouTube); gunakan Lacak Redirect atau JSON Lab."
            }
        )

        // 3. Numeric Author ID / URL Kind validity (20 pts)
        val kindValid = profile.urlKind == UrlKind.PROFILE &&
            (post.urlKind == UrlKind.POST || post.urlKind == UrlKind.SHORTLINK)
        val authorIdMatched = !profile.authorId.isNullOrBlank() && profile.authorId == post.authorId
        val structureScore = when {
            authorIdMatched -> 20
            kindValid -> 15
            else -> 0
        }
        score += structureScore
        evidences += CorrelationEvidence(
            title = "Struktur & Identitas Entitas",
            matched = structureScore > 0,
            weight = 20,
            detail = when {
                authorIdMatched -> "Numeric Author ID cocok (${profile.authorId})."
                kindValid -> "Struktur URL valid: ${profile.urlKind.label} dipasangkan dengan ${post.urlKind.label}."
                else -> "Periksa apakah salah satu tautan bukan URL profil/postingan yang sesuai."
            }
        )

        // 4. Timestamp & Canonical Metadata Verification (15 pts)
        val hasVerifiedTimeOrMeta = post.snowflakeBreakdown != null ||
            !post.publicPublishedTime.isNullOrBlank() ||
            !networkTrace?.extractedCanonicalUrl.isNullOrBlank()
        if (hasVerifiedTimeOrMeta) score += 15
        evidences += CorrelationEvidence(
            title = "Bukti Forensik Waktu & Metadata Kanonik",
            matched = hasVerifiedTimeOrMeta,
            weight = 15,
            detail = buildString {
                if (post.snowflakeBreakdown != null) {
                    append("Timestamp 64-bit terdekode (${post.snowflakeBreakdown.wibFormatted}). ")
                } else if (!post.publicPublishedTime.isNullOrBlank()) {
                    append("Waktu publikasi publik terdeteksi (${post.publicPublishedTime}). ")
                }
                if (!networkTrace?.extractedCanonicalUrl.isNullOrBlank()) {
                    append("URL kanonik HTML terverifikasi. ")
                }
                if (post.strippedTrackers.isNotEmpty()) {
                    append("(${post.strippedTrackers.size} token referral ditemukan: ${post.strippedTrackers.joinToString { it.key }}).")
                } else if (!hasVerifiedTimeOrMeta) {
                    append("Belum ada timestamp 64-bit atau metadata kanonik terverifikasi pada tautan ini.")
                }
            }
        )

        val finalScore = score.coerceIn(0, 100)
        val confidence = when {
            finalScore >= 85 -> "Korelasi Kuat"
            finalScore >= 60 -> "Indikasi Moderat"
            finalScore >= 35 -> "Perlu Verifikasi Lanjut"
            else -> "Tidak Terkorelasi"
        }

        val now = System.currentTimeMillis()
        return CorrelationReport(
            reportId = UUID.randomUUID().toString().take(8).uppercase(Locale.US),
            analyzedAtEpochMs = now,
            analyzedAtFormatted = SocialIdDecoders.formatEpoch(now, "Asia/Jakarta"),
            profileParsed = profile,
            postParsed = post,
            scorePercent = finalScore,
            confidenceLabel = confidence,
            evidences = evidences,
            networkTrace = networkTrace,
            summaryNote = buildSummaryNote(profile, post, finalScore)
        )
    }

    private fun buildSummaryNote(
        profile: ParsedSocialUrl,
        post: ParsedSocialUrl,
        score: Int
    ): String {
        return when {
            score >= 85 ->
                "URL Profil dan Postingan konsisten milik entitas yang sama di ${post.platform.displayName}."
            post.urlKind == UrlKind.SHORTLINK ->
                "URL Postingan masih berupa shortlink (${post.platform.displayName}). Jalankan 'Lacak Redirect & CDN' untuk membuka URL kanonik dan handle pembuat."
            profile.platform != post.platform ->
                "Terdapat perbedaan platform antara URL Profil dan URL Postingan."
            else ->
                "Sebagian bukti forensik tersedia; lengkapi dengan pelacakan redirect atau impor JSON aktivitas."
        }
    }

    private fun matchesDomain(host: String, domain: String): Boolean {
        return host == domain || host.endsWith(".$domain")
    }

    private fun detectPlatform(host: String): SocialPlatform {
        return when {
            matchesDomain(host, "tiktok.com") -> SocialPlatform.TIKTOK
            matchesDomain(host, "facebook.com") || matchesDomain(host, "fb.watch") || matchesDomain(host, "fb.com") ->
                SocialPlatform.FACEBOOK
            matchesDomain(host, "instagram.com") || matchesDomain(host, "instagr.am") ->
                SocialPlatform.INSTAGRAM
            matchesDomain(host, "threads.net") || matchesDomain(host, "threads.com") ->
                SocialPlatform.THREADS
            matchesDomain(host, "youtube.com") || matchesDomain(host, "youtu.be") ->
                SocialPlatform.YOUTUBE
            else -> SocialPlatform.UNKNOWN
        }
    }

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val result = linkedMapOf<String, String>()
        for (pair in rawQuery.split('&')) {
            if (pair.isBlank()) continue
            val idx = pair.indexOf('=')
            val key = if (idx > 0) pair.substring(0, idx) else pair
            val rawVal = if (idx > 0 && idx + 1 < pair.length) pair.substring(idx + 1) else ""
            val decodedKey = runCatching { URLDecoder.decode(key, "UTF-8") }.getOrDefault(key)
            val decodedVal = runCatching { URLDecoder.decode(rawVal, "UTF-8") }.getOrDefault(rawVal)
            result[decodedKey] = decodedVal
        }
        return result
    }

    private fun extractTrackers(queryMap: Map<String, String>): List<ShareTokenInfo> {
        val list = mutableListOf<ShareTokenInfo>()
        for ((k, v) in queryMap) {
            val meaning = trackerDescriptions[k]
                ?: if (k.startsWith("utm_")) "Parameter kampanye/rujukan UTM" else null
            if (meaning != null) {
                var hint: String? = null
                if (k == "_t" || k == "timestamp") {
                    val num = v.toLongOrNull()
                    if (num != null && num > 1_500_000_000L) {
                        val ms = if (num < 10_000_000_000L) num * 1000L else num
                        hint = "Waktu share: ${SocialIdDecoders.formatEpoch(ms, "Asia/Jakarta")}"
                    }
                } else if (k == "share_app_id") {
                    hint = when (v) {
                        "1233" -> "Aplikasi: TikTok Global"
                        "1180" -> "Aplikasi: TikTok Regional / Asia"
                        else -> "App ID: $v"
                    }
                }
                list += ShareTokenInfo(
                    key = k,
                    value = v,
                    forensicMeaning = meaning,
                    decodedHint = hint
                )
            }
        }
        return list
    }
}
