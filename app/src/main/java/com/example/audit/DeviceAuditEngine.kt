package com.example.audit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.ExifInterface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

data class PermissionAuditItem(
    val permissionName: String,
    val label: String,
    val granted: Boolean,
    val purpose: String
)

data class NetworkAuditSnapshot(
    val isConnected: Boolean,
    val transportLabel: String,
    val interfaceName: String,
    val localIps: List<String>,
    val dnsServers: List<String>,
    val isVpnActive: Boolean
)

data class OwnGpsSnapshot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val provider: String,
    val capturedAt: String
)

data class MediaExifAuditResult(
    val fileNameHint: String,
    val hasGpsCoordinates: Boolean,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val capturedDateTime: String? = null,
    val deviceMakeModel: String? = null,
    val privacySummary: String
)

object DeviceAuditEngine {

    fun inspectPermissions(context: Context): List<PermissionAuditItem> {
        val checks = listOf(
            Triple(
                Manifest.permission.INTERNET,
                "Akses Internet (Install-Time)",
                "Resolusi redirect tautan dan inspeksi header server CDN."
            ),
            Triple(
                Manifest.permission.ACCESS_NETWORK_STATE,
                "Status Jaringan (Install-Time)",
                "Membaca antarmuka Wi-Fi/Seluler dan DNS aktif perangkat sendiri."
            ),
            Triple(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                "Lokasi Perkiraan (Runtime)",
                "Opsional untuk uji baca lokasi kasar perangkat sendiri."
            ),
            Triple(
                Manifest.permission.ACCESS_FINE_LOCATION,
                "GPS Presisi (Runtime)",
                "Opsional untuk uji baca koordinat GPS perangkat sendiri saat tombol ditekan."
            )
        )
        return checks.map { (perm, label, purpose) ->
            PermissionAuditItem(
                permissionName = perm,
                label = label,
                granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED,
                purpose = purpose
            )
        }
    }

    fun inspectOwnNetwork(context: Context): NetworkAuditSnapshot {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkAuditSnapshot(false, "Tidak Tersedia", "-", emptyList(), emptyList(), false)

        val activeNetwork = cm.activeNetwork
            ?: return NetworkAuditSnapshot(false, "Offline / Tidak Terhubung", "-", emptyList(), emptyList(), false)

        val caps = cm.getNetworkCapabilities(activeNetwork)
        val linkProps = cm.getLinkProperties(activeNetwork)

        val transports = buildList {
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) add("Wi-Fi")
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) add("Seluler")
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true) add("Ethernet")
            if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) add("VPN")
        }.ifEmpty { listOf("Jaringan Aktif") }

        val ips = linkProps?.linkAddresses
            ?.mapNotNull { it.address?.hostAddress }
            ?.sortedByDescending { addr ->
                // Prioritize IPv4 for readability
                runCatching { Inet4Address.getByName(addr) is Inet4Address }.getOrDefault(false)
            }
            ?: emptyList()

        val dns = linkProps?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()

        return NetworkAuditSnapshot(
            isConnected = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            transportLabel = transports.joinToString(" + "),
            interfaceName = linkProps?.interfaceName ?: "-",
            localIps = ips,
            dnsServers = dns,
            isVpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        )
    }

    @SuppressLint("MissingPermission")
    suspend fun readOwnDeviceGps(context: Context): Result<OwnGpsSnapshot> = withContext(Dispatchers.Main) {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            return@withContext Result.failure(
                SecurityException("Izin lokasi belum diberikan. Ketuk tombol untuk mengizinkan uji GPS perangkat sendiri.")
            )
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@withContext Result.failure(IllegalStateException("LocationManager tidak tersedia."))

        val provider = when {
            hasFine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> LocationManager.PASSIVE_PROVIDER
        }

        val freshLocation: Location? = withTimeoutOrNull(4500L) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                try {
                    LocationManagerCompat.getCurrentLocation(
                        lm,
                        provider,
                        signal,
                        ContextCompat.getMainExecutor(context)
                    ) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }

        val resolved = freshLocation
            ?: runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            ?: runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()

        if (resolved == null) {
            return@withContext Result.failure(
                IllegalStateException("GPS aktif belum mengembalikan koordinat (pastikan layanan lokasi emulator/HP menyala).")
            )
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        Result.success(
            OwnGpsSnapshot(
                latitude = resolved.latitude,
                longitude = resolved.longitude,
                accuracyMeters = resolved.accuracy,
                provider = resolved.provider ?: provider,
                capturedAt = sdf.format(Date(resolved.time))
            )
        )
    }

    suspend fun inspectMediaExif(context: Context, uri: Uri): Result<MediaExifAuditResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: error("Tidak dapat membuka file media terpilih.")
                inputStream.use { stream ->
                    val exif = ExifInterface(stream)
                    val latLong = FloatArray(2)
                    @Suppress("DEPRECATION")
                    val hasLatLong = exif.getLatLong(latLong)
                    val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                        ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    val make = exif.getAttribute(ExifInterface.TAG_MAKE)
                    val model = exif.getAttribute(ExifInterface.TAG_MODEL)
                    val makeModel = listOfNotNull(make, model).joinToString(" ").takeIf { it.isNotBlank() }

                    val summary = if (hasLatLong) {
                        "PERINGATAN PRIVASI: File ini menyimpan koordinat GPS asli. Platform seperti TikTok/Instagram biasanya menghapus EXIF saat unggah, tetapi pengiriman sebagai dokumen mentah tetap membocorkan lokasi."
                    } else {
                        "AMAN: Tidak ditemukan tag koordinat GPS EXIF pada file media ini."
                    }

                    MediaExifAuditResult(
                        fileNameHint = uri.lastPathSegment ?: "Media Terpilih",
                        hasGpsCoordinates = hasLatLong,
                        latitude = if (hasLatLong) latLong[0].toDouble() else null,
                        longitude = if (hasLatLong) latLong[1].toDouble() else null,
                        capturedDateTime = dateTime,
                        deviceMakeModel = makeModel,
                        privacySummary = summary
                    )
                }
            }
        }
}
