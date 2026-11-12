package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.forensic.CorrelationReport
import com.example.jsonlab.InteractionActionType
import com.example.jsonlab.JsonAnalysisSummary
import com.example.jsonlab.JsonForensicEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class JsonLabUiState(
    val activeSummary: JsonAnalysisSummary? = null,
    val rawJsonInput: String = "",
    val selectedActionFilter: InteractionActionType? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

class JsonLabViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(JsonLabUiState())
    val uiState: StateFlow<JsonLabUiState> = _uiState.asStateFlow()

    fun setActionFilter(filter: InteractionActionType?) {
        _uiState.update { it.copy(selectedActionFilter = filter) }
    }

    fun updateRawJsonInput(raw: String) {
        _uiState.update { it.copy(rawJsonInput = raw) }
    }

    fun analyzePastedJson() {
        val raw = _uiState.value.rawJsonInput.trim()
        if (raw.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Tempel payload JSON atau impor file .json terlebih dahulu.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = withContext(Dispatchers.Default) {
                JsonForensicEngine.analyzeJsonString(raw, "Payload JSON Input Manual")
            }
            result.fold(
                onSuccess = { summary ->
                    _uiState.update {
                        it.copy(
                            activeSummary = summary,
                            isLoading = false,
                            selectedActionFilter = null,
                            statusMessage = "JSON manual berhasil dianalisis (${summary.totalRecords} entri)."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "JSON tidak valid: ${err.localizedMessage}")
                    }
                }
            )
        }
    }

    fun analyzeActiveReportJson(report: CorrelationReport) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = withContext(Dispatchers.Default) {
                val json = JsonForensicEngine.exportReportToJson(report)
                JsonForensicEngine.analyzeJsonString(json, "Laporan Korelasi #${report.reportId}")
            }
            result.fold(
                onSuccess = { summary ->
                    _uiState.update {
                        it.copy(
                            activeSummary = summary,
                            isLoading = false,
                            selectedActionFilter = null,
                            statusMessage = "JSON laporan aktif siap diinspeksi."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = err.localizedMessage) }
                }
            )
        }
    }

    fun exportReportToUri(uri: Uri, report: CorrelationReport) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = JsonForensicEngine.exportReportToJson(report)
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("Gagal membuka stream tujuan ekspor.")
                    json
                }
            }
            result.fold(
                onSuccess = { json ->
                    val summary = JsonForensicEngine.analyzeJsonString(
                        json,
                        "Ekspor Laporan #${report.reportId}"
                    ).getOrNull()
                    _uiState.update {
                        it.copy(
                            activeSummary = summary ?: it.activeSummary,
                            isLoading = false,
                            statusMessage = "File JSON berhasil diekspor ke lokasi pilihan Anda."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.localizedMessage ?: "Ekspor gagal.")
                    }
                }
            )
        }
    }

    fun importAndAnalyzeUri(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val content = getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: error("Gagal membaca file JSON.")
                    JsonForensicEngine.analyzeJsonString(content, uri.lastPathSegment ?: "File JSON Impor").getOrThrow()
                }
            }
            result.fold(
                onSuccess = { summary ->
                    _uiState.update {
                        it.copy(
                            activeSummary = summary,
                            isLoading = false,
                            selectedActionFilter = null,
                            statusMessage = "File JSON berhasil dimuat (${summary.totalRecords} entri)."
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = "JSON tidak valid: ${err.localizedMessage}")
                    }
                }
            )
        }
    }

    fun clearStatus() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}
