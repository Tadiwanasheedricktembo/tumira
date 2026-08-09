package com.querycubix.queryshare.data.model

import org.json.JSONObject

data class QrPayload(
    val deviceName: String,
    val ip: String,
    val alternateIps: List<String> = emptyList(),
    val port: Int,
    val protocol: String,
    val version: String
)

fun parseQrPayload(rawValue: String): QrPayload? {
    return try {
        android.util.Log.d("QrPayload", "PARSE_START: $rawValue")
        val json = JSONObject(rawValue)
        
        // Parse IP - required field
        val ip = json.optString("ip").takeIf { it.isNotBlank() } ?: run { android.util.Log.e("QrPayload", "PARSE_FAILED: no IP"); return null }
        
        // Parse port - must be valid number in range
        val rawPort = json.optInt("port", -1)
        val port = rawPort.takeIf { it in 1..65535 } ?: run { android.util.Log.e("QrPayload", "PARSE_FAILED: invalid port $rawPort"); return null }
        
        // Parse protocol - case-insensitive check for http
        val protocol = json.optString("protocol").takeIf { it.isNotBlank() } ?: run { android.util.Log.e("QrPayload", "PARSE_FAILED: no protocol"); return null }
        if (protocol.lowercase() != "http" && protocol.lowercase() != "https") { 
            android.util.Log.e("QrPayload", "PARSE_FAILED: unsupported protocol '$protocol'"); return null 
        }
        
        // Validate IP format - allow IPv4 addresses
        if (ip.contains(':')) { android.util.Log.e("QrPayload", "PARSE_FAILED: IP contains colon"); return null }
        if (!isValidIpAddress(ip)) { android.util.Log.e("QrPayload", "PARSE_FAILED: invalid IP format '$ip'"); return null }

        val deviceName = json.optString("deviceName").ifBlank { "Tumira Device" }
        val version = json.optString("version").ifBlank { "1.0" }
        val alternateIps = parseAlternateIps(json).filter { it != ip }

        val payload = QrPayload(
            deviceName = deviceName,
            ip = ip,
            alternateIps = alternateIps,
            port = port,
            protocol = protocol.lowercase(),
            version = version
        )
        android.util.Log.d("QrPayload", "PARSE_SUCCESS: $payload")
        payload
    } catch (exception: Exception) {
        android.util.Log.e("QrPayload", "PARSE_EXCEPTION: ${exception.message}", exception)
        null
    }
}

private fun parseAlternateIps(json: JSONObject): List<String> {
    val values = mutableListOf<String>()
    json.optJSONArray("alternateIps")?.let { alternates ->
        for (index in 0 until alternates.length()) {
            alternates.optString(index).takeIf { isValidIpAddress(it) }?.let(values::add)
        }
    }

    json.optJSONArray("interfaces")?.let { interfaces ->
        for (index in 0 until interfaces.length()) {
            interfaces.optJSONObject(index)
                ?.optString("address")
                ?.takeIf { isValidIpAddress(it) }
                ?.let(values::add)
        }
    }

    return values.distinct()
}

private fun isValidIpAddress(ip: String): Boolean {
    return try {
        // Split into parts
        val parts = ip.split(".")
        if (parts.size != 4) return false
        
        // Check each octet
        parts.all { part ->
            part.isNotEmpty() && part.toIntOrNull()?.let { it in 0..255 } ?: false
        }
    } catch (e: Exception) {
        false
    }
}
