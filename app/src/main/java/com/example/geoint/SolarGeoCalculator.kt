package com.example.geoint

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class GeoCoordinatePreset(
    val name: String,
    val province: String,
    val latitude: Double,
    val longitude: Double,
    val timezoneLabel: String,
    val utcOffsetHours: Int
)

data class SolarPositionResult(
    val unixSeconds: Long,
    val locationLabel: String,
    val latitude: Double,
    val longitude: Double,
    val solarElevationDeg: Double,
    val solarAzimuthDeg: Double,
    val shadowDirectionDeg: Double,
    val shadowLengthRatio: Double?,
    val solarNoonLocalHint: String,
    val daylightPhase: String,
    val compassDirectionSun: String,
    val compassDirectionShadow: String
)

object SolarGeoCalculator {

    val indonesianCityPresets = listOf(
        GeoCoordinatePreset("Jakarta", "DKI Jakarta", -6.2088, 106.8456, "WIB", 7),
        GeoCoordinatePreset("Bandung", "Jawa Barat", -6.9175, 107.6191, "WIB", 7),
        GeoCoordinatePreset("Bogor", "Jawa Barat", -6.5971, 106.8060, "WIB", 7),
        GeoCoordinatePreset("Semarang", "Jawa Tengah", -6.9667, 110.4167, "WIB", 7),
        GeoCoordinatePreset("Yogyakarta", "DI Yogyakarta", -7.7956, 110.3695, "WIB", 7),
        GeoCoordinatePreset("Surabaya", "Jawa Timur", -7.2575, 112.7521, "WIB", 7),
        GeoCoordinatePreset("Malang", "Jawa Timur", -7.9666, 112.6326, "WIB", 7),
        GeoCoordinatePreset("Medan", "Sumatera Utara", 3.5952, 98.6722, "WIB", 7),
        GeoCoordinatePreset("Palembang", "Sumatera Selatan", -2.9761, 104.7754, "WIB", 7),
        GeoCoordinatePreset("Padang", "Sumatera Barat", -0.9471, 100.4172, "WIB", 7),
        GeoCoordinatePreset("Pekanbaru", "Riau", 0.5071, 101.4478, "WIB", 7),
        GeoCoordinatePreset("Pontianak", "Kalimantan Barat", -0.0263, 109.3425, "WIB", 7),
        GeoCoordinatePreset("Denpasar / Bali", "Bali", -8.6705, 115.2126, "WITA", 8),
        GeoCoordinatePreset("Makassar", "Sulawesi Selatan", -5.1477, 119.4327, "WITA", 8),
        GeoCoordinatePreset("Balikpapan", "Kalimantan Timur", -1.2379, 116.8529, "WITA", 8),
        GeoCoordinatePreset("Banjarmasin", "Kalimantan Selatan", -3.3194, 114.5908, "WITA", 8),
        GeoCoordinatePreset("Manado", "Sulawesi Utara", 1.4748, 124.8421, "WITA", 8),
        GeoCoordinatePreset("Mataram", "NTB", -8.5833, 116.1167, "WITA", 8),
        GeoCoordinatePreset("Ambon", "Maluku", -3.6954, 128.1814, "WIT", 9),
        GeoCoordinatePreset("Jayapura", "Papua", -2.5916, 140.6690, "WIT", 9)
    )

    /**
     * Computes true solar elevation, solar azimuth, shadow direction, and shadow length ratio
     * using NOAA Solar Position equations from a Unix epoch timestamp (seconds) and WGS84 lat/lon.
     */
    fun calculate(
        unixSeconds: Long,
        latitude: Double,
        longitude: Double,
        locationLabel: String,
        utcOffsetHours: Int = 7
    ): SolarPositionResult {
        val clampedLat = latitude.coerceIn(-89.9, 89.9)
        val clampedLon = longitude.coerceIn(-180.0, 180.0)

        // Julian Day from Unix epoch (1970-01-01T00:00:00Z = JD 2440587.5)
        val julianDay = (unixSeconds / 86400.0) + 2440587.5
        val julianCentury = (julianDay - 2451545.0) / 36525.0

        // Geometric Mean Longitude of Sun (deg)
        val l0 = normalizeDeg(280.46646 + julianCentury * (36000.76983 + 0.0003032 * julianCentury))
        // Geometric Mean Anomaly of Sun (deg)
        val m = normalizeDeg(357.52911 + julianCentury * (35999.05029 - 0.0001537 * julianCentury))
        val mRad = toRad(m)

        // Eccentricity of Earth's orbit
        val e = 0.016708634 - julianCentury * (0.000042037 + 0.0000001267 * julianCentury)

        // Sun Equation of Center
        val c = sin(mRad) * (1.914602 - julianCentury * (0.004817 + 0.000014 * julianCentury)) +
            sin(2.0 * mRad) * (0.019993 - 0.000101 * julianCentury) +
            sin(3.0 * mRad) * 0.000289

        val trueLong = l0 + c
        val omega = 125.04 - 1934.136 * julianCentury
        val apparentLong = trueLong - 0.00569 - 0.00478 * sin(toRad(omega))

        // Mean and corrected Obliquity of Ecliptic (deg)
        val meanObliq = 23.0 + (26.0 + (21.448 - julianCentury * (46.815 + julianCentury * (0.00059 - julianCentury * 0.001813))) / 60.0) / 60.0
        val obliqCorr = meanObliq + 0.00256 * cos(toRad(omega))

        // Solar Declination (rad)
        val sinDeclin = sin(toRad(obliqCorr)) * sin(toRad(apparentLong))
        val declinRad = asin(sinDeclin.coerceIn(-1.0, 1.0))

        // Equation of Time (minutes)
        val y = tan(toRad(obliqCorr / 2.0)).let { it * it }
        val l0Rad = toRad(l0)
        val eqOfTimeMin = 4.0 * toDeg(
            y * sin(2.0 * l0Rad) -
                2.0 * e * sin(mRad) +
                4.0 * e * y * sin(mRad) * cos(2.0 * l0Rad) -
                0.5 * y * y * sin(4.0 * l0Rad) -
                1.25 * e * e * sin(2.0 * mRad)
        )

        // UTC minutes since midnight
        val utcSecondsInDay = ((unixSeconds % 86400L) + 86400L) % 86400L
        val utcMinutes = utcSecondsInDay / 60.0

        // True Solar Time (minutes)
        val trueSolarTimeMin = ((utcMinutes + eqOfTimeMin + 4.0 * clampedLon) % 1440.0 + 1440.0) % 1440.0

        // Hour Angle (deg)
        val hourAngleDeg = if (trueSolarTimeMin / 4.0 < 0.0) {
            trueSolarTimeMin / 4.0 + 180.0
        } else {
            trueSolarTimeMin / 4.0 - 180.0
        }
        val haRad = toRad(hourAngleDeg)
        val latRad = toRad(clampedLat)

        // Solar Zenith Angle (rad)
        val cosZenith = (sin(latRad) * sin(declinRad) + cos(latRad) * cos(declinRad) * cos(haRad))
            .coerceIn(-1.0, 1.0)
        val zenithRad = acos(cosZenith)
        val zenithDeg = toDeg(zenithRad)
        val elevationDeg = 90.0 - zenithDeg

        // Solar Azimuth Angle (deg clockwise from North)
        val azimuthDeg = if (abs(cos(latRad) * sin(zenithRad)) < 1e-9) {
            180.0
        } else {
            val rawAcosArg = ((sin(latRad) * cosZenith - sin(declinRad)) / (cos(latRad) * sin(zenithRad)))
                .coerceIn(-1.0, 1.0)
            val baseDeg = toDeg(acos(rawAcosArg))
            if (hourAngleDeg > 0.0) {
                normalizeDeg(baseDeg + 180.0)
            } else {
                normalizeDeg(540.0 - baseDeg)
            }
        }

        val shadowDirDeg = normalizeDeg(azimuthDeg + 180.0)
        val shadowRatio = if (elevationDeg > 0.5) {
            1.0 / tan(toRad(elevationDeg))
        } else {
            null
        }

        // Solar Noon in local timezone
        val noonMinutes = ((720.0 - 4.0 * clampedLon - eqOfTimeMin + utcOffsetHours * 60.0) % 1440.0 + 1440.0) % 1440.0
        val noonHour = (noonMinutes / 60.0).toInt()
        val noonMin = (noonMinutes % 60.0).toInt()
        val solarNoonStr = "%02d:%02d".format(noonHour, noonMin)

        val phase = when {
            elevationDeg >= 35.0 -> "Siang Terik (Bayangan Pendek & Tajam)"
            elevationDeg >= 12.0 -> "Pagi / Sore Cerah (Bayangan Menengah)"
            elevationDeg >= 0.0 -> "Golden Hour / Terbit-Terbenam (Bayangan Sangat Panjang)"
            elevationDeg >= -6.0 -> "Twilight Sipil (Langit Remang, Tanpa Bayangan Matahari)"
            elevationDeg >= -18.0 -> "Twilight Nautika / Astronomis (Gelap)"
            else -> "Malam Penuh (Matahari Di Bawah Horizon)"
        }

        return SolarPositionResult(
            unixSeconds = unixSeconds,
            locationLabel = locationLabel,
            latitude = clampedLat,
            longitude = clampedLon,
            solarElevationDeg = elevationDeg,
            solarAzimuthDeg = azimuthDeg,
            shadowDirectionDeg = shadowDirDeg,
            shadowLengthRatio = shadowRatio,
            solarNoonLocalHint = solarNoonStr,
            daylightPhase = phase,
            compassDirectionSun = degreesToCompass(azimuthDeg),
            compassDirectionShadow = degreesToCompass(shadowDirDeg)
        )
    }

    private fun degreesToCompass(deg: Double): String {
        val dirs = listOf(
            "Utara (U)", "Timur Laut (TL)", "Timur (T)", "Tenggara (TG)",
            "Selatan (S)", "Barat Daya (BD)", "Barat (B)", "Barat Laut (BL)"
        )
        val idx = (((normalizeDeg(deg) + 22.5) / 45.0).toInt()) % 8
        return dirs[idx]
    }

    private fun normalizeDeg(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0
    private fun toRad(deg: Double): Double = deg * PI / 180.0
    private fun toDeg(rad: Double): Double = rad * 180.0 / PI
}
