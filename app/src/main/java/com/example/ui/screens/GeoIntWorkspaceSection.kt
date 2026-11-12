package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.forensic.CorrelationReport
import com.example.geoint.ChronolocationSummary
import com.example.geoint.GeoCoordinatePreset
import com.example.geoint.SolarGeoCalculator
import com.example.geoint.SolarPositionResult
import com.example.ui.components.CopyableMonoRow
import com.example.ui.components.ForensicCard
import com.example.ui.theme.SignalTheme
import com.example.ui.viewmodel.GeoIntWorkspaceState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeoIntWorkspaceSection(
    geoState: GeoIntWorkspaceState,
    activeReport: CorrelationReport?,
    chronolocation: ChronolocationSummary?,
    availableChronoAccounts: List<String>,
    selectedChronoAccount: String?,
    onSelectChronoAccount: (String?) -> Unit,
    onCaptionChange: (String) -> Unit,
    onSelectCity: (GeoCoordinatePreset) -> Unit,
    onCustomCoordsChange: (String, String) -> Unit,
    onAnalyzeScreenshot: (Uri) -> Unit,
    onAnalystNotesChange: (String, String, String, String) -> Unit
) {
    val colors = SignalTheme.colors
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onAnalyzeScreenshot(uri)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Caption, Hashtag, Plate & Area Code Analyzer
        ForensicCard(borderColor = colors.emerald.copy(alpha = 0.45f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.LocationCity, contentDescription = null, tint = colors.emerald)
                Text(
                    text = "Ekstraktor Caption, Tagar, Pelat & Kode Area",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Tempel caption, deskripsi video, atau komentar postingan (otomatis terisi jika Lacak CDN mendapat og:description terbuka) untuk mendeteksi kota, kode area telepon, pelat kendaraan Indonesia, dan koordinat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = geoState.captionInput,
                onValueChange = onCaptionChange,
                label = { Text("Caption / Transkrip / Deskripsi Konten") },
                placeholder = { Text("Contoh: Macet di Dago Bandung, tampak B 1234 ABC dan telp (022) 555123 #bandung") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("geoint_caption_input")
            )

            val txtRes = geoState.textAnalysis
            Text(
                text = txtRes.summaryMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = if (txtRes.geoHits.isNotEmpty()) colors.emerald else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (txtRes.geoHits.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    txtRes.geoHits.forEach { hit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${hit.category}: \"${hit.matchedToken}\"",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.cyan
                                )
                                Text(
                                    text = hit.regionHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            if (txtRes.hashtags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    txtRes.hashtags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.cyanContainer
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.cyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Real Astronomical Solar Azimuth & Shadow Verification
        ForensicCard(borderColor = colors.amber.copy(alpha = 0.5f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.WbSunny, contentDescription = null, tint = colors.amber)
                Text(
                    text = "Kalkulator Surya & Konsistensi Bayangan (NOAA)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "Basis Timestamp: ${geoState.solarTimestampSource}",
                style = MaterialTheme.typography.labelMedium,
                color = if (geoState.hasVerifiedPostTimestamp) colors.cyan else colors.amber
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SolarGeoCalculator.indonesianCityPresets) { preset ->
                    FilterChip(
                        selected = geoState.selectedPreset.name == preset.name,
                        onClick = { onSelectCity(preset) },
                        label = { Text("${preset.name} (${preset.timezoneLabel})") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = geoState.customLatInput,
                    onValueChange = { onCustomCoordsChange(it, geoState.customLonInput) },
                    label = { Text("Latitude") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = geoState.customLonInput,
                    onValueChange = { onCustomCoordsChange(geoState.customLatInput, it) },
                    label = { Text("Longitude") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
            }

            val solar = geoState.solarResult
            if (solar == null) {
                Text(
                    text = "Kalkulasi posisi matahari NOAA membutuhkan timestamp unggahan asli dari postingan (URL TikTok/Instagram/Threads dengan ID 64-bit, atau hasil Lacak CDN yang memuat waktu publikasi HTML). Tidak menggunakan jam ponsel sebagai pengganti palsu waktu postingan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SolarCompassCanvas(solar = solar, modifier = Modifier.size(120.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = solar.daylightPhase,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (solar.solarElevationDeg >= 0.0) colors.amber else colors.cyan
                        )
                        Text(
                            text = "Elevasi Matahari: ${"%.1f".format(solar.solarElevationDeg)}°",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Azimuth Matahari: ${"%.1f".format(solar.solarAzimuthDeg)}° (${solar.compassDirectionSun})",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.amber
                        )
                        Text(
                            text = "Arah Bayangan: ${"%.1f".format(solar.shadowDirectionDeg)}° (${solar.compassDirectionShadow})",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.emerald
                        )
                        Text(
                            text = solar.shadowLengthRatio?.let {
                                "Rasio Panjang Bayangan: ${"%.2f".format(it)}x tinggi objek"
                            } ?: "Tidak ada bayangan matahari langsung",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Solar Noon lokal: ~${solar.solarNoonLocalHint}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 3. Real Pixel Luminance & EXIF Cross-Check
        ForensicCard(borderColor = colors.cyan.copy(alpha = 0.45f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.ImageSearch, contentDescription = null, tint = colors.cyan)
                Text(
                    text = "Inspeksi Luminans & EXIF Frame Konten",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Pilih screenshot atau frame foto konten via Photo Picker untuk menghitung rasio kecerahan siang/malam, spektrum langit atas, serta tag EXIF software/kamera lalu mencocokkannya dengan elevasi matahari di atas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                enabled = !geoState.isAnalyzingImage,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("geoint_pick_image_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.cyan,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Default.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (geoState.isAnalyzingImage) "Menganalisis Piksel & EXIF..." else "Pilih Screenshot / Frame Konten")
            }

            geoState.imageError?.let { err ->
                Text(text = err, style = MaterialTheme.typography.bodyMedium, color = colors.coral)
            }

            geoState.imageAnalysis?.let { img ->
                CopyableMonoRow(
                    label = "Klasifikasi Cahaya & Rasio (${img.widthPx}x${img.heightPx}px • ${img.aspectRatioLabel})",
                    value = "${img.lightingClassification} • Luma rata-rata ${img.meanLuminancePercent}% (Atas ${img.topQuarterLuminancePercent}%, Bawah ${img.bottomQuarterLuminancePercent}%)"
                )
                CopyableMonoRow(
                    label = "SHA-256 Frame Bukti (${img.fileSizeBytes} byte)",
                    value = img.sha256Hex
                )
                Text(
                    text = img.consistencyHint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.emerald
                )
                val exifMeta = listOfNotNull(
                    img.exifSoftwareTag?.let { "Software: $it" },
                    img.exifDeviceTag?.let { "Kamera: $it" },
                    img.exifDateTime?.let { "Waktu EXIF: $it" },
                    img.exifLatLon?.let { "GPS EXIF: ${it.first}, ${it.second}" }
                )
                if (exifMeta.isNotEmpty()) {
                    Text(
                        text = exifMeta.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.amber
                    )
                }
                img.elaResult?.let { ela ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Peta Error Level Analysis (ELA JPEG Q90)",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.cyan
                        )
                        Image(
                            bitmap = ela.heatmapBitmap.asImageBitmap(),
                            contentDescription = "Visualisasi Error Level Analysis gambar",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 240.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            text = ela.verdict,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Mean Error: ${"%.2f".format(ela.meanDiff)} • StdDev σ: ${"%.2f".format(ela.stdDevDiff)} • Maks: ${ela.maxDiff} • Hotspot ≥12: ${"%.1f".format(ela.hotspotPercent)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted
                        )
                    }
                }
            }
        }

        // 4. Multi-Post Chronolocation Histogram (Filterable Per Target Account)
        ForensicCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AccessTime, contentDescription = null, tint = colors.threadsViolet)
                Text(
                    text = "Kronolokasi Pola Jam Aktif (Per Akun / Semua)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (availableChronoAccounts.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedChronoAccount == null,
                            onClick = { onSelectChronoAccount(null) },
                            label = { Text("Semua Kasus Vault") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                    items(availableChronoAccounts) { account ->
                        FilterChip(
                            selected = selectedChronoAccount == account,
                            onClick = {
                                onSelectChronoAccount(if (selectedChronoAccount == account) null else account)
                            },
                            label = { Text(account) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
            if (chronolocation == null) {
                Text(
                    text = "Simpan beberapa kasus TikTok/Instagram/Threads ke Vault untuk membangun histogram 24 jam WIB per akun guna menginferensi jendela jam aktif & istirahat target.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Target: ${selectedChronoAccount ?: "Gabungan Semua Vault"} • Sampel: ${chronolocation.sampleCount} postingan • Puncak Aktif: ${chronolocation.peakActiveWindowWib}",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.emerald
                )
                Text(
                    text = "Jendela Senyap: ${chronolocation.likelySleepWindowWib}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.cyan
                )
                Text(
                    text = chronolocation.inferredTimezoneConsistency,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HourlyHistogramBar(buckets = chronolocation.hourlyHistogramWib)
            }
        }

        // 5. Analyst GEOINT Evidence Log (saved to Vault & exported to JSON)
        ForensicCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null, tint = colors.emerald)
                Text(
                    text = "Catatan Bukti Visual & Audio GEOINT (Masuk Vault & JSON)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            OutlinedTextField(
                value = geoState.analystLandmarkNote,
                onValueChange = { onAnalystNotesChange(it, geoState.analystPlateNote, geoState.analystDialectNote, geoState.analystShadowVerdict) },
                label = { Text("Penanda Lingkungan / Landmark / Rambu") },
                placeholder = { Text("Mis. Halte TransJakarta / papan nama Jl. Braga") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = geoState.analystPlateNote,
                onValueChange = { onAnalystNotesChange(geoState.analystLandmarkNote, it, geoState.analystDialectNote, geoState.analystShadowVerdict) },
                label = { Text("Pelat Kendaraan / Atribut Lokal Terlihat") },
                placeholder = { Text("Mis. Pelat awalan D / seragam daerah") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = geoState.analystDialectNote,
                onValueChange = { onAnalystNotesChange(geoState.analystLandmarkNote, geoState.analystPlateNote, it, geoState.analystShadowVerdict) },
                label = { Text("Bahasa / Aksen Audio / Konteks Lokal") },
                placeholder = { Text("Mis. Dialek Sunda / Jawa Timur") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = geoState.analystShadowVerdict,
                onValueChange = { onAnalystNotesChange(geoState.analystLandmarkNote, geoState.analystPlateNote, geoState.analystDialectNote, it) },
                label = { Text("Verifikasi Arah Bayangan & Cahaya") },
                placeholder = { Text("Mis. Bayangan ke barat daya sesuai jam 10:10 WIB") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SolarCompassCanvas(
    solar: SolarPositionResult,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val ringColor = colors.borderSubtle
    val sunColor = colors.amber
    val shadowColor = colors.emerald
    val northColor = colors.coral

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val radius = size.minDimension / 2f - 8.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)

            drawCircle(
                color = ringColor,
                radius = radius,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
            // North marker tick
            drawLine(
                color = northColor,
                start = Offset(center.x, center.y - radius - 4.dp.toPx()),
                end = Offset(center.x, center.y - radius + 6.dp.toPx()),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Sun azimuth vector (0° = North = -PI/2 in Canvas)
            val sunRad = (solar.solarAzimuthDeg - 90.0) * PI / 180.0
            val sunEnd = Offset(
                x = center.x + (radius * 0.85f * cos(sunRad)).toFloat(),
                y = center.y + (radius * 0.85f * sin(sunRad)).toFloat()
            )
            drawLine(
                color = sunColor,
                start = center,
                end = sunEnd,
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(color = sunColor, radius = 6.dp.toPx(), center = sunEnd)

            // Opposite Shadow vector
            val shRad = (solar.shadowDirectionDeg - 90.0) * PI / 180.0
            val shEnd = Offset(
                x = center.x + (radius * 0.78f * cos(shRad)).toFloat(),
                y = center.y + (radius * 0.78f * sin(shRad)).toFloat()
            )
            drawLine(
                color = shadowColor,
                start = center,
                end = shEnd,
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(color = colors.cyan, radius = 3.dp.toPx(), center = center)
        }
        Text(
            text = "U",
            style = MaterialTheme.typography.labelSmall,
            color = colors.coral,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        )
    }
}

@Composable
private fun HourlyHistogramBar(buckets: List<Int>) {
    val colors = SignalTheme.colors
    val maxVal = (buckets.maxOrNull() ?: 1).coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            buckets.forEach { count ->
                val frac = if (count == 0) 0.08f else (count.toFloat() / maxVal).coerceIn(0.15f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((44f * frac).dp)
                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                        .background(if (count > 0) colors.emerald else colors.borderSubtle.copy(alpha = 0.45f))
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("00:00", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            Text("06:00", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            Text("12:00", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            Text("18:00", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            Text("23:00 WIB", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
        }
    }
}
