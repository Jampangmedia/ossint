package com.example.forensic

import com.example.aiforensic.AiForensicReport
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

object ForensicReportExporter {

    fun buildHtmlReport(
        report: CorrelationReport,
        decodedMetadata: DecodedUrlMetadata?,
        fileAiReport: AiForensicReport?,
        urlAiReport: AiForensicReport?
    ): String {
        fun esc(s: String?): String = (s ?: "-")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        val rdap = report.networkTrace?.rdapInfo
        val geo = report.geoIntEvidence
        val activeAi = fileAiReport ?: urlAiReport

        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"id\"><head><meta charset=\"UTF-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\">")
            appendLine("<title>Laporan Forensik SignalTrace #${esc(report.reportId)}</title>")
            appendLine("<style>")
            appendLine("body{font-family:system-ui,-apple-system,sans-serif;background:#0d131a;color:#e6edf3;margin:0;padding:24px;line-height:1.5;}")
            appendLine(".wrap{max-width:860px;margin:0 auto;} .card{background:#151e29;border:1px solid #263547;border-radius:12px;padding:18px;margin-bottom:16px;}")
            appendLine("h1{color:#38bdf8;margin:0 0 4px;} h2{color:#34d399;font-size:1.05rem;margin-top:0;border-bottom:1px solid #263547;padding-bottom:6px;}")
            appendLine("code,pre{font-family:ui-monospace,SFMono-Regular,monospace;background:#0b1016;padding:3px 6px;border-radius:4px;word-break:break-all;color:#7dd3fc;}")
            appendLine(".badge{display:inline-block;padding:3px 10px;border-radius:999px;font-size:0.8rem;font-weight:600;background:#1e293b;color:#34d399;border:1px solid #34d399;}")
            appendLine(".warn{color:#fbbf24;} table{width:100%;border-collapse:collapse;} td{padding:6px 4px;border-bottom:1px solid #1e293b;vertical-align:top;}")
            appendLine("td:first-child{width:220px;color:#94a3b8;font-weight:600;}")
            appendLine("</style></head><body><div class=\"wrap\">")

            appendLine("<div class=\"card\">")
            appendLine("<span class=\"badge\">SANDBOX COMPLIANT • TANPA HOOKING</span>")
            appendLine("<h1>Laporan Audit Forensik #${esc(report.reportId)}</h1>")
            appendLine("<p>Waktu Analisis: <code>${esc(report.analyzedAtFormatted)}</code></p>")
            appendLine("<p>Skor Korelasi: <strong>${report.scorePercent}%</strong> — ${esc(report.confidenceLabel)}</p>")
            appendLine("<p>${esc(report.summaryNote)}</p>")
            appendLine("</div>")

            if (decodedMetadata != null) {
                appendLine("<div class=\"card\"><h2>1. URLMetadataDecoder</h2><table>")
                appendLine("<tr><td>Platform / Jenis</td><td>${esc(decodedMetadata.platform.displayName)} (${esc(decodedMetadata.urlKind.label)})</td></tr>")
                appendLine("<tr><td>user_id</td><td><code>${esc(decodedMetadata.userId)}</code></td></tr>")
                appendLine("<tr><td>post_id</td><td><code>${esc(decodedMetadata.postId)}</code></td></tr>")
                appendLine("<tr><td>Canonical Owner URL</td><td><code>${esc(decodedMetadata.canonicalOwnerUrl)}</code></td></tr>")
                appendLine("<tr><td>Resolusi Pemilik</td><td>${esc(decodedMetadata.ownerResolutionMethod)}</td></tr>")
                appendLine("<tr><td>Waktu Terdekode (WIB)</td><td><code>${esc(decodedMetadata.decodedTimestampWib)}</code></td></tr>")
                appendLine("<tr><td>Referral Tracking Params</td><td><code>${esc(decodedMetadata.referralTrackingParams.entries.joinToString { "${it.key}=${it.value}" })}</code></td></tr>")
                appendLine("</table></div>")
            }

            appendLine("<div class=\"card\"><h2>2. Evaluasi Bukti Korelasi</h2><ul>")
            report.evidences.forEach { ev ->
                val mark = if (ev.matched) "✔" else "✖"
                appendLine("<li><strong>$mark ${esc(ev.title)}</strong> (+${if (ev.matched) ev.weight else 0}/${ev.weight}): ${esc(ev.detail)}</li>")
            }
            appendLine("</ul></div>")

            report.networkTrace?.let { net ->
                appendLine("<div class=\"card\"><h2>3. Jejak Redirect, CDN & RDAP/ASN Publik</h2><table>")
                appendLine("<tr><td>Edge Host</td><td><code>${esc(net.edgeServerHost)}</code></td></tr>")
                appendLine("<tr><td>Edge IPs</td><td><code>${esc(net.edgeServerIps.joinToString(", "))}</code></td></tr>")
                if (rdap != null) {
                    appendLine("<tr><td>Organisasi ASN (RDAP)</td><td>${esc(rdap.organizationName ?: rdap.networkName)} (${esc(rdap.countryCode)})</td></tr>")
                    appendLine("<tr><td>Blok CIDR / Handle</td><td><code>${esc(rdap.cidr)}</code> • <code>${esc(rdap.handle)}</code></td></tr>")
                }
                appendLine("<tr><td>Catatan Privasi IP</td><td class=\"warn\">IP di atas adalah IP node CDN/Edge platform, BUKAN IP perangkat pengunggah.</td></tr>")
                appendLine("</table></div>")
            }

            if (geo != null) {
                appendLine("<div class=\"card\"><h2>4. Bukti GEOINT, Surya NOAA & Visual ELA</h2><table>")
                appendLine("<tr><td>Lokasi Referensi Surya</td><td>${esc(geo.solarCityOrCoords)}</td></tr>")
                appendLine("<tr><td>Azimuth / Elevasi / Bayangan</td><td>Az ${geo.solarAzimuthDeg ?: "-"}° • El ${geo.solarElevationDeg ?: "-"}° • Bayangan ${geo.shadowDirectionDeg ?: "-"}°</td></tr>")
                appendLine("<tr><td>Luminans Frame</td><td>${esc(geo.imageLightingSummary)}</td></tr>")
                appendLine("<tr><td>SHA-256 Gambar GEOINT</td><td><code>${esc(geo.mediaSha256)}</code></td></tr>")
                appendLine("<tr><td>Ringkasan ELA</td><td>${esc(geo.elaSummary)}</td></tr>")
                appendLine("<tr><td>Catatan Analis</td><td>Landmark: ${esc(geo.analystLandmarkNote)} | Pelat: ${esc(geo.analystPlateOrSignNote)} | Dialek: ${esc(geo.analystAudioDialectNote)}</td></tr>")
                appendLine("</table></div>")
            }

            if (activeAi != null) {
                appendLine("<div class=\"card\"><h2>5. Forensik Sinyal AI, SHA-256 & C2PA</h2><table>")
                appendLine("<tr><td>Status Akurasi Prompt</td><td><strong>${esc(activeAi.accuracyStatus.label)}</strong></td></tr>")
                appendLine("<tr><td>SHA-256 Berkas Media</td><td><code>${esc(activeAi.sha256Hex)}</code> (${activeAi.fileSizeBytes ?: 0} byte)</td></tr>")
                appendLine("<tr><td>Generator / Sinyal</td><td>${esc(activeAi.primaryToolOrPlatform)} (${esc(activeAi.binaryContainerFormat)})</td></tr>")
                activeAi.c2paClaims?.let { c2pa ->
                    appendLine("<tr><td>C2PA Claim Generator</td><td>${esc(c2pa.claimGenerator)} • Issuer: ${esc(c2pa.signerIssuer)}</td></tr>")
                    appendLine("<tr><td>C2PA Actions</td><td><code>${esc(c2pa.actions.joinToString(", "))}</code></td></tr>")
                }
                activeAi.extractedPrompt?.let { p ->
                    appendLine("<tr><td>Positive Prompt</td><td><pre>${esc(p.positivePrompt)}</pre></td></tr>")
                    if (!p.negativePrompt.isNullOrBlank()) {
                        appendLine("<tr><td>Negative Prompt</td><td><pre>${esc(p.negativePrompt)}</pre></td></tr>")
                    }
                }
                appendLine("<tr><td>Transparansi</td><td class=\"warn\">${esc(activeAi.honestyLimitationNote)}</td></tr>")
                appendLine("</table></div>")
            }

            appendLine("</div></body></html>")
        }
    }

    private data class PdfTextStyle(
        val fontKey: String,
        val sizePt: Float,
        val rgb: String,
        val lineHeight: Float,
        val maxCharsPerLine: Int
    )

    fun writePdfReport(
        outputStream: OutputStream,
        report: CorrelationReport,
        decodedMetadata: DecodedUrlMetadata?,
        fileAiReport: AiForensicReport?,
        urlAiReport: AiForensicReport?
    ) {
        val pageWidthPt = 595
        val pageHeightPt = 842
        val marginPt = 42f
        val topY = pageHeightPt - 48f
        val minY = 48f

        val titleStyle = PdfTextStyle("F2", 15f, "0.05 0.29 0.43", 20f, 64)
        val sectionStyle = PdfTextStyle("F2", 11.5f, "0.02 0.37 0.27", 15f, 76)
        val bodyStyle = PdfTextStyle("F1", 9.5f, "0.12 0.16 0.23", 13f, 94)
        val monoStyle = PdfTextStyle("F3", 8.5f, "0.06 0.09 0.16", 12f, 92)

        val pages = mutableListOf<StringBuilder>()
        var currentStream = StringBuilder()
        var cursorY = topY

        fun startNewPage() {
            if (currentStream.isNotEmpty()) {
                pages += currentStream
            }
            currentStream = StringBuilder()
            cursorY = topY
        }

        fun ensureSpace(neededHeight: Float) {
            if (cursorY - neededHeight < minY) {
                startNewPage()
            }
        }

        fun appendWrapped(rawText: String, style: PdfTextStyle) {
            val clean = toPdfWinAnsi(rawText)
            val lines = wrapText(clean, style.maxCharsPerLine)
            for (line in lines) {
                ensureSpace(style.lineHeight)
                currentStream.append("BT\n")
                    .append("/${style.fontKey} ${style.sizePt} Tf\n")
                    .append("${style.rgb} rg\n")
                    .append(String.format(Locale.US, "1 0 0 1 %.1f %.1f Tm\n", marginPt, cursorY))
                    .append("(${escapePdfLiteral(line)}) Tj\n")
                    .append("ET\n")
                cursorY -= style.lineHeight
            }
        }

        fun appendSectionHeader(title: String) {
            cursorY -= 6f
            ensureSpace(22f)
            appendWrapped(title, sectionStyle)
            val ruleY = cursorY + 3f
            currentStream.append(
                String.format(
                    Locale.US,
                    "0.78 0.84 0.90 RG 0.7 w %.1f %.1f m %.1f %.1f l S\n",
                    marginPt, ruleY, pageWidthPt - marginPt, ruleY
                )
            )
            cursorY -= 4f
        }

        appendWrapped("LAPORAN FORENSIK SIGNALTRACE #${report.reportId}", titleStyle)
        appendWrapped("Mode: Sandbox Compliant (Tanpa Hooking) | Waktu: ${report.analyzedAtFormatted}", bodyStyle)
        appendWrapped("Skor Korelasi: ${report.scorePercent}% (${report.confidenceLabel})", bodyStyle)
        appendWrapped(report.summaryNote, bodyStyle)

        if (decodedMetadata != null) {
            appendSectionHeader("1. Dekomposisi URLMetadataDecoder")
            appendWrapped("Platform: ${decodedMetadata.platform.displayName} (${decodedMetadata.urlKind.label})", bodyStyle)
            appendWrapped("user_id: ${decodedMetadata.userId ?: "-"} | post_id: ${decodedMetadata.postId ?: "-"}", monoStyle)
            appendWrapped("Canonical Owner: ${decodedMetadata.canonicalOwnerUrl ?: "-"} (${decodedMetadata.ownerResolutionMethod})", monoStyle)
            if (decodedMetadata.referralTrackingParams.isNotEmpty()) {
                appendWrapped("Referral Params: ${decodedMetadata.referralTrackingParams}", monoStyle)
            }
        }

        appendSectionHeader("2. Rincian Bukti Korelasi")
        report.evidences.forEach { ev ->
            val mark = if (ev.matched) "[COCOK]" else "[TIDAK]"
            appendWrapped("$mark ${ev.title} (+${if (ev.matched) ev.weight else 0}/${ev.weight}): ${ev.detail}", bodyStyle)
        }

        report.networkTrace?.let { net ->
            appendSectionHeader("3. Jejak Redirect, Server CDN & RDAP/ASN")
            appendWrapped("Edge Host: ${net.edgeServerHost.ifBlank { "-" }} | IP: ${net.edgeServerIps.joinToString(", ").ifBlank { "-" }}", monoStyle)
            net.rdapInfo?.let { r ->
                appendWrapped("RDAP ASN/Org: ${r.organizationName ?: r.networkName ?: "-"} | Negara: ${r.countryCode ?: "-"} | CIDR: ${r.cidr ?: "-"}", monoStyle)
            }
            appendWrapped("Catatan: IP hasil resolusi adalah IP node CDN/Edge, bukan IP pengunggah.", bodyStyle)
        }

        report.geoIntEvidence?.let { geo ->
            appendSectionHeader("4. Analisis GEOINT, Surya NOAA & Visual ELA")
            appendWrapped("Referensi Surya: ${geo.solarCityOrCoords ?: "-"} | El: ${geo.solarElevationDeg ?: "-"}° | Az: ${geo.solarAzimuthDeg ?: "-"}° | Bayangan: ${geo.shadowDirectionDeg ?: "-"}°", bodyStyle)
            if (!geo.mediaSha256.isNullOrBlank()) {
                appendWrapped("SHA-256 Frame: ${geo.mediaSha256}", monoStyle)
            }
            if (!geo.elaSummary.isNullOrBlank()) {
                appendWrapped("ELA: ${geo.elaSummary}", bodyStyle)
            }
        }

        val activeAi = fileAiReport ?: urlAiReport
        if (activeAi != null) {
            appendSectionHeader("5. Forensik Sinyal AI, SHA-256 & C2PA")
            appendWrapped("Status: ${activeAi.accuracyStatus.label}", bodyStyle)
            if (!activeAi.sha256Hex.isNullOrBlank()) {
                appendWrapped("SHA-256 Media: ${activeAi.sha256Hex} (${activeAi.fileSizeBytes ?: 0} byte)", monoStyle)
            }
            activeAi.c2paClaims?.let { c2pa ->
                appendWrapped("C2PA Generator: ${c2pa.claimGenerator ?: "-"} | Issuer: ${c2pa.signerIssuer ?: "-"} | Actions: ${c2pa.actions.joinToString(", ")}", monoStyle)
            }
            activeAi.extractedPrompt?.let { p ->
                appendWrapped("Positive Prompt: ${p.positivePrompt}", monoStyle)
                if (!p.negativePrompt.isNullOrBlank()) {
                    appendWrapped("Negative Prompt: ${p.negativePrompt}", monoStyle)
                }
            }
            appendWrapped(activeAi.honestyLimitationNote, bodyStyle)
        }

        if (currentStream.isNotEmpty() || pages.isEmpty()) {
            pages += currentStream
        }

        val pdfBytes = assemblePdf14Document(pages, pageWidthPt, pageHeightPt)
        outputStream.write(pdfBytes)
        outputStream.flush()
    }

    private fun assemblePdf14Document(
        pageStreams: List<StringBuilder>,
        pageWidthPt: Int,
        pageHeightPt: Int
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val offsets = mutableListOf<Int>()

        fun writeAscii(s: String) {
            out.write(s.toByteArray(StandardCharsets.ISO_8859_1))
        }

        fun beginObj(id: Int) {
            offsets += out.size()
            writeAscii("$id 0 obj\n")
        }

        writeAscii("%PDF-1.4\n")

        val pageCount = pageStreams.size
        val firstPageContentId = 6
        val pageObjIds = (0 until pageCount).map { firstPageContentId + it * 2 + 1 }

        // 1: Catalog
        beginObj(1)
        writeAscii("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        // 2: Pages root
        beginObj(2)
        val kidsStr = pageObjIds.joinToString(" ") { "$it 0 R" }
        writeAscii("<< /Type /Pages /Kids [ $kidsStr ] /Count $pageCount >>\nendobj\n")

        // 3..5: Standard Base14 Fonts
        beginObj(3)
        writeAscii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\nendobj\n")
        beginObj(4)
        writeAscii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>\nendobj\n")
        beginObj(5)
        writeAscii("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>\nendobj\n")

        // Page content streams + page objects
        for ((index, streamBuilder) in pageStreams.withIndex()) {
            val contentId = firstPageContentId + index * 2
            val pageId = contentId + 1
            val contentBytes = streamBuilder.toString().toByteArray(StandardCharsets.ISO_8859_1)

            beginObj(contentId)
            writeAscii("<< /Length ${contentBytes.size} >>\nstream\n")
            out.write(contentBytes)
            writeAscii("endstream\nendobj\n")

            beginObj(pageId)
            writeAscii(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageWidthPt $pageHeightPt] " +
                    "/Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >> >> " +
                    "/Contents $contentId 0 R >>\nendobj\n"
            )
        }

        val xrefOffset = out.size()
        val totalObjects = offsets.size + 1
        writeAscii("xref\n0 $totalObjects\n")
        writeAscii("0000000000 65535 f \n")
        for (offset in offsets) {
            writeAscii(String.format(Locale.US, "%010d 00000 n \n", offset))
        }
        writeAscii("trailer\n<< /Size $totalObjects /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        return out.toByteArray()
    }

    private fun wrapText(text: String, maxChars: Int): List<String> {
        val result = mutableListOf<String>()
        for (paragraph in text.split('\n')) {
            if (paragraph.isBlank()) {
                result += ""
                continue
            }
            var current = StringBuilder()
            for (word in paragraph.split(' ')) {
                if (word.length > maxChars) {
                    if (current.isNotEmpty()) {
                        result += current.toString()
                        current = StringBuilder()
                    }
                    var idx = 0
                    while (idx < word.length) {
                        val end = minOf(idx + maxChars, word.length)
                        result += word.substring(idx, end)
                        idx = end
                    }
                } else if (current.isEmpty()) {
                    current.append(word)
                } else if (current.length + 1 + word.length <= maxChars) {
                    current.append(' ').append(word)
                } else {
                    result += current.toString()
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) {
                result += current.toString()
            }
        }
        return result.ifEmpty { listOf("") }
    }

    private fun escapePdfLiteral(text: String): String {
        val sb = StringBuilder(text.length + 8)
        for (ch in text) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '(' -> sb.append("\\(")
                ')' -> sb.append("\\)")
                '\r', '\n' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun toPdfWinAnsi(text: String): String {
        val normalized = text
            .replace("✔", "[OK]")
            .replace("✖", "[X]")
            .replace("→", "->")
            .replace("•", "-")
            .replace("—", "-")
            .replace("–", "-")
            .replace("σ", "sigma")
            .replace("μ", "u")
        val sb = StringBuilder(normalized.length)
        for (ch in normalized) {
            val code = ch.code
            if (code == 0x0A || code in 0x20..0x7E || code in 0xA0..0xFF) {
                sb.append(ch)
            } else {
                sb.append('?')
            }
        }
        return sb.toString()
    }
}

