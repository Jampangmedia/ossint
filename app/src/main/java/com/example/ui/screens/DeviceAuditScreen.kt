package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.CopyableMonoRow
import com.example.ui.components.ForensicCard
import com.example.ui.theme.SignalTheme
import com.example.ui.viewmodel.DeviceAuditUiState

@Composable
fun DeviceAuditScreen(
    uiState: DeviceAuditUiState,
    onRefreshDiagnostics: () -> Unit,
    onRequestAndReadGps: () -> Unit,
    onLocationPermissionDenied: () -> Unit,
    onMediaPicked: (android.net.Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            onRequestAndReadGps()
        } else {
            onLocationPermissionDenied()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onMediaPicked(uri)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Audit Perangkat Sendiri",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Inspeksi antarmuka IP aktif, matriks izin Android, GPS perangkat sendiri, dan kebocoran EXIF media.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onRefreshDiagnostics,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("refresh_audit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Segarkan Audit Perangkat",
                            tint = colors.cyan
                        )
                    }
                }
            }

            // Network & Local IP Card
            item {
                val net = uiState.networkSnapshot
                ForensicCard(borderColor = colors.cyan.copy(alpha = 0.45f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = colors.cyan)
                        Text(
                            text = "Telemetri Jaringan & IP Antarmuka Aktif",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (net != null) {
                        CopyableMonoRow(
                            label = "Transport & Interface (${if (net.isVpnActive) "VPN Aktif" else "Langsung"})",
                            value = "${net.transportLabel} • ${net.interfaceName}"
                        )
                        CopyableMonoRow(
                            label = "Alamat IP Antarmuka Perangkat Sendiri",
                            value = net.localIps.joinToString(", ").ifBlank { "Tidak ada IP aktif" }
                        )
                        CopyableMonoRow(
                            label = "Server DNS Aktif",
                            value = net.dnsServers.joinToString(", ").ifBlank { "-" }
                        )
                    }
                }
            }

            // Runtime Permissions Matrix
            item {
                ForensicCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = colors.emerald)
                        Text(
                            text = "Matriks Izin Sistem Perangkat",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    uiState.permissions.forEach { perm ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (perm.granted) Icons.Default.CheckCircle else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (perm.granted) colors.emerald else colors.amber,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = perm.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = perm.purpose,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = (if (perm.granted) colors.emerald else colors.amber).copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = if (perm.granted) "AKTIF" else "NONAKTIF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (perm.granted) colors.emerald else colors.amber,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Explicit Own-Device GPS Tester
            item {
                ForensicCard(borderColor = colors.emerald.copy(alpha = 0.45f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null, tint = colors.emerald)
                        Text(
                            text = "Uji GPS Perangkat Sendiri (Opsional)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Hanya berjalan saat Anda menekan tombol di bawah dan menyetujui dialog izin lokasi Android standar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        enabled = !uiState.isReadingGps,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("test_own_gps_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.emerald,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.isReadingGps) "Membaca GPS..." else "Uji Baca GPS Perangkat Saya")
                    }

                    uiState.gpsStatusMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.cyan
                        )
                    }

                    uiState.ownGpsSnapshot?.let { gps ->
                        CopyableMonoRow(
                            label = "Koordinat GPS Perangkat (${gps.provider})",
                            value = "${gps.latitude}, ${gps.longitude} (±${gps.accuracyMeters}m)"
                        )
                        Text(
                            text = "Waktu Fix: ${gps.capturedAt}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Zero-Permission Photo Picker EXIF GPS Inspector
            item {
                ForensicCard(borderColor = colors.amber.copy(alpha = 0.45f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = colors.amber)
                        Text(
                            text = "Cek Kebocoran EXIF GPS Sebelum Unggah",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Menggunakan Android Photo Picker (tanpa izin akses galeri luas) untuk memeriksa apakah foto lokal mengandung koordinat GPS sebelum diunggah ke TikTok/Facebook/Instagram.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !uiState.isInspectingExif,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("pick_photo_exif_button"),
                        border = BorderStroke(1.dp, colors.amber),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.amber)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState.isInspectingExif) "Memeriksa EXIF..." else "Pilih Foto via Photo Picker")
                    }

                    uiState.exifErrorMessage?.let { err ->
                        Text(text = err, style = MaterialTheme.typography.bodyMedium, color = colors.coral)
                    }

                    uiState.exifAuditResult?.let { exif ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (exif.hasGpsCoordinates) colors.coralContainer else colors.emeraldContainer,
                            border = BorderStroke(
                                1.dp,
                                if (exif.hasGpsCoordinates) colors.coral else colors.emerald
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = if (exif.hasGpsCoordinates) Icons.Default.Warning else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (exif.hasGpsCoordinates) colors.coral else colors.emerald
                                )
                                Text(
                                    text = exif.privacySummary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (exif.hasGpsCoordinates) colors.coral else colors.emerald
                                )
                            }
                        }
                        if (exif.hasGpsCoordinates) {
                            CopyableMonoRow(
                                label = "Koordinat GPS EXIF Tertanam",
                                value = "${exif.latitude}, ${exif.longitude}"
                            )
                        }
                        exif.deviceMakeModel?.let { dev ->
                            Text(
                                text = "Perangkat Kamera EXIF: $dev",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        exif.capturedDateTime?.let { dt ->
                            Text(
                                text = "Waktu Pengambilan EXIF: $dt",
                                style = MaterialTheme.typography.bodySmall,
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
