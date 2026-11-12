package com.example.forensic

import com.example.aiforensic.AiSignalMatch

enum class SocialPlatform(
    val displayName: String,
    val primaryDomain: String
) {
    TIKTOK("TikTok", "tiktok.com"),
    FACEBOOK("Facebook", "facebook.com"),
    INSTAGRAM("Instagram", "instagram.com"),
    THREADS("Threads", "threads.net"),
    YOUTUBE("YouTube", "youtube.com"),
    UNKNOWN("Tidak Dikenal", "")
}

enum class UrlKind(val label: String) {
    PROFILE("Profil / Akun"),
    POST("Postingan / Video"),
    SHORTLINK("Shortlink / Redirect"),
    UNKNOWN("URL Umum")
}

data class ShareTokenInfo(
    val key: String,
    val value: String,
    val forensicMeaning: String,
    val decodedHint: String? = null
)

data class SnowflakeBreakdown(
    val rawId: String,
    val algorithmName: String,
    val binary64: String,
    val upperTimestampBits: String,
    val lowerSequenceBits: String,
    val unixSeconds: Long,
    val utcFormatted: String,
    val wibFormatted: String,
    val witaFormatted: String,
    val witFormatted: String
)

data class ParsedSocialUrl(
    val originalUrl: String,
    val canonicalUrl: String,
    val platform: SocialPlatform,
    val urlKind: UrlKind,
    val usernameOrHandle: String? = null,
    val authorId: String? = null,
    val contentId: String? = null,
    val shortcode: String? = null,
    val strippedTrackers: List<ShareTokenInfo> = emptyList(),
    val snowflakeBreakdown: SnowflakeBreakdown? = null,
    val publicPublishedTime: String? = null,
    val publicDeclaredLocation: String? = null,
    val metadataNotes: List<String> = emptyList()
)

data class CorrelationEvidence(
    val title: String,
    val matched: Boolean,
    val weight: Int,
    val detail: String
)

data class RedirectHop(
    val step: Int,
    val url: String,
    val statusCode: Int,
    val host: String,
    val resolvedIps: List<String>
)

data class RdapIpInfo(
    val queriedIp: String,
    val networkName: String? = null,
    val handle: String? = null,
    val cidr: String? = null,
    val countryCode: String? = null,
    val organizationName: String? = null,
    val registrySource: String? = null,
    val errorMessage: String? = null
)

data class NetworkTraceResult(
    val targetUrl: String,
    val finalUrl: String,
    val hops: List<RedirectHop> = emptyList(),
    val edgeServerHost: String = "",
    val edgeServerIps: List<String> = emptyList(),
    val rdapInfo: RdapIpInfo? = null,
    val serverHeaders: Map<String, String> = emptyMap(),
    val extractedOgTitle: String? = null,
    val extractedDescription: String? = null,
    val extractedCanonicalUrl: String? = null,
    val extractedOgAuthor: String? = null,
    val extractedPublishedTime: String? = null,
    val extractedLocationTag: String? = null,
    val aiHtmlSignals: List<AiSignalMatch> = emptyList(),
    val errorMessage: String? = null
)

data class GeoIntEvidenceBundle(
    val captionText: String = "",
    val detectedRegions: List<String> = emptyList(),
    val detectedHashtags: List<String> = emptyList(),
    val solarCityOrCoords: String? = null,
    val solarElevationDeg: Double? = null,
    val solarAzimuthDeg: Double? = null,
    val shadowDirectionDeg: Double? = null,
    val daylightPhase: String? = null,
    val imageLightingSummary: String? = null,
    val mediaSha256: String? = null,
    val elaSummary: String? = null,
    val analystLandmarkNote: String = "",
    val analystPlateOrSignNote: String = "",
    val analystAudioDialectNote: String = "",
    val analystShadowVerdict: String = ""
)

data class CorrelationReport(
    val reportId: String,
    val analyzedAtEpochMs: Long,
    val analyzedAtFormatted: String,
    val profileParsed: ParsedSocialUrl,
    val postParsed: ParsedSocialUrl,
    val scorePercent: Int,
    val confidenceLabel: String,
    val evidences: List<CorrelationEvidence>,
    val networkTrace: NetworkTraceResult? = null,
    val geoIntEvidence: GeoIntEvidenceBundle? = null,
    val summaryNote: String
)
