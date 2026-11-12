package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.forensic.CorrelationReport
import com.example.forensic.DecodedUrlMetadata
import com.example.ui.components.CorrelationScoreGauge
import com.example.ui.components.ForensicCard
import com.example.ui.components.PlatformBadge
import com.example.ui.components.UrlMetadataDecoderCard
import com.example.ui.theme.SignalTheme
import com.example.ui.viewmodel.AnalysisUiState
import com.example.ui.viewmodel.CorrelatorFormState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CorrelatorScreen(
    formState: CorrelatorFormState,
    onProfileUrlChange: (String) -> Unit,
    onPostUrlChange: (String) -> Unit,
    onAnalyzeOffline: () -> Unit,
    onTraceNetwork: () -> Unit,
    onApplyOwnerAsProfile: (String) -> Unit,
    onSaveVault: () -> Unit,
    onExportJson: (CorrelationReport) -> Unit,
    onExportPdfUri: (Uri) -> Unit,
    onExportHtmlUri: (Uri) -> Unit,
    onOpenTraceTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val clipboard = LocalClipboardManager.current

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
            // Hero Forensic Banner
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_forensic_banner),
                        contentDescription = "Ilustrasi sinyal korelasi URL forensik",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.45f
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.25f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.emerald.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, colors.emerald.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "SANDBOX COMPLIANT • TANPA HOOKING",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.emerald,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = "Forensik Korelasi URL",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Dekoder ID 64-bit, pembersih token share, & verifikasi kecocokan Profil–Postingan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Dual URL Inputs
            item {
                ForensicCard {
                    Text(
                        text = "Input Pasangan Tautan (TikTok / FB / IG / Threads / YT)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = formState.profileUrlInput,
                        onValueChange = onProfileUrlChange,
                        label = { Text("URL Profil Target (Opsional)") },
                        placeholder = { Text("Tempel tautan profil atau isi otomatis via Lacak Pemilik") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.PersonSearch,
                                contentDescription = "Ikon URL Profil",
                                tint = colors.cyan
                            )
                        },
                        trailingIcon = {
                            if (formState.profileUrlInput.isNotEmpty()) {
                                IconButton(
                                    onClick = { onProfileUrlChange("") },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Hapus URL Profil")
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let {
                                            onProfileUrlChange(it)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("paste_profile_url_button")
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Tempel URL Profil")
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.cyan,
                            unfocusedBorderColor = colors.borderSubtle
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_url_input")
                    )

                    OutlinedTextField(
                        value = formState.postUrlInput,
                        onValueChange = onPostUrlChange,
                        label = { Text("URL Postingan / Video / Reel") },
                        placeholder = { Text("Tempel tautan postingan atau bagikan dari Share Sheet") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Ikon URL Postingan",
                                tint = colors.emerald
                            )
                        },
                        trailingIcon = {
                            if (formState.postUrlInput.isNotEmpty()) {
                                IconButton(
                                    onClick = { onPostUrlChange("") },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Hapus URL Postingan")
                                }
                            } else {
                                IconButton(
                                    onClick = {
                                        clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let {
                                            onPostUrlChange(it)
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("paste_post_url_button")
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Tempel URL Postingan")
                                }
                            }
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.emerald,
                            unfocusedBorderColor = colors.borderSubtle
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("post_url_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onAnalyzeOffline,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("analyze_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.emerald,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Analisis Korelasi")
                        }

                        OutlinedButton(
                            onClick = onTraceNetwork,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("trace_network_button"),
                            border = BorderStroke(1.dp, colors.cyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.cyan)
                        ) {
                            Text("Lacak CDN & Hop")
                        }
                    }
                }
            }

            // Result / Tracing / Error Display
            item {
                when (val state = formState.analysisState) {
                    is AnalysisUiState.Idle -> {
                        ForensicCard(borderColor = colors.borderSubtle) {
                            Text(
                                text = "Siap Menginspeksi",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.cyan
                            )
                            Text(
                                text = "Tempel tautan TikTok, Facebook, Instagram, Threads, atau YouTube—atau bagikan tautan langsung dari Share Sheet Android ke SignalTrace.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is AnalysisUiState.Tracing -> {
                        ForensicCard(borderColor = colors.cyan) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = colors.cyan,
                                    strokeWidth = 3.dp
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    is AnalysisUiState.Error -> {
                        ForensicCard(borderColor = colors.coral) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Peringatan",
                                    tint = colors.coral
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.coral
                                )
                            }
                        }
                    }

                    is AnalysisUiState.Ready -> {
                        AnimatedVisibility(visible = true) {
                            CorrelationResultSection(
                                report = state.report,
                                decodedMetadata = formState.decodedPostMetadata,
                                onApplyOwnerAsProfile = onApplyOwnerAsProfile,
                                onSaveVault = onSaveVault,
                                onExportJson = { onExportJson(state.report) },
                                onExportPdfUri = onExportPdfUri,
                                onExportHtmlUri = onExportHtmlUri,
                                onOpenTraceTab = onOpenTraceTab
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CorrelationResultSection(
    report: CorrelationReport,
    decodedMetadata: DecodedUrlMetadata?,
    onApplyOwnerAsProfile: (String) -> Unit,
    onSaveVault: () -> Unit,
    onExportJson: () -> Unit,
    onExportPdfUri: (Uri) -> Unit,
    onExportHtmlUri: (Uri) -> Unit,
    onOpenTraceTab: () -> Unit
) {
    val colors = SignalTheme.colors
    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) onExportPdfUri(uri)
    }
    val htmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/html")
    ) { uri ->
        if (uri != null) onExportHtmlUri(uri)
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (decodedMetadata != null) {
            UrlMetadataDecoderCard(
                decoded = decodedMetadata,
                onApplyOwnerAsProfile = onApplyOwnerAsProfile
            )
        }
        ForensicCard(borderColor = colors.emerald.copy(alpha = 0.55f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "KASUS #${report.reportId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.cyan
                    )
                    Text(
                        text = "Hasil Korelasi",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlatformBadge(platform = report.profileParsed.platform)
                    if (report.postParsed.platform != report.profileParsed.platform) {
                        PlatformBadge(platform = report.postParsed.platform)
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CorrelationScoreGauge(
                    scorePercent = report.scorePercent,
                    confidenceLabel = report.confidenceLabel
                )
            }

            Text(
                text = report.summaryNote,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Evidences
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                report.evidences.forEach { ev ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = if (ev.matched) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (ev.matched) colors.emerald else colors.amber,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = ev.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "+${if (ev.matched) ev.weight else 0}/${ev.weight}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (ev.matched) colors.emerald else colors.textMuted
                                )
                            }
                            Text(
                                text = ev.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Quick Actions
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenTraceTab,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("open_trace_tab_button")
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Waktu & CDN")
                }
                OutlinedButton(
                    onClick = onSaveVault,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("save_vault_button")
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Simpan Vault")
                }
                OutlinedButton(
                    onClick = onExportJson,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("export_json_quick_button")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ekspor JSON")
                }
                OutlinedButton(
                    onClick = { pdfLauncher.launch("SignalTrace_${report.reportId}.pdf") },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("export_pdf_quick_button"),
                    border = BorderStroke(1.dp, colors.emerald),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.emerald)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ekspor PDF")
                }
                OutlinedButton(
                    onClick = { htmlLauncher.launch("SignalTrace_${report.reportId}.html") },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("export_html_quick_button"),
                    border = BorderStroke(1.dp, colors.cyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.cyan)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ekspor HTML")
                }
            }
        }
    }
}
