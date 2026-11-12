package com.example.forensic

import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SocialIdDecoders {

    private const val IG_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val IG_EPOCH_MS = 1314220021721L // 2011-08-24T21:07:01.721Z
    private const val MIN_VALID_UNIX_SEC = 1_300_000_000L // 2011
    private const val MAX_VALID_UNIX_SEC = 2_200_000_000L // 2039

    fun decodeTikTokSnowflake(rawId: String): SnowflakeBreakdown? {
        val id = rawId.trim().toLongOrNull() ?: return null
        if (id <= 0L) return null
        val unixSeconds = id ushr 32
        if (unixSeconds !in MIN_VALID_UNIX_SEC..MAX_VALID_UNIX_SEC) return null

        val binary64 = id.toULong().toString(2).padStart(64, '0')
        val upperBits = binary64.substring(0, 32)
        val lowerBits = binary64.substring(32, 64)
        val epochMs = unixSeconds * 1000L

        return SnowflakeBreakdown(
            rawId = rawId.trim(),
            algorithmName = "TikTok 64-bit Snowflake (id ushr 32)",
            binary64 = binary64,
            upperTimestampBits = upperBits,
            lowerSequenceBits = lowerBits,
            unixSeconds = unixSeconds,
            utcFormatted = formatEpoch(epochMs, "UTC"),
            wibFormatted = formatEpoch(epochMs, "Asia/Jakarta"),
            witaFormatted = formatEpoch(epochMs, "Asia/Makassar"),
            witFormatted = formatEpoch(epochMs, "Asia/Jayapura")
        )
    }

    fun decodeMetaShortcode(shortcode: String, platform: SocialPlatform): SnowflakeBreakdown? {
        val clean = shortcode.trim()
        if (clean.length < 8) return null
        // Standard 11-char shortcode encodes a 64-bit Instagram/Threads media ID;
        // longer private/share shortcodes encode media ID in the leading 11 characters.
        val mediaSegment = if (clean.length > 11) clean.substring(0, 11) else clean
        var acc = BigInteger.ZERO
        val base = BigInteger.valueOf(64L)
        for (ch in mediaSegment) {
            val idx = IG_ALPHABET.indexOf(ch)
            if (idx < 0) return null
            acc = acc.multiply(base).add(BigInteger.valueOf(idx.toLong()))
        }
        if (acc > BigInteger.valueOf(Long.MAX_VALUE) || acc <= BigInteger.ZERO) return null

        val mediaId = acc.toLong()
        val offsetMs = mediaId ushr 23
        val epochMs = offsetMs + IG_EPOCH_MS
        val unixSeconds = epochMs / 1000L
        if (unixSeconds !in MIN_VALID_UNIX_SEC..MAX_VALID_UNIX_SEC) return null

        val binary64 = mediaId.toULong().toString(2).padStart(64, '0')
        val upperBits = binary64.substring(0, 41)
        val lowerBits = binary64.substring(41, 64)

        return SnowflakeBreakdown(
            rawId = mediaId.toString(),
            algorithmName = "${platform.displayName} Base64URL → 64-bit Media ID ((id ushr 23) + Meta Epoch)",
            binary64 = binary64,
            upperTimestampBits = upperBits,
            lowerSequenceBits = lowerBits,
            unixSeconds = unixSeconds,
            utcFormatted = formatEpoch(epochMs, "UTC"),
            wibFormatted = formatEpoch(epochMs, "Asia/Jakarta"),
            witaFormatted = formatEpoch(epochMs, "Asia/Makassar"),
            witFormatted = formatEpoch(epochMs, "Asia/Jayapura")
        )
    }

    fun formatEpoch(epochMs: Long, tzId: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone(tzId)
        return sdf.format(Date(epochMs))
    }
}
