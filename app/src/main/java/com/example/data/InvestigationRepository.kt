package com.example.data

import com.example.forensic.CorrelationReport
import com.example.forensic.DecodedUrlMetadata
import com.example.forensic.MultiPlatformUrlParser
import com.example.forensic.NetworkTraceEngine
import com.example.forensic.NetworkTraceResult
import com.example.forensic.URLMetadataDecoder
import com.example.jsonlab.JsonForensicEngine
import kotlinx.coroutines.flow.Flow

class InvestigationRepository(
    private val dao: ForensicCaseDao,
    private val networkEngine: NetworkTraceEngine = NetworkTraceEngine(),
    private val urlMetadataDecoder: URLMetadataDecoder = URLMetadataDecoder(networkEngine)
) {
    val savedCases: Flow<List<ForensicCaseEntity>> = dao.observeAllCases()

    fun decodeUrlOffline(url: String): DecodedUrlMetadata {
        return urlMetadataDecoder.decode(url)
    }

    fun analyzeOffline(profileUrl: String, postUrl: String): CorrelationReport {
        return MultiPlatformUrlParser.correlate(profileUrl, postUrl, null)
    }

    suspend fun traceAndCorrelate(
        profileUrl: String,
        postUrl: String
    ): Pair<CorrelationReport, DecodedUrlMetadata?> {
        val targetForTrace = postUrl.trim().ifBlank { profileUrl.trim() }
        if (targetForTrace.isBlank()) {
            return MultiPlatformUrlParser.correlate(profileUrl, postUrl, null) to null
        }

        val tracedMetadata = urlMetadataDecoder.decodeAndTraceOwner(targetForTrace)
        val enrichedTrace: NetworkTraceResult? = tracedMetadata.networkTrace?.copy(
            extractedOgAuthor = tracedMetadata.networkTrace.extractedOgAuthor
                ?: tracedMetadata.userId,
            extractedCanonicalUrl = tracedMetadata.networkTrace.extractedCanonicalUrl
                ?: tracedMetadata.originalPostSourceUrl.takeIf { it.isNotBlank() }
        )
        val report = MultiPlatformUrlParser.correlate(profileUrl, postUrl, enrichedTrace)
        return report to tracedMetadata
    }

    suspend fun saveReportToVault(report: CorrelationReport) {
        val handle = report.profileParsed.usernameOrHandle
            ?: report.postParsed.usernameOrHandle
            ?: "Investigasi ${report.reportId}"
        val platformName = if (report.postParsed.platform.name != "UNKNOWN") {
            report.postParsed.platform.displayName
        } else {
            report.profileParsed.platform.displayName
        }
        val timestampWib = report.postParsed.snowflakeBreakdown?.wibFormatted
            ?: report.postParsed.publicPublishedTime
            ?: "Metadata waktu tidak tersedia"

        val unixSec = report.postParsed.snowflakeBreakdown?.unixSeconds ?: 0L
        val geoSummary = report.geoIntEvidence?.let { g ->
            buildString {
                if (g.detectedRegions.isNotEmpty()) append(g.detectedRegions.joinToString(", "))
                g.solarCityOrCoords?.let {
                    if (isNotEmpty()) append(" • ")
                    append("Surya: $it")
                }
                if (g.analystLandmarkNote.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(g.analystLandmarkNote)
                }
            }.ifBlank { "Belum ada catatan GEOINT" }
        } ?: "Belum ada catatan GEOINT"

        val entity = ForensicCaseEntity(
            id = report.reportId,
            title = "@$handle • $platformName",
            platform = platformName,
            profileUrl = report.profileParsed.originalUrl,
            postUrl = report.postParsed.originalUrl,
            correlationScore = report.scorePercent,
            confidenceLabel = report.confidenceLabel,
            decodedTimestampWib = timestampWib,
            decodedUnixSeconds = unixSec,
            geoIntSummary = geoSummary,
            reportJson = JsonForensicEngine.exportReportToJson(report),
            createdAtMs = report.analyzedAtEpochMs
        )
        dao.insertCase(entity)
    }

    suspend fun deleteCase(id: String) {
        dao.deleteCaseById(id)
    }
}
