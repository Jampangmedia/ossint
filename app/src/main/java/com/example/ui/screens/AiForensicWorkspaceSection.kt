package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.aiforensic.AiForensicReport
import com.example.aiforensic.ExtractedAiPrompt
import com.example.aiforensic.PromptAccuracyStatus
import com.example.forensic.CorrelationReport
import com.example.ui.components.CopyableMonoRow
import com.example.ui.components.ForensicCard
import com.example.ui.theme.SignalTheme
import com.example.ui.viewmodel.AiForensicUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiForensicWorkspaceSection(
    aiState: AiForensicUiState,
    activeReport: CorrelationReport?,
    isTracingUrl: Boolean,
    onTriggerUrlTrace: () -> Unit,
    onInspectFileUri: (Uri) -> Unit,
    onManualInputChange: (String) -> Unit,
    onAnalyzeManualInput: () -> Unit
) {
    val colors = SignalTheme.colors

    val safDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onInspectFileUri(uri)
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onInspectFileUri(uri)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Guardrail & Mathematical Boundary Card
        ForensicCard(borderColor = colors.cyan.copy(alpha = 0.5f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = "Forensik Sinyal AI", tint = colors.cyan)
                Text(
                    text = "Forensik Sinyal AI & Pembaca Prompt Eksak",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Akurasi 100% hanya tercapai jika metadata/chunk prompt asli (PNG tEXt/iTXt/zTXt A1111, ComfyUI JSON, NovelAI, InvokeAI, EXIF UserComment, XMP, atau caption eksplisit) masih utuh. Bila server media sosial telah menghapus metadata dan hanya menyisakan label AIGC/IPTC/C2PA, aplikasi melaporkan sinyal AI secara jujur tanpa menebak isi prompt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 1. Raw Media File Provenance & Prompt Extractor (Zero-Compression SAF + Photo Picker)
        ForensicCard(borderColor = colors.emerald.copy(alpha = 0.5f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.FileOpen, contentDescription = null, tint = colors.emerald)
                Text(
                    text = "1. Ekstraktor Biner File Media (PNG / JPG / WebP / MP4)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Gunakan 'Pilih File Asli (SAF)' untuk membaca stream byte mentah tanpa re-kompresi Galeri (menjaga chunk PNG A1111/ComfyUI, IPTC trainedAlgorithmicMedia, dan box C2PA JUMBF).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { safDocumentPicker.launch(arrayOf("image/*", "video/*")) },
                    enabled = !aiState.isInspectingFile,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ai_pick_raw_file_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.emerald,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (aiState.isInspectingFile) "Memindai..." else "File Asli (SAF)")
                }

                OutlinedButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !aiState.isInspectingFile,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ai_pick_gallery_button")
                ) {
                    Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Photo Picker")
                }
            }

            if (aiState.isInspectingFile) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Text(
                        text = "Membaca chunk PNG, XMP, EXIF, dan struktur C2PA...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            aiState.fileAiError?.let { err ->
                Text(text = err, style = MaterialTheme.typography.bodySmall, color = colors.coral)
            }

            aiState.fileAiReport?.let { fileReport ->
                AiForensicReportDetailView(report = fileReport)
            }
        }

        // 2. Social URL & Platform AIGC Signal Inspector
        ForensicCard(borderColor = colors.threadsViolet.copy(alpha = 0.5f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Public, contentDescription = null, tint = colors.threadsViolet)
                Text(
                    text = "2. Pelacak Sinyal AI dari URL & HTML Publik",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Memeriksa flag AIGC TikTok (isAigc/aigcInfo), disclosure YouTube (alteredContent/isSynthetic), label Made with AI Meta, serta signature tool/prompt di og:description.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onTriggerUrlTrace,
                enabled = !isTracingUrl && activeReport != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("ai_trace_url_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.threadsViolet,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        isTracingUrl -> "Melacak Sinyal HTML/JSON..."
                        activeReport == null -> "Analisis URL di Tab Korelasi Dulu"
                        else -> "Lacak Sinyal AI pada URL Aktif"
                    }
                )
            }

            aiState.urlAiReport?.let { urlReport ->
                AiForensicReportDetailView(report = urlReport)
            }
        }

        // 3. Manual Text / A1111 / ComfyUI / Midjourney Metadata Parser
        ForensicCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Code, contentDescription = null, tint = colors.amber)
                Text(
                    text = "3. Dekoder Teks / JSON / Metadata AI Manual",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            OutlinedTextField(
                value = aiState.manualMetadataInput,
                onValueChange = onManualInputChange,
                label = { Text("Tempel blok parameters A1111, JSON ComfyUI, atau Prompt/Caption") },
                placeholder = { Text("Contoh: cinematic street Jakarta --ar 16:9 --v 6.1 atau Steps: 30, Sampler: Euler a...") },
                minLines = 3,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_manual_metadata_input")
            )
            OutlinedButton(
                onClick = onAnalyzeManualInput,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("ai_analyze_manual_button")
            ) {
                Text("Dekode Sinyal & Prompt Teks")
            }

            aiState.manualAiReport?.let { manualRep ->
                AiForensicReportDetailView(report = manualRep)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiForensicReportDetailView(report: AiForensicReport) {
    val colors = SignalTheme.colors
    val statusColor = when (report.accuracyStatus) {
        PromptAccuracyStatus.EXACT_EMBEDDED_100_PERCENT -> colors.emerald
        PromptAccuracyStatus.PLATFORM_AI_SIGNAL_ONLY -> colors.amber
        PromptAccuracyStatus.NO_VERIFIED_AI_SIGNAL -> colors.textMuted
    }
    val statusIcon = when (report.accuracyStatus) {
        PromptAccuracyStatus.EXACT_EMBEDDED_100_PERCENT -> Icons.Default.CheckCircle
        PromptAccuracyStatus.PLATFORM_AI_SIGNAL_ONLY -> Icons.Default.Warning
        PromptAccuracyStatus.NO_VERIFIED_AI_SIGNAL -> Icons.Default.Info
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = statusColor.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                Text(
                    text = report.accuracyStatus.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor
                )
            }
        }

        Text(
            text = buildString {
                append("Sumber: ${report.targetLabel}")
                if (!report.binaryContainerFormat.isNullOrBlank()) {
                    append(" • Format: ${report.binaryContainerFormat}")
                }
                if (!report.primaryToolOrPlatform.isNullOrBlank()) {
                    append(" • Generator/Sinyal: ${report.primaryToolOrPlatform}")
                }
                if (report.fileSizeBytes != null && report.fileSizeBytes > 0L) {
                    append(" • Ukuran: ${report.fileSizeBytes} byte")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (!report.sha256Hex.isNullOrBlank()) {
            CopyableMonoRow(
                label = "SHA-256 Chain of Custody (Sidik Jari Berkas)",
                value = report.sha256Hex
            )
        }

        if (report.rawChunkKeys.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                report.rawChunkKeys.take(12).forEach { key ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, colors.borderSubtle)
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.cyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        report.c2paClaims?.let { c2pa ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Detail Klaim C2PA / Content Credentials",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.emerald
                )
                c2pa.claimGenerator?.let {
                    Text("Claim Generator: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
                c2pa.signerIssuer?.let {
                    Text("Penandatangan / Issuer: $it", style = MaterialTheme.typography.bodySmall, color = colors.cyan)
                }
                c2pa.digitalSourceType?.let {
                    Text("IPTC DigitalSourceType: $it", style = MaterialTheme.typography.bodySmall, color = colors.amber)
                }
                if (c2pa.actions.isNotEmpty()) {
                    Text("Aksi C2PA: ${c2pa.actions.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (c2pa.assertionLabels.isNotEmpty()) {
                    Text("Assertion JUMBF: ${c2pa.assertionLabels.joinToString(", ")}", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                }
            }
        }

        if (report.containerNodes.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Inspektor Struktur Biner (${report.containerNodes.size} node):",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.cyan
                )
                report.containerNodes.take(18).forEach { node ->
                    val indent = "  ".repeat(node.depth)
                    val detailSuffix = if (node.detail.isNotBlank()) " • ${node.detail}" else ""
                    Text(
                        text = "$indent[${node.type}] ${node.sizeBytes} B @ 0x${node.offsetBytes.toString(16)}$detailSuffix",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        report.extractedPrompt?.let { prompt ->
            ExtractedPromptDetailBlock(prompt = prompt)
        }

        if (report.signals.isNotEmpty()) {
            Text(
                text = "Jejak Sinyal AI Terdeteksi (${report.signals.size}):",
                style = MaterialTheme.typography.labelMedium,
                color = colors.cyan
            )
            report.signals.forEach { sig ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "${sig.sourceLayer} → ${sig.signalName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (sig.isCryptographicOrStandard) colors.emerald else colors.amber
                    )
                    if (!sig.detectedToolOrModel.isNullOrBlank()) {
                        Text(
                            text = "Tool/Standar: ${sig.detectedToolOrModel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = sig.evidenceSnippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Text(
            text = report.honestyLimitationNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtractedPromptDetailBlock(prompt: ExtractedAiPrompt) {
    val colors = SignalTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Prompt Asli Terbaca (${prompt.generatorFormat})",
            style = MaterialTheme.typography.titleSmall,
            color = colors.emerald
        )

        CopyableMonoRow(
            label = "Positive Prompt",
            value = prompt.positivePrompt
        )

        if (!prompt.negativePrompt.isNullOrBlank()) {
            CopyableMonoRow(
                label = "Negative Prompt",
                value = prompt.negativePrompt
            )
        }

        val specs = listOfNotNull(
            prompt.modelName?.let { "Model" to it },
            prompt.sampler?.let { "Sampler" to it },
            prompt.steps?.let { "Steps" to it },
            prompt.cfgScale?.let { "CFG" to it },
            prompt.seed?.let { "Seed" to it }
        ) + prompt.extraParams.entries.map { it.key to it.value }

        if (specs.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                specs.forEach { (k, v) ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = colors.cyanContainer,
                        border = BorderStroke(1.dp, colors.cyan.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "$k: $v",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.cyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
