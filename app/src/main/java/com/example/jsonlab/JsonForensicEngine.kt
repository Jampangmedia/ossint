package com.example.jsonlab

import com.example.forensic.CorrelationReport
import com.example.forensic.MultiPlatformUrlParser
import com.example.forensic.SocialIdDecoders
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object JsonForensicEngine {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val envelopeAdapter = moshi.adapter(ForensicExportEnvelope::class.java).indent("  ")

    fun parseExportEnvelope(rawJson: String): ForensicExportEnvelope? {
        return runCatching { envelopeAdapter.fromJson(rawJson) }.getOrNull()
    }

    fun exportReportToJson(report: CorrelationReport): String {
        val envelope = ForensicExportEnvelope(
            exportedAt = SocialIdDecoders.formatEpoch(System.currentTimeMillis(), "UTC"),
            correlationReport = report
        )
        return envelopeAdapter.toJson(envelope)
    }

    fun analyzeJsonString(rawJson: String, sourceLabel: String): Result<JsonAnalysisSummary> {
        return runCatching {
            val trimmed = rawJson.trim()
            require(trimmed.isNotEmpty()) { "File JSON kosong." }

            val records = mutableListOf<ActivityJsonRecord>()
            val notes = mutableListOf<String>()
            val pretty: String

            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                pretty = array.toString(2)
                parseArrayIntoRecords(array, "interactions", records)
            } else {
                val root = JSONObject(trimmed)
                pretty = root.toString(2)

                // 1. Check if this is a SignalTrace ForensicExportEnvelope
                if (root.has("correlationReport")) {
                    val cr = root.getJSONObject("correlationReport")
                    val profile = cr.optJSONObject("profileParsed")
                    val post = cr.optJSONObject("postParsed")
                    val platform = post?.optString("platform", "UNKNOWN") ?: "UNKNOWN"
                    val actor = profile?.optString("usernameOrHandle").orEmpty().ifBlank { "unknown_profile" }
                    val targetAuthor = post?.optString("usernameOrHandle").orEmpty().ifBlank { actor }
                    val postUrl = post?.optString("canonicalUrl").orEmpty()
                    val ts = post?.optString("publicPublishedTime").orEmpty()
                        .ifBlank { cr.optString("analyzedAtFormatted") }
                    records += ActivityJsonRecord(
                        id = cr.optString("reportId", "REPORT-1"),
                        platform = platform,
                        actionType = InteractionActionType.POST_INSPECTION,
                        actorAccount = "@$actor",
                        targetAuthor = "@$targetAuthor",
                        postUrl = postUrl,
                        sharedDestination = null,
                        timestamp = ts,
                        commentSnippet = "Skor Korelasi: ${cr.optInt("scorePercent", 0)}% (${cr.optString("confidenceLabel")})"
                    )
                    notes += "Terdeteksi skema laporan ekspor SignalTrace."
                }

                // 2. Check structured arrays and recurse into nested objects (e.g., real TikTok GDPR export Activity -> Like List -> ItemFavoriteList)
                val topLevelArrayKeys = setOf(
                    "interactions", "activities", "activity_log",
                    "likes", "comments", "shares", "events", "records"
                )
                for (key in topLevelArrayKeys) {
                    val arr = root.optJSONArray(key)
                    if (arr != null) {
                        parseArrayIntoRecords(arr, key, records)
                    }
                }
                collectNestedActivityArrays(root, "", topLevelArrayKeys, records)
            }

            if (records.isEmpty()) {
                notes += "Tidak ada array interaksi standar ditemukan; menampilkan struktur JSON mentah."
            }

            val likeCount = records.count { it.actionType == InteractionActionType.LIKE }
            val commentCount = records.count { it.actionType == InteractionActionType.COMMENT }
            val shareCount = records.count { it.actionType == InteractionActionType.SHARE }

            val platformCounts = records.groupingBy { it.platform }.eachCount()

            val actorMap = linkedMapOf<String, MutablePair>()
            for (r in records) {
                val key = r.actorAccount.ifBlank { "Anonim" }
                val entry = actorMap.getOrPut(key) { MutablePair() }
                entry.count++
                entry.actions += r.actionType.label
            }
            val topActors = actorMap.entries
                .sortedByDescending { it.value.count }
                .take(8)
                .map { AccountFrequency(it.key, it.value.count, it.value.actions) }

            val destCounts = records
                .mapNotNull { it.sharedDestination?.takeIf { d -> d.isNotBlank() } }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(8)
                .map { DestinationFrequency(it.key, it.value) }

            val referralCounts = linkedMapOf<String, Int>()
            val hostCounts = linkedMapOf<String, Int>()
            var correlatedPairs = 0

            for (rec in records) {
                if (rec.postUrl.isBlank()) continue
                val parsed = MultiPlatformUrlParser.parse(rec.postUrl)
                val authorClean = rec.targetAuthor.removePrefix("@").lowercase(Locale.US)
                if (parsed.usernameOrHandle?.lowercase(Locale.US) == authorClean || parsed.snowflakeBreakdown != null) {
                    correlatedPairs++
                }
                for (tok in parsed.strippedTrackers) {
                    referralCounts[tok.key] = (referralCounts[tok.key] ?: 0) + 1
                }
                val host = runCatching { java.net.URI(rec.postUrl.trim()).host.orEmpty().lowercase(Locale.US) }.getOrDefault("")
                if (host.isNotBlank()) {
                    hostCounts[host] = (hostCounts[host] ?: 0) + 1
                }
            }

            val topReferrals = referralCounts.entries
                .sortedByDescending { it.value }
                .take(8)
                .map { DestinationFrequency(it.key, it.value) }

            val topHosts = hostCounts.entries
                .sortedByDescending { it.value }
                .take(8)
                .map { DestinationFrequency(it.key, it.value) }

            JsonAnalysisSummary(
                sourceLabel = sourceLabel,
                totalRecords = records.size,
                likeCount = likeCount,
                commentCount = commentCount,
                shareCount = shareCount,
                platformCounts = platformCounts,
                topActors = topActors,
                topDestinations = destCounts,
                topReferralParams = topReferrals,
                topShortlinkHosts = topHosts,
                correlatedUrlPairsCount = correlatedPairs,
                records = records,
                prettyJson = pretty.take(12_000),
                parseNotes = notes
            )
        }
    }

    private fun collectNestedActivityArrays(
        obj: JSONObject,
        pathHint: String,
        alreadyParsedTopKeys: Set<String>,
        out: MutableList<ActivityJsonRecord>,
        depth: Int = 0
    ) {
        if (depth > 5) return
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (depth == 0 && (key == "correlationReport" || key in alreadyParsedTopKeys)) continue
            val nextHint = if (pathHint.isBlank()) key else "$pathHint $key"
            val childArr = obj.optJSONArray(key)
            if (childArr != null) {
                parseArrayIntoRecords(childArr, nextHint, out)
            } else {
                val childObj = obj.optJSONObject(key)
                if (childObj != null) {
                    collectNestedActivityArrays(childObj, nextHint, alreadyParsedTopKeys, out, depth + 1)
                }
            }
        }
    }

    private fun parseArrayIntoRecords(
        array: JSONArray,
        sourceKey: String,
        out: MutableList<ActivityJsonRecord>
    ) {
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val rawAction = optStringAny(obj, "action", "actionType").ifBlank { sourceKey }
                .uppercase(Locale.US)
            val actionType = when {
                rawAction.contains("LIKE") || rawAction.contains("FAVORITE") -> InteractionActionType.LIKE
                rawAction.contains("COMMENT") || rawAction.contains("KOMEN") -> InteractionActionType.COMMENT
                rawAction.contains("SHARE") || rawAction.contains("FORWARD") -> InteractionActionType.SHARE
                rawAction.contains("POST") -> InteractionActionType.POST_INSPECTION
                else -> InteractionActionType.OTHER
            }
            val postUrl = optStringAny(obj, "postUrl", "url", "link", "Link")
            val parsedUrl = if (postUrl.isNotBlank()) MultiPlatformUrlParser.parse(postUrl) else null
            val detectedPlatform = optStringAny(obj, "platform").ifBlank {
                parsedUrl?.platform?.displayName ?: "Unknown"
            }
            val actor = optStringAny(obj, "actorAccount", "account", "user").ifBlank { "Pemilik Arsip" }
            val fallbackAuthor = parsedUrl?.usernameOrHandle?.let { "@$it" } ?: "-"
            val targetAuthor = optStringAny(obj, "targetAuthor", "author").ifBlank { fallbackAuthor }
            val dest = optStringAny(obj, "sharedDestination", "destination", "sharedTo", "Method")
                .takeIf { it.isNotBlank() }
            val ts = optStringAny(obj, "timestamp", "createdAt", "Date").ifBlank { "-" }
            val snippet = optStringAny(obj, "commentSnippet", "text", "Comment").takeIf { it.isNotBlank() }

            out += ActivityJsonRecord(
                id = optStringAny(obj, "id").ifBlank { "REC-${out.size + 1}" },
                platform = detectedPlatform,
                actionType = actionType,
                actorAccount = actor,
                targetAuthor = targetAuthor,
                postUrl = postUrl,
                sharedDestination = dest,
                timestamp = ts,
                commentSnippet = snippet
            )
        }
    }

    private fun optStringAny(obj: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val v = obj.optString(k, "")
            if (v.isNotBlank()) return v
        }
        return ""
    }

    private class MutablePair(
        var count: Int = 0,
        val actions: MutableSet<String> = linkedSetOf()
    )
}
