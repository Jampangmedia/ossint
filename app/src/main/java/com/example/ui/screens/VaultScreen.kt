package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ForensicCaseEntity
import com.example.ui.components.ForensicCard
import com.example.ui.theme.SignalTheme

@Composable
fun VaultScreen(
    cases: List<ForensicCaseEntity>,
    onSelectCase: (ForensicCaseEntity) -> Unit,
    onDeleteCase: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    var searchQuery by remember { mutableStateOf("") }
    var selectedPlatform by remember { mutableStateOf<String?>(null) }

    val platformOptions = listOf("TikTok", "Facebook", "Instagram", "Threads", "YouTube")

    val filteredCases = remember(cases, searchQuery, selectedPlatform) {
        cases.filter { item ->
            val matchesPlatform = selectedPlatform == null ||
                item.platform.equals(selectedPlatform, ignoreCase = true)
            val q = searchQuery.trim().lowercase()
            val matchesQuery = q.isEmpty() ||
                item.title.lowercase().contains(q) ||
                item.profileUrl.lowercase().contains(q) ||
                item.postUrl.lowercase().contains(q)
            matchesPlatform && matchesQuery
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Vault Investigasi Lokal",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Riwayat korelasi URL & metadata yang tersimpan secara privat di perangkat (Room SQLite).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Cari kasus, handle, atau URL") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = colors.cyan)
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_search_input")
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedPlatform == null,
                            onClick = { selectedPlatform = null },
                            label = { Text("Semua") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                    items(platformOptions) { platform ->
                        FilterChip(
                            selected = selectedPlatform == platform,
                            onClick = {
                                selectedPlatform = if (selectedPlatform == platform) null else platform
                            },
                            label = { Text(platform) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }

            if (filteredCases.isEmpty()) {
                item {
                    ForensicCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = colors.cyan
                            )
                            Text(
                                text = if (cases.isEmpty()) {
                                    "Vault masih kosong. Simpan hasil analisis dari tab Korelasi untuk membangun arsip kasus."
                                } else {
                                    "Tidak ada kasus yang cocok dengan filter pencarian."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredCases, key = { it.id }) { item ->
                    ForensicCard(
                        modifier = Modifier
                            .clickable { onSelectCase(item) }
                            .testTag("vault_case_${item.id}")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Skor ${item.correlationScore}% • ${item.confidenceLabel}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (item.correlationScore >= 85) colors.emerald else colors.cyan
                                )
                            }
                            IconButton(
                                onClick = { onDeleteCase(item.id) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("delete_vault_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Hapus kasus ${item.title}",
                                    tint = colors.coral
                                )
                            }
                        }
                        if (item.postUrl.isNotBlank()) {
                            Text(
                                text = item.postUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "Waktu Publikasi: ${item.decodedTimestampWib}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.amber
                        )
                        Text(
                            text = "GEOINT: ${item.geoIntSummary}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.cyan,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}
