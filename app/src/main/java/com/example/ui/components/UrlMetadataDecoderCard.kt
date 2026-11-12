package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.forensic.DecodedUrlMetadata
import com.example.ui.theme.SignalTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UrlMetadataDecoderCard(
    decoded: DecodedUrlMetadata,
    onApplyOwnerAsProfile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors

    ForensicCard(
        modifier = modifier.testTag("url_metadata_decoder_card"),
        borderColor = colors.cyan.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "URLMetadataDecoder",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.cyan
                )
                Text(
                    text = "Dekomposisi user_id, post_id, referral & pelacakan pemilik asli",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PlatformBadge(platform = decoded.platform)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, colors.borderSubtle)
                ) {
                    Text(
                        text = decoded.urlKind.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Core decomposed components: user_id & post_id
        CopyableMonoRow(
            label = "user_id",
            value = decoded.userId ?: "Tidak eksplisit di URL (gunakan Lacak CDN & Hop)"
        )

        CopyableMonoRow(
            label = "post_id",
            value = decoded.postId ?: "-"
        )

        if (!decoded.mediaNumericId.isNullOrBlank() && decoded.mediaNumericId != decoded.postId) {
            CopyableMonoRow(
                label = "media_numeric_id (64-bit)",
                value = decoded.mediaNumericId
            )
        }

        if (!decoded.decodedTimestampWib.isNullOrBlank()) {
            CopyableMonoRow(
                label = "waktu_publikasi_terdekode",
                value = decoded.decodedTimestampWib
            )
        }

        // Owner resolution & source trace
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (decoded.canonicalOwnerUrl != null) {
                        Icons.Default.VerifiedUser
                    } else {
                        Icons.Default.AccountCircle
                    },
                    contentDescription = "Status pelacakan pemilik asli",
                    tint = if (decoded.canonicalOwnerUrl != null) colors.emerald else colors.amber,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Pelacakan Sumber Pemilik Asli",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "Metode: ${decoded.ownerResolutionMethod}" +
                    if (decoded.redirectHopsCount > 0) " • ${decoded.redirectHopsCount} hop HTTP" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!decoded.ownerDisplayName.isNullOrBlank()) {
                Text(
                    text = "Nama Pemilik (oEmbed/Meta): ${decoded.ownerDisplayName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.emerald
                )
            }

            val ownerUrl = decoded.canonicalOwnerUrl
            if (!ownerUrl.isNullOrBlank()) {
                CopyableMonoRow(
                    label = "canonical_owner_url",
                    value = ownerUrl
                )
                Button(
                    onClick = { onApplyOwnerAsProfile(ownerUrl) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("apply_owner_url_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.cyan,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Jadikan URL Profil")
                }
            }

            if (decoded.originalPostSourceUrl.isNotBlank()) {
                CopyableMonoRow(
                    label = "original_post_source_url",
                    value = decoded.originalPostSourceUrl
                )
            }
        }

        // Referral & tracking parameters
        Text(
            text = "referral_tracking_params (${decoded.referralTrackingParams.size}):",
            style = MaterialTheme.typography.labelMedium,
            color = if (decoded.referralTrackingParams.isNotEmpty()) {
                colors.amber
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        if (decoded.referralTrackingParams.isNotEmpty()) {
            val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                decoded.referralTrackingParams.forEach { (key, value) ->
                    FilterChip(
                        selected = true,
                        onClick = {
                            clipboard.setText(androidx.compose.ui.text.AnnotatedString("$key=$value"))
                        },
                        label = {
                            Text(
                                text = "$key=${value.take(18)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.amberContainer,
                            selectedLabelColor = colors.amber
                        ),
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
            decoded.shareTokens.forEach { token ->
                Text(
                    text = "• ${token.key}: ${token.forensicMeaning}" +
                        (token.decodedHint?.let { " → $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = "Tidak ada parameter referral/share tracking pada URL target.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (decoded.notes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                decoded.notes.forEach { note ->
                    Text(
                        text = "• $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
