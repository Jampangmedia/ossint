package com.example.aiforensic

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32

object BinaryContainerInspector {

    private val pngSignature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    private val isoContainerBoxes = setOf(
        "moov", "trak", "mdia", "minf", "stbl", "udta", "ilst", "edts", "jumb", "c2pa", "c2as"
    )

    fun computeSha256(stream: InputStream): Pair<String, Long> {
        val digest = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(16 * 1024)
        var totalBytes = 0L
        while (true) {
            val read = stream.read(buf)
            if (read <= 0) break
            digest.update(buf, 0, read)
            totalBytes += read
        }
        return toHex(digest.digest()) to totalBytes
    }

    fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return toHex(digest.digest(bytes))
    }

    private fun toHex(hash: ByteArray): String {
        val sb = StringBuilder(hash.size * 2)
        for (b in hash) {
            val v = b.toInt() and 0xFF
            if (v < 16) sb.append('0')
            sb.append(v.toString(16))
        }
        return sb.toString()
    }

    fun inspectPngStructure(stream: InputStream, maxNodes: Int = 80): List<ContainerStructureNode> {
        val sig = ByteArray(8)
        if (!readExact(stream, sig) || !sig.contentEquals(pngSignature)) return emptyList()

        val nodes = mutableListOf<ContainerStructureNode>()
        var offset = 8L
        val header = ByteArray(8)
        val scratch = ByteArray(8192)
        val crcBuf = ByteArray(4)

        while (readExact(stream, header)) {
            val length = ((header[0].toInt() and 0xFF) shl 24) or
                ((header[1].toInt() and 0xFF) shl 16) or
                ((header[2].toInt() and 0xFF) shl 8) or
                (header[3].toInt() and 0xFF)
            val typeBytes = header.copyOfRange(4, 8)
            val type = String(typeBytes, StandardCharsets.US_ASCII)
            if (length < 0) break

            val crc = CRC32()
            crc.update(typeBytes)
            var remaining = length.toLong()
            var readFailed = false
            while (remaining > 0) {
                val n = stream.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
                if (n < 0) {
                    readFailed = true
                    break
                }
                crc.update(scratch, 0, n)
                remaining -= n
            }
            if (readFailed || !readExact(stream, crcBuf)) break

            val storedCrc = ((crcBuf[0].toLong() and 0xFF) shl 24) or
                ((crcBuf[1].toLong() and 0xFF) shl 16) or
                ((crcBuf[2].toLong() and 0xFF) shl 8) or
                (crcBuf[3].toLong() and 0xFF)
            val crcOk = crc.value == storedCrc

            if (nodes.size < maxNodes) {
                nodes += ContainerStructureNode(
                    type = type,
                    sizeBytes = length.toLong(),
                    offsetBytes = offset,
                    depth = 0,
                    detail = if (crcOk) "CRC-32 valid" else "CRC-32 tidak cocok!"
                )
            }
            offset += 12L + length.toLong()
            if (type == "IEND") break
        }
        return nodes
    }

    fun inspectIsoBmffAtoms(bytes: ByteArray, maxNodes: Int = 80): List<ContainerStructureNode> {
        if (bytes.size < 8 || String(bytes, 4, 4, StandardCharsets.US_ASCII) != "ftyp") {
            return emptyList()
        }
        val nodes = mutableListOf<ContainerStructureNode>()
        walkIsoRange(
            bytes = bytes,
            start = 0,
            end = bytes.size,
            depth = 0,
            maxNodes = maxNodes,
            out = nodes
        )
        return nodes
    }

    private fun walkIsoRange(
        bytes: ByteArray,
        start: Int,
        end: Int,
        depth: Int,
        maxNodes: Int,
        out: MutableList<ContainerStructureNode>
    ) {
        if (depth > 3) return
        var pos = start
        while (pos + 8 <= end && out.size < maxNodes) {
            val size32 = readUInt32(bytes, pos)
            val type = String(bytes, pos + 4, 4, StandardCharsets.US_ASCII)
            if (!type.all { it.code in 0x20..0x7E }) break

            var headerSize = 8
            val boxSize: Long = when (size32) {
                0L -> (end - pos).toLong()
                1L -> {
                    if (pos + 16 > end) break
                    headerSize = 16
                    readUInt64(bytes, pos + 8)
                }
                else -> size32
            }
            if (boxSize < headerSize) break
            val boxEnd = (pos + boxSize.coerceAtMost((end - pos).toLong()).toInt()).coerceAtMost(end)

            val detail = when (type) {
                "ftyp" -> if (pos + 12 <= boxEnd) {
                    "Brand: ${String(bytes, pos + 8, 4, StandardCharsets.US_ASCII)}"
                } else ""
                "moov" -> "Movie Metadata Header"
                "udta" -> "User Data Box"
                "meta" -> "ISO Metadata Container"
                "mdat" -> "Stream Media Payload"
                "uuid" -> "UUID Extension Box (XMP/C2PA)"
                "jumb", "c2pa" -> "JUMBF / Content Credentials"
                else -> ""
            }

            out += ContainerStructureNode(
                type = type,
                sizeBytes = boxSize,
                offsetBytes = pos.toLong(),
                depth = depth,
                detail = detail
            )

            val childStart = when (type) {
                "meta" -> pos + headerSize + 4 // FullBox skips 4 bytes (version + flags)
                "jumb" -> pos + headerSize + 8 // skips jumb description/label prelude if aligned
                else -> pos + headerSize
            }
            if ((type in isoContainerBoxes || type == "meta") && childStart < boxEnd) {
                if (type == "meta" && pos + headerSize + 4 < boxEnd) {
                    walkIsoRange(bytes, pos + headerSize + 4, boxEnd, depth + 1, maxNodes, out)
                } else if (type != "meta") {
                    walkIsoRange(bytes, pos + headerSize, boxEnd, depth + 1, maxNodes, out)
                }
            }

            if (boxEnd <= pos) break
            pos = boxEnd
        }
    }

    fun inspectJpegSegments(bytes: ByteArray, maxNodes: Int = 60): List<ContainerStructureNode> {
        if (bytes.size < 4 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) {
            return emptyList()
        }
        val nodes = mutableListOf<ContainerStructureNode>()
        nodes += ContainerStructureNode("SOI", 2, 0, 0, "Start of Image (JPEG)")
        var pos = 2
        while (pos + 4 <= bytes.size && nodes.size < maxNodes) {
            if (bytes[pos] != 0xFF.toByte()) break
            while (pos < bytes.size && bytes[pos] == 0xFF.toByte()) pos++
            if (pos >= bytes.size) break
            val marker = bytes[pos].toInt() and 0xFF
            pos++
            if (marker == 0xD9) {
                nodes += ContainerStructureNode("EOI", 2, (pos - 2).toLong(), 0, "End of Image")
                break
            }
            if (marker == 0x00 || marker in 0xD0..0xD7) continue
            if (pos + 2 > bytes.size) break
            val segLen = ((bytes[pos].toInt() and 0xFF) shl 8) or (bytes[pos + 1].toInt() and 0xFF)
            if (segLen < 2) break

            val markerName = when (marker) {
                0xE0 -> "APP0 (JFIF)"
                0xE1 -> {
                    val sig = if (pos + 8 <= bytes.size) String(bytes, pos + 2, 6, StandardCharsets.US_ASCII) else ""
                    when {
                        sig.startsWith("Exif") -> "APP1 (EXIF)"
                        sig.startsWith("http:") -> "APP1 (XMP)"
                        else -> "APP1"
                    }
                }
                0xE2 -> "APP2 (ICC Profile)"
                0xEB -> "APP11 (JUMBF / C2PA)"
                0xED -> "APP13 (IPTC / Photoshop)"
                0xDB -> "DQT (Tabel Kuantisasi)"
                0xC4 -> "DHT (Tabel Huffman)"
                0xC0 -> "SOF0 (Baseline DCT)"
                0xC2 -> "SOF2 (Progressive DCT)"
                0xDA -> "SOS (Start of Scan)"
                else -> "MARKER 0x%02X".format(Locale.US, marker)
            }

            val extra = when (marker) {
                0xDB -> {
                    val count64 = ((segLen - 2) / 65).coerceAtLeast(1)
                    "$count64 tabel kuantisasi"
                }
                0xC0, 0xC2 -> {
                    if (pos + 7 < bytes.size) {
                        val h = ((bytes[pos + 3].toInt() and 0xFF) shl 8) or (bytes[pos + 4].toInt() and 0xFF)
                        val w = ((bytes[pos + 5].toInt() and 0xFF) shl 8) or (bytes[pos + 6].toInt() and 0xFF)
                        "${w}x${h}px"
                    } else ""
                }
                else -> ""
            }

            nodes += ContainerStructureNode(
                type = markerName,
                sizeBytes = segLen.toLong(),
                offsetBytes = (pos - 2).toLong(),
                depth = 0,
                detail = extra
            )

            if (marker == 0xDA) break // Entropy-coded scan follows
            pos += segLen
        }
        return nodes
    }

    fun parseC2paClaims(bytes: ByteArray): C2paClaimDetails? {
        val text = String(bytes, StandardCharsets.ISO_8859_1)
        val hasC2paMarkers = text.contains("c2pa") || text.contains("jumb") || text.contains("c2ma")
        if (!hasC2paMarkers) return null

        val generator = listOf(
            "ChatGPT" to "OpenAI ChatGPT / GPT Image",
            "DALL-E" to "OpenAI DALL-E",
            "OpenAI" to "OpenAI",
            "Adobe Firefly" to "Adobe Firefly",
            "Firefly" to "Adobe Firefly",
            "Adobe Photoshop" to "Adobe Photoshop",
            "Made with Google AI" to "Google AI (Imagen/Veo)",
            "Google" to "Google LLC / Imagen / Veo",
            "Truepic" to "Truepic Lens",
            "ByteDance" to "ByteDance / TikTok AIGC",
            "Meta" to "Meta AI",
            "Leica" to "Leica Content Credentials",
            "Sony" to "Sony Camera Authenticity",
            "Midjourney" to "Midjourney",
            "Runway" to "Runway ML"
        ).firstOrNull { (k, _) -> text.contains(k, ignoreCase = true) }?.second

        val knownActions = listOf(
            "c2pa.created",
            "c2pa.edited",
            "c2pa.converted",
            "c2pa.cropped",
            "c2pa.resized",
            "c2pa.filtered",
            "c2pa.placed",
            "c2pa.published",
            "c2pa.opened",
            "c2pa.color_adjustments"
        ).filter { text.contains(it, ignoreCase = true) }

        val digitalSourceType = when {
            text.contains("compositeWithTrainedAlgorithmicMedia", ignoreCase = true) ->
                "compositeWithTrainedAlgorithmicMedia (Komposit AI)"
            text.contains("trainedAlgorithmicMedia", ignoreCase = true) ->
                "trainedAlgorithmicMedia (Sepenuhnya AI Generatif)"
            text.contains("digitalCapture", ignoreCase = true) ->
                "digitalCapture (Tangkapan Kamera Fisik)"
            text.contains("screenCapture", ignoreCase = true) ->
                "screenCapture (Tangkapan Layar)"
            else -> null
        }

        val assertionLabels = listOf(
            "c2pa.actions",
            "c2pa.actions.v2",
            "c2pa.hash.data",
            "c2pa.hash.bmff",
            "c2pa.ingredient",
            "stds.exif",
            "stds.iptc",
            "stds.schema-org.CreativeWork",
            "c2pa.thumbnail.claim"
        ).filter { text.contains(it, ignoreCase = true) }

        val signerIssuer = listOf(
            "OpenAI", "Adobe Inc.", "Adobe", "Google LLC", "Truepic", "Microsoft", "Digicert", "GlobalSign", "Leica", "Sony"
        ).firstOrNull { issuer ->
            val idx = text.indexOf("c2pa", ignoreCase = true).coerceAtLeast(0)
            val region = text.substring(maxOf(0, idx - 4096), minOf(text.length, idx + 32768))
            region.contains(issuer, ignoreCase = true)
        }

        if (generator == null && knownActions.isEmpty() && digitalSourceType == null && assertionLabels.isEmpty()) {
            return null
        }

        return C2paClaimDetails(
            claimGenerator = generator,
            actions = knownActions,
            digitalSourceType = digitalSourceType,
            signerIssuer = signerIssuer,
            assertionLabels = assertionLabels
        )
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Long {
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }

    private fun readUInt64(bytes: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }
        return result
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
}
