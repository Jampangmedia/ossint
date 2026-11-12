package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.forensic.SnowflakeBreakdown
import com.example.forensic.SocialPlatform
import com.example.ui.theme.SignalTheme

@Composable
fun ForensicCard(
    modifier: Modifier = Modifier,
    borderColor: Color = SignalTheme.colors.borderSubtle,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun platformAccentColor(platform: SocialPlatform): Color {
    val colors = SignalTheme.colors
    return when (platform) {
        SocialPlatform.TIKTOK -> colors.emerald
        SocialPlatform.FACEBOOK -> colors.facebookBlue
        SocialPlatform.INSTAGRAM -> colors.instagramPink
        SocialPlatform.THREADS -> colors.threadsViolet
        SocialPlatform.YOUTUBE -> colors.youtubeRed
        SocialPlatform.UNKNOWN -> colors.textMuted
    }
}

@Composable
fun PlatformBadge(
    platform: SocialPlatform,
    modifier: Modifier = Modifier
) {
    val accent = platformAccentColor(platform)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Text(
            text = platform.displayName.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun CorrelationScoreGauge(
    scorePercent: Int,
    confidenceLabel: String,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val targetFraction = (scorePercent.coerceIn(0, 100) / 100f)
    val animatedSweep by animateFloatAsState(
        targetValue = targetFraction * 240f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "score_sweep"
    )
    val gaugeColor = when {
        scorePercent >= 85 -> colors.emerald
        scorePercent >= 60 -> colors.cyan
        scorePercent >= 35 -> colors.amber
        else -> colors.coral
    }
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(136.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(124.dp)) {
                val strokeWidth = 12.dp.toPx()
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
                drawArc(
                    color = trackColor,
                    startAngle = 150f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
                drawArc(
                    color = gaugeColor,
                    startAngle = 150f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$scorePercent%",
                    style = MaterialTheme.typography.displaySmall,
                    color = gaugeColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "SKOR",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            shape = RoundedCornerShape(50),
            color = gaugeColor.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, gaugeColor.copy(alpha = 0.4f))
        ) {
            Text(
                text = confidenceLabel,
                style = MaterialTheme.typography.labelMedium,
                color = gaugeColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun SnowflakeBitVisualizer(
    breakdown: SnowflakeBreakdown,
    modifier: Modifier = Modifier
) {
    val colors = SignalTheme.colors
    val tsRatio = breakdown.upperTimestampBits.length / 64f
    val seqRatio = 1f - tsRatio

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = breakdown.algorithmName,
            style = MaterialTheme.typography.labelMedium,
            color = colors.cyan
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
        ) {
            Box(
                modifier = Modifier
                    .weight(tsRatio)
                    .height(14.dp)
                    .background(colors.emerald)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .weight(seqRatio)
                    .height(14.dp)
                    .background(colors.cyan.copy(alpha = 0.5f))
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Timestamp (${breakdown.upperTimestampBits.length} bit): ${breakdown.upperTimestampBits.take(16)}…",
                style = MaterialTheme.typography.labelSmall,
                color = colors.emerald
            )
            Text(
                text = "Sequence (${breakdown.lowerSequenceBits.length} bit)",
                style = MaterialTheme.typography.labelSmall,
                color = colors.cyan
            )
        }
        Text(
            text = "ID Numerik: ${breakdown.rawId} • Unix: ${breakdown.unixSeconds}s",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CopyableMonoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onCopied: (() -> Unit)? = null
) {
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = {
                clipboard.setText(AnnotatedString(value))
                onCopied?.invoke()
            },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "Salin $label",
                tint = SignalTheme.colors.cyan,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
