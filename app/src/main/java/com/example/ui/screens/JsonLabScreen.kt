package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.forensic.CorrelationReport
import com.example.jsonlab.InteractionActionType
import com.example.ui.components.ForensicCard
import com.example.ui.theme.SignalTheme
import com.example.ui.viewmodel.JsonLabUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JsonLabScreen(
    uiState: JsonLabUiState,
    activeReport: CorrelationReport?,
    onRawJsonInputChange: (String) -> Unit,
    onAnalyzePastedJson: () -> Unit,
    onAnalyzeActiveReport: (CorrelationReport) -> Unit,
    onExportReportUri: (android.net.Uri, CorrelationReport) -> Unit,
    onImportJsonUri: (android.net.Uri) -> Unit,
    onFilterSelected: (InteractionActionType?) -> Unit,
    onInspectRecordUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    var showRawJson by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && activeReport != null) {
            onExportReportUri(uri, activeReport)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportJsonUri(uri)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "JSON Forensics Lab",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Ekspor laporan korelasi ke file .json secara transparan atau analisis dataset JSON (Like, Komentar, Share, Akun, & Tujuan Share).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Action Controls
            item {
                ForensicCard {
                    Text(
                        text = "Sumber & Ekspor Dataset JSON",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("import_json_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.emerald,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Impor File .JSON")
                        }

                        if (activeReport != null) {
                            OutlinedButton(
                                onClick = { onAnalyzeActiveReport(activeReport) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("analyze_active_report_json_button")
                            ) {
                                Icon(Icons.Default.DataObject, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Pratinjau JSON Kasus")
                            }

                            OutlinedButton(
                                onClick = {
                                    exportLauncher.launch("signaltrace_${activeReport.reportId}.json")
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("export_saf_json_button"),
                                border = BorderStroke(1.dp, colors.cyan),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.cyan)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Simpan .JSON ke File")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = uiState.rawJsonInput,
                        onValueChange = onRawJsonInputChange,
                        label = { Text("Atau tempel payload JSON arsip aktivitas secara langsung") },
                        minLines = 3,
                        maxLines = 6,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_json_input")
                    )

                    OutlinedButton(
                        onClick = onAnalyzePastedJson,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("analyze_manual_json_button")
                    ) {
                        Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Analisis JSON Tempelan")
                    }

                    uiState.errorMessage?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.coral
                        )
                    }
                    uiState.statusMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.emerald
                        )
                    }
                }
            }

            val summary = uiState.activeSummary
            if (summary == null) {
                item {
                    ForensicCard {
                        Text(
                            text = "Belum Ada Dataset JSON Dimuat",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.cyan
                        )
                        Text(
                            text = "Impor file .json ekspor data resmi platform, tempel teks JSON langsung di atas, atau pratinjau JSON dari kasus korelasi aktif.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Summary Metrics
                item {
                    ForensicCard(borderColor = colors.cyan.copy(alpha = 0.45f)) {
                        Text(
                            text = summary.sourceLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricPill("Total", summary.totalRecords.toString(), colors.cyan, Modifier.weight(1f))
                            MetricPill("Like", summary.likeCount.toString(), colors.emerald, Modifier.weight(1f))
                            MetricPill("Komen", summary.commentCount.toString(), colors.amber, Modifier.weight(1f))
                            MetricPill("Share", summary.shareCount.toString(), colors.threadsViolet, Modifier.weight(1f))
                        }
                        Text(
                            text = "Korelasi URL–Author Terverifikasi: ${summary.correlatedUrlPairsCount} entri • Platform: ${
                                summary.platformCounts.entries.joinToString { "${it.key} (${it.value})" }
                            }",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Actors & Share Destinations
                item {
                    ForensicCard {
                        Text(
                            text = "Akun Berinteraksi & Alur Tujuan Share",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (summary.topActors.isNotEmpty()) {
                            Text(
                                text = "Akun Paling Aktif:",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.emerald
                            )
                            summary.topActors.forEach { actor ->
                                Text(
                                    text = "• ${actor.account} — ${actor.count} aktivitas (${actor.actions.joinToString()})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (summary.topDestinations.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Jejak Tujuan Share:",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.cyan
                            )
                            summary.topDestinations.forEach { dest ->
                                Text(
                                    text = "• ${dest.destination} (${dest.count}x)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (summary.topReferralParams.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Parameter Pelacak / Referral Terbanyak:",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.amber
                            )
                            Text(
                                text = summary.topReferralParams.joinToString(" • ") { "${it.destination} (${it.count}x)" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (summary.topShortlinkHosts.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Domain / Shortlink Dominan:",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.threadsViolet
                            )
                            Text(
                                text = summary.topShortlinkHosts.joinToString(" • ") { "${it.destination} (${it.count}x)" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Filter & Raw JSON Toggle
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filters = listOf(
                            null to "Semua",
                            InteractionActionType.LIKE to "Like",
                            InteractionActionType.COMMENT to "Komentar",
                            InteractionActionType.SHARE to "Share"
                        )
                        filters.forEach { (type, label) ->
                            FilterChip(
                                selected = uiState.selectedActionFilter == type,
                                onClick = { onFilterSelected(type) },
                                label = { Text(label) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                        FilterChip(
                            selected = showRawJson,
                            onClick = { showRawJson = !showRawJson },
                            leadingIcon = {
                                Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(if (showRawJson) "Sembunyikan JSON" else "Lihat Raw JSON") },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("toggle_raw_json_chip")
                        )
                    }
                }

                if (showRawJson) {
                    item {
                        ForensicCard(borderColor = colors.borderSubtle) {
                            Text(
                                text = "Inspektor Payload JSON",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.cyan
                            )
                            Text(
                                text = summary.prettyJson,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                val filteredRecords = summary.records.filter {
                    uiState.selectedActionFilter == null || it.actionType == uiState.selectedActionFilter
                }

                items(filteredRecords, key = { "${it.id}_${it.timestamp}" }) { rec ->
                    ForensicCard(
                        modifier = if (rec.postUrl.isNotBlank()) {
                            Modifier.clickable { onInspectRecordUrl(rec.postUrl) }
                        } else {
                            Modifier
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${rec.platform} • ${rec.actionType.label}",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.emerald
                            )
                            Text(
                                text = rec.timestamp,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Aktor: ${rec.actorAccount}  →  Pembuat: ${rec.targetAuthor}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (rec.postUrl.isNotBlank()) {
                            Text(
                                text = rec.postUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.cyan
                            )
                            Text(
                                text = "Ketuk kartu untuk periksa di URLMetadataDecoder & Korelasi →",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.emerald
                            )
                        }
                        rec.sharedDestination?.let { dest ->
                            Text(
                                text = "Dishare ke: $dest",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.threadsViolet
                            )
                        }
                        rec.commentSnippet?.let { note ->
                            Text(
                                text = "Catatan: \"$note\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clip(RoundedCornerShape(12.dp)),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
