package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.CorrelatorScreen
import com.example.ui.screens.DeviceAuditScreen
import com.example.ui.screens.JsonLabScreen
import com.example.ui.screens.TraceGeoScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.SignalTraceTheme
import com.example.ui.viewmodel.AnalysisUiState
import com.example.ui.viewmodel.DeviceAuditViewModel
import com.example.ui.viewmodel.ForensicViewModel
import com.example.ui.viewmodel.JsonLabViewModel

sealed class SignalDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Correlator : SignalDestination(
        route = "correlator",
        label = "Korelasi",
        selectedIcon = Icons.Filled.Hub,
        unselectedIcon = Icons.Outlined.Hub
    )
    data object TraceGeo : SignalDestination(
        route = "trace_geo",
        label = "Jejak",
        selectedIcon = Icons.Filled.Public,
        unselectedIcon = Icons.Outlined.Public
    )
    data object JsonLab : SignalDestination(
        route = "json_lab",
        label = "JSON Lab",
        selectedIcon = Icons.Filled.DataObject,
        unselectedIcon = Icons.Outlined.DataObject
    )
    data object DeviceAudit : SignalDestination(
        route = "device_audit",
        label = "Audit",
        selectedIcon = Icons.Filled.Security,
        unselectedIcon = Icons.Outlined.Security
    )
    data object Vault : SignalDestination(
        route = "vault",
        label = "Vault",
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder
    )
}

private val topDestinations = listOf(
    SignalDestination.Correlator,
    SignalDestination.TraceGeo,
    SignalDestination.JsonLab,
    SignalDestination.DeviceAudit,
    SignalDestination.Vault
)

class MainActivity : ComponentActivity() {

    private val forensicViewModel: ForensicViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingShareIntent(intent)

        setContent {
            SignalTraceTheme {
                SignalTraceApp(forensicViewModel = forensicViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            forensicViewModel.ingestSharedText(sharedText)
        }
    }
}

@Composable
fun SignalTraceApp(
    forensicViewModel: ForensicViewModel = viewModel(),
    jsonLabViewModel: JsonLabViewModel = viewModel(),
    deviceAuditViewModel: DeviceAuditViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: SignalDestination.Correlator.route

    val formState by forensicViewModel.formState.collectAsStateWithLifecycle()
    val geoIntState by forensicViewModel.geoIntState.collectAsStateWithLifecycle()
    val aiForensicState by forensicViewModel.aiForensicState.collectAsStateWithLifecycle()
    val chronolocation by forensicViewModel.chronolocationSummary.collectAsStateWithLifecycle()
    val availableChronoAccounts by forensicViewModel.availableChronoAccounts.collectAsStateWithLifecycle()
    val selectedChronoAccount by forensicViewModel.selectedChronoAccount.collectAsStateWithLifecycle()
    val vaultCases by forensicViewModel.vaultCases.collectAsStateWithLifecycle()
    val jsonState by jsonLabViewModel.uiState.collectAsStateWithLifecycle()
    val auditState by deviceAuditViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(formState.statusBannerMessage) {
        val msg = formState.statusBannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            forensicViewModel.clearBanner()
        }
    }

    LaunchedEffect(jsonState.statusMessage) {
        val msg = jsonState.statusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            jsonLabViewModel.clearStatus()
        }
    }

    val activeReport = (formState.analysisState as? AnalysisUiState.Ready)?.report

    fun navigateTo(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.route}")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = SignalDestination.Correlator.route,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(SignalDestination.Correlator.route) {
                        CorrelatorScreen(
                            formState = formState,
                            onProfileUrlChange = forensicViewModel::updateProfileUrl,
                            onPostUrlChange = forensicViewModel::updatePostUrl,
                            onAnalyzeOffline = forensicViewModel::runOfflineAnalysis,
                            onTraceNetwork = forensicViewModel::runNetworkTrace,
                            onApplyOwnerAsProfile = forensicViewModel::applyDiscoveredOwnerAsProfile,
                            onSaveVault = forensicViewModel::saveCurrentReportToVault,
                            onExportJson = { report ->
                                jsonLabViewModel.analyzeActiveReportJson(report)
                                navigateTo(SignalDestination.JsonLab.route)
                            },
                            onExportPdfUri = forensicViewModel::exportReportPdfToUri,
                            onExportHtmlUri = forensicViewModel::exportReportHtmlToUri,
                            onOpenTraceTab = { navigateTo(SignalDestination.TraceGeo.route) }
                        )
                    }

                    composable(SignalDestination.TraceGeo.route) {
                        TraceGeoScreen(
                            report = activeReport,
                            isTracing = formState.analysisState is AnalysisUiState.Tracing,
                            geoState = geoIntState,
                            aiState = aiForensicState,
                            chronolocation = chronolocation,
                            availableChronoAccounts = availableChronoAccounts,
                            selectedChronoAccount = selectedChronoAccount,
                            onSelectChronoAccount = forensicViewModel::selectChronoAccount,
                            onTriggerTrace = forensicViewModel::runNetworkTrace,
                            onCaptionChange = forensicViewModel::updateGeoIntCaption,
                            onSelectCity = forensicViewModel::selectCityPreset,
                            onCustomCoordsChange = forensicViewModel::updateCustomCoordinates,
                            onAnalyzeScreenshot = forensicViewModel::analyzeVisualScreenshot,
                            onAnalystNotesChange = forensicViewModel::updateAnalystNotes,
                            onInspectAiFile = forensicViewModel::inspectMediaFileForAi,
                            onManualAiInputChange = forensicViewModel::updateManualAiMetadataInput,
                            onAnalyzeManualAi = forensicViewModel::analyzeManualAiMetadata
                        )
                    }

                    composable(SignalDestination.JsonLab.route) {
                        JsonLabScreen(
                            uiState = jsonState,
                            activeReport = activeReport,
                            onRawJsonInputChange = jsonLabViewModel::updateRawJsonInput,
                            onAnalyzePastedJson = jsonLabViewModel::analyzePastedJson,
                            onAnalyzeActiveReport = jsonLabViewModel::analyzeActiveReportJson,
                            onExportReportUri = jsonLabViewModel::exportReportToUri,
                            onImportJsonUri = jsonLabViewModel::importAndAnalyzeUri,
                            onFilterSelected = jsonLabViewModel::setActionFilter,
                            onInspectRecordUrl = { url ->
                                forensicViewModel.ingestSharedText(url)
                                navigateTo(SignalDestination.Correlator.route)
                            }
                        )
                    }

                    composable(SignalDestination.DeviceAudit.route) {
                        DeviceAuditScreen(
                            uiState = auditState,
                            onRefreshDiagnostics = deviceAuditViewModel::refreshDiagnostics,
                            onRequestAndReadGps = deviceAuditViewModel::readOwnDeviceGps,
                            onLocationPermissionDenied = deviceAuditViewModel::onLocationPermissionDenied,
                            onMediaPicked = deviceAuditViewModel::inspectPickedMedia
                        )
                    }

                    composable(SignalDestination.Vault.route) {
                        VaultScreen(
                            cases = vaultCases,
                            onSelectCase = { entity ->
                                forensicViewModel.loadFromVault(entity)
                                navigateTo(SignalDestination.Correlator.route)
                            },
                            onDeleteCase = forensicViewModel::deleteVaultCase
                        )
                    }
                }
            }
        }
    }
}
