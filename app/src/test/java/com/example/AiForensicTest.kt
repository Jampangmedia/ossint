package com.example

import com.example.aiforensic.AiUrlSignalAnalyzer
import com.example.aiforensic.PngAndBinaryAiParser
import com.example.aiforensic.PromptAccuracyStatus
import com.example.forensic.NetworkTraceResult
import com.example.forensic.SocialPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.Deflater

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiForensicTest {

    @Test
    fun `png tEXt chunk with A1111 parameters extracts 100 percent exact prompt and hyperparameters`() {
        val rawParams = """
            cyberpunk jakarta street at night, neon reflections, 8k
            Negative prompt: blurry, lowres, watermark
            Steps: 28, Sampler: DPM++ 2M Karras, CFG scale: 7.5, Seed: 409182736, Size: 1024x1024, Model: juggernautXL_v9
        """.trimIndent()

        val pngBytes = buildMinimalPng(
            listOf(
                buildTextChunk("parameters", rawParams)
            )
        )

        val report = PngAndBinaryAiParser.inspectBytes(pngBytes, "sample_a1111.png")
        assertEquals(PromptAccuracyStatus.EXACT_EMBEDDED_100_PERCENT, report.accuracyStatus)
        assertTrue(report.aiOriginDetected)
        val prompt = report.extractedPrompt
        assertNotNull(prompt)
        assertEquals("cyberpunk jakarta street at night, neon reflections, 8k", prompt!!.positivePrompt)
        assertEquals("blurry, lowres, watermark", prompt.negativePrompt)
        assertEquals("28", prompt.steps)
        assertEquals("7.5", prompt.cfgScale)
        assertEquals("409182736", prompt.seed)
        assertEquals("juggernautXL_v9", prompt.modelName)
        assertTrue(prompt.isVerbatimByteExact)
    }

    @Test
    fun `png iTXt compressed comfyui json graph traces KSampler edges to exact prompts`() {
        val comfyJson = """
            {
              "3": {
                "class_type": "KSampler",
                "inputs": {
                  "seed": 99887766,
                  "steps": 20,
                  "cfg": 6.0,
                  "sampler_name": "euler",
                  "scheduler": "normal",
                  "positive": ["6", 0],
                  "negative": ["7", 0]
                }
              },
              "4": {
                "class_type": "CheckpointLoaderSimple",
                "inputs": { "ckpt_name": "flux1-dev-fp8.safetensors" }
              },
              "6": {
                "class_type": "CLIPTextEncode",
                "inputs": { "text": "aerial view of mount bromo sunrise mist" }
              },
              "7": {
                "class_type": "CLIPTextEncode",
                "inputs": { "text": "cartoon, oversaturated" }
              }
            }
        """.trimIndent()

        val pngBytes = buildMinimalPng(
            listOf(
                buildCompressedItxtChunk("prompt", comfyJson)
            )
        )

        val report = PngAndBinaryAiParser.inspectBytes(pngBytes, "comfy_sample.png")
        assertEquals(PromptAccuracyStatus.EXACT_EMBEDDED_100_PERCENT, report.accuracyStatus)
        val prompt = report.extractedPrompt
        assertNotNull(prompt)
        assertEquals("aerial view of mount bromo sunrise mist", prompt!!.positivePrompt)
        assertEquals("cartoon, oversaturated", prompt.negativePrompt)
        assertEquals("flux1-dev-fp8.safetensors", prompt.modelName)
        assertEquals("euler", prompt.sampler)
        assertEquals("20", prompt.steps)
        assertEquals("99887766", prompt.seed)
    }

    @Test
    fun `jpeg container with iptc trainedAlgorithmicMedia and c2pa marker reports signal without fabricating prompt`() {
        val fakeJpegHeader = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte())
        val xmpAndC2paPayload = (
            "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">" +
                "<rdf:RDF><rdf:Description iptcExt:DigitalSourceType=\"http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia\" " +
                "xmp:CreatorTool=\"ChatGPT\"/></rdf:RDF></x:xmpmeta>jumb....c2pa.actions....OpenAI"
            ).toByteArray(StandardCharsets.UTF_8)

        val bytes = fakeJpegHeader + xmpAndC2paPayload
        val report = PngAndBinaryAiParser.inspectBytes(bytes, "reencoded_social.jpg")
        assertEquals(PromptAccuracyStatus.PLATFORM_AI_SIGNAL_ONLY, report.accuracyStatus)
        assertTrue(report.aiOriginDetected)
        assertNull(report.extractedPrompt)
        assertTrue(report.signals.any { it.sourceLayer.contains("IPTC") || it.sourceLayer.contains("C2PA") })
    }

    @Test
    fun `url html analyzer detects tiktok aigc disclosure and midjourney prompt flags`() {
        val html = """<html><body><script>{"itemInfo":{"isAigc":true,"aigc_label_type":1}}</script></body></html>"""
        val htmlSignals = AiUrlSignalAnalyzer.extractHtmlAiSignals(html)
        assertTrue(htmlSignals.isNotEmpty())

        val trace = NetworkTraceResult(
            targetUrl = "https://www.tiktok.com/@ai_lab/video/7348921048571234567",
            finalUrl = "https://www.tiktok.com/@ai_lab/video/7348921048571234567",
            aiHtmlSignals = htmlSignals
        )
        val signalOnlyReport = AiUrlSignalAnalyzer.analyzeUrlAndCaption(
            postUrl = trace.targetUrl,
            platform = SocialPlatform.TIKTOK,
            networkTrace = trace,
            captionInput = "Karya eksperimen video #fyp"
        )
        assertEquals(PromptAccuracyStatus.PLATFORM_AI_SIGNAL_ONLY, signalOnlyReport.accuracyStatus)
        assertNull(signalOnlyReport.extractedPrompt)

        val mjReport = AiUrlSignalAnalyzer.analyzeUrlAndCaption(
            postUrl = trace.targetUrl,
            platform = SocialPlatform.INSTAGRAM,
            networkTrace = null,
            captionInput = "Retro batik mech warrior --ar 9:16 --v 6.1 --seed 1024"
        )
        assertEquals(PromptAccuracyStatus.EXACT_EMBEDDED_100_PERCENT, mjReport.accuracyStatus)
        assertEquals("Retro batik mech warrior", mjReport.extractedPrompt?.positivePrompt)
        assertEquals("Midjourney v6.1", mjReport.extractedPrompt?.modelName)
        assertEquals("1024", mjReport.extractedPrompt?.seed)
    }

    @Test
    fun `plain container with no ai metadata returns NO_VERIFIED_AI_SIGNAL`() {
        val cleanPng = buildMinimalPng(emptyList())
        val report = PngAndBinaryAiParser.inspectBytes(cleanPng, "camera_clean.png")
        assertEquals(PromptAccuracyStatus.NO_VERIFIED_AI_SIGNAL, report.accuracyStatus)
        assertFalse(report.aiOriginDetected)
        assertNull(report.extractedPrompt)
    }

    @Test
    fun `large mp4 buffer over 4mb still detects c2pa and xmp atoms located at file tail`() {
        val mp4Header = byteArrayOf(
            0x00, 0x00, 0x00, 0x18,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'm'.code.toByte(), 'p'.code.toByte(), '4'.code.toByte(), '2'.code.toByte()
        )
        val bodyPadding = ByteArray(4_500_000)
        val tailMoovXmp = (
            "moov....iptcExt:DigitalSourceType=\"http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia\" " +
                "c2pa.actions....Google Veo"
            ).toByteArray(StandardCharsets.UTF_8)

        val largeBuffer = mp4Header + bodyPadding + tailMoovXmp
        val report = PngAndBinaryAiParser.inspectBytes(largeBuffer, "large_veo_clip.mp4")
        assertEquals(PromptAccuracyStatus.PLATFORM_AI_SIGNAL_ONLY, report.accuracyStatus)
        assertTrue(report.aiOriginDetected)
        assertTrue(report.signals.any { it.sourceLayer.contains("IPTC") || it.sourceLayer.contains("C2PA") })
        assertNull(report.extractedPrompt)
        assertNotNull(report.sha256Hex)
        assertEquals(64, report.sha256Hex?.length)
        assertTrue(report.containerNodes.any { it.type == "ftyp" })
    }

    @Test
    fun `binary container inspector computes sha256 verifies png crc32 and parses c2pa claims`() {
        val c2paPayload = "jumb....c2pa.claim....c2pa.actions....c2pa.created....c2pa.hash.data....Adobe Firefly....Adobe Inc.....trainedAlgorithmicMedia"
            .toByteArray(StandardCharsets.UTF_8)
        val pngWithCabx = buildMinimalPng(
            listOf(
                buildChunk("caBX", c2paPayload)
            )
        )
        val report = PngAndBinaryAiParser.inspectBytes(pngWithCabx, "firefly_signed.png")
        assertEquals(PromptAccuracyStatus.PLATFORM_AI_SIGNAL_ONLY, report.accuracyStatus)
        assertNotNull(report.sha256Hex)
        assertEquals(64, report.sha256Hex!!.length)
        assertEquals(pngWithCabx.size.toLong(), report.fileSizeBytes)

        assertTrue(report.containerNodes.any { it.type == "IHDR" && it.detail == "CRC-32 valid" })
        assertTrue(report.containerNodes.any { it.type == "caBX" && it.detail == "CRC-32 valid" })

        val c2pa = report.c2paClaims
        assertNotNull(c2pa)
        assertEquals("Adobe Firefly", c2pa!!.claimGenerator)
        assertEquals("Adobe Inc.", c2pa.signerIssuer)
        assertTrue(c2pa.actions.contains("c2pa.created"))
        assertTrue(c2pa.assertionLabels.contains("c2pa.hash.data"))
    }

    private fun buildMinimalPng(extraChunks: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        // Minimal 1x1 IHDR
        out.write(buildChunk("IHDR", ByteArray(13).apply { this[3] = 1; this[7] = 1; this[8] = 8 }))
        extraChunks.forEach { out.write(it) }
        out.write(buildChunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }

    private fun buildTextChunk(keyword: String, text: String): ByteArray {
        val data = keyword.toByteArray(StandardCharsets.ISO_8859_1) +
            byteArrayOf(0) +
            text.toByteArray(StandardCharsets.ISO_8859_1)
        return buildChunk("tEXt", data)
    }

    private fun buildCompressedItxtChunk(keyword: String, text: String): ByteArray {
        val deflater = Deflater()
        val input = text.toByteArray(StandardCharsets.UTF_8)
        deflater.setInput(input)
        deflater.finish()
        val compressedOut = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            compressedOut.write(buf, 0, n)
        }
        deflater.end()

        val payload = ByteArrayOutputStream().apply {
            write(keyword.toByteArray(StandardCharsets.ISO_8859_1))
            write(0) // null after keyword
            write(1) // compression flag = 1 (compressed)
            write(0) // compression method = 0 (zlib)
            write(0) // empty language tag
            write(0) // empty translated keyword
            write(compressedOut.toByteArray())
        }.toByteArray()
        return buildChunk("iTXt", payload)
    }

    private fun buildChunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(StandardCharsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }.value
        return ByteBuffer.allocate(12 + data.size)
            .putInt(data.size)
            .put(typeBytes)
            .put(data)
            .putInt(crc.toInt())
            .array()
    }
}
