package com.example.aiforensic

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

object AiPromptTextParsers {

    fun parseA1111Parameters(raw: String): ExtractedAiPrompt? {
        val text = raw.trim()
        if (text.isBlank()) return null

        val lines = text.lines()
        val trailerIndex = lines.indexOfLast { line ->
            line.contains("Steps:", ignoreCase = true) &&
                (line.contains("Sampler:", ignoreCase = true) ||
                    line.contains("CFG scale:", ignoreCase = true) ||
                    line.contains("Seed:", ignoreCase = true))
        }

        val negIndex = lines.indexOfFirst {
            it.trimStart().startsWith("Negative prompt:", ignoreCase = true)
        }

        if (trailerIndex < 0 && negIndex < 0) return null

        val bodyEnd = if (trailerIndex >= 0) trailerIndex else lines.size
        val positiveEnd = if (negIndex in 0 until bodyEnd) negIndex else bodyEnd
        val positive = lines.subList(0, positiveEnd).joinToString("\n").trim()

        val negative = if (negIndex in 0 until bodyEnd) {
            val firstNeg = lines[negIndex].trimStart()
                .removePrefix("Negative prompt:")
                .removePrefix("negative prompt:")
                .trim()
            val restNeg = if (negIndex + 1 < bodyEnd) {
                lines.subList(negIndex + 1, bodyEnd).joinToString("\n").trim()
            } else {
                ""
            }
            listOf(firstNeg, restNeg).filter { it.isNotBlank() }.joinToString("\n").ifBlank { null }
        } else {
            null
        }

        val paramMap = if (trailerIndex >= 0) {
            parseKeyValueTrailer(lines[trailerIndex])
        } else {
            emptyMap()
        }

        if (positive.isBlank() && negative.isNullOrBlank() && paramMap.isEmpty()) return null

        return ExtractedAiPrompt(
            generatorFormat = "Automatic1111 / SD WebUI / Forge",
            positivePrompt = positive.ifBlank { "(Prompt positif kosong)" },
            negativePrompt = negative,
            modelName = paramMap["Model"] ?: paramMap["Model hash"],
            sampler = listOfNotNull(paramMap["Sampler"], paramMap["Schedule type"])
                .joinToString(" • ").ifBlank { null },
            steps = paramMap["Steps"],
            cfgScale = paramMap["CFG scale"],
            seed = paramMap["Seed"],
            extraParams = paramMap.filterKeys {
                it !in setOf("Model", "Sampler", "Schedule type", "Steps", "CFG scale", "Seed")
            },
            rawChunkText = text,
            isVerbatimByteExact = true
        )
    }

    private fun parseKeyValueTrailer(trailer: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        // Split carefully on ", Key:" patterns so commas inside values (e.g. Lora hashes) stay intact
        val tokenRegex = Regex("""(?:^|,\s*)([A-Za-z0-9 _/\-]+):\s*""")
        val matches = tokenRegex.findAll(trailer).toList()
        for (i in matches.indices) {
            val key = matches[i].groupValues[1].trim()
            val valStart = matches[i].range.last + 1
            val valEnd = if (i + 1 < matches.size) matches[i + 1].range.first else trailer.length
            if (valStart <= valEnd) {
                val value = trailer.substring(valStart, valEnd).trim().trimEnd(',')
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    result[key] = value
                }
            }
        }
        return result
    }

    fun parseComfyUiPromptJson(rawJson: String): ExtractedAiPrompt? {
        val trimmed = rawJson.trim()
        if (!trimmed.startsWith("{")) return null
        val root = runCatching { JSONObject(trimmed) }.getOrNull() ?: return null

        val nodes = mutableMapOf<String, JSONObject>()
        val keys = root.keys()
        while (keys.hasNext()) {
            val nodeId = keys.next()
            val obj = root.optJSONObject(nodeId) ?: continue
            if (obj.has("class_type") && obj.has("inputs")) {
                nodes[nodeId] = obj
            }
        }
        if (nodes.isEmpty()) return null

        var steps: String? = null
        var cfg: String? = null
        var sampler: String? = null
        var seed: String? = null
        var scheduler: String? = null
        val positiveTexts = mutableListOf<String>()
        val negativeTexts = mutableListOf<String>()
        val models = mutableListOf<String>()
        val loras = mutableListOf<String>()

        fun traceConditioningTexts(nodeId: String?, visited: MutableSet<String>, out: MutableList<String>) {
            if (nodeId == null || !visited.add(nodeId)) return
            val node = nodes[nodeId] ?: return
            val classType = node.optString("class_type")
            val inputs = node.optJSONObject("inputs") ?: return

            if (classType.contains("TextEncode", ignoreCase = true) ||
                classType.contains("Prompt", ignoreCase = true)
            ) {
                listOf("text", "text_g", "text_l", "t5xxl", "clip_l", "prompt", "positive")
                    .mapNotNull { key -> inputs.opt(key) as? String }
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .forEach { if (it !in out) out += it }
            }

            // Follow conditioning/guidance chaining links such as FluxGuidance or ConditioningCombine
            val linkKeys = inputs.keys()
            while (linkKeys.hasNext()) {
                val k = linkKeys.next()
                val arr = inputs.optJSONArray(k)
                if (arr != null && arr.length() >= 1) {
                    val refId = arr.opt(0)?.toString()
                    if (refId != null && nodes.containsKey(refId)) {
                        traceConditioningTexts(refId, visited, out)
                    }
                }
            }
        }

        for ((_, node) in nodes) {
            val classType = node.optString("class_type")
            val inputs = node.optJSONObject("inputs") ?: continue

            when {
                classType.contains("KSampler", ignoreCase = true) ||
                    classType.contains("SamplerCustom", ignoreCase = true) -> {
                    if (steps == null) steps = inputs.opt("steps")?.takeIf { it !is JSONArray }?.toString()
                    if (cfg == null) cfg = inputs.opt("cfg")?.takeIf { it !is JSONArray }?.toString()
                    if (sampler == null) sampler = inputs.optString("sampler_name").takeIf { it.isNotBlank() }
                    if (scheduler == null) scheduler = inputs.optString("scheduler").takeIf { it.isNotBlank() }
                    if (seed == null) {
                        seed = (inputs.opt("seed") ?: inputs.opt("noise_seed"))
                            ?.takeIf { it !is JSONArray }?.toString()
                    }
                    inputs.optJSONArray("positive")?.opt(0)?.toString()?.let { refId ->
                        traceConditioningTexts(refId, mutableSetOf(), positiveTexts)
                    }
                    inputs.optJSONArray("negative")?.opt(0)?.toString()?.let { refId ->
                        traceConditioningTexts(refId, mutableSetOf(), negativeTexts)
                    }
                }

                classType.contains("CheckpointLoader", ignoreCase = true) ||
                    classType.contains("UNETLoader", ignoreCase = true) ||
                    classType.contains("DiffusionModel", ignoreCase = true) -> {
                    val ckpt = inputs.optString("ckpt_name")
                        .ifBlank { inputs.optString("unet_name") }
                        .ifBlank { inputs.optString("model_name") }
                    if (ckpt.isNotBlank() && ckpt !in models) models += ckpt
                }

                classType.contains("LoraLoader", ignoreCase = true) -> {
                    val lora = inputs.optString("lora_name")
                    if (lora.isNotBlank() && lora !in loras) loras += lora
                }
            }
        }

        // Fallback if graph has TextEncode nodes without KSampler edges
        if (positiveTexts.isEmpty() && negativeTexts.isEmpty()) {
            for ((_, node) in nodes) {
                val classType = node.optString("class_type")
                if (!classType.contains("TextEncode", ignoreCase = true)) continue
                val title = node.optJSONObject("_meta")?.optString("title").orEmpty()
                val inputs = node.optJSONObject("inputs") ?: continue
                val text = inputs.optString("text").ifBlank { inputs.optString("prompt") }.trim()
                if (text.isNotBlank()) {
                    if (title.contains("neg", ignoreCase = true)) {
                        negativeTexts += text
                    } else {
                        positiveTexts += text
                    }
                }
            }
        }

        if (positiveTexts.isEmpty() && models.isEmpty() && sampler == null) return null

        val extra = linkedMapOf<String, String>()
        if (loras.isNotEmpty()) extra["LoRA"] = loras.joinToString(", ")
        if (scheduler != null) extra["Scheduler"] = scheduler
        extra["ComfyUI Nodes"] = "${nodes.size} node"

        return ExtractedAiPrompt(
            generatorFormat = "ComfyUI Node Graph JSON",
            positivePrompt = positiveTexts.joinToString("\n\n")
                .ifBlank { "(Node graf ComfyUI tanpa teks prompt eksplisit)" },
            negativePrompt = negativeTexts.joinToString("\n\n").ifBlank { null },
            modelName = models.joinToString(", ").ifBlank { null },
            sampler = sampler,
            steps = steps,
            cfgScale = cfg,
            seed = seed,
            extraParams = extra,
            rawChunkText = rawJson,
            isVerbatimByteExact = true
        )
    }

    fun parseNovelAiChunks(description: String?, commentJson: String?): ExtractedAiPrompt? {
        val commentObj = commentJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        val positive = commentObj?.optString("prompt")?.takeIf { it.isNotBlank() }
            ?: description?.takeIf { it.isNotBlank() }
            ?: return null
        val negative = commentObj?.optString("uc")?.takeIf { it.isNotBlank() }
        val steps = commentObj?.opt("steps")?.toString()
        val scale = commentObj?.opt("scale")?.toString()
        val seed = commentObj?.opt("seed")?.toString()
        val sampler = commentObj?.optString("sampler")?.takeIf { it.isNotBlank() }

        return ExtractedAiPrompt(
            generatorFormat = "NovelAI PNG Metadata",
            positivePrompt = positive,
            negativePrompt = negative,
            modelName = "NovelAI Diffusion",
            sampler = sampler,
            steps = steps,
            cfgScale = scale,
            seed = seed,
            rawChunkText = commentJson ?: description.orEmpty(),
            isVerbatimByteExact = true
        )
    }

    fun parseInvokeAiMetadata(rawJson: String): ExtractedAiPrompt? {
        val obj = runCatching { JSONObject(rawJson.trim()) }.getOrNull() ?: return null
        val positive = obj.optString("positive_prompt").ifBlank { obj.optString("prompt") }
        if (positive.isBlank()) return null
        val negative = obj.optString("negative_prompt").takeIf { it.isNotBlank() }
        val modelObj = obj.optJSONObject("model")
        val modelName = modelObj?.optString("name")?.takeIf { it.isNotBlank() }
            ?: obj.optString("model").takeIf { it.isNotBlank() }

        return ExtractedAiPrompt(
            generatorFormat = "InvokeAI JSON Metadata",
            positivePrompt = positive,
            negativePrompt = negative,
            modelName = modelName,
            sampler = obj.optString("scheduler").takeIf { it.isNotBlank() },
            steps = obj.opt("steps")?.toString(),
            cfgScale = obj.opt("cfg_scale")?.toString(),
            seed = obj.opt("seed")?.toString(),
            rawChunkText = rawJson,
            isVerbatimByteExact = true
        )
    }

    fun parseMidjourneyOrCaptionPrompt(rawText: String): ExtractedAiPrompt? {
        val text = rawText.trim()
        if (text.isBlank()) return null

        // Check Midjourney flags (--v, --ar, --stylize, --niji, --chaos, --sref, --cref)
        val mjFlagRegex = Regex("""--(v|ar|stylize|s|chaos|c|niji|sref|cref|seed|no|style|weird|w)\s+([^\s]+)""", RegexOption.IGNORE_CASE)
        val mjMatches = mjFlagRegex.findAll(text).toList()
        if (mjMatches.isNotEmpty()) {
            val extra = linkedMapOf<String, String>()
            var version: String? = null
            var seed: String? = null
            var negative: String? = null
            mjMatches.forEach { m ->
                val flag = m.groupValues[1].lowercase(Locale.US)
                val value = m.groupValues[2]
                when (flag) {
                    "v" -> version = "Midjourney v$value"
                    "niji" -> version = "Midjourney Niji $value"
                    "seed" -> seed = value
                    "no" -> negative = value
                    else -> extra["--$flag"] = value
                }
            }
            val cleanedPrompt = text.replace(mjFlagRegex, "").trim().trimEnd(',', ' ')
            if (cleanedPrompt.isNotBlank()) {
                return ExtractedAiPrompt(
                    generatorFormat = "Midjourney Parameter Prompt",
                    positivePrompt = cleanedPrompt,
                    negativePrompt = negative,
                    modelName = version ?: "Midjourney",
                    seed = seed,
                    extraParams = extra,
                    rawChunkText = text,
                    isVerbatimByteExact = true
                )
            }
        }

        // Check explicit "Prompt: ..." declaration inside caption/metadata
        val explicitPromptRegex = Regex("""(?im)^(?:ai\s+)?(?:positive\s+)?prompt\s*[:=]\s*(.+)$""")
        val promptMatch = explicitPromptRegex.find(text)
        if (promptMatch != null) {
            val extractedPos = promptMatch.groupValues[1].trim()
            val negMatch = Regex("""(?im)^negative\s+prompt\s*[:=]\s*(.+)$""").find(text)
            val modelMatch = Regex("""(?im)^(?:model|tool|generator)\s*[:=]\s*(.+)$""").find(text)
            if (extractedPos.length >= 6) {
                return ExtractedAiPrompt(
                    generatorFormat = "Deklarasi Prompt Eksplisit (Caption/Metadata)",
                    positivePrompt = extractedPos,
                    negativePrompt = negMatch?.groupValues?.get(1)?.trim(),
                    modelName = modelMatch?.groupValues?.get(1)?.trim(),
                    rawChunkText = text,
                    isVerbatimByteExact = true
                )
            }
        }

        return null
    }
}
