package com.example.aiforensic

import com.example.forensic.NetworkTraceResult
import com.example.forensic.SocialPlatform

object AiUrlSignalAnalyzer {

    private data class PlatformHtmlPattern(
        val platformHint: String,
        val regex: Regex,
        val signalName: String,
        val toolLabel: String
    )

    private val platformPatterns = listOf(
        PlatformHtmlPattern(
            platformHint = "TikTok AIGC",
            regex = Regex(""""(?:isAigc|is_aigc|aigc_label_type)"\s*:\s*(?:true|1|2)"""),
            signalName = "Flag AIGC Resmi TikTok (HTML/SSR JSON)",
            toolLabel = "TikTok AIGC Disclosure"
        ),
        PlatformHtmlPattern(
            platformHint = "TikTok AIGC",
            regex = Regex(""""aigcInfo"\s*:\s*\{[^}]*?"aigc_label_type"\s*:\s*[1-9]"""),
            signalName = "Objek aigcInfo TikTok",
            toolLabel = "TikTok AIGC"
        ),
        PlatformHtmlPattern(
            platformHint = "YouTube Synthetic Media",
            regex = Regex(""""(?:isSynthetic|hasSyntheticMedia|isAlteredOrSyntheticContent)"\s*:\s*true"""),
            signalName = "Flag Altered/Synthetic Content YouTube",
            toolLabel = "YouTube Synthetic Content"
        ),
        PlatformHtmlPattern(
            platformHint = "Meta AI Label",
            regex = Regex(""""(?:made_with_ai|has_ai_generated_label|is_ai_generated)"\s*:\s*true"""),
            signalName = "Label 'Made with AI' Meta (IG/FB/Threads)",
            toolLabel = "Meta AI Info"
        ),
        PlatformHtmlPattern(
            platformHint = "IPTC / C2PA",
            regex = Regex("""trainedAlgorithmicMedia|compositeWithTrainedAlgorithmicMedia""", RegexOption.IGNORE_CASE),
            signalName = "IPTC DigitalSourceType (trainedAlgorithmicMedia)",
            toolLabel = "Generative AI (IPTC/C2PA)"
        )
    )

    private val aiToolKeywords: List<Pair<Regex, String>> = listOf(
        Regex("""\bmidjourney\b|--niji\b|--sref\b|--cref\b""", RegexOption.IGNORE_CASE) to "Midjourney",
        Regex("""\bcomfyui\b""", RegexOption.IGNORE_CASE) to "ComfyUI",
        Regex("""\bautomatic1111\b|\ba1111\b|\bsd\s*webui\b|\bforge\s*webui\b""", RegexOption.IGNORE_CASE) to "Automatic1111 / Forge",
        Regex("""\bstable\s*diffusion\b|\bsdxl\b|\bsd1\.5\b""", RegexOption.IGNORE_CASE) to "Stable Diffusion / SDXL",
        Regex("""\bflux(?:\.1)?(?:\s*(?:dev|schnell|pro))?\b""", RegexOption.IGNORE_CASE) to "Black Forest Labs Flux",
        Regex("""\bsora\b(?=\s+(?:ai|openai|video|prompt|gen))""", RegexOption.IGNORE_CASE) to "OpenAI Sora",
        Regex("""\bdall-?e(?:\s*[23])?\b""", RegexOption.IGNORE_CASE) to "OpenAI DALL-E",
        Regex("""\bgoogle\s*(?:veo|imagen)\b|\bveo\s*[23]\b|\bimagen\s*[234]\b""", RegexOption.IGNORE_CASE) to "Google Veo / Imagen",
        Regex("""\brunway(?:\s*gen-?[234])?\b""", RegexOption.IGNORE_CASE) to "Runway Gen",
        Regex("""\bkling(?:\s*ai)?\b""", RegexOption.IGNORE_CASE) to "Kling AI",
        Regex("""\bluma\s*dream\s*machine\b""", RegexOption.IGNORE_CASE) to "Luma Dream Machine",
        Regex("""\bhailuo(?:\s*ai)?\b|\bminimax\b""", RegexOption.IGNORE_CASE) to "Hailuo / MiniMax",
        Regex("""\bsuno(?:\s*ai)?\b""", RegexOption.IGNORE_CASE) to "Suno AI (Audio/Musik)",
        Regex("""\budio(?:\s*ai)?\b""", RegexOption.IGNORE_CASE) to "Udio AI (Audio/Musik)",
        Regex("""\belevenlabs\b""", RegexOption.IGNORE_CASE) to "ElevenLabs (Voice AI)",
        Regex("""\bheygen\b|\bsynthesia\b""", RegexOption.IGNORE_CASE) to "HeyGen / Synthesia (Avatar AI)",
        Regex("""\bgemini\b|\bchatgpt\b|\bclaude\b""", RegexOption.IGNORE_CASE) to "LLM (Gemini / ChatGPT / Claude)",
        Regex("""#(?:aigenerated|aiart|midjourney|stablediffusion|comfyui|soraai|klingai|madewithai|dibuatdenganai)\b""", RegexOption.IGNORE_CASE) to "Tagar Pengungkapan AI"
    )

    fun extractHtmlAiSignals(html: String): List<AiSignalMatch> {
        if (html.isBlank()) return emptyList()
        val matches = mutableListOf<AiSignalMatch>()
        for (pattern in platformPatterns) {
            val found = pattern.regex.find(html)
            if (found != null) {
                matches += AiSignalMatch(
                    sourceLayer = "HTML / JSON Publik (${pattern.platformHint})",
                    signalName = pattern.signalName,
                    detectedToolOrModel = pattern.toolLabel,
                    evidenceSnippet = found.value.take(120),
                    isCryptographicOrStandard = true
                )
            }
        }
        return matches
    }

    fun scanTextForAiToolKeywords(text: String, sourceLayer: String): List<AiSignalMatch> {
        if (text.isBlank()) return emptyList()
        val results = mutableListOf<AiSignalMatch>()
        for ((regex, toolName) in aiToolKeywords) {
            val m = regex.find(text)
            if (m != null) {
                results += AiSignalMatch(
                    sourceLayer = sourceLayer,
                    signalName = "Penanda / Kata Kunci Generator AI",
                    detectedToolOrModel = toolName,
                    evidenceSnippet = m.value,
                    isCryptographicOrStandard = false
                )
            }
        }
        return results.distinctBy { "${it.sourceLayer}|${it.detectedToolOrModel}" }
    }

    fun analyzeUrlAndCaption(
        postUrl: String,
        platform: SocialPlatform,
        networkTrace: NetworkTraceResult?,
        captionInput: String
    ): AiForensicReport {
        val signals = mutableListOf<AiSignalMatch>()
        var extractedPrompt: ExtractedAiPrompt? = null

        // 1. Platform HTML / SSR signals captured during NetworkTraceEngine
        networkTrace?.aiHtmlSignals?.let { htmlSignals ->
            signals += htmlSignals
        }

        // 2. Scan public OG Title + OG Description + User Caption
        val textSources = listOfNotNull(
            networkTrace?.extractedOgTitle?.takeIf { it.isNotBlank() }?.let { "HTML og:title" to it },
            networkTrace?.extractedDescription?.takeIf { it.isNotBlank() }?.let { "HTML og:description" to it },
            captionInput.takeIf { it.isNotBlank() }?.let { "Caption / Teks Analis" to it }
        )

        for ((layer, text) in textSources) {
            if (extractedPrompt == null) {
                extractedPrompt = AiPromptTextParsers.parseA1111Parameters(text)
                    ?: AiPromptTextParsers.parseComfyUiPromptJson(text)
                    ?: AiPromptTextParsers.parseMidjourneyOrCaptionPrompt(text)
                if (extractedPrompt != null) {
                    signals += AiSignalMatch(
                        sourceLayer = layer,
                        signalName = extractedPrompt.generatorFormat,
                        detectedToolOrModel = extractedPrompt.modelName,
                        evidenceSnippet = extractedPrompt.positivePrompt.take(120),
                        isCryptographicOrStandard = false
                    )
                }
            }
            signals += scanTextForAiToolKeywords(text, layer)
        }

        val uniqueSignals = signals.distinctBy { "${it.sourceLayer}|${it.signalName}|${it.detectedToolOrModel}" }
        val status = when {
            extractedPrompt != null -> PromptAccuracyStatus.EXACT_EMBEDDED_100_PERCENT
            uniqueSignals.isNotEmpty() -> PromptAccuracyStatus.PLATFORM_AI_SIGNAL_ONLY
            else -> PromptAccuracyStatus.NO_VERIFIED_AI_SIGNAL
        }

        val label = if (postUrl.isNotBlank()) {
            "${platform.displayName}: ${postUrl.take(48)}"
        } else {
            "Analisis URL & Caption"
        }

        return AiForensicReport(
            targetLabel = label,
            accuracyStatus = status,
            aiOriginDetected = status != PromptAccuracyStatus.NO_VERIFIED_AI_SIGNAL,
            primaryToolOrPlatform = extractedPrompt?.modelName
                ?: uniqueSignals.firstOrNull { !it.detectedToolOrModel.isNullOrBlank() }?.detectedToolOrModel,
            binaryContainerFormat = if (networkTrace != null) "HTML/JSON Publik (${networkTrace.hops.size} hop)" else "Teks/URL Offline",
            signals = uniqueSignals,
            extractedPrompt = extractedPrompt,
            honestyLimitationNote = PngAndBinaryAiParser.buildHonestyNote(status, hasEmbeddedChunks = false)
        )
    }
}
