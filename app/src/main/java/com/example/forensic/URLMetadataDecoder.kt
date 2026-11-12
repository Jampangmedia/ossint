package com.example.forensic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class DecodedUrlMetadata(
    val originalUrl: String,
    val platform: SocialPlatform,
    val urlKind: UrlKind,
    val userId: String?,
    val postId: String?,
    val mediaNumericId: String?,
    val referralTrackingParams: Map<String, String>,
    val shareTokens: List<ShareTokenInfo>,
    val canonicalOwnerUrl: String?,
    val originalPostSourceUrl: String,
    val ownerDisplayName: String?,
    val ownerResolutionMethod: String,
    val decodedTimestampWib: String?,
    val redirectHopsCount: Int = 0,
    val networkTrace: NetworkTraceResult? = null,
    val notes: List<String> = emptyList()
)

/**
 * Decodes social media URLs (TikTok, Facebook, Instagram, Threads, YouTube) into
 * structured components: user_id, post_id, referral_tracking_params, and resolves
 * the canonical source URL of the original post owner.
 */
class URLMetadataDecoder(
    private val networkEngine: NetworkTraceEngine = NetworkTraceEngine()
) {

    private val oembedClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    fun decode(rawUrl: String): DecodedUrlMetadata {
        val parsed = MultiPlatformUrlParser.parse(rawUrl)
        val userId = extractNormalizedUserId(parsed)
        val postId = parsed.shortcode ?: parsed.contentId
        val mediaNumericId = parsed.snowflakeBreakdown?.rawId
            ?: parsed.contentId?.takeIf { it.all(Char::isDigit) && it.length >= 10 }
        val referralMap = linkedMapOf<String, String>().apply {
            parsed.strippedTrackers.forEach { put(it.key, it.value) }
        }
        val ownerUrl = buildCanonicalOwnerUrl(parsed.platform, userId, parsed.authorId)
        val resolutionMethod = when {
            ownerUrl != null && parsed.urlKind != UrlKind.SHORTLINK ->
                "Ekstraksi Langsung dari Struktur URL"
            parsed.urlKind == UrlKind.SHORTLINK ->
                "Shortlink Terdeteksi (Jalankan Pelacakan Sumber Pemilik)"
            else ->
                "Pemilik tidak eksplisit di URL (Jalankan Pelacakan Sumber Pemilik)"
        }

        return DecodedUrlMetadata(
            originalUrl = parsed.originalUrl,
            platform = parsed.platform,
            urlKind = parsed.urlKind,
            userId = userId,
            postId = postId,
            mediaNumericId = mediaNumericId,
            referralTrackingParams = referralMap,
            shareTokens = parsed.strippedTrackers,
            canonicalOwnerUrl = ownerUrl,
            originalPostSourceUrl = parsed.canonicalUrl,
            ownerDisplayName = null,
            ownerResolutionMethod = resolutionMethod,
            decodedTimestampWib = parsed.publicPublishedTime,
            redirectHopsCount = 0,
            networkTrace = null,
            notes = parsed.metadataNotes
        )
    }

    suspend fun decodeAndTraceOwner(rawUrl: String): DecodedUrlMetadata = withContext(Dispatchers.IO) {
        val base = decode(rawUrl)
        if (base.originalUrl.isBlank()) return@withContext base

        val trace = networkEngine.traceUrl(base.originalUrl)
        val candidateUrl = trace.extractedCanonicalUrl?.takeIf { it.isNotBlank() }
            ?: trace.finalUrl.takeIf { it.isNotBlank() }
            ?: base.originalUrl

        val reparsed = MultiPlatformUrlParser.parse(candidateUrl)
        val mergedNotes = (base.notes + reparsed.metadataNotes).distinct().toMutableList()

        // Merge trackers from both original shortlink and final canonical URL
        val mergedTokens = (base.shareTokens + reparsed.strippedTrackers)
            .distinctBy { "${it.key}=${it.value}" }
        val mergedReferralMap = linkedMapOf<String, String>().apply {
            mergedTokens.forEach { put(it.key, it.value) }
        }

        var resolvedUserId = extractNormalizedUserId(reparsed) ?: base.userId
        var resolvedOwnerUrl = buildCanonicalOwnerUrl(
            reparsed.platform.takeIf { it != SocialPlatform.UNKNOWN } ?: base.platform,
            resolvedUserId,
            reparsed.authorId
        ) ?: base.canonicalOwnerUrl
        var ownerName: String? = trace.extractedOgAuthor?.takeIf { it.isNotBlank() }
        var method = when {
            resolvedOwnerUrl != null && trace.hops.size > 1 ->
                "Resolusi HTTP Redirect (${trace.hops.size} hop) & URL Kanonik"
            resolvedOwnerUrl != null ->
                "Ekstraksi Langsung & Verifikasi Canonical HTML"
            else ->
                "Belum Ditemukan dari Redirect/HTML"
        }

        // If owner is still unknown (e.g., YouTube watch?v= / youtu.be / TikTok mobile share),
        // query official public oEmbed JSON endpoints for TikTok or YouTube.
        val effectivePlatform = reparsed.platform.takeIf { it != SocialPlatform.UNKNOWN } ?: base.platform
        val cleanPostUrl = reparsed.canonicalUrl.ifBlank { base.originalPostSourceUrl }

        if ((resolvedOwnerUrl == null || ownerName == null) &&
            (effectivePlatform == SocialPlatform.YOUTUBE || effectivePlatform == SocialPlatform.TIKTOK) &&
            cleanPostUrl.isNotBlank()
        ) {
            val oembed = fetchPublicOembedOwner(effectivePlatform, cleanPostUrl)
            if (oembed != null) {
                ownerName = oembed.authorName ?: ownerName
                if (!oembed.authorUrl.isNullOrBlank()) {
                    resolvedOwnerUrl = oembed.authorUrl
                    val ownerParsed = MultiPlatformUrlParser.parse(oembed.authorUrl)
                    resolvedUserId = extractNormalizedUserId(ownerParsed)
                        ?: oembed.authorUniqueId
                        ?: resolvedUserId
                    method = "oEmbed Publik Resmi ${effectivePlatform.displayName}"
                    mergedNotes += "Pemilik asli ditemukan melalui oEmbed publik (${oembed.authorName ?: resolvedUserId})."
                }
            }
        }

        val finalPostId = reparsed.shortcode ?: reparsed.contentId ?: base.postId
        val finalMediaId = reparsed.snowflakeBreakdown?.rawId
            ?: base.mediaNumericId
            ?: finalPostId?.takeIf { it.all(Char::isDigit) && it.length >= 10 }

        DecodedUrlMetadata(
            originalUrl = base.originalUrl,
            platform = effectivePlatform,
            urlKind = reparsed.urlKind.takeIf { it != UrlKind.UNKNOWN } ?: base.urlKind,
            userId = resolvedUserId,
            postId = finalPostId,
            mediaNumericId = finalMediaId,
            referralTrackingParams = mergedReferralMap,
            shareTokens = mergedTokens,
            canonicalOwnerUrl = resolvedOwnerUrl,
            originalPostSourceUrl = cleanPostUrl,
            ownerDisplayName = ownerName,
            ownerResolutionMethod = method,
            decodedTimestampWib = reparsed.publicPublishedTime
                ?: trace.extractedPublishedTime
                ?: base.decodedTimestampWib,
            redirectHopsCount = trace.hops.size,
            networkTrace = trace,
            notes = mergedNotes
        )
    }

    private data class OembedOwnerInfo(
        val authorName: String?,
        val authorUniqueId: String?,
        val authorUrl: String?
    )

    private fun fetchPublicOembedOwner(platform: SocialPlatform, targetPostUrl: String): OembedOwnerInfo? {
        val encoded = runCatching { URLEncoder.encode(targetPostUrl, "UTF-8") }.getOrNull() ?: return null
        val endpoint = when (platform) {
            SocialPlatform.TIKTOK -> "https://www.tiktok.com/oembed?url=$encoded"
            SocialPlatform.YOUTUBE -> "https://www.youtube.com/oembed?url=$encoded&format=json"
            else -> return null
        }

        return runCatching {
            val req = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/json")
                .get()
                .build()
            oembedClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@runCatching null
                val json = JSONObject(body)
                OembedOwnerInfo(
                    authorName = json.optString("author_name").takeIf { it.isNotBlank() },
                    authorUniqueId = json.optString("author_unique_id").takeIf { it.isNotBlank() },
                    authorUrl = json.optString("author_url").takeIf { it.isNotBlank() }
                )
            }
        }.getOrNull()
    }

    companion object {

        fun extractNormalizedUserId(parsed: ParsedSocialUrl): String? {
            return when {
                !parsed.authorId.isNullOrBlank() -> parsed.authorId
                !parsed.usernameOrHandle.isNullOrBlank() ->
                    parsed.usernameOrHandle.removePrefix("id:")
                else -> null
            }
        }

        fun buildCanonicalOwnerUrl(
            platform: SocialPlatform,
            userId: String?,
            authorId: String?
        ): String? {
            val cleanUser = userId?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
            val cleanAuthorId = authorId?.trim()?.takeIf { it.isNotBlank() }

            return when (platform) {
                SocialPlatform.TIKTOK ->
                    cleanUser?.let { "https://www.tiktok.com/@$it" }
                SocialPlatform.FACEBOOK -> when {
                    cleanAuthorId != null -> "https://www.facebook.com/profile.php?id=$cleanAuthorId"
                    cleanUser != null -> "https://www.facebook.com/$cleanUser"
                    else -> null
                }
                SocialPlatform.INSTAGRAM ->
                    cleanUser?.let { "https://www.instagram.com/$it/" }
                SocialPlatform.THREADS ->
                    cleanUser?.let { "https://www.threads.net/@$it" }
                SocialPlatform.YOUTUBE -> when {
                    cleanAuthorId != null && cleanAuthorId.startsWith("UC") ->
                        "https://www.youtube.com/channel/$cleanAuthorId"
                    cleanUser != null && cleanUser.startsWith("UC") ->
                        "https://www.youtube.com/channel/$cleanUser"
                    cleanUser != null ->
                        "https://www.youtube.com/@$cleanUser"
                    else -> null
                }
                SocialPlatform.UNKNOWN -> null
            }
        }

        fun isValidUrl(input: String): Boolean {
            val normalized = input.trim().let {
                if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
            }
            val uri = runCatching { URI(normalized) }.getOrNull() ?: return false
            return !uri.host.isNullOrBlank() && uri.host.lowercase(Locale.US).contains('.')
        }
    }
}
