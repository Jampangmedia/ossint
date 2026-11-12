package com.example.geoint

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class GeoEntityHit(
    val category: String,
    val matchedToken: String,
    val regionHint: String,
    val suggestedTimezone: String? = null,
    val suggestedPresetName: String? = null
)

data class ChronolocationSummary(
    val sampleCount: Int,
    val hourlyHistogramWib: List<Int>, // 24 buckets (0..23)
    val peakActiveWindowWib: String,
    val likelySleepWindowWib: String,
    val inferredTimezoneConsistency: String
)

data class GeoIntTextAnalysisResult(
    val sourceTextLength: Int,
    val hashtags: List<String>,
    val mentions: List<String>,
    val geoHits: List<GeoEntityHit>,
    val recommendedPreset: GeoCoordinatePreset?,
    val summaryMessage: String
)

object GeoIntTextAnalyzer {

    private val platePrefixMap = mapOf(
        "A" to "Banten / Serang / Cilegon",
        "B" to "DKI Jakarta / Bekasi / Depok / Tangerang",
        "D" to "Bandung Raya / Cimahi",
        "E" to "Cirebon / Indramayu / Majalengka / Kuningan",
        "F" to "Bogor / Sukabumi / Cianjur",
        "T" to "Purwakarta / Karawang / Subang",
        "Z" to "Garut / Tasikmalaya / Ciamis / Sumedang",
        "G" to "Pekalongan / Tegal / Brebes",
        "H" to "Semarang / Salatiga / Kendal / Demak",
        "K" to "Pati / Kudus / Jepara / Blora",
        "R" to "Banyumas / Purwokerto / Cilacap",
        "AA" to "Magelang / Kebumen / Purworejo",
        "AB" to "DI Yogyakarta",
        "AD" to "Solo / Surakarta / Sukoharjo / Klaten",
        "L" to "Kota Surabaya",
        "M" to "Madura (Bangkalan / Sampang / Pamekasan / Sumenep)",
        "N" to "Malang / Pasuruan / Probolinggo",
        "P" to "Besuki / Banyuwangi / Jember",
        "S" to "Bojonegoro / Mojokerto / Lamongan",
        "W" to "Sidoarjo / Gresik",
        "AE" to "Madiun / Ngawi / Ponorogo",
        "AG" to "Kediri / Blitar / Tulungagung",
        "DK" to "Bali",
        "DR" to "Lombok / Mataram (NTB)",
        "DH" to "Kupang / Timor (NTT)",
        "BK" to "Medan / Sumatera Utara Timur",
        "BB" to "Tapanuli / Sumatera Utara Barat",
        "BA" to "Sumatera Barat / Padang",
        "BM" to "Riau / Pekanbaru",
        "BP" to "Kepulauan Riau / Batam",
        "BG" to "Sumatera Selatan / Palembang",
        "BE" to "Lampung",
        "BH" to "Jambi",
        "BD" to "Bengkulu",
        "BL" to "Aceh",
        "KB" to "Kalimantan Barat / Pontianak",
        "DA" to "Kalimantan Selatan / Banjarmasin",
        "KT" to "Kalimantan Timur / Balikpapan / Samarinda",
        "KH" to "Kalimantan Tengah / Palangkaraya",
        "DD" to "Makassar / Sulawesi Selatan",
        "DB" to "Manado / Sulawesi Utara",
        "DN" to "Palu / Sulawesi Tengah",
        "DT" to "Kendari / Sulawesi Tenggara",
        "DE" to "Maluku / Ambon",
        "PA" to "Papua / Jayapura"
    )

    private val areaCodeMap = mapOf(
        "021" to ("Jabodetabek" to "WIB"),
        "022" to ("Bandung Raya" to "WIB"),
        "0251" to ("Bogor" to "WIB"),
        "024" to ("Semarang" to "WIB"),
        "0274" to ("Yogyakarta" to "WIB"),
        "0271" to ("Solo / Surakarta" to "WIB"),
        "031" to ("Surabaya / Sidoarjo / Gresik" to "WIB"),
        "0341" to ("Malang" to "WIB"),
        "061" to ("Medan" to "WIB"),
        "0711" to ("Palembang" to "WIB"),
        "0751" to ("Padang" to "WIB"),
        "0761" to ("Pekanbaru" to "WIB"),
        "0561" to ("Pontianak" to "WIB"),
        "0361" to ("Denpasar / Bali" to "WITA"),
        "0411" to ("Makassar" to "WITA"),
        "0542" to ("Balikpapan" to "WITA"),
        "0511" to ("Banjarmasin" to "WITA"),
        "0431" to ("Manado" to "WITA"),
        "0911" to ("Ambon" to "WIT"),
        "0967" to ("Jayapura" to "WIT")
    )

    private val cityKeywords = listOf(
        Triple(listOf("jakarta", "jaksel", "jakpus", "jakbar", "jaktim", "jakut", "jabodetabek", "sudirman", "thamrin", "kemang"), "Jakarta", "WIB"),
        Triple(listOf("bandung", "cimahi", "dago", "braga", "jabar"), "Bandung", "WIB"),
        Triple(listOf("bogor", "puncak", "sentul"), "Bogor", "WIB"),
        Triple(listOf("semarang", "simpang lima", "jateng"), "Semarang", "WIB"),
        Triple(listOf("jogja", "yogyakarta", "yogya", "malioboro", "sleman", "bantul"), "Yogyakarta", "WIB"),
        Triple(listOf("surabaya", "sby", "jatim", "tunjungan"), "Surabaya", "WIB"),
        Triple(listOf("malang", "batu"), "Malang", "WIB"),
        Triple(listOf("medan", "sumut"), "Medan", "WIB"),
        Triple(listOf("palembang", "sumsel", "ampera"), "Palembang", "WIB"),
        Triple(listOf("padang", "sumbar"), "Padang", "WIB"),
        Triple(listOf("pekanbaru", "riau"), "Pekanbaru", "WIB"),
        Triple(listOf("pontianak", "kalbar"), "Pontianak", "WIB"),
        Triple(listOf("bali", "denpasar", "canggu", "seminyak", "ubud", "kuta"), "Denpasar / Bali", "WITA"),
        Triple(listOf("makassar", "sulsel", "losari"), "Makassar", "WITA"),
        Triple(listOf("balikpapan", "kaltim", "samarinda", "ikn"), "Balikpapan", "WITA"),
        Triple(listOf("banjarmasin", "kalsel"), "Banjarmasin", "WITA"),
        Triple(listOf("manado", "sulut"), "Manado", "WITA"),
        Triple(listOf("lombok", "mataram", "mandalika"), "Mataram", "WITA"),
        Triple(listOf("ambon", "maluku"), "Ambon", "WIT"),
        Triple(listOf("jayapura", "papua"), "Jayapura", "WIT")
    )

    fun analyzeText(rawText: String): GeoIntTextAnalysisResult {
        val text = rawText.trim()
        if (text.isEmpty()) {
            return GeoIntTextAnalysisResult(
                sourceTextLength = 0,
                hashtags = emptyList(),
                mentions = emptyList(),
                geoHits = emptyList(),
                recommendedPreset = null,
                summaryMessage = "Masukkan teks caption, deskripsi postingan, atau catatan untuk menganalisis petunjuk lokasi."
            )
        }

        val hashtags = Regex("""#[A-Za-z0-9_]+""")
            .findAll(text)
            .map { it.value }
            .distinct()
            .toList()

        val mentions = Regex("""@[A-Za-z0-9_.]+""")
            .findAll(text)
            .map { it.value }
            .distinct()
            .toList()

        val hits = mutableListOf<GeoEntityHit>()
        val lower = text.lowercase(Locale.US)

        // 1. City & regional gazetteer keywords
        for ((keywords, presetName, tz) in cityKeywords) {
            val matchedKw = keywords.firstOrNull { kw ->
                Regex("""(?<![a-z0-9])${Regex.escape(kw)}(?![a-z0-9])""").containsMatchIn(lower)
            }
            if (matchedKw != null) {
                hits += GeoEntityHit(
                    category = "Kata Kunci Kota / Wilayah",
                    matchedToken = matchedKw,
                    regionHint = "$presetName ($tz)",
                    suggestedTimezone = tz,
                    suggestedPresetName = presetName
                )
            }
        }

        // 2. Indonesian vehicle plate pattern (strict uppercase boundary: e.g. "B 1234 ABC" or "DK 889 XY")
        val plateRegex = Regex("""\b([A-Z]{1,2})\s+(\d{1,4})\s+([A-Z]{1,3})\b""")
        for (match in plateRegex.findAll(text)) {
            val prefix = match.groupValues[1]
            val region = platePrefixMap[prefix]
            if (region != null) {
                hits += GeoEntityHit(
                    category = "Pola Pelat Kendaraan",
                    matchedToken = match.value,
                    regionHint = region
                )
            }
        }

        // 3. Indonesian landline area codes (e.g., 021-..., (022) ..., +62 21 ...)
        for ((code, info) in areaCodeMap) {
            val noLeadingZero = code.removePrefix("0")
            val areaRegex = Regex("""(?:\b$code[\s\-)]|\+62[\s\-]?$noLeadingZero[\s\-])""")
            if (areaRegex.containsMatchIn(text)) {
                hits += GeoEntityHit(
                    category = "Kode Area Telepon",
                    matchedToken = code,
                    regionHint = "${info.first} (${info.second})",
                    suggestedTimezone = info.second
                )
            }
        }

        // 4. Explicit GPS coordinate pattern inside caption/notes (e.g. -6.2088, 106.8456)
        val coordRegex = Regex("""(-?\d{1,2}\.\d{3,6})\s*,\s*(-?\d{1,3}\.\d{3,6})""")
        for (match in coordRegex.findAll(text)) {
            val lat = match.groupValues[1].toDoubleOrNull()
            val lon = match.groupValues[2].toDoubleOrNull()
            if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
                hits += GeoEntityHit(
                    category = "Koordinat Tertulis",
                    matchedToken = match.value,
                    regionHint = "Koordinat eksplisit terdeteksi ($lat, $lon)"
                )
            }
        }

        val recommendedPreset = hits.firstNotNullOfOrNull { hit ->
            hit.suggestedPresetName?.let { name ->
                SolarGeoCalculator.indonesianCityPresets.firstOrNull { it.name == name }
            }
        }

        val summary = if (hits.isEmpty()) {
            "Tidak ditemukan nama wilayah, kode area, atau pola pelat kendaraan eksplisit pada teks (${hashtags.size} tagar)."
        } else {
            "Ditemukan ${hits.size} indikator geografis teks (${hits.joinToString { it.regionHint }})."
        }

        return GeoIntTextAnalysisResult(
            sourceTextLength = text.length,
            hashtags = hashtags,
            mentions = mentions,
            geoHits = hits,
            recommendedPreset = recommendedPreset,
            summaryMessage = summary
        )
    }

    /**
     * Aggregates Unix epoch timestamps (in seconds) across multiple posts into a 24-hour WIB distribution
     * to infer active waking window vs quiet/sleep window.
     */
    fun analyzeChronolocation(unixSecondsList: List<Long>): ChronolocationSummary? {
        val valid = unixSecondsList.filter { it > 1_300_000_000L }
        if (valid.isEmpty()) return null

        val buckets = IntArray(24)
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Jakarta"))
        for (sec in valid) {
            cal.timeInMillis = sec * 1000L
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            buckets[hour]++
        }

        val maxVal = buckets.maxOrNull() ?: 0
        val peakHours = buckets.indices.filter { buckets[it] == maxVal && maxVal > 0 }
        val peakWindow = if (peakHours.isNotEmpty()) {
            val start = peakHours.first()
            val end = (peakHours.last() + 1) % 24
            "%02d:00–%02d:00 WIB".format(start, end)
        } else {
            "-"
        }

        // Find longest contiguous zero-activity block (proxy for quiet/sleep window when sample >= 4)
        var bestZeroStart = 0
        var bestZeroLen = 0
        var curStart = -1
        var curLen = 0
        for (i in 0 until 48) {
            val h = i % 24
            if (buckets[h] == 0) {
                if (curStart < 0) curStart = h
                curLen++
                if (curLen > bestZeroLen && curLen <= 24) {
                    bestZeroLen = curLen
                    bestZeroStart = curStart
                }
            } else {
                curStart = -1
                curLen = 0
            }
        }
        val sleepWindow = if (valid.size >= 3 && bestZeroLen in 2..20) {
            val endHour = (bestZeroStart + bestZeroLen) % 24
            "%02d:00–%02d:00 WIB ($bestZeroLen jam senyap)".format(bestZeroStart, endHour)
        } else {
            "Perlu ≥ 3 postingan terdekode untuk pola jam senyap"
        }

        val inference = when {
            valid.size < 3 -> "Sampel baru ${valid.size} postingan; kumpulkan ≥ 3 URL untuk inferensi zona waktu."
            bestZeroStart in 22..23 || bestZeroStart in 0..4 ->
                "Jam senyap jatuh pada malam/dini hari WIB — konsisten dengan zona waktu Asia/Jakarta (UTC+7)."
            bestZeroStart in 19..21 ->
                "Jam senyap lebih awal di WIB — mengarah ke zona waktu lebih timur (WITA/WIT atau Asia Timur)."
            else ->
                "Pola unggah tersebar di jam yang tidak mengikuti siklus tidur malam WIB."
        }

        return ChronolocationSummary(
            sampleCount = valid.size,
            hourlyHistogramWib = buckets.toList(),
            peakActiveWindowWib = peakWindow,
            likelySleepWindowWib = sleepWindow,
            inferredTimezoneConsistency = inference
        )
    }
}
