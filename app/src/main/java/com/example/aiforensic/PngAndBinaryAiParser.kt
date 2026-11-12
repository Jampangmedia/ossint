package com.example.aiforensic

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.Inflater

object PngAndBinaryAiParser {

    private val pngSignature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    private const val MAX_TEXT_CHUNK_BYTES = 2 * 1024 * 1024 // 2 MB cap per metadata chunk
    private const val MAX_GENERIC_SCAN_BYTES = 4 * 1024 * 1024 // 4 MB cap for JPEG/WebP/MP4 head scan

    private val c2paClaimGenerators = listOf(
        "ChatGPT" to "OpenAI ChatGPT / GPT-4o Image Gen",
        "DALL-E" to "OpenAI DALL-E",
        "OpenAI" to "OpenAI",
        "Adobe Firefly" to "Adobe Firefly",
        "Firefly" to "Adobe Firefly",
        "Google" to "Google (Imagen / Veo / SynthID)",
        "Imagen" to "Google Imagen",
        "Made with Google AI" to "Google AI",
        "Midjourney" to "Midjourney",
        "Bing Image Creator" to "Microsoft Bing Image Creator",
        "Truepic" to "Truepic Content Credentials",
        "Runway" to "Runway ML",
        "Sora" to "OpenAI Sora",
        "ByteDance" to "ByteDance / CapCut / TikTok AIGC",
        "Meta" to "Meta AI"
    )

    data class PngParseOutput(
        val isPng: Boolean,
        val chunkTypes: List<String> = emptyList(),
        val textChunks: Map<String, String> = emptyMap(),
        val hasCabxChunk: Boolean = false,
        val cabxBytes: ByteArray? = null
    )

    suspend fun inspectUri(
        context: Context,
        uri: Uri,
        displayName: String = "Media Terpilih"
    ): Result<AiForensicReport> = withContext(Dispatchers.IO) {
        runCatching {
            val (sha256, fileSizeBytes) = context.contentResolver.openInputStream(uri)?.use { stream ->
                BinaryContainerInspector.computeSha256(stream)
            } ?: error("Tidak dapat membuka file dari URI.")

            val exifMap = linkedMapOf<String, String>()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                runCatching {
                    val exif = ExifInterface(stream)
                    listOf(
                        ExifInterface.TAG_USER_COMMENT to "EXIF:UserComment",
                        ExifInterface.TAG_IMAGE_DESCRIPTION to "EXIF:ImageDescription",
                        ExifInterface.TAG_SOFTWARE to "EXIF:Software",
                        ExifInterface.TAG_ARTIST to "EXIF:Artist",
                        ExifInterface.TAG_COPYRIGHT to "EXIF:Copyright",
                        ExifInterface.TAG_MAKE to "EXIF:Make",
                        ExifInterface.TAG_MODEL to "EXIF:Model"
                    ).forEach { (tag, key) ->
                        exif.getAttribute(tag)?.trim()?.takeIf { it.isNotBlank() }?.let {
                            exifMap[key] = it
                        }
                    }
                }
            }

            // Stream PNG chunks & verify CRC without buffering IDAT into RAM
            val pngCheck = context.contentResolver.openInputStream(uri)?.use { stream ->
                parsePngChunksFromStream(stream)
            } ?: error("Tidak dapat membuka file dari URI.")

            if (pngCheck.isPng) {
                val pngNodes = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BinaryContainerInspector.inspectPngStructure(stream)
                }.orEmpty()
                buildReportFromChunksAndScan(
                    targetLabel = displayName,
                    containerFormat = "PNG (${pngCheck.chunkTypes.size} chunk)",
                    chunkTypes = pngCheck.chunkTypes,
                    pngTextChunks = pngCheck.textChunks,
                    hasCabx = pngCheck.hasCabxChunk,
                    rawScanBytes = pngCheck.cabxBytes,
                    exifMetadata = exifMap,
                    sha256Hex = sha256,
                    fileSizeBytes = fileSizeBytes,
                    containerNodes = pngNodes
                )
            } else {
                val scannedBytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                    readHeadAndTailBytes(stream, MAX_GENERIC_SCAN_BYTES / 2)
                } ?: ByteArray(0)
                val nonPngNodes = BinaryContainerInspector.inspectIsoBmffAtoms(scannedBytes)
                    .ifEmpty { BinaryContainerInspector.inspectJpegSegments(scannedBytes) }
                inspectNonPngBytes(
                    bytes = scannedBytes,
                    targetLabel = displayName,
                    exifMetadata = exifMap,
                    sha256Hex = sha256,
                    fileSizeBytes = fileSizeBytes,
                    containerNodes = nonPngNodes
                )
            }
        }
    }

    fun inspectBytes(
        bytes: ByteArray,
        fileNameHint: String = "Buffer Biner",
        exifMetadata: Map<String, String> = emptyMap()
    ): AiForensicReport {
        val sha256 = BinaryContainerInspector.computeSha256(bytes)
        val fileSizeBytes = bytes.size.toLong()
        val pngCheck = ByteArrayInputStream(bytes).use { parsePngChunksFromStream(it) }
        val boundedScan = if (bytes.size <= MAX_GENERIC_SCAN_BYTES) {
            bytes
        } else {
            val half = MAX_GENERIC_SCAN_BYTES / 2
            bytes.copyOfRange(0, half) + bytes.copyOfRange(bytes.size - half, bytes.size)
        }
        return if (pngCheck.isPng) {
            val pngNodes = ByteArrayInputStream(bytes).use { BinaryContainerInspector.inspectPngStructure(it) }
            buildReportFromChunksAndScan(
                targetLabel = fileNameHint,
                containerFormat = "PNG (${pngCheck.chunkTypes.size} chunk)",
                chunkTypes = pngCheck.chunkTypes,
                pngTextChunks = pngCheck.textChunks,
                hasCabx = pngCheck.hasCabxChunk,
                rawScanBytes = boundedScan,
                exifMetadata = exifMetadata,
                sha256Hex = sha256,
                fileSizeBytes = fileSizeBytes,
                containerNodes = pngNodes
            )
        } else {
            val nonPngNodes = BinaryContainerInspector.inspectIsoBmffAtoms(boundedScan)
                .ifEmpty { BinaryContainerInspector.inspectJpegSegments(boundedScan) }
            inspectNonPngBytes(
                bytes = boundedScan,
                targetLabel = fileNameHint,
                exifMetadata = exifMetadata,
                sha256Hex = sha256,
                fileSizeBytes = fileSizeBytes,
                containerNodes = nonPngNodes
            )
        }
    }

    fun inspectPastedMetadataText(rawText: String, sourceLabel: String = "Teks / Metadata Manual"): AiForensicReport {
        val trimmed = rawText.trim()
        if (trimmed.isBlank()) {
            return AiForensicReport(
                targetLabel = sourceLabel,
                accuracyStatus = PromptAccuracyStatus.NO_VERIFIED_AI_SIGNAL,
                aiOriginDetected = false,
                honestyLimitationNote = "Input teks/metadata masih kosong."
            )
        }

        val signals = mutableListOf<AiSignalMatch>()
        var extracted: ExtractedAiPrompt? = null

        AiPromptTextParsers.parseComfyUiPromptJson(trimmed)?.let {
            extracted = it
            signals += AiSignalMatch(
                sourceLayer = "Teks JSON ComfyUI",
                signalName = "Node Graph ComfyUI Utuh",
                detectedToolOrModel = it.modelName ?: "ComfyUI",
                evidenceSnippet = it.positivePrompt.take(120),
                isCryptographicOrStandard = true
            )
        }

        if (extracted == null) {
            AiPromptTextParsers.parseA1111Parameters(trimmed)?.let {
                extracted = it
                signals += AiSignalMatch(
                    sourceLayer = "Blok Parameters A1111/Forge",
                    signalName = "Parameter Generasi Stable Diffusion",
                    detectedToolOrModel = it.modelName ?: "Automatic1111 / Forge",
                    evidenceSnippet = it.positivePrompt.take(120),
                    isCryptographicOrStandard = true
                )
            }
        }

        if (extracted == null) {
            AiPromptTextParsers.parseInvokeAiMetadata(trimmed)?.let {
                extracted = it
                signals += AiSignalMatch(
                    sourceLayer = "Metadata InvokeAI",
                    signalName = "InvokeAI JSON",
                    detectedToolOrModel = it.modelName ?: "InvokeAI",
                    evidenceSnippet = it.positivePrompt.take(120),
                    isCryptographicOrStandard = true
                )
            }
        }

        if (extracted == null) {
            AiPromptTextParsers.parseMidjourneyOrCaptionPrompt(trimmed)?.let {
                extracted = it
                signals += AiSignalMatch(
                    sourceLayer = "Teks / Caption Eksplisit",
                    signalName = it.generatorFormat,
                    detectedToolOrModel = it.modelName,
                    evidenceSnippet = it.positivePrompt.take(120),
                    isCryptographicOrStandard = false
                )
            }
        }

        // Also scan any AI tool keywords or XMP/C2PA snippets
        signals += AiUrlSignalAnalyzer.scanTextForAiToolKeywords(trimmed, "Teks / Metadata Manual")

        val uniqueSignals = signals.distinctBy { "${it.sourceLayer}|${it.signalName}|${it.detectedToolOrModel}" }
        val status = when {
            extracted != null -> PromptAccuracyStatus.EXACT_EMBEDDED_100_PERCENT
            uniqueSignals.isNotEmpty() -> PromptAccuracyStatus.PLATFORM_AI_SIGNAL_ONLY
            else -> PromptAccuracyStatus.NO_VERIFIED_AI_SIGNAL
        }

        return AiForensicReport(
            targetLabel = sourceLabel,
            accuracyStatus = status,
            aiOriginDetected = status != PromptAccuracyStatus.NO_VERIFIED_AI_SIGNAL,
            primaryToolOrPlatform = extracted?.modelName
                ?: uniqueSignals.firstOrNull { !it.detectedToolOrModel.isNullOrBlank() }?.detectedToolOrModel,
            binaryContainerFormat = "Teks / JSON Metadata",
            signals = uniqueSignals,
            extractedPrompt = extracted,
            honestyLimitationNote = buildHonestyNote(status, hasEmbeddedChunks = extracted != null)
        )
    }

    private fun inspectNonPngBytes(
        bytes: ByteArray,
        targetLabel: String,
        exifMetadata: Map<String, String>,
        sha256Hex: String? = null,
        fileSizeBytes: Long? = null,
        containerNodes: List<ContainerStructureNode> = emptyList()
    ): AiForensicReport {
        val format = detectContainerFormat(bytes)
        return buildReportFromChunksAndScan(
            targetLabel = targetLabel,
            containerFormat = format,
            chunkTypes = emptyList(),
            pngTextChunks = emptyMap(),
            hasCabx = false,
            rawScanBytes = bytes,
            exifMetadata = exifMetadata,
            sha256Hex = sha256Hex,
            fileSizeBytes = fileSizeBytes,
            containerNodes = containerNodes
        )
    }

    private fun buildReportFromChunksAndScan(
        targetLabel: String,
        containerFormat: String,
        chunkTypes: List<String>,
        pngTextChunks: Map<String, String>,
        hasCabx: Boolean,
        rawScanBytes: ByteArray?,
        exifMetadata: Map<String, String>,
        sha256Hex: String? = null,
        fileSizeBytes: Long? = null,
        containerNodes: List<ContainerStructureNode> = emptyList()
    ): AiForensicReport {
        val signals = mutableListOf<AiSignalMatch>()
        var extractedPrompt: ExtractedAiPrompt? = null
        val chunkKeySummary = mutableListOf<String>()

        chunkKeySummary += chunkTypes.filter { it != "IDAT" }.distinct()
        pngTextChunks.keys.forEach { key -> chunkKeySummary += "PNG:$key" }
        chunkKeySummary += exifMetadata.keys

        // 1. Inspect PNG Text Chunks (100% verbatim when present)
        val softwareTag = pngTextChunks["Software"] ?: exifMetadata["EXIF:Software"]
        if (softwareTag?.contains("NovelAI", ignoreCase = true) == true ||
            (pngTextChunks.containsKey("Description") && pngTextChunks.containsKey("Comment"))
        ) {
            AiPromptTextParsers.parseNovelAiChunks(
                pngTextChunks["Description"],
                pngTextChunks["Comment"]
            )?.let { nai ->
                extractedPrompt = nai
                signals += AiSignalMatch(
                    sourceLayer = "PNG Chunk (NovelAI)",
                    signalName = "NovelAI Description + Comment JSON",
                    detectedToolOrModel = "NovelAI Diffusion",
                    evidenceSnippet = nai.positivePrompt.take(120),
                    isCryptographicOrStandard = true
                )
            }
        }

        val a1111Raw = pngTextChunks["parameters"] ?: pngTextChunks["Parameters"]
        if (extractedPrompt == null && !a1111Raw.isNullOrBlank()) {
            AiPromptTextParsers.parseA1111Parameters(a1111Raw)?.let { a1111 ->
                extractedPrompt = a1111
                signals += AiSignalMatch(
                    sourceLayer = "PNG Chunk (parameters)",
                    signalName = "Metadata A1111 / Forge / Fooocus",
                    detectedToolOrModel = a1111.modelName ?: "Stable Diffusion",
                    evidenceSnippet = a1111.positivePrompt.take(120),
                    isCryptographicOrStandard = true
                )
            }
        }

        val comfyPromptRaw = pngTextChunks["prompt"]
        if (!comfyPromptRaw.isNullOrBlank()) {
            AiPromptTextParsers.parseComfyUiPromptJson(comfyPromptRaw)?.let { comfy ->
                if (extractedPrompt == null) extractedPrompt = comfy
                signals += AiSignalMatch(
                    sourceLayer = "PNG Chunk (prompt JSON)",
                    signalName = "ComfyUI Node Graph",
                    detectedToolOrModel = comfy.modelName ?: "ComfyUI",
                    evidenceSnippet = comfy.positivePrompt.take(120),
                    isCryptographicOrStandard = true
                )
            }
        }
        if (pngTextChunks.containsKey("workflow")) {
            signals += AiSignalMatch(
                sourceLayer = "PNG Chunk (workflow JSON)",
                signalName = "Graf Alur Kerja ComfyUI",
                detectedToolOrModel = "ComfyUI",
                evidenceSnippet = "Terdeteksi definisi graf node ComfyUI lengkap (${pngTextChunks["workflow"]?.length ?: 0} karakter).",
                isCryptographicOrStandard = true
            )
        }

        val invokeRaw = pngTextChunks["invokeai_metadata"] ?: pngTextChunks["Dream"]
        if (extractedPrompt == null && !invokeRaw.isNullOrBlank()) {
            AiPromptTextParsers.parseInvokeAiMetadata(invokeRaw)?.let { inv ->
                extractedPrompt = inv
                signals += AiSignalMatch(
                    sourceLayer = "PNG Chunk (invokeai_metadata)",
                    signalName = "InvokeAI JSON",
                    detectedToolOrModel = inv.modelName ?: "InvokeAI",
                    evidenceSnippet = inv.positivePrompt.take(120),
                    isCryptographicOrStandard = true
                )
            }
        }

        // Other PNG text chunks (e.g. Midjourney Description/Author/Job ID or XML XMP)
        pngTextChunks.forEach { (key, value) ->
            if (key.equals("XML:com.adobe.xmp", ignoreCase = true)) {
                inspectXmpString(value, signals)?.let { xmpPrompt ->
                    if (extractedPrompt == null) extractedPrompt = xmpPrompt
                }
            } else if (key !in setOf("parameters", "Parameters", "prompt", "workflow", "invokeai_metadata", "Comment")) {
                if (extractedPrompt == null) {
                    AiPromptTextParsers.parseMidjourneyOrCaptionPrompt(value)?.let { mj ->
                        extractedPrompt = mj
                        signals += AiSignalMatch(
                            sourceLayer = "PNG Chunk ($key)",
                            signalName = mj.generatorFormat,
                            detectedToolOrModel = mj.modelName,
                            evidenceSnippet = mj.positivePrompt.take(120),
                            isCryptographicOrStandard = true
                        )
                    }
                }
                if (value.contains("Midjourney", ignoreCase = true) || key == "Job ID") {
                    signals += AiSignalMatch(
                        sourceLayer = "PNG Chunk ($key)",
                        signalName = "Penanda Generator AI pada Chunk PNG",
                        detectedToolOrModel = "Midjourney / Generator PNG",
                        evidenceSnippet = value.take(120),
                        isCryptographicOrStandard = true
                    )
                }
            }
        }

        // 2. Inspect EXIF UserComment / ImageDescription / Software (JPEG/WebP A1111 or tool tags)
        val userComment = exifMetadata["EXIF:UserComment"]
        if (!userComment.isNullOrBlank() && extractedPrompt == null) {
            val cleanedComment = cleanExifUserComment(userComment)
            val parsedExif = AiPromptTextParsers.parseA1111Parameters(cleanedComment)
                ?: AiPromptTextParsers.parseComfyUiPromptJson(cleanedComment)
                ?: AiPromptTextParsers.parseMidjourneyOrCaptionPrompt(cleanedComment)
            if (parsedExif != null) {
                extractedPrompt = parsedExif
                signals += AiSignalMatch(
                    sourceLayer = "EXIF UserComment",
                    signalName = "Prompt Tersemat di EXIF",
                    detectedToolOrModel = parsedExif.modelName ?: parsedExif.generatorFormat,
                    evidenceSnippet = parsedExif.positivePrompt.take(120),
                    isCryptographicOrStandard = true
                )
            }
        }

        val imageDesc = exifMetadata["EXIF:ImageDescription"]
        if (!imageDesc.isNullOrBlank() && extractedPrompt == null) {
            val parsedDesc = AiPromptTextParsers.parseA1111Parameters(imageDesc)
                ?: AiPromptTextParsers.parseMidjourneyOrCaptionPrompt(imageDesc)
            if (parsedDesc != null) {
                extractedPrompt = parsedDesc
                signals += AiSignalMatch(
                    sourceLayer = "EXIF ImageDescription",
                    signalName = "Prompt Tersemat di ImageDescription",
                    detectedToolOrModel = parsedDesc.modelName,
                    evidenceSnippet = parsedDesc.positivePrompt.take(120),
                    isCryptographicOrStandard = true
                )
            }
        }

        exifMetadata["EXIF:Software"]?.let { sw ->
            val toolHits = AiUrlSignalAnalyzer.scanTextForAiToolKeywords(sw, "EXIF Software")
            signals += toolHits
        }

        // 3. Scan raw bytes for XMP packets (<x:xmpmeta) and C2PA / JUMBF boxes (c2pa, jumb, caBX)
        if (hasCabx) {
            chunkKeySummary += "PNG:caBX(C2PA)"
            signals += AiSignalMatch(
                sourceLayer = "PNG Chunk (caBX)",
                signalName = "Box C2PA Content Credentials",
                detectedToolOrModel = "C2PA JUMBF Container",
                evidenceSnippet = "Chunk biner `caBX` (Content Credentials C2PA) ditemukan di dalam struktur PNG.",
                isCryptographicOrStandard = true
            )
        }

        if (rawScanBytes != null && rawScanBytes.isNotEmpty()) {
            val asciiText = String(rawScanBytes, StandardCharsets.ISO_8859_1)

            // XMP block extraction
            val xmpStart = asciiText.indexOf("<x:xmpmeta")
            val xmpEnd = asciiText.indexOf("</x:xmpmeta>")
            if (xmpStart >= 0 && xmpEnd > xmpStart) {
                val xmpSlice = String(
                    rawScanBytes,
                    xmpStart,
                    (xmpEnd + "</x:xmpmeta>".length - xmpStart).coerceAtMost(rawScanBytes.size - xmpStart),
                    StandardCharsets.UTF_8
                )
                chunkKeySummary += "XMP:Packet"
                inspectXmpString(xmpSlice, signals)?.let { xmpPrompt ->
                    if (extractedPrompt == null) extractedPrompt = xmpPrompt
                }
            }

            // C2PA / JUMBF binary box markers
            val hasJumbC2pa = asciiText.contains("jumb") &&
                (asciiText.contains("c2pa") || asciiText.contains("c2ma") || asciiText.contains("c2cl"))
            if (hasJumbC2pa || asciiText.contains("c2pa.actions") || asciiText.contains("c2pa.claim")) {
                chunkKeySummary += "C2PA:JUMBF"
                val matchedClaim = c2paClaimGenerators.firstOrNull { (needle, _) ->
                    asciiText.contains(needle, ignoreCase = true)
                }
                signals += AiSignalMatch(
                    sourceLayer = "C2PA / JUMBF Box",
                    signalName = "Content Credentials (C2PA Provenance)",
                    detectedToolOrModel = matchedClaim?.second ?: "C2PA Signed Provenance",
                    evidenceSnippet = buildString {
                        append("Signature struktur JUMBF/C2PA ditemukan di byte kontainer.")
                        if (matchedClaim != null) append(" Generator claim: ${matchedClaim.second}.")
                    },
                    isCryptographicOrStandard = true
                )
            }
        }

        val c2paClaims = rawScanBytes?.let { BinaryContainerInspector.parseC2paClaims(it) }
        val uniqueSignals = signals.distinctBy { "${it.sourceLayer}|${it.signalName}|${it.detectedToolOrModel}" }
        val status = when {
            extractedPrompt != null -> PromptAccuracyStatus.EXACT_EMBEDDED_100_PERCENT
            uniqueSignals.isNotEmpty() || c2paClaims != null -> PromptAccuracyStatus.PLATFORM_AI_SIGNAL_ONLY
            else -> PromptAccuracyStatus.NO_VERIFIED_AI_SIGNAL
        }

        val primaryTool = extractedPrompt?.modelName
            ?: c2paClaims?.claimGenerator
            ?: uniqueSignals.firstOrNull { !it.detectedToolOrModel.isNullOrBlank() }?.detectedToolOrModel

        return AiForensicReport(
            targetLabel = targetLabel,
            accuracyStatus = status,
            aiOriginDetected = status != PromptAccuracyStatus.NO_VERIFIED_AI_SIGNAL,
            primaryToolOrPlatform = primaryTool,
            binaryContainerFormat = containerFormat,
            sha256Hex = sha256Hex,
            fileSizeBytes = fileSizeBytes,
            containerNodes = containerNodes,
            c2paClaims = c2paClaims,
            rawChunkKeys = chunkKeySummary.distinct(),
            signals = uniqueSignals,
            extractedPrompt = extractedPrompt,
            honestyLimitationNote = buildHonestyNote(status, hasEmbeddedChunks = extractedPrompt != null)
        )
    }

    private fun inspectXmpString(
        xmp: String,
        signals: MutableList<AiSignalMatch>
    ): ExtractedAiPrompt? {
        var foundPrompt: ExtractedAiPrompt? = null

        val lower = xmp.lowercase(Locale.US)
        when {
            lower.contains("trainedalgorithmicmedia") -> {
                signals += AiSignalMatch(
                    sourceLayer = "IPTC / XMP DigitalSourceType",
                    signalName = "iptc:trainedAlgorithmicMedia (Standar Media AI Generatif)",
                    detectedToolOrModel = extractXmpAttr(xmp, "CreatorTool")
                        ?: extractXmpAttr(xmp, "Software")
                        ?: extractXmpAttr(xmp, "Credit")
                        ?: "Generative AI (IPTC Standard)",
                    evidenceSnippet = "Tag IPTC `trainedAlgorithmicMedia` menunjukkan media dihasilkan oleh model AI generatif.",
                    isCryptographicOrStandard = true
                )
            }
            lower.contains("compositewithtrainedalgorithmicmedia") -> {
                signals += AiSignalMatch(
                    sourceLayer = "IPTC / XMP DigitalSourceType",
                    signalName = "iptc:compositeWithTrainedAlgorithmicMedia",
                    detectedToolOrModel = extractXmpAttr(xmp, "CreatorTool") ?: "AI Composite Edit",
                    evidenceSnippet = "Media mengandung elemen komposit yang dihasilkan oleh AI generatif.",
                    isCryptographicOrStandard = true
                )
            }
        }

        val creatorTool = extractXmpAttr(xmp, "CreatorTool") ?: extractXmpAttr(xmp, "Software")
        if (!creatorTool.isNullOrBlank()) {
            signals += AiUrlSignalAnalyzer.scanTextForAiToolKeywords(creatorTool, "XMP CreatorTool/Software")
        }

        val credit = extractXmpAttr(xmp, "Credit")
        if (!credit.isNullOrBlank() && (credit.contains("AI", ignoreCase = true) ||
                credit.contains("Google", ignoreCase = true) ||
                credit.contains("OpenAI", ignoreCase = true))
        ) {
            signals += AiSignalMatch(
                sourceLayer = "XMP photoshop:Credit",
                signalName = "Atribusi Kredit AI",
                detectedToolOrModel = credit,
                evidenceSnippet = credit,
                isCryptographicOrStandard = true
            )
        }

        val description = extractXmpTagValue(xmp, "dc:description")
            ?: extractXmpTagValue(xmp, "exif:UserComment")
        if (!description.isNullOrBlank()) {
            foundPrompt = AiPromptTextParsers.parseA1111Parameters(description)
                ?: AiPromptTextParsers.parseComfyUiPromptJson(description)
                ?: AiPromptTextParsers.parseMidjourneyOrCaptionPrompt(description)
            if (foundPrompt != null) {
                signals += AiSignalMatch(
                    sourceLayer = "XMP Description / UserComment",
                    signalName = "Prompt Tersemat di Paket XMP",
                    detectedToolOrModel = foundPrompt.modelName ?: creatorTool,
                    evidenceSnippet = foundPrompt.positivePrompt.take(120),
                    isCryptographicOrStandard = true
                )
            }
        }
        return foundPrompt
    }

    private fun extractXmpAttr(xmp: String, attrLocalName: String): String? {
        val regex = Regex("""(?:[a-zA-Z0-9_]+:)?$attrLocalName\s*=\s*["']([^"']+)["']""")
        return regex.find(xmp)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractXmpTagValue(xmp: String, tagName: String): String? {
        val regex = Regex("""<$tagName[^>]*>(.*?)</$tagName>""", RegexOption.DOT_MATCHES_ALL)
        val inner = regex.find(xmp)?.groupValues?.getOrNull(1) ?: return null
        val liMatch = Regex("""<rdf:li[^>]*>(.*?)</rdf:li>""", RegexOption.DOT_MATCHES_ALL).find(inner)
        return (liMatch?.groupValues?.getOrNull(1) ?: inner)
            .replace(Regex("<[^>]+>"), " ")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun cleanExifUserComment(raw: String): String {
        return raw.removePrefix("UNICODE\u0000")
            .removePrefix("ASCII\u0000")
            .trim('\u0000', ' ', '\n', '\r')
    }

    fun parsePngChunksFromStream(stream: InputStream): PngParseOutput {
        val sig = ByteArray(8)
        if (!readExact(stream, sig) || !sig.contentEquals(pngSignature)) {
            return PngParseOutput(isPng = false)
        }

        val chunkTypes = mutableListOf<String>()
        val textChunks = linkedMapOf<String, String>()
        var hasCabx = false
        var cabxBytes: ByteArray? = null

        val header = ByteArray(8)
        while (readExact(stream, header)) {
            val length = ((header[0].toInt() and 0xFF) shl 24) or
                ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
            val type = String(header, 4, 4, StandardCharsets.US_ASCII)
            if (length < 0) break

            chunkTypes += type
            if (type == "caBX") {
                hasCabx = true
            }

            if (type == "caBX" && length <= MAX_TEXT_CHUNK_BYTES) {
                val payload = ByteArray(length)
                if (!readExact(stream, payload)) break
                cabxBytes = payload
            } else if (type in setOf("tEXt", "zTXt", "iTXt") && length <= MAX_TEXT_CHUNK_BYTES) {
                val payload = ByteArray(length)
                if (!readExact(stream, payload)) break
                decodePngTextChunk(type, payload)?.let { (k, v) ->
                    textChunks[k] = v
                }
            } else {
                if (!skipExact(stream, length.toLong())) break
            }

            // Skip 4-byte CRC
            if (!skipExact(stream, 4L)) break
            if (type == "IEND") break
        }

        return PngParseOutput(
            isPng = true,
            chunkTypes = chunkTypes,
            textChunks = textChunks,
            hasCabxChunk = hasCabx,
            cabxBytes = cabxBytes
        )
    }

    private fun decodePngTextChunk(type: String, data: ByteArray): Pair<String, String>? {
        val nullIdx = data.indexOf(0.toByte())
        if (nullIdx <= 0) return null
        val keyword = String(data, 0, nullIdx, StandardCharsets.ISO_8859_1)

        return when (type) {
            "tEXt" -> {
                val text = String(data, nullIdx + 1, data.size - nullIdx - 1, StandardCharsets.ISO_8859_1)
                keyword to text
            }
            "zTXt" -> {
                if (nullIdx + 2 > data.size) return null
                // compression method at nullIdx + 1 (0 = zlib/deflate)
                val compressed = data.copyOfRange(nullIdx + 2, data.size)
                val text = inflateBytes(compressed, StandardCharsets.ISO_8859_1) ?: return null
                keyword to text
            }
            "iTXt" -> {
                if (nullIdx + 3 > data.size) return null
                val compFlag = data[nullIdx + 1].toInt() and 0xFF
                // compression method at nullIdx + 2
                var pos = nullIdx + 3
                // Skip language tag (null-terminated)
                while (pos < data.size && data[pos] != 0.toByte()) pos++
                pos++ // pass null
                // Skip translated keyword (null-terminated)
                while (pos < data.size && data[pos] != 0.toByte()) pos++
                pos++ // pass null
                if (pos > data.size) return null
                val remaining = data.copyOfRange(pos, data.size)
                val text = if (compFlag == 1) {
                    inflateBytes(remaining, StandardCharsets.UTF_8) ?: return null
                } else {
                    String(remaining, StandardCharsets.UTF_8)
                }
                keyword to text
            }
            else -> null
        }
    }

    private fun inflateBytes(compressed: ByteArray, charset: Charset): String? {
        return runCatching {
            val inflater = Inflater()
            inflater.setInput(compressed)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                } else {
                    out.write(buf, 0, count)
                }
            }
            inflater.end()
            String(out.toByteArray(), charset)
        }.getOrNull()
    }

    private fun detectContainerFormat(bytes: ByteArray): String {
        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "JPEG / JFIF"
        }
        if (bytes.size >= 12 &&
            String(bytes, 0, 4, StandardCharsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, StandardCharsets.US_ASCII) == "WEBP"
        ) {
            return "WebP (RIFF)"
        }
        if (bytes.size >= 8 && String(bytes, 4, 4, StandardCharsets.US_ASCII) == "ftyp") {
            return "MP4 / ISOBMFF"
        }
        return "Biner (${bytes.size} byte dipindai)"
    }

    private fun readHeadAndTailBytes(stream: InputStream, halfLimit: Int): ByteArray {
        val head = ByteArrayOutputStream()
        val tailChunks = ArrayDeque<ByteArray>()
        var tailTotalBytes = 0
        val buf = ByteArray(16384)
        var totalRead = 0L

        while (true) {
            val n = stream.read(buf)
            if (n <= 0) break
            val copy = buf.copyOf(n)
            if (totalRead < halfLimit) {
                val headRemaining = (halfLimit - totalRead.toInt()).coerceAtMost(n)
                head.write(copy, 0, headRemaining)
                if (headRemaining < n) {
                    val rem = copy.copyOfRange(headRemaining, n)
                    tailChunks.addLast(rem)
                    tailTotalBytes += rem.size
                }
            } else {
                tailChunks.addLast(copy)
                tailTotalBytes += copy.size
            }
            while (tailTotalBytes > halfLimit && tailChunks.isNotEmpty()) {
                val dropped = tailChunks.removeFirst()
                tailTotalBytes -= dropped.size
            }
            totalRead += n
        }

        val out = ByteArrayOutputStream(head.size() + tailTotalBytes + 1)
        out.write(head.toByteArray())
        if (tailChunks.isNotEmpty()) {
            out.write('\n'.code)
            tailChunks.forEach { out.write(it) }
        }
        return out.toByteArray()
    }

    private fun readExact(stream: InputStream, dst: ByteArray): Boolean {
        var offset = 0
        while (offset < dst.size) {
            val n = stream.read(dst, offset, dst.size - offset)
            if (n < 0) return false
            offset += n
        }
        return true
    }

    private fun skipExact(stream: InputStream, count: Long): Boolean {
        var remaining = count
        val scratch = ByteArray(4096)
        while (remaining > 0) {
            val skipped = stream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val n = stream.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
                if (n < 0) return false
                remaining -= n
            }
        }
        return true
    }

    internal fun buildHonestyNote(
        status: PromptAccuracyStatus,
        hasEmbeddedChunks: Boolean
    ): String {
        return when (status) {
            PromptAccuracyStatus.EXACT_EMBEDDED_100_PERCENT ->
                if (hasEmbeddedChunks) {
                    "TERVERIFIKASI 100% EKSAK: Prompt dan hyperparameter dibaca langsung karakter-demi-karakter dari chunk/metadata asli tanpa tebakan."
                } else {
                    "TERVERIFIKASI EKSAK: Teks prompt eksplisit ditemukan tertulis langsung pada metadata publik."
                }
            PromptAccuracyStatus.PLATFORM_AI_SIGNAL_ONLY ->
                "TRANSPARANSI FORENSIK: Sinyal asal AI (label platform / IPTC trainedAlgorithmicMedia / C2PA / signature generator) terdeteksi, tetapi teks prompt asli TELAH DIHAPUS oleh kompresi atau CDN. Secara matematis gambar/video terkompresi tidak dapat dibalik menjadi kata-kata prompt 100% eksak — gunakan file PNG/JPEG asli sebelum diunggah via 'Pilih File Media Asli (SAF)' bila tersedia."
            PromptAccuracyStatus.NO_VERIFIED_AI_SIGNAL ->
                "Tidak ditemukan metadata AIGC platform, XMP/IPTC AI, C2PA JUMBF, atau chunk prompt PNG/EXIF pada objek ini. Hal ini bisa berarti konten asli non-AI, atau seluruh metadata telah dibersihkan (stripped) oleh CDN media sosial."
        }
    }
}
