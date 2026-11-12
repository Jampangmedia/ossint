package com.example.jsonlab

import com.example.forensic.CorrelationReport

enum class InteractionActionType(val label: String) {
    LIKE("Like"),
    COMMENT("Komentar"),
    SHARE("Share"),
    POST_INSPECTION("Inspeksi Post"),
    OTHER("Lainnya")
}

data class ActivityJsonRecord(
    val id: String,
    val platform: String,
    val actionType: InteractionActionType,
    val actorAccount: String,
    val targetAuthor: String,
    val postUrl: String,
    val sharedDestination: String? = null,
    val timestamp: String,
    val commentSnippet: String? = null
)

data class AccountFrequency(
    val account: String,
    val count: Int,
    val actions: Set<String>
)

data class DestinationFrequency(
    val destination: String,
    val count: Int
)

data class JsonAnalysisSummary(
    val sourceLabel: String,
    val totalRecords: Int,
    val likeCount: Int,
    val commentCount: Int,
    val shareCount: Int,
    val platformCounts: Map<String, Int>,
    val topActors: List<AccountFrequency>,
    val topDestinations: List<DestinationFrequency>,
    val topReferralParams: List<DestinationFrequency> = emptyList(),
    val topShortlinkHosts: List<DestinationFrequency> = emptyList(),
    val correlatedUrlPairsCount: Int,
    val records: List<ActivityJsonRecord>,
    val prettyJson: String,
    val parseNotes: List<String>
)

data class ForensicExportEnvelope(
    val schemaVersion: String = "signaltrace-forensic-v1",
    val exportedAt: String,
    val methodology: String = "sandbox-compliant-no-hooking",
    val correlationReport: CorrelationReport
)
