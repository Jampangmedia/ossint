package com.example

import android.graphics.Bitmap
import android.graphics.Color
import com.example.forensic.ForensicReportExporter
import com.example.forensic.MultiPlatformUrlParser
import com.example.forensic.NetworkTraceResult
import com.example.forensic.RdapLookupEngine
import com.example.forensic.SocialIdDecoders
import com.example.forensic.SocialPlatform
import com.example.forensic.URLMetadataDecoder
import com.example.forensic.UrlKind
import com.example.geoint.GeoIntTextAnalyzer
import com.example.geoint.ImageForensicAnalyzer
import com.example.geoint.SolarGeoCalculator
import com.example.jsonlab.JsonForensicEngine
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ForensicEngineTest {

    @Test
    fun `tiktok 64 bit id decodes upper 32 bit unix timestamp`() {
        val breakdown = SocialIdDecoders.decodeTikTokSnowflake("7348921048571234567")
        assertNotNull(breakdown)
        assertEquals(7348921048571234567L ushr 32, breakdown!!.unixSeconds)
        assertEquals(64, breakdown.binary64.length)
    }

    @Test
    fun `instagram and threads base64 shortcode decodes media id and epoch`() {
        val ig = SocialIdDecoders.decodeMetaShortcode("C5xYz12AbCd", SocialPlatform.INSTAGRAM)
        val th = SocialIdDecoders.decodeMetaShortcode("C5xYz12AbCd", SocialPlatform.THREADS)
        assertNotNull(ig)
        assertNotNull(th)
        assertEquals(ig!!.unixSeconds, th!!.unixSeconds)
    }

    @Test
    fun `tiktok profile and post correlation yields high score and strips trackers`() {
        val report = MultiPlatformUrlParser.correlate(
            profileInput = "https://www.tiktok.com/@kreator_teknik",
            postInput = "https://www.tiktok.com/@kreator_teknik/video/7348921048571234567?_t=1711090000&_r=1&share_app_id=1233"
        )
        assertTrue(report.scorePercent >= 85)
        assertEquals(3, report.postParsed.strippedTrackers.size)
        assertEquals(
            "https://tiktok.com/@kreator_teknik/video/7348921048571234567",
            report.postParsed.canonicalUrl
        )
    }

    @Test
    fun `json forensic engine parses real export structure with nested timestamps`() {
        val sampleExportJson = """
            {
              "Activity": {
                "Like List": {
                  "ItemFavoriteList": [
                    { "Date": "2026-04-01 12:30:00", "Link": "https://www.tiktok.com/@creator/video/7348921048571234567" },
                    { "Date": "2026-04-01 13:45:00", "Link": "https://www.instagram.com/reel/C5xYz12AbCd/" }
                  ]
                },
                "Share History": {
                  "ShareHistoryList": [
                    { "Date": "2026-04-02 09:10:00", "Link": "https://youtu.be/dQw4w9WgXcQ?si=tracker123", "Method": "WhatsApp" }
                  ]
                },
                "Comment": {
                  "CommentsList": [
                    { "Date": "2026-04-02 10:00:00", "Comment": "Bagus!", "Link": "https://www.threads.net/@author/post/C5xYz12AbCd" }
                  ]
                }
              }
            }
        """.trimIndent()
        val result = JsonForensicEngine.analyzeJsonString(
            sampleExportJson,
            "Unit Test Export"
        )
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(4, summary.totalRecords)
        assertEquals(2, summary.likeCount)
        assertEquals(1, summary.commentCount)
        assertEquals(1, summary.shareCount)
        assertTrue(summary.topDestinations.isNotEmpty())
    }

    @Test
    fun `spoofed lookalike domains are rejected as unknown platform`() {
        val spoofed = MultiPlatformUrlParser.parse("https://not-tiktok.com/@user/video/7348921048571234567")
        assertEquals(SocialPlatform.UNKNOWN, spoofed.platform)

        val evilSub = MultiPlatformUrlParser.parse("https://tiktok.com.attacker.tld/@user/video/7348921048571234567")
        assertEquals(SocialPlatform.UNKNOWN, evilSub.platform)
    }

    @Test
    fun `referral tracking tokens do not inflate correlation score without canonical or timestamp proof`() {
        val withTracker = MultiPlatformUrlParser.correlate(
            profileInput = "https://www.youtube.com/@somechannel",
            postInput = "https://youtu.be/dQw4w9WgXcQ?si=tracking123"
        )
        val withoutTracker = MultiPlatformUrlParser.correlate(
            profileInput = "https://www.youtube.com/@somechannel",
            postInput = "https://youtu.be/dQw4w9WgXcQ"
        )
        assertEquals(withTracker.scorePercent, withoutTracker.scorePercent)
    }

    @Test
    fun `geoint text analyzer extracts city plate area code and hashtags`() {
        val res = GeoIntTextAnalyzer.analyzeText(
            "Macet di Jl. Dago Bandung, terlihat mobil D 1234 ABC dan kontak (022) 5551234 #bandung #infobdg"
        )
        assertEquals(2, res.hashtags.size)
        assertTrue(res.geoHits.any { it.category == "Kata Kunci Kota / Wilayah" && it.suggestedPresetName == "Bandung" })
        assertTrue(res.geoHits.any { it.category == "Pola Pelat Kendaraan" && it.matchedToken == "D 1234 ABC" })
        assertTrue(res.geoHits.any { it.category == "Kode Area Telepon" && it.matchedToken == "022" })
        assertEquals("Bandung", res.recommendedPreset?.name)
    }

    @Test
    fun `solar calculator computes daytime sun in Jakarta at 0500 UTC (1200 WIB) and night at 1700 UTC (0000 WIB)`() {
        // 2026-06-21T05:00:00Z = 1782018000 (12:00 WIB in Jakarta -> sun high in north sky)
        val noonJakarta = SolarGeoCalculator.calculate(
            unixSeconds = 1782018000L,
            latitude = -6.2088,
            longitude = 106.8456,
            locationLabel = "Jakarta"
        )
        assertTrue(noonJakarta.solarElevationDeg > 50.0)
        assertNotNull(noonJakarta.shadowLengthRatio)

        // 2026-06-21T17:00:00Z = 1782061200 (00:00 WIB in Jakarta -> midnight, sun well below horizon)
        val midnightJakarta = SolarGeoCalculator.calculate(
            unixSeconds = 1782061200L,
            latitude = -6.2088,
            longitude = 106.8456,
            locationLabel = "Jakarta"
        )
        assertTrue(midnightJakarta.solarElevationDeg < -40.0)
        assertNull(midnightJakarta.shadowLengthRatio)
    }

    @Test
    fun `chronolocation aggregates timestamps into 24h WIB histogram`() {
        // 3 timestamps at 09:00, 13:00, 13:00 WIB (02:00, 06:00, 06:00 UTC)
        val baseUtcMidnight = 1782000000L // 2026-06-21T00:00:00Z (07:00 WIB)
        val summary = GeoIntTextAnalyzer.analyzeChronolocation(
            listOf(
                baseUtcMidnight + 2 * 3600L, // 09:00 WIB
                baseUtcMidnight + 6 * 3600L, // 13:00 WIB
                baseUtcMidnight + 6 * 3600L  // 13:00 WIB
            )
        )
        assertNotNull(summary)
        assertEquals(3, summary!!.sampleCount)
        assertEquals(24, summary.hourlyHistogramWib.size)
        assertEquals(1, summary.hourlyHistogramWib[9])
        assertEquals(2, summary.hourlyHistogramWib[13])
    }

    @Test
    fun `url metadata decoder decomposes tiktok userId postId referral params and owner url`() {
        val decoder = URLMetadataDecoder()
        val decoded = decoder.decode(
            "https://www.tiktok.com/@kreator_teknik/video/7348921048571234567?_t=1711090000&_r=1&share_app_id=1233"
        )
        assertEquals(SocialPlatform.TIKTOK, decoded.platform)
        assertEquals(UrlKind.POST, decoded.urlKind)
        assertEquals("kreator_teknik", decoded.userId)
        assertEquals("7348921048571234567", decoded.postId)
        assertEquals("7348921048571234567", decoded.mediaNumericId)
        assertEquals(
            mapOf("_t" to "1711090000", "_r" to "1", "share_app_id" to "1233"),
            decoded.referralTrackingParams
        )
        assertEquals("https://www.tiktok.com/@kreator_teknik", decoded.canonicalOwnerUrl)
        assertNotNull(decoded.decodedTimestampWib)
    }

    @Test
    fun `url metadata decoder decomposes facebook permalink and pfbid post formats`() {
        val decoder = URLMetadataDecoder()

        val permalink = decoder.decode(
            "https://www.facebook.com/permalink.php?story_fbid=123456789012&id=987654321&mibextid=ZbWKwL&fbclid=IwAR3Test"
        )
        assertEquals(SocialPlatform.FACEBOOK, permalink.platform)
        assertEquals("987654321", permalink.userId)
        assertEquals("123456789012", permalink.postId)
        assertEquals("ZbWKwL", permalink.referralTrackingParams["mibextid"])
        assertEquals("IwAR3Test", permalink.referralTrackingParams["fbclid"])
        assertEquals("https://www.facebook.com/profile.php?id=987654321", permalink.canonicalOwnerUrl)

        val slugPost = decoder.decode(
            "https://www.facebook.com/komunitas_android/posts/pfbid02AbCdEfGh?ref=share"
        )
        assertEquals("komunitas_android", slugPost.userId)
        assertEquals("pfbid02AbCdEfGh", slugPost.postId)
        assertEquals("share", slugPost.referralTrackingParams["ref"])
        assertEquals("https://www.facebook.com/komunitas_android", slugPost.canonicalOwnerUrl)
    }

    @Test
    fun `url metadata decoder decomposes instagram and threads shortcodes and trackers`() {
        val decoder = URLMetadataDecoder()

        val ig = decoder.decode("https://www.instagram.com/reel/C5xYz12AbCd/?igsh=MW9xZ2Z3")
        assertEquals(SocialPlatform.INSTAGRAM, ig.platform)
        assertEquals("C5xYz12AbCd", ig.postId)
        assertNotNull(ig.mediaNumericId)
        assertEquals("MW9xZ2Z3", ig.referralTrackingParams["igsh"])
        assertNull(ig.canonicalOwnerUrl)

        val threads = decoder.decode("https://www.threads.net/@android_kernel_notes/post/C5xYz12AbCd?xmt=AQGzLab")
        assertEquals(SocialPlatform.THREADS, threads.platform)
        assertEquals("android_kernel_notes", threads.userId)
        assertEquals("C5xYz12AbCd", threads.postId)
        assertNotNull(threads.mediaNumericId)
        assertEquals("AQGzLab", threads.referralTrackingParams["xmt"])
        assertEquals("https://www.threads.net/@android_kernel_notes", threads.canonicalOwnerUrl)
    }

    @Test
    fun `url metadata decoder decomposes youtube video and channel ids`() {
        val decoder = URLMetadataDecoder()

        val video = decoder.decode("https://youtu.be/dQw4w9WgXcQ?si=AbCdEf123456")
        assertEquals(SocialPlatform.YOUTUBE, video.platform)
        assertEquals("dQw4w9WgXcQ", video.postId)
        assertEquals("AbCdEf123456", video.referralTrackingParams["si"])
        assertNull(video.canonicalOwnerUrl)

        val channel = decoder.decode("https://www.youtube.com/channel/UC1234567890abcdef")
        assertEquals(SocialPlatform.YOUTUBE, channel.platform)
        assertEquals("UC1234567890abcdef", channel.userId)
        assertEquals("https://www.youtube.com/channel/UC1234567890abcdef", channel.canonicalOwnerUrl)
    }

    @Test
    fun `rdap parser extracts asn org country and cidr block from rfc9083 json`() {
        val sampleRdapJson = """
            {
              "handle": "NET-104-16-0-0-1",
              "name": "CLOUDFLARENET",
              "country": "US",
              "port43": "whois.arin.net",
              "cidr0_cidrs": [
                { "v4prefix": "104.16.0.0", "length": 12 }
              ],
              "entities": [
                {
                  "roles": ["registrant"],
                  "vcardArray": [
                    "vcard",
                    [
                      ["version", {}, "text", "4.0"],
                      ["fn", {}, "text", "Cloudflare, Inc."]
                    ]
                  ]
                }
              ]
            }
        """.trimIndent()
        val info = RdapLookupEngine.parseRdapJson("104.16.1.1", sampleRdapJson, "rdap.arin.net")
        assertEquals("CLOUDFLARENET", info.networkName)
        assertEquals("NET-104-16-0-0-1", info.handle)
        assertEquals("104.16.0.0/12", info.cidr)
        assertEquals("US", info.countryCode)
        assertEquals("Cloudflare, Inc.", info.organizationName)
        assertEquals("whois.arin.net", info.registrySource)
    }

    @Test
    fun `json forensic engine aggregates referral params and domain hosts across entries`() {
        val raw = """
            {
              "shares": [
                { "postUrl": "https://youtu.be/abc123def45?si=track1", "timestamp": "2026-05-01 10:00:00" },
                { "postUrl": "https://youtu.be/xyz987uvw65?si=track2", "timestamp": "2026-05-01 11:00:00" }
              ]
            }
        """.trimIndent()
        val summary = JsonForensicEngine.analyzeJsonString(raw, "Test").getOrThrow()
        assertEquals("si", summary.topReferralParams.firstOrNull()?.destination)
        assertEquals(2, summary.topReferralParams.firstOrNull()?.count)
        assertEquals("youtu.be", summary.topShortlinkHosts.firstOrNull()?.destination)
        assertEquals(2, summary.topShortlinkHosts.firstOrNull()?.count)
    }

    @Test
    fun `ela computation detects localized tampered region delta and generates heatmap bitmap`() {
        val src = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(40, 80, 120))
            for (x in 5..14) {
                for (y in 5..14) {
                    setPixel(x, y, Color.rgb(220, 220, 220))
                }
            }
        }
        val rec = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(40, 80, 120))
            // Keep center significantly shifted in recompressed buffer
            for (x in 5..14) {
                for (y in 5..14) {
                    setPixel(x, y, Color.rgb(180, 180, 180))
                }
            }
        }
        val ela = ImageForensicAnalyzer.computeElaFromPair(src, rec)
        assertEquals(20, ela.heatmapBitmap.width)
        assertEquals(40, ela.maxDiff)
        assertTrue(ela.hotspotPercent >= 24.0f)
        src.recycle()
        rec.recycle()
    }

    @Test
    fun `forensic report exporter builds escaped html and non-empty pdf`() {
        val trace = NetworkTraceResult(
            targetUrl = "https://www.tiktok.com/@creator/video/7348921048571234567",
            finalUrl = "https://www.tiktok.com/@creator/video/7348921048571234567",
            edgeServerHost = "www.tiktok.com",
            edgeServerIps = listOf("23.50.10.1"),
            rdapInfo = RdapLookupEngine.parseRdapJson(
                "23.50.10.1",
                """{"name":"AKAMAI","country":"SG","cidr0_cidrs":[{"v4prefix":"23.50.0.0","length":16}]}"""
            )
        )
        val report = MultiPlatformUrlParser.correlate(
            profileInput = "https://www.tiktok.com/@creator",
            postInput = "https://www.tiktok.com/@creator/video/7348921048571234567",
            networkTrace = trace
        )
        val decoded = URLMetadataDecoder().decode(report.postParsed.originalUrl)
        val html = ForensicReportExporter.buildHtmlReport(report, decoded, null, null)
        assertTrue(html.contains("Laporan Audit Forensik"))
        assertTrue(html.contains("AKAMAI"))
        assertTrue(html.contains("23.50.0.0/16"))

        val pdfOut = ByteArrayOutputStream()
        ForensicReportExporter.writePdfReport(pdfOut, report, decoded, null, null)
        val pdfText = String(pdfOut.toByteArray(), Charsets.ISO_8859_1)
        assertTrue(pdfText.startsWith("%PDF-1.4"))
        assertTrue(pdfText.contains("AKAMAI"))
        assertTrue(pdfText.contains("\nxref\n"))
        assertTrue(pdfText.trimEnd().endsWith("%%EOF"))
    }
}
