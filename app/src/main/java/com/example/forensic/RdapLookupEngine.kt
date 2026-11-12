package com.example.forensic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object RdapLookupEngine {

    private val rdapClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun lookupIp(ipAddress: String): RdapIpInfo = withContext(Dispatchers.IO) {
        val cleanIp = ipAddress.trim()
        if (cleanIp.isBlank()) {
            return@withContext RdapIpInfo(queriedIp = "", errorMessage = "IP kosong.")
        }
        runCatching {
            val request = Request.Builder()
                .url("https://rdap.org/ip/$cleanIp")
                .header("Accept", "application/rdap+json, application/json")
                .header("User-Agent", "SignalTrace/1.0")
                .get()
                .build()

            rdapClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@use RdapIpInfo(
                        queriedIp = cleanIp,
                        errorMessage = "RDAP HTTP ${resp.code}"
                    )
                }
                val body = resp.body?.string().orEmpty()
                parseRdapJson(cleanIp, body, resp.request.url.host)
            }
        }.getOrElse { err ->
            RdapIpInfo(
                queriedIp = cleanIp,
                errorMessage = err.localizedMessage ?: "Gagal menghubungi server RDAP publik."
            )
        }
    }

    internal fun parseRdapJson(ipAddress: String, rawJson: String, registryHost: String? = null): RdapIpInfo {
        val root = JSONObject(rawJson)
        val netName = root.optString("name").takeIf { it.isNotBlank() }
        val handle = root.optString("handle").takeIf { it.isNotBlank() }
        val country = root.optString("country").takeIf { it.isNotBlank() }
        val port43 = root.optString("port43").takeIf { it.isNotBlank() }

        val cidrList = mutableListOf<String>()
        val cidrArr = root.optJSONArray("cidr0_cidrs")
        if (cidrArr != null) {
            for (i in 0 until cidrArr.length()) {
                val c = cidrArr.optJSONObject(i) ?: continue
                val prefix = c.optString("v4prefix").ifBlank { c.optString("v6prefix") }
                val len = c.optInt("length", -1)
                if (prefix.isNotBlank() && len >= 0) {
                    cidrList += "$prefix/$len"
                }
            }
        }
        val cidrStr = cidrList.firstOrNull() ?: run {
            val start = root.optString("startAddress")
            val end = root.optString("endAddress")
            if (start.isNotBlank() && end.isNotBlank()) "$start - $end" else null
        }

        val orgName = extractOrgNameFromEntities(root.optJSONArray("entities"))

        return RdapIpInfo(
            queriedIp = ipAddress,
            networkName = netName,
            handle = handle,
            cidr = cidrStr,
            countryCode = country,
            organizationName = orgName,
            registrySource = port43 ?: registryHost
        )
    }

    private fun extractOrgNameFromEntities(entities: JSONArray?): String? {
        if (entities == null) return null
        for (i in 0 until entities.length()) {
            val ent = entities.optJSONObject(i) ?: continue
            val vcard = ent.optJSONArray("vcardArray")
            if (vcard != null && vcard.length() >= 2) {
                val props = vcard.optJSONArray(1)
                if (props != null) {
                    for (j in 0 until props.length()) {
                        val row = props.optJSONArray(j) ?: continue
                        if (row.optString(0) == "fn" && row.length() >= 4) {
                            val fnVal = row.optString(3).trim()
                            if (fnVal.isNotBlank()) return fnVal
                        }
                    }
                }
            }
            extractOrgNameFromEntities(ent.optJSONArray("entities"))?.let { return it }
        }
        return null
    }
}
