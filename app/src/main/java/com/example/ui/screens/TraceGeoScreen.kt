package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.forensic.CorrelationReport
import com.example.geoint.ChronolocationSummary
import com.example.geoint.GeoCoordinatePreset
import com.example.ui.components.CopyableMonoRow
import com.example.ui.components.ForensicCard
import com.example.ui.components.PlatformBadge
import com.example.ui.components.SnowflakeBitVisualizer
import com.example.ui.theme.SignalTheme
import com.example.ui.viewmodel.AiForensicUiState
import com.example.ui.viewmodel.GeoIntWorkspaceState

@Composable
fun TraceGeoScreen(
    report: CorrelationReport?,
    isTracing: Boolean,
    geoState: GeoIntWorkspaceState,
    aiState: AiForensicUiState,
    chronolocation: ChronolocationSummary?,
    availableChronoAccounts: List<String>,
    selectedChronoAccount: String?,
    onSelectChronoAccount: (String?) -> Unit,
    onTriggerTrace: () -> Unit,
    onCaptionChange: (String) -> Unit,
    onSelectCity: (GeoCoordinatePreset) -> Unit,
    onCustomCoordsChange: (String, String) -> Unit,
    onAnalyzeScreenshot: (Uri) -> Unit,
    onAnalystNotesChange: (String, String, String, String) -> Unit,
    onInspectAiFile: (Uri) -> Unit,
    onManualAiInputChange: (String) -> Unit,
    onAnalyzeManualAi: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) }

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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Jejak Waktu, CDN & GEOINT",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Dekoding waktu 64-bit, pelacakan server Edge/CDN, dan analisis petunjuk lokasi publik (GEOINT).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = selectedSubTab == 0,
                                onClick = { selectedSubTab = 0 },
                                leadingIcon = {
                                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("Waktu & CDN") },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("subtab_waktu_cdn")
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedSubTab == 1,
                                onClick = { selectedSubTab = 1 },
                                leadingIcon = {
                                    Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("GEOINT") },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("subtab_geoint")
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedSubTab == 2,
                                onClick = { selectedSubTab = 2 },
                                leadingIcon = {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("Sinyal AI & Prompt") },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("subtab_ai_signal")
                            )
                        }
                    }
                }
            }

            if (selectedSubTab == 2) {
                item {
                    AiForensicWorkspaceSection(
                        aiState = aiState,
                        activeReport = report,
                        isTracingUrl = isTracing,
                        onTriggerUrlTrace = onTriggerTrace,
                        onInspectFileUri = onInspectAiFile,
                        onManualInputChange = onManualAiInputChange,
                        onAnalyzeManualInput = onAnalyzeManualAi
                    )
                }
            } else if (selectedSubTab == 1) {
                item {
                    GeoIntWorkspaceSection(
                        geoState = geoState,
                        activeReport = report,
                        chronolocation = chronolocation,
                        availableChronoAccounts = availableChronoAccounts,
                        selectedChronoAccount = selectedChronoAccount,
                        onSelectChronoAccount = onSelectChronoAccount,
                        onCaptionChange = onCaptionChange,
                        onSelectCity = onSelectCity,
                        onCustomCoordsChange = onCustomCoordsChange,
                        onAnalyzeScreenshot = onAnalyzeScreenshot,
                        onAnalystNotesChange = onAnalystNotesChange
                    )
                }
            } else if (report == null) {
                item {
                    ForensicCard {
                        Text(
                            text = "Belum Ada Kasus URL Aktif",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.cyan
                        )
                        Text(
                            text = "Analisis URL terlebih dahulu di tab Korelasi untuk melihat dekoding 64-bit dan jejak redirect, atau beralih ke tab 'GEOINT' dan 'Sinyal AI & Prompt'.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // "Kapan" — Timestamp Reverse Engineering Card
                item {
                    ForensicCard(borderColor = colors.emerald.copy(alpha = 0.5f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = "Waktu Publikasi",
                                    tint = colors.emerald
                                )
                                Text(
                                    text = "KAPAN (Waktu Unggah)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            PlatformBadge(platform = report.postParsed.platform)
                        }

                        val breakdown = report.postParsed.snowflakeBreakdown
                        if (breakdown != null) {
                            SnowflakeBitVisualizer(breakdown = breakdown)
                            CopyableMonoRow(label = "UTC (Standar Global)", value = breakdown.utcFormatted)
                            CopyableMonoRow(label = "WIB (Jakarta / UTC+7)", value = breakdown.wibFormatted)
                            CopyableMonoRow(label = "WITA (Makassar / UTC+8)", value = breakdown.witaFormatted)
                            CopyableMonoRow(label = "WIT (Jayapura / UTC+9)", value = breakdown.witFormatted)
                        } else if (!report.postParsed.publicPublishedTime.isNullOrBlank()) {
                            CopyableMonoRow(
                                label = "Waktu Publikasi Metadata Publik",
                                value = report.postParsed.publicPublishedTime
                            )
                        } else {
                            Text(
                                text = "ID URL ini tidak menyematkan epoch timestamp secara langsung (misalnya shortlink atau ID acak YouTube/pfbid Facebook). Jalankan Lacak CDN untuk membaca tag waktu HTML publik.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // "Di Mana" — CDN / Edge Server vs. Declared Public Location
                item {
                    ForensicCard(borderColor = colors.cyan.copy(alpha = 0.5f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = "Server CDN",
                                tint = colors.cyan
                            )
                            Text(
                                text = "DI MANA (Server CDN & Tag Lokasi Publik)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.amberContainer,
                            border = BorderStroke(1.dp, colors.amber.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = colors.amber,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Transparansi Rekayasa Sistem: Alamat IP yang terdeteksi dari URL media sosial adalah IP server Edge/CDN platform, BUKAN IP smartphone pembuat konten.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.amber
                                )
                            }
                        }

                        Button(
                            onClick = onTriggerTrace,
                            enabled = !isTracing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("run_trace_geo_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.cyan,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            )
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isTracing) "Melacak Hop & DNS..." else "Jalankan / Segarkan Lacak Redirect & DNS")
                        }

                        val trace = report.networkTrace
                        if (trace != null) {
                            if (trace.errorMessage != null) {
                                Text(
                                    text = "Catatan Jaringan: ${trace.errorMessage}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.coral
                                )
                            }
                            if (trace.edgeServerHost.isNotBlank()) {
                                CopyableMonoRow(
                                    label = "Host Server Edge / CDN",
                                    value = trace.edgeServerHost
                                )
                            }
                            if (trace.edgeServerIps.isNotEmpty()) {
                                CopyableMonoRow(
                                    label = "IP Server Edge / CDN Terresolusi",
                                    value = trace.edgeServerIps.joinToString(", ")
                                )
                            }
                            trace.rdapInfo?.let { rdap ->
                                if (rdap.errorMessage != null) {
                                    Text(
                                        text = "Kueri RDAP/ASN (${rdap.queriedIp}): ${rdap.errorMessage}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    val orgSummary = listOfNotNull(
                                        rdap.organizationName ?: rdap.networkName,
                                        rdap.countryCode?.let { "Negara Node: $it" },
                                        rdap.cidr,
                                        rdap.handle
                                    ).joinToString(" • ")
                                    CopyableMonoRow(
                                        label = "Registrasi ASN / RDAP Server Edge (${rdap.registrySource ?: "RDAP Publik"})",
                                        value = orgSummary.ifBlank { rdap.queriedIp }
                                    )
                                }
                            }
                            if (!trace.extractedLocationTag.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = colors.emerald)
                                    Text(
                                        text = "Tag Lokasi Publik: ${trace.extractedLocationTag}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.emerald
                                    )
                                }
                            } else {
                                Text(
                                    text = "Tag lokasi publik (POI / og:locality) tidak ditemukan di metadata HTML terbuka.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!trace.extractedDescription.isNullOrBlank()) {
                                CopyableMonoRow(
                                    label = "Deskripsi / Caption HTML Publik",
                                    value = trace.extractedDescription
                                )
                            }

                            if (trace.hops.isNotEmpty()) {
                                Text(
                                    text = "Rantai HTTP Redirect (${trace.hops.size} Hop):",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                trace.hops.forEach { hop ->
                                    CopyableMonoRow(
                                        label = "Hop #${hop.step} • HTTP ${hop.statusCode} • ${hop.host}",
                                        value = "${hop.url}\nDNS: ${hop.resolvedIps.joinToString().ifBlank { "-" }}"
                                    )
                                }
                            }

                            if (trace.serverHeaders.isNotEmpty()) {
                                Text(
                                    text = "Header Forensik Server:",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                trace.serverHeaders.forEach { (k, v) ->
                                    Text(
                                        text = "$k: $v",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
