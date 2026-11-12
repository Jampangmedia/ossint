package com.example.geoint

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import com.example.aiforensic.BinaryContainerInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class ElaAnalysisResult(
    val heatmapBitmap: Bitmap,
    val meanDiff: Float,
    val stdDevDiff: Float,
    val maxDiff: Int,
    val hotspotPercent: Float,
    val verdict: String
)

data class ImageVisualForensicResult(
    val widthPx: Int,
    val heightPx: Int,
    val aspectRatioLabel: String,
    val fileSizeBytes: Long,
    val sha256Hex: String,
    val meanLuminancePercent: Int,
    val topQuarterSkyBlueRatio: Float,
    val topQuarterLuminancePercent: Int,
    val bottomQuarterLuminancePercent: Int,
    val lightingClassification: String,
    val exifSoftwareTag: String?,
    val exifDeviceTag: String?,
    val exifDateTime: String?,
    val exifHasGps: Boolean,
    val exifLatLon: Pair<Double, Double>?,
    val consistencyHint: String,
    val elaResult: ElaAnalysisResult? = null
)

object ImageForensicAnalyzer {

    suspend fun analyzeImage(
        context: Context,
        uri: Uri,
        solarPosition: SolarPositionResult?
    ): Result<ImageVisualForensicResult> = withContext(Dispatchers.IO) {
        runCatching {
            val (sha256Hex, fileSizeBytes) = context.contentResolver.openInputStream(uri)?.use { stream ->
                BinaryContainerInspector.computeSha256(stream)
            } ?: error("Tidak dapat membuka stream gambar.")

            // 1. Read EXIF tags first
            var software: String? = null
            var makeModel: String? = null
            var dateTime: String? = null
            var hasGps = false
            var latLonPair: Pair<Double, Double>? = null

            context.contentResolver.openInputStream(uri)?.use { stream ->
                runCatching {
                    val exif = ExifInterface(stream)
                    software = exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.takeIf { it.isNotBlank() }
                    val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                    val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                    makeModel = listOfNotNull(make, model).joinToString(" ").takeIf { it.isNotBlank() }
                    dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    val coords = FloatArray(2)
                    @Suppress("DEPRECATION")
                    hasGps = exif.getLatLong(coords)
                    if (hasGps) {
                        latLonPair = coords[0].toDouble() to coords[1].toDouble()
                    }
                }
            }

            // 2. Decode bounds and downsample safely to ~420px max dimension for accurate ELA + pixel stats
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val origW = boundsOpts.outWidth.coerceAtLeast(1)
            val origH = boundsOpts.outHeight.coerceAtLeast(1)
            var sampleSize = 1
            while ((origW / sampleSize) > 420 || (origH / sampleSize) > 420) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: error("Gagal mendekode bitmap gambar.")

            try {
                val w = bitmap.width
                val h = bitmap.height
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

                var totalLuma = 0.0
                var topLuma = 0.0
                var topBlueRatioSum = 0.0
                var topCount = 0
                var bottomLuma = 0.0
                var bottomCount = 0

                val topLimitY = (h * 0.25f).roundToInt().coerceAtLeast(1)
                val bottomStartY = (h * 0.75f).roundToInt().coerceAtMost(h - 1)

                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val px = pixels[y * w + x]
                        val r = Color.red(px)
                        val g = Color.green(px)
                        val b = Color.blue(px)
                        val luma = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
                        totalLuma += luma

                        if (y < topLimitY) {
                            topLuma += luma
                            val denom = (r + g + b).coerceAtLeast(1)
                            topBlueRatioSum += b.toDouble() / denom
                            topCount++
                        }
                        if (y >= bottomStartY) {
                            bottomLuma += luma
                            bottomCount++
                        }
                    }
                }

                val meanPct = ((totalLuma / pixels.size) * 100.0).roundToInt().coerceIn(0, 100)
                val topPct = ((topLuma / topCount.coerceAtLeast(1)) * 100.0).roundToInt().coerceIn(0, 100)
                val bottomPct = ((bottomLuma / bottomCount.coerceAtLeast(1)) * 100.0).roundToInt().coerceIn(0, 100)
                val blueRatio = (topBlueRatioSum / topCount.coerceAtLeast(1)).toFloat()

                val lightingClass = when {
                    meanPct >= 55 && topPct >= 55 && blueRatio >= 0.35f ->
                        "Terang Outdoor / Langit Cerah (Siang)"
                    meanPct >= 48 ->
                        "Pencahayaan Terang (Siang / Indoor Terang)"
                    meanPct in 24..47 ->
                        "Pencahayaan Menengah (Teduh / Golden Hour / Indoor)"
                    else ->
                        "Gelap / Malam / Low-Light"
                }

                val consistency = if (solarPosition == null) {
                    "Analisis posisi matahari belum dihitung; jalankan Kalkulator Surya untuk membandingkan fase siang/malam."
                } else {
                    val sunUp = solarPosition.solarElevationDeg >= 0.0
                    val imgBright = meanPct >= 42
                    when {
                        sunUp && imgBright ->
                            "KONSISTEN: Gambar terang ($meanPct% luma) selaras dengan posisi matahari di atas horizon (${"%.1f".format(solarPosition.solarElevationDeg)}°)."
                        !sunUp && !imgBright ->
                            "KONSISTEN: Gambar gelap/low-light ($meanPct% luma) selaras dengan fase matahari di bawah horizon (${"%.1f".format(solarPosition.solarElevationDeg)}°)."
                        !sunUp && imgBright ->
                            "PERHATIAN: Gambar tampak terang ($meanPct% luma) padahal pada timestamp unggahan matahari berada di bawah horizon (${"%.1f".format(solarPosition.solarElevationDeg)}°) — bisa berarti indoor lampu terang atau konten direkam sebelumnya (re-upload)."
                        else ->
                            "CATATAN: Gambar cenderung gelap ($meanPct% luma) meski timestamp menunjukkan siang (${"%.1f".format(solarPosition.solarElevationDeg)}°) — kemungkinan indoor gelap atau unggah tertunda."
                    }
                }

                val ela = runCatching { computeElaAnalysis(bitmap) }.getOrNull()

                ImageVisualForensicResult(
                    widthPx = origW,
                    heightPx = origH,
                    aspectRatioLabel = classifyAspectRatio(origW, origH),
                    fileSizeBytes = fileSizeBytes,
                    sha256Hex = sha256Hex,
                    meanLuminancePercent = meanPct,
                    topQuarterSkyBlueRatio = blueRatio,
                    topQuarterLuminancePercent = topPct,
                    bottomQuarterLuminancePercent = bottomPct,
                    lightingClassification = lightingClass,
                    exifSoftwareTag = software,
                    exifDeviceTag = makeModel,
                    exifDateTime = dateTime,
                    exifHasGps = hasGps,
                    exifLatLon = latLonPair,
                    consistencyHint = consistency,
                    elaResult = ela
                )
            } finally {
                bitmap.recycle()
            }
        }
    }

    fun computeElaAnalysis(source: Bitmap, quality: Int = 90): ElaAnalysisResult {
        val baos = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val recompressedBytes = baos.toByteArray()
        val recompressed = BitmapFactory.decodeByteArray(recompressedBytes, 0, recompressedBytes.size)
            ?: error("Gagal mendekompresi JPEG untuk ELA.")
        try {
            return computeElaFromPair(source, recompressed)
        } finally {
            recompressed.recycle()
        }
    }

    internal fun computeElaFromPair(source: Bitmap, recompressed: Bitmap): ElaAnalysisResult {
        val w = minOf(source.width, recompressed.width)
        val h = minOf(source.height, recompressed.height)
        val count = w * h
        val srcPixels = IntArray(count)
        val recPixels = IntArray(count)
        source.getPixels(srcPixels, 0, w, 0, 0, w, h)
        recompressed.getPixels(recPixels, 0, w, 0, 0, w, h)

        val heatmapPixels = IntArray(count)
        val diffs = FloatArray(count)
        var sum = 0.0
        var maxDiff = 0
        var hotspotCount = 0

        for (i in 0 until count) {
            val s = srcPixels[i]
            val r = recPixels[i]
            val dr = abs(Color.red(s) - Color.red(r))
            val dg = abs(Color.green(s) - Color.green(r))
            val db = abs(Color.blue(s) - Color.blue(r))
            val meanErr = (dr + dg + db) / 3f
            diffs[i] = meanErr
            sum += meanErr
            val maxChan = maxOf(dr, maxOf(dg, db))
            if (maxChan > maxDiff) maxDiff = maxChan
            if (meanErr >= 12f) hotspotCount++

            // False-color thermal palette: low=deep navy, mid=cyan/emerald, high=amber/coral
            val amplified = (meanErr * 12f).roundToInt().coerceIn(0, 255)
            heatmapPixels[i] = when {
                amplified < 45 -> Color.rgb(10, 22, 48 + amplified * 2)
                amplified < 120 -> Color.rgb(14, 140 + (amplified - 45), 185)
                amplified < 200 -> Color.rgb(245, 165 - ((amplified - 120) / 2), 40)
                else -> Color.rgb(255, 65, 65)
            }
        }

        val mean = (sum / count.coerceAtLeast(1)).toFloat()
        var sqSum = 0.0
        for (d in diffs) {
            val delta = d - mean
            sqSum += delta * delta
        }
        val stdDev = sqrt(sqSum / count.coerceAtLeast(1)).toFloat()
        val hotspotPct = (hotspotCount.toFloat() / count.coerceAtLeast(1)) * 100f

        val heatmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        heatmap.setPixels(heatmapPixels, 0, w, 0, 0, w, h)

        val verdict = when {
            hotspotPct >= 12f || stdDev >= 6.5f ->
                "Anomali ELA Tinggi (${"%.1f".format(hotspotPct)}% hotspot, σ=${"%.1f".format(stdDev)}): Ada area berenergi error lokal berbeda — periksa teks tempelan, objek editan, atau tepi tajam."
            mean <= 1.2f && stdDev <= 1.5f ->
                "ELA Sangat Rendah & Seragam (μ=${"%.2f".format(mean)}, σ=${"%.2f".format(stdDev)}): Umum pada gambar yang sudah dikompresi ulang berkali-kali oleh CDN media sosial."
            else ->
                "ELA Wajar / Seragam (μ=${"%.2f".format(mean)}, hotspot ${"%.1f".format(hotspotPct)}%): Tidak tampak perbedaan tingkat kompresi ekstrem antarwilayah piksel."
        }

        return ElaAnalysisResult(
            heatmapBitmap = heatmap,
            meanDiff = mean,
            stdDevDiff = stdDev,
            maxDiff = maxDiff,
            hotspotPercent = hotspotPct,
            verdict = verdict
        )
    }

    private fun classifyAspectRatio(w: Int, h: Int): String {
        val ratio = w.toDouble() / h.coerceAtLeast(1)
        return when {
            abs(ratio - 1.0) < 0.04 -> "1:1 (Persegi • umum pada avatar / generator gambar)"
            abs(ratio - (9.0 / 16.0)) < 0.05 -> "9:16 (Vertikal Reels / TikTok / Shorts)"
            abs(ratio - (16.0 / 9.0)) < 0.05 -> "16:9 (Lanskap YouTube / Widescreen)"
            abs(ratio - (3.0 / 4.0)) < 0.05 -> "3:4 (Potret Standar)"
            abs(ratio - (4.0 / 3.0)) < 0.05 -> "4:3 (Kamera Standar)"
            else -> "%.2f:1 (%dx%d)".format(ratio, w, h)
        }
    }
}
