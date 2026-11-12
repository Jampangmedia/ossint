package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audit.DeviceAuditEngine
import com.example.audit.MediaExifAuditResult
import com.example.audit.NetworkAuditSnapshot
import com.example.audit.OwnGpsSnapshot
import com.example.audit.PermissionAuditItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DeviceAuditUiState(
    val permissions: List<PermissionAuditItem> = emptyList(),
    val networkSnapshot: NetworkAuditSnapshot? = null,
    val ownGpsSnapshot: OwnGpsSnapshot? = null,
    val isReadingGps: Boolean = false,
    val gpsStatusMessage: String? = null,
    val exifAuditResult: MediaExifAuditResult? = null,
    val isInspectingExif: Boolean = false,
    val exifErrorMessage: String? = null
)

class DeviceAuditViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DeviceAuditUiState())
    val uiState: StateFlow<DeviceAuditUiState> = _uiState.asStateFlow()

    init {
        refreshDiagnostics()
    }

    fun refreshDiagnostics() {
        val ctx = getApplication<Application>()
        _uiState.update {
            it.copy(
                permissions = DeviceAuditEngine.inspectPermissions(ctx),
                networkSnapshot = DeviceAuditEngine.inspectOwnNetwork(ctx)
            )
        }
    }

    fun readOwnDeviceGps() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isReadingGps = true, gpsStatusMessage = null) }
            val result = DeviceAuditEngine.readOwnDeviceGps(ctx)
            refreshDiagnostics()
            result.fold(
                onSuccess = { gps ->
                    _uiState.update {
                        it.copy(
                            ownGpsSnapshot = gps,
                            isReadingGps = false,
                            gpsStatusMessage = "Koordinat GPS perangkat sendiri berhasil dibaca."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isReadingGps = false,
                            gpsStatusMessage = err.localizedMessage ?: "Gagal membaca GPS."
                        )
                    }
                }
            )
        }
    }

    fun onLocationPermissionDenied() {
        refreshDiagnostics()
        _uiState.update {
            it.copy(
                gpsStatusMessage = "Izin lokasi ditolak. Audit GPS perangkat sendiri tetap nonaktif sesuai preferensi privasi Anda."
            )
        }
    }

    fun inspectPickedMedia(uri: Uri) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isInspectingExif = true, exifErrorMessage = null) }
            val result = DeviceAuditEngine.inspectMediaExif(ctx, uri)
            result.fold(
                onSuccess = { exif ->
                    _uiState.update {
                        it.copy(exifAuditResult = exif, isInspectingExif = false)
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isInspectingExif = false,
                            exifErrorMessage = err.localizedMessage ?: "Gagal membaca metadata EXIF."
                        )
                    }
                }
            )
        }
    }
}
