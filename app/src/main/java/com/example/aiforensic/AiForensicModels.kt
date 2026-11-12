package com.example.aiforensic

enum class PromptAccuracyStatus(val label: String) {
    EXACT_EMBEDDED_100_PERCENT("Terverifikasi 100% Eksak (Metadata Utuh)"),
    PLATFORM_AI_SIGNAL_ONLY("Sinyal AI Terdeteksi • Prompt Dihapus Platform"),
    NO_VERIFIED_AI_SIGNAL("Tidak Ada Sinyal AI Terverifikasi")
}

data class AiSignalMatch(
    val sourceLayer: String,
    val signalName: String,
    val detectedToolOrModel: String? = null,
    val evidenceSnippet: String,
    val isCryptographicOrStandard: Boolean = false
)

data class ExtractedAiPrompt(
    val generatorFormat: String,
    val positivePrompt: String,
    val negativePrompt: String? = null,
    val modelName: String? = null,
    val sampler: String? = null,
    val steps: String? = null,
    val cfgScale: String? = null,
    val seed: String? = null,
    val extraParams: Map<String, String> = emptyMap(),
    val rawChunkText: String,
    val isVerbatimByteExact: Boolean = true
)

data class ContainerStructureNode(
    val type: String,
    val sizeBytes: Long,
    val offsetBytes: Long,
    val depth: Int = 0,
    val detail: String = ""
)

data class C2paClaimDetails(
    val claimGenerator: String? = null,
    val actions: List<String> = emptyList(),
    val digitalSourceType: String? = null,
    val signerIssuer: String? = null,
    val assertionLabels: List<String> = emptyList()
)

data class AiForensicReport(
    val targetLabel: String,
    val accuracyStatus: PromptAccuracyStatus,
    val aiOriginDetected: Boolean,
    val primaryToolOrPlatform: String? = null,
    val binaryContainerFormat: String? = null,
    val sha256Hex: String? = null,
    val fileSizeBytes: Long? = null,
    val containerNodes: List<ContainerStructureNode> = emptyList(),
    val c2paClaims: C2paClaimDetails? = null,
    val rawChunkKeys: List<String> = emptyList(),
    val signals: List<AiSignalMatch> = emptyList(),
    val extractedPrompt: ExtractedAiPrompt? = null,
    val honestyLimitationNote: String
)
