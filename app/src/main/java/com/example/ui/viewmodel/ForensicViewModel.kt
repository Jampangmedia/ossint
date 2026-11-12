package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiforensic.AiForensicReport
import com.example.aiforensic.AiUrlSignalAnalyzer
import com.example.aiforensic.PngAndBinaryAiParser
import com.example.data.ForensicCaseEntity
import com.example.data.InvestigationRepository
import com.example.data.SignalTraceDatabase
import com.example.forensic.CorrelationReport
import com.example.forensic.DecodedUrlMetadata
import com.example.forensic.GeoIntEvidenceBundle
import com.example.forensic.MultiPlatformUrlParser
import com.example.forensic.SocialPlatform
import com.example.forensic.UrlKind
import com.example.geoint.ChronolocationSummary
import com.example.geoint.GeoCoordinatePreset
import com.example.geoint.GeoIntTextAnalysisResult
import com.example.geoint.GeoIntTextAnalyzer
import com.example.geoint.ImageForensicAnalyzer
import com.example.geoint.ImageVisualForensicResult
import com.example.geoint.SolarGeoCalculator
import com.example.geoint.SolarPositionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

sealed interface AnalysisUiState {
    data object Idle : AnalysisUiState
    data class Tracing(val message: String) : AnalysisUiState
    data class Ready(val report: CorrelationReport) : AnalysisUiState
    data class Error(val message: String) : AnalysisUiState
}

data class GeoIntWorkspaceState(
    val captionInput: String = "",
    val textAnalysis: GeoIntTextAnalysisResult = GeoIntTextAnalyzer.analyzeText(""),
    val selectedPreset: GeoCoordinatePreset = SolarGeoCalculator.indonesianCityPresets.first(),
    val customLatInput: String = SolarGeoCalculator.indonesianCityPresets.first().latitude.toString(),
    val customLonInput: String = SolarGeoCalculator.indonesianCityPresets.first().longitude.toString(),
    val hasVerifiedPostTimestamp: Boolean = false,
    val solarTimestampSource: String = "Belum ada timestamp postingan terdekode",
    val solarResult: SolarPositionResult? = null,
    val isAnalyzingImage: Boolean = false,
    val imageAnalysis: ImageVisualForensicResult? = null,
    val imageError: String? = null,
    val analystLandmarkNote: String = "",
    val analystPlateNote: String = "",
    val analystDialectNote: String = "",
    val analystShadowVerdict: String = ""
)

data class AiForensicUiState(
    val urlAiReport: AiForensicReport? = null,
    val isInspectingFile: Boolean = false,
    val fileAiReport: AiForensicReport? = null,
    val fileAiError: String? = null,
    val manualMetadataInput: String = "",
    val manualAiReport: AiForensicReport? = null
)

data class CorrelatorFormState(
    val profileUrlInput: String = "",
    val postUrlInput: String = "",
    val decodedPostMetadata: DecodedUrlMetadata? = null,
    val analysisState: AnalysisUiState = AnalysisUiState.Idle,
    val statusBannerMessage: String? = null
)

class ForensicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: InvestigationRepository by lazy {
        val db = SignalTraceDatabase.getInstance(application)
        InvestigationRepository(db.forensicCaseDao())
    }

    private val _formState = MutableStateFlow(CorrelatorFormState())
    val formState: StateFlow<CorrelatorFormState> = _formState.asStateFlow()

    private val _geoIntState = MutableStateFlow(GeoIntWorkspaceState())
    val geoIntState: StateFlow<GeoIntWorkspaceState> = _geoIntState.asStateFlow()

    private val _aiForensicState = MutableStateFlow(AiForensicUiState())
    val aiForensicState: StateFlow<AiForensicUiState> = _aiForensicState.asStateFlow()

    val vaultCases: StateFlow<List<ForensicCaseEntity>> = repository.savedCases
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedChronoAccount = MutableStateFlow<String?>(null)
    val selectedChronoAccount: StateFlow<String?> = _selectedChronoAccount.asStateFlow()

    val availableChronoAccounts: StateFlow<List<String>> = combine(
        vaultCases,
        formState
    ) { cases, form ->
        val handles = linkedSetOf<String>()
        cases.forEach { entity ->
            extractAccountKeyFromEntity(entity)?.let { handles.add(it) }
        }
        val activeReport = (form.analysisState as? AnalysisUiState.Ready)?.report
        if (activeReport != null) {
            extractAccountKeyFromReport(activeReport)?.let { handles.add(it) }
        }
        handles.toList()
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val chronolocationSummary: StateFlow<ChronolocationSummary?> = combine(
        vaultCases,
        formState,
        _selectedChronoAccount
    ) { cases, form, selectedAcct ->
        val filteredCases = if (selectedAcct.isNullOrBlank()) {
            cases
        } else {
            cases.filter { extractAccountKeyFromEntity(it).equals(selectedAcct, ignoreCase = true) }
        }
        val timestamps = filteredCases
            .mapNotNull { it.decodedUnixSeconds.takeIf { sec -> sec > 0L } }
            .toMutableList()

        val activeReport = (form.analysisState as? AnalysisUiState.Ready)?.report
        val activeUnix = activeReport?.postParsed?.snowflakeBreakdown?.unixSeconds
        val activeMatchesFilter = selectedAcct.isNullOrBlank() ||
            (activeReport != null && extractAccountKeyFromReport(activeReport).equals(selectedAcct, ignoreCase = true))
        if (activeMatchesFilter && activeUnix != null && activeUnix > 0L && activeUnix !in timestamps) {
            timestamps.add(activeUnix)
        }
        GeoIntTextAnalyzer.analyzeChronolocation(timestamps)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun selectChronoAccount(account: String?) {
        _selectedChronoAccount.value = account
    }

    private fun extractAccountKeyFromEntity(entity: ForensicCaseEntity): String? {
        val rawPrefix = entity.title.substringBefore(" • ").trim()
        if (rawPrefix.startsWith("@") && !rawPrefix.startsWith("@Investigasi")) {
            return rawPrefix
        }
        val parsed = MultiPlatformUrlParser.parse(entity.postUrl.ifBlank { entity.profileUrl })
        return parsed.usernameOrHandle?.let { "@$it" } ?: parsed.authorId?.let { "ID:$it" }
    }

    private fun extractAccountKeyFromReport(report: CorrelationReport): String? {
        val handle = report.profileParsed.usernameOrHandle
            ?: report.postParsed.usernameOrHandle
            ?: report.networkTrace?.extractedOgAuthor
        return handle?.trim()?.takeIf { it.isNotBlank() }?.let {
            if (it.startsWith("@")) it else "@$it"
        } ?: (report.postParsed.authorId ?: report.profileParsed.authorId)?.let { "ID:$it" }
    }

    fun updateProfileUrl(url: String) {
        _formState.update { it.copy(profileUrlInput = url) }
    }

    fun updatePostUrl(url: String) {
        _formState.update { it.copy(postUrlInput = url) }
    }

    fun applyDiscoveredOwnerAsProfile(ownerUrl: String) {
        _formState.update {
            it.copy(
                profileUrlInput = ownerUrl,
                statusBannerMessage = "URL pemilik asli diterapkan ke kolom URL Profil."
            )
        }
        runOfflineAnalysis()
    }

    fun ingestSharedText(sharedText: String) {
        val urlRegex = Regex("""https?://\S+""")
        val extractedUrl = urlRegex.find(sharedText)?.value ?: sharedText.trim()
        if (extractedUrl.isBlank()) return

        val parsed = MultiPlatformUrlParser.parse(extractedUrl)
        _formState.update { current ->
            if (parsed.urlKind == UrlKind.PROFILE) {
                current.copy(
                    profileUrlInput = extractedUrl,
                    statusBannerMessage = "URL Profil diterima dari Share Sheet (${parsed.platform.displayName})."
                )
            } else {
                current.copy(
                    postUrlInput = extractedUrl,
                    statusBannerMessage = "URL Postingan diterima dari Share Sheet (${parsed.platform.displayName})."
                )
            }
        }
        runOfflineAnalysis()
    }

    fun runOfflineAnalysis() {
        val profile = _formState.value.profileUrlInput.trim()
        val post = _formState.value.postUrlInput.trim()
        if (profile.isBlank() && post.isBlank()) {
            _formState.update {
                it.copy(analysisState = AnalysisUiState.Error("Masukkan minimal satu URL Profil atau URL Postingan."))
            }
            return
        }
        val decodedTarget = repository.decodeUrlOffline(post.ifBlank { profile })
        val report = attachGeoIntToReport(repository.analyzeOffline(profile, post))
        _formState.update {
            it.copy(
                decodedPostMetadata = decodedTarget,
                analysisState = AnalysisUiState.Ready(report)
            )
        }
        recalculateSolarPosition(report)
        refreshUrlAiReport(report)
    }

    fun runNetworkTrace() {
        val profile = _formState.value.profileUrlInput.trim()
        val post = _formState.value.postUrlInput.trim()
        if (profile.isBlank() && post.isBlank()) {
            _formState.update {
                it.copy(analysisState = AnalysisUiState.Error("Masukkan URL target sebelum melacak redirect & CDN."))
            }
            return
        }
        viewModelScope.launch {
            _formState.update {
                it.copy(analysisState = AnalysisUiState.Tracing("Melacak redirect hop, oEmbed pemilik, DNS CDN & metadata publik..."))
            }
            val (tracedReport, tracedMetadata) = repository.traceAndCorrelate(profile, post)
            val traceText = listOfNotNull(
                tracedReport.networkTrace?.extractedOgTitle,
                tracedReport.networkTrace?.extractedDescription
            ).joinToString("\n").trim()

            if (traceText.isNotBlank() && _geoIntState.value.captionInput.isBlank()) {
                updateGeoIntCaption(traceText)
            }

            val enriched = attachGeoIntToReport(tracedReport)
            _formState.update {
                it.copy(
                    decodedPostMetadata = tracedMetadata ?: it.decodedPostMetadata,
                    analysisState = AnalysisUiState.Ready(enriched)
                )
            }
            recalculateSolarPosition(enriched)
            refreshUrlAiReport(enriched)
        }
    }

    fun updateGeoIntCaption(text: String) {
        val analysis = GeoIntTextAnalyzer.analyzeText(text)
        _geoIntState.update { current ->
            val newPreset = analysis.recommendedPreset ?: current.selectedPreset
            current.copy(
                captionInput = text,
                textAnalysis = analysis,
                selectedPreset = newPreset,
                customLatInput = if (analysis.recommendedPreset != null) {
                    newPreset.latitude.toString()
                } else {
                    current.customLatInput
                },
                customLonInput = if (analysis.recommendedPreset != null) {
                    newPreset.longitude.toString()
                } else {
                    current.customLonInput
                }
            )
        }
        recalculateSolarPosition()
        syncGeoIntIntoActiveReport()
        refreshUrlAiReport()
    }

    fun selectCityPreset(preset: GeoCoordinatePreset) {
        _geoIntState.update {
            it.copy(
                selectedPreset = preset,
                customLatInput = preset.latitude.toString(),
                customLonInput = preset.longitude.toString()
            )
        }
        recalculateSolarPosition()
        syncGeoIntIntoActiveReport()
    }

    fun updateCustomCoordinates(latStr: String, lonStr: String) {
        _geoIntState.update {
            it.copy(customLatInput = latStr, customLonInput = lonStr)
        }
        recalculateSolarPosition()
        syncGeoIntIntoActiveReport()
    }

    fun updateAnalystNotes(
        landmark: String = _geoIntState.value.analystLandmarkNote,
        plateOrSign: String = _geoIntState.value.analystPlateNote,
        dialect: String = _geoIntState.value.analystDialectNote,
        shadowVerdict: String = _geoIntState.value.analystShadowVerdict
    ) {
        _geoIntState.update {
            it.copy(
                analystLandmarkNote = landmark,
                analystPlateNote = plateOrSign,
                analystDialectNote = dialect,
                analystShadowVerdict = shadowVerdict
            )
        }
        syncGeoIntIntoActiveReport()
    }

    fun analyzeVisualScreenshot(uri: Uri) {
        val ctx = getApplication<Application>()
        val solar = _geoIntState.value.solarResult
        viewModelScope.launch {
            _geoIntState.update { it.copy(isAnalyzingImage = true, imageError = null) }
            val result = ImageForensicAnalyzer.analyzeImage(ctx, uri, solar)
            result.fold(
                onSuccess = { imgRes ->
                    _geoIntState.update {
                        var nextLat = it.customLatInput
                        var nextLon = it.customLonInput
                        if (imgRes.exifLatLon != null) {
                            nextLat = "%.5f".format(imgRes.exifLatLon.first)
                            nextLon = "%.5f".format(imgRes.exifLatLon.second)
                        }
                        it.copy(
                            isAnalyzingImage = false,
                            imageAnalysis = imgRes,
                            customLatInput = nextLat,
                            customLonInput = nextLon
                        )
                    }
                    if (imgRes.exifLatLon != null) {
                        recalculateSolarPosition()
                    }
                    syncGeoIntIntoActiveReport()
                },
                onFailure = { err ->
                    _geoIntState.update {
                        it.copy(
                            isAnalyzingImage = false,
                            imageError = err.localizedMessage ?: "Gagal menganalisis gambar."
                        )
                    }
                }
            )
        }
    }

    fun inspectMediaFileForAi(uri: Uri) {
        val ctx = getApplication<Application>()
        val displayName = resolveUriDisplayName(ctx, uri)
        viewModelScope.launch {
            _aiForensicState.update { it.copy(isInspectingFile = true, fileAiError = null) }
            val result = PngAndBinaryAiParser.inspectUri(ctx, uri, displayName)
            result.fold(
                onSuccess = { aiReport ->
                    _aiForensicState.update {
                        it.copy(
                            isInspectingFile = false,
                            fileAiReport = aiReport,
                            fileAiError = null
                        )
                    }
                },
                onFailure = { err ->
                    _aiForensicState.update {
                        it.copy(
                            isInspectingFile = false,
                            fileAiError = err.localizedMessage ?: "Gagal membaca metadata biner file."
                        )
                    }
                }
            )
        }
    }

    fun updateManualAiMetadataInput(text: String) {
        _aiForensicState.update { it.copy(manualMetadataInput = text) }
    }

    fun analyzeManualAiMetadata() {
        val raw = _aiForensicState.value.manualMetadataInput
        val parsed = PngAndBinaryAiParser.inspectPastedMetadataText(raw)
        _aiForensicState.update { it.copy(manualAiReport = parsed) }
    }

    private fun refreshUrlAiReport(explicitReport: CorrelationReport? = null) {
        val report = explicitReport ?: (_formState.value.analysisState as? AnalysisUiState.Ready)?.report
        val postUrl = report?.postParsed?.originalUrl?.ifBlank { _formState.value.postUrlInput }
            ?: _formState.value.postUrlInput
        val platform = report?.postParsed?.platform ?: SocialPlatform.UNKNOWN
        val aiReport = AiUrlSignalAnalyzer.analyzeUrlAndCaption(
            postUrl = postUrl,
            platform = platform,
            networkTrace = report?.networkTrace,
            captionInput = _geoIntState.value.captionInput
        )
        _aiForensicState.update { it.copy(urlAiReport = aiReport) }
    }

    private fun resolveUriDisplayName(context: Application, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment ?: "Media Terpilih")
    }

    private fun recalculateSolarPosition(explicitReport: CorrelationReport? = null) {
        val report = explicitReport ?: (_formState.value.analysisState as? AnalysisUiState.Ready)?.report
        val snowflakeUnix = report?.postParsed?.snowflakeBreakdown?.unixSeconds
        val htmlPublishedUnix = parseIsoToUnixSeconds(report?.networkTrace?.extractedPublishedTime)

        val (unixSec, sourceLabel) = when {
            snowflakeUnix != null && snowflakeUnix > 0L ->
                snowflakeUnix to "Timestamp 64-bit Terdekode (${report.postParsed.snowflakeBreakdown.wibFormatted})"
            htmlPublishedUnix != null && htmlPublishedUnix > 0L ->
                htmlPublishedUnix to "Metadata Publikasi HTML (${report?.networkTrace?.extractedPublishedTime})"
            else -> null to "Belum ada timestamp postingan terdekode"
        }

        if (unixSec == null) {
            _geoIntState.update {
                it.copy(
                    hasVerifiedPostTimestamp = false,
                    solarTimestampSource = sourceLabel,
                    solarResult = null
                )
            }
            return
        }

        val gState = _geoIntState.value
        val lat = gState.customLatInput.toDoubleOrNull() ?: gState.selectedPreset.latitude
        val lon = gState.customLonInput.toDoubleOrNull() ?: gState.selectedPreset.longitude
        val matchesPreset = lat == gState.selectedPreset.latitude && lon == gState.selectedPreset.longitude
        val label = if (matchesPreset) {
            "${gState.selectedPreset.name} (${gState.selectedPreset.timezoneLabel})"
        } else {
            "Koordinat Kustom (%.4f, %.4f)".format(lat, lon)
        }
        val offsetHours = if (matchesPreset) {
            gState.selectedPreset.utcOffsetHours
        } else {
            (lon / 15.0).roundToInt().coerceIn(-12, 14)
        }

        val solar = SolarGeoCalculator.calculate(
            unixSeconds = unixSec,
            latitude = lat,
            longitude = lon,
            locationLabel = label,
            utcOffsetHours = offsetHours
        )
        _geoIntState.update {
            it.copy(
                hasVerifiedPostTimestamp = true,
                solarTimestampSource = sourceLabel,
                solarResult = solar
            )
        }
    }

    private fun parseIsoToUnixSeconds(raw: String?): Long? {
        val text = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(text)
            }.getOrNull()
            if (parsed != null) return parsed.time / 1000L
        }
        return null
    }

    private fun syncGeoIntIntoActiveReport() {
        val ready = _formState.value.analysisState as? AnalysisUiState.Ready ?: return
        val updated = attachGeoIntToReport(ready.report)
        _formState.update { it.copy(analysisState = AnalysisUiState.Ready(updated)) }
    }

    private fun attachGeoIntToReport(report: CorrelationReport): CorrelationReport {
        val g = _geoIntState.value
        val bundle = GeoIntEvidenceBundle(
            captionText = g.captionInput,
            detectedRegions = g.textAnalysis.geoHits.map { "${it.category}: ${it.regionHint}" },
            detectedHashtags = g.textAnalysis.hashtags,
            solarCityOrCoords = g.solarResult?.locationLabel,
            solarElevationDeg = g.solarResult?.solarElevationDeg,
            solarAzimuthDeg = g.solarResult?.solarAzimuthDeg,
            shadowDirectionDeg = g.solarResult?.shadowDirectionDeg,
            daylightPhase = g.solarResult?.daylightPhase,
            imageLightingSummary = g.imageAnalysis?.let {
                "${it.lightingClassification} (${it.meanLuminancePercent}% luma • ${it.aspectRatioLabel})"
            },
            mediaSha256 = g.imageAnalysis?.sha256Hex,
            elaSummary = g.imageAnalysis?.elaResult?.verdict,
            analystLandmarkNote = g.analystLandmarkNote,
            analystPlateOrSignNote = g.analystPlateNote,
            analystAudioDialectNote = g.analystDialectNote,
            analystShadowVerdict = g.analystShadowVerdict
        )
        return report.copy(geoIntEvidence = bundle)
    }

    fun exportReportHtmlToUri(uri: Uri) {
        val current = (_formState.value.analysisState as? AnalysisUiState.Ready)?.report ?: return
        val enriched = attachGeoIntToReport(current)
        val decoded = _formState.value.decodedPostMetadata
        val fileAi = _aiForensicState.value.fileAiReport
        val urlAi = _aiForensicState.value.urlAiReport
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val html = com.example.forensic.ForensicReportExporter.buildHtmlReport(
                        report = enriched,
                        decodedMetadata = decoded,
                        fileAiReport = fileAi,
                        urlAiReport = urlAi
                    )
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(html.toByteArray(Charsets.UTF_8))
                    } ?: error("Gagal membuka file tujuan ekspor HTML.")
                }
            }
            _formState.update {
                it.copy(
                    statusBannerMessage = if (res.isSuccess) {
                        "Laporan HTML Forensik #${enriched.reportId} berhasil diekspor."
                    } else {
                        "Gagal ekspor HTML: ${res.exceptionOrNull()?.localizedMessage}"
                    }
                )
            }
        }
    }

    fun exportReportPdfToUri(uri: Uri) {
        val current = (_formState.value.analysisState as? AnalysisUiState.Ready)?.report ?: return
        val enriched = attachGeoIntToReport(current)
        val decoded = _formState.value.decodedPostMetadata
        val fileAi = _aiForensicState.value.fileAiReport
        val urlAi = _aiForensicState.value.urlAiReport
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val res = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    ctx.contentResolver.openOutputStream(uri)?.use { out ->
                        com.example.forensic.ForensicReportExporter.writePdfReport(
                            outputStream = out,
                            report = enriched,
                            decodedMetadata = decoded,
                            fileAiReport = fileAi,
                            urlAiReport = urlAi
                        )
                    } ?: error("Gagal membuka file tujuan ekspor PDF.")
                }
            }
            _formState.update {
                it.copy(
                    statusBannerMessage = if (res.isSuccess) {
                        "Laporan PDF Forensik #${enriched.reportId} berhasil dibuat."
                    } else {
                        "Gagal ekspor PDF: ${res.exceptionOrNull()?.localizedMessage}"
                    }
                )
            }
        }
    }

    fun saveCurrentReportToVault() {
        val current = (_formState.value.analysisState as? AnalysisUiState.Ready)?.report ?: return
        val enriched = attachGeoIntToReport(current)
        viewModelScope.launch {
            repository.saveReportToVault(enriched)
            _formState.update {
                it.copy(
                    analysisState = AnalysisUiState.Ready(enriched),
                    statusBannerMessage = "Kasus #${enriched.reportId} (+ GEOINT) disimpan ke Vault lokal."
                )
            }
        }
    }

    fun loadFromVault(entity: ForensicCaseEntity) {
        val restored = com.example.jsonlab.JsonForensicEngine.parseExportEnvelope(entity.reportJson)
            ?.correlationReport?.geoIntEvidence
        if (restored != null) {
            if (restored.captionText.isNotBlank()) {
                updateGeoIntCaption(restored.captionText)
            }
            updateAnalystNotes(
                landmark = restored.analystLandmarkNote,
                plateOrSign = restored.analystPlateOrSignNote,
                dialect = restored.analystAudioDialectNote,
                shadowVerdict = restored.analystShadowVerdict
            )
        }
        _formState.update {
            it.copy(
                profileUrlInput = entity.profileUrl,
                postUrlInput = entity.postUrl,
                statusBannerMessage = "Memuat kasus #${entity.id} dari Vault."
            )
        }
        runOfflineAnalysis()
    }

    fun deleteVaultCase(id: String) {
        viewModelScope.launch {
            repository.deleteCase(id)
        }
    }

    fun clearBanner() {
        _formState.update { it.copy(statusBannerMessage = null) }
    }
}
