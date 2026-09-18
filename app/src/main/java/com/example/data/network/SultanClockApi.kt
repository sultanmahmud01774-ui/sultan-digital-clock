package com.example.data.network

import android.util.Log
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.*
import java.util.concurrent.TimeUnit

open class Esp32Api {

    companion object {
        private const val TAG = "Esp32Api"
        private const val DEFAULT_TIMEOUT_SEC = 5L
        private const val LONG_TIMEOUT_SEC = 15L
    }

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private fun getClientWithAuth(username: String, password: String): OkHttpClient {
        return baseClient.newBuilder()
            .authenticator { _, response ->
                if (response.request.header("Authorization") != null) {
                    null // Already attempted authentication, don't loop
                } else {
                    val credential = Credentials.basic(username, password)
                    response.request.newBuilder()
                        .header("Authorization", credential)
                        .build()
                }
            }
            .build()
    }

    private fun formatBaseUrl(hostOrIp: String): String {
        var clean = hostOrIp.trim()
        if (clean.endsWith("/")) clean = clean.substring(0, clean.length - 1)
        return if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            "http://$clean"
        } else {
            clean
        }
    }

    /**
     * Check TCP connection to candidate host and port 80
     */
    suspend fun pingHost(hostOrIp: String, port: Int = 80, timeoutMs: Int = 2000): Boolean =
        withContext(Dispatchers.IO) {
            try {
                var cleanHost = hostOrIp.replace("http://", "").replace("https://", "")
                if (cleanHost.contains("/")) cleanHost = cleanHost.substringBefore("/")
                if (cleanHost.contains(":")) cleanHost = cleanHost.substringBefore(":")

                Socket().use { socket ->
                    socket.connect(InetSocketAddress(cleanHost, port), timeoutMs)
                    true
                }
            } catch (e: Exception) {
                false
            }
        }

    /**
     * Test connection and authentication using /checkauth with fallback to root /
     */
    suspend fun checkConnection(
        host: String,
        user: String = "admin",
        pass: String = ""
    ): Pair<ConnectionStatus, String> = withContext(Dispatchers.IO) {
        val checkAuthUrl = "${formatBaseUrl(host)}/checkauth"
        try {
            val client = getClientWithAuth(user, pass)
            val request = Request.Builder()
                .url(checkAuthUrl)
                .addHeader("Authorization", Credentials.basic(user, pass))
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> ConnectionStatus.CONNECTED to "Connected successfully"
                    401 -> ConnectionStatus.AUTH_REQUIRED to "Authentication required (401)"
                    404 -> {
                        // Fallback to root / if /checkauth is not defined
                        val rootReq = Request.Builder()
                            .url("${formatBaseUrl(host)}/")
                            .addHeader("Authorization", Credentials.basic(user, pass))
                            .get()
                            .build()
                        client.newCall(rootReq).execute().use { rootResp ->
                            when (rootResp.code) {
                                200 -> ConnectionStatus.CONNECTED to "Connected successfully"
                                401 -> ConnectionStatus.AUTH_REQUIRED to "Authentication required (401)"
                                else -> ConnectionStatus.ERROR to "HTTP status: ${rootResp.code}"
                            }
                        }
                    }
                    else -> ConnectionStatus.ERROR to "HTTP status: ${response.code}"
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Connection failed to $checkAuthUrl: ${e.message}")
            ConnectionStatus.DISCONNECTED to (e.localizedMessage ?: "ESP32 offline or unreachable")
        } catch (e: Exception) {
            ConnectionStatus.ERROR to (e.localizedMessage ?: "Connection error")
        }
    }

    /**
     * Fetch live clock status: tries /api/status first, falls back to parsing root / HTML
     */
    suspend fun getStatus(
        host: String,
        user: String = "admin",
        pass: String = ""
    ): ClockDashboardData = withContext(Dispatchers.IO) {
        val client = getClientWithAuth(user, pass)

        // 1. Try /api/status first (if firmware provides JSON)
        try {
            val apiUrl = "${formatBaseUrl(host)}/api/status"
            val request = Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", Credentials.basic(user, pass))
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful && body.trim().startsWith("{")) {
                    return@withContext parseStatusJson(body, host)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "/api/status not available, falling back to root HTML: ${e.message}")
        }

        // 2. Fetch root / (Web UI HTML) and parse live telemetry
        try {
            val rootUrl = "${formatBaseUrl(host)}/"
            val request = Request.Builder()
                .url(rootUrl)
                .addHeader("Authorization", Credentials.basic(user, pass))
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val html = response.body?.string() ?: ""
                if (response.isSuccessful && html.isNotBlank()) {
                    return@withContext parseRootHtml(html, host)
                } else if (response.code == 401) {
                    Log.w(TAG, "getStatus 401 Unauthorized for $host")
                    return@withContext ClockDashboardData(ipAddress = host)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getStatus error: ${e.message}")
        }

        ClockDashboardData(ipAddress = host)
    }

    /**
     * Parse JSON returned by /api/status endpoint
     */
    fun parseStatusJson(jsonString: String, host: String): ClockDashboardData {
        return try {
            val json = JSONObject(jsonString)
            val isApMode = json.optBoolean("apMode", false)
            val isWifiConn = json.optBoolean("wifiConnected", true)
            val wifiSsid = json.optString("wifiSsid", "")
            val bangla = if (json.has("banglaDate") && !json.isNull("banglaDate")) {
                val b = json.optString("banglaDate")
                if (b.isNotBlank() && b != "null") b else null
            } else null

            // Parse prayerTimes object if present
            val prayerTimes = if (json.has("prayerTimes") && !json.isNull("prayerTimes")) {
                val ptObj = json.optJSONObject("prayerTimes")
                if (ptObj != null) {
                    PrayerTimes(
                        fajr = ptObj.optString("fajr", "04:12"),
                        sunrise = ptObj.optString("sunrise", "05:28"),
                        dhuhr = ptObj.optString("dhuhr", "12:05"),
                        asr = ptObj.optString("asr", "16:35"),
                        maghrib = ptObj.optString("maghrib", "18:32"),
                        isha = ptObj.optString("isha", "19:48")
                    )
                } else null
            } else null

            // azanWaqtEnabled array (5 booleans: Fajr, Dhuhr, Asr, Maghrib, Isha)
            val azanWaqtEnabled = mutableListOf<Boolean>()
            val azanWaqtArr = json.optJSONArray("azanWaqtEnabled")
            if (azanWaqtArr != null) {
                for (i in 0 until azanWaqtArr.length()) {
                    azanWaqtEnabled.add(azanWaqtArr.optBoolean(i, true))
                }
            }
            val finalAzanWaqtEnabled = if (azanWaqtEnabled.isNotEmpty()) azanWaqtEnabled else listOf(true, true, true, true, true)

            // azanTrack array (5 ints, same order)
            val azanTrack = mutableListOf<Int>()
            val azanTrackArr = json.optJSONArray("azanTrack")
            if (azanTrackArr != null) {
                for (i in 0 until azanTrackArr.length()) {
                    azanTrack.add(azanTrackArr.optInt(i, i + 1))
                }
            }
            val finalAzanTrack = if (azanTrack.isNotEmpty()) azanTrack else listOf(1, 2, 3, 4, 5)

            // alarms array
            val alarms = mutableListOf<ClockAlarmStatus>()
            val alarmsArr = json.optJSONArray("alarms")
            if (alarmsArr != null) {
                for (i in 0 until alarmsArr.length()) {
                    val aObj = alarmsArr.optJSONObject(i)
                    if (aObj != null) {
                        alarms.add(
                            ClockAlarmStatus(
                                hour = aObj.optInt("hour", 0),
                                minute = aObj.optInt("minute", 0),
                                enabled = aObj.optBoolean("enabled", false),
                                track = aObj.optInt("track", 1)
                            )
                        )
                    }
                }
            }
            val finalAlarms = if (alarms.isNotEmpty()) alarms else listOf(
                ClockAlarmStatus(6, 30, false, 1),
                ClockAlarmStatus(18, 30, false, 2)
            )

            // weeklyPlaylist array (today's resolved track and 7-day tracks array)
            val weeklyPlaylist = mutableListOf<WeeklyPlaylistTodayStatus>()
            val wpArr = json.optJSONArray("weeklyPlaylist")
            if (wpArr != null) {
                for (i in 0 until wpArr.length()) {
                    val wpObj = wpArr.optJSONObject(i)
                    if (wpObj != null) {
                        val todayTrk = wpObj.optInt("todayTrack", 10)
                        val tracksList = mutableListOf<Int>()
                        val tracksArr = wpObj.optJSONArray("tracks")
                        if (tracksArr != null) {
                            for (t in 0 until tracksArr.length()) {
                                tracksList.add(tracksArr.optInt(t, todayTrk))
                            }
                        }
                        val finalTracks = if (tracksList.size == 7) {
                            tracksList
                        } else if (tracksList.isNotEmpty()) {
                            tracksList + List((7 - tracksList.size).coerceAtLeast(0)) { todayTrk }
                        } else {
                            listOf(todayTrk, todayTrk, todayTrk, todayTrk, todayTrk, todayTrk, todayTrk)
                        }

                        weeklyPlaylist.add(
                            WeeklyPlaylistTodayStatus(
                                enabled = wpObj.optBoolean("enabled", false),
                                time = wpObj.optString("time", "08:00"),
                                todayTrack = todayTrk,
                                tracks = finalTracks
                            )
                        )
                    }
                }
            }
            val finalWeeklyPlaylist = if (weeklyPlaylist.isNotEmpty()) weeklyPlaylist else listOf(
                WeeklyPlaylistTodayStatus(true, "08:00", 10, listOf(10, 10, 10, 11, 11, 15, 10)),
                WeeklyPlaylistTodayStatus(false, "14:00", 12, listOf(12, 12, 12, 12, 12, 12, 12))
            )

            val connLabel = if (isApMode) {
                "Connected via Clock's Hotspot (AP mode)"
            } else if (isWifiConn) {
                if (wifiSsid.isNotBlank()) "Connected via Home WiFi: $wifiSsid" else "Connected via Home WiFi"
            } else {
                "WiFi Disconnected"
            }

            ClockDashboardData(
                currentTimeStr = json.optString("time", "00:00:00"),
                currentDateStr = json.optString("date", ""),
                banglaDate = bangla,
                temperatureC = json.optDouble("temperature", 28.5).toFloat(),
                is12Hour = json.optInt("hourFormat", 0) == 1,
                isDisplayOn = json.optBoolean("displayOn", true),
                isLightOn = json.optBoolean("lightOn", false),
                isPrayerAlarmOn = json.optBoolean("prayerAlarmEnabled", false),
                isTempSensorOn = json.optBoolean("tempSensorEnabled", true),
                ldrRaw = json.optInt("ldrRaw", 450),
                appliedBrightness = json.optInt("brightness", 128),
                autoLdr = json.optBoolean("autoLdr", false),
                colorMode = json.optInt("colorMode", 0),
                playlistEnabled = json.optBoolean("playlistEnabled", false),
                playlistStep = json.optInt("playlistStep", 0),
                playlistCount = json.optInt("playlistCount", 0),
                hourlyChimeEnabled = json.optBoolean("hourlyChimeEnabled", false),
                hourlyChimeMode = json.optInt("hourlyChimeMode", 0),
                dfConnected = json.optBoolean("dfPlayerOk", false),
                dfVolume = json.optInt("dfVolume", 22),
                wifiConnected = isWifiConn,
                apMode = isApMode,
                wifiSsid = json.optString("wifiSsid", ""),
                ipAddress = json.optString("ip", host),
                connectionType = connLabel,
                firmwareVersion = "v5.0-ESP32",
                prayerTimes = prayerTimes,
                azanWaqtEnabled = finalAzanWaqtEnabled,
                azanTrack = finalAzanTrack,
                alarms = finalAlarms,
                weeklyPlaylist = finalWeeklyPlaylist
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse JSON status: ${e.message}")
            ClockDashboardData(ipAddress = host)
        }
    }

    /**
     * Parse HTML returned by the ESP32 root (/) Web UI
     * Extracts live clock telemetry, sensors, hardware status, and saved configs
     */
    fun parseRootHtml(html: String, host: String): ClockDashboardData {
        return try {
            fun extractRegex(pattern: String, default: String = ""): String {
                val match = Regex(pattern, RegexOption.IGNORE_CASE).find(html)
                return match?.groups?.get(1)?.value?.trim() ?: default
            }

            fun hasChecked(id: String): Boolean {
                val pattern = "id=['\"]$id['\"][^>]*checked|checked[^>]*id=['\"]$id['\"]"
                return Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(html)
            }

            // 1. Live Time & Date
            val rawTime = extractRegex("<div[^>]*class=['\"][^'\"]*time-main[^'\"]*['\"][^>]*>([^<]+)</div>", "12:00:00")
            val rawDate = extractRegex("<div[^>]*class=['\"][^'\"]*time-date[^'\"]*['\"][^>]*>([^<]+)</div>", "")
            val bangla = extractRegex("বাংলা:\\s*([0-9/]+|[^<\\s]+)").takeIf { it.isNotBlank() }

            // 2. Hardware Switch States
            val isDisplayOn = extractRegex("id=['\"]displaystatus['\"][^>]*class=['\"]status-([a-zA-Z]+)['\"]", "on").equals("on", ignoreCase = true)
            val isLightOn = extractRegex("id=['\"]lightstatus['\"][^>]*class=['\"]status-([a-zA-Z]+)['\"]", "off").equals("on", ignoreCase = true)
            val isTempSensorOn = extractRegex("id=['\"]tempstatus['\"][^>]*class=['\"]status-([a-zA-Z]+)['\"]", "on").equals("on", ignoreCase = true)
            val tempVal = extractRegex("([0-9.]+)\\s*&deg;C", "28.5").toFloatOrNull() ?: 28.5f
            val isPrayerAlarmOn = extractRegex("id=['\"]prayerstatus['\"][^>]*class=['\"]status-([a-zA-Z]+)['\"]", "on").equals("on", ignoreCase = true)

            // 3. Prayer Times
            val fajrTime = extractRegex("Fajr<br>([0-9:]+)", "04:12")
            val sunriseTime = extractRegex("Sunrise<br>([0-9:]+)", "05:28")
            val dhuhrTime = extractRegex("Dhuhr<br>([0-9:]+)", "12:05")
            val asrTime = extractRegex("Asr<br>([0-9:]+)", "16:35")
            val maghribTime = extractRegex("Maghrib<br>([0-9:]+)", "18:32")
            val ishaTime = extractRegex("Isha<br>([0-9:]+)", "19:48")
            val prayerTimes = PrayerTimes(
                fajr = fajrTime,
                sunrise = sunriseTime,
                dhuhr = dhuhrTime,
                asr = asrTime,
                maghrib = maghribTime,
                isha = ishaTime,
                isAzanAlarmEnabled = isPrayerAlarmOn
            )

            // 4. DFPlayer & Audio
            val dfConnected = html.contains("DFPlayer Mini ready", ignoreCase = true)
            val dfVol = extractRegex("id=['\"]dfvol['\"][^>]*value=['\"]([0-9]+)['\"]", "22").toIntOrNull() ?: 22
            val hourlyChimeEnabled = hasChecked("hourlybeep2") || hasChecked("hourlybeep")
            val hourlyChimeMode = extractRegex("value=['\"]([0-3])['\"][^>]*name=['\"]hmode['\"][^>]*checked|name=['\"]hmode['\"][^>]*value=['\"]([0-3])['\"][^>]*checked", "0").toIntOrNull() ?: 0

            // 5. Brightness & LDR Telemetry
            val autoLdr = hasChecked("autoldr")
            val ldrRaw = extractRegex("Live LDR Raw:\\s*<strong>([0-9]+)</strong>", "450").toIntOrNull() ?: 450
            val appliedBrightness = extractRegex("Applied Brightness:\\s*<strong>([0-9]+)</strong>", "128").toIntOrNull()
                ?: extractRegex("id=['\"]bright['\"][^>]*value=['\"]([0-9]+)['\"]", "128").toIntOrNull() ?: 128

            // 6. Color Mode & Playlist
            val colorMode = extractRegex("<select[^>]*id=['\"]colormode['\"][^>]*>.*?<option value=['\"]([0-4])['\"]\\s*selected", "0").toIntOrNull() ?: 0
            val playlistOn = extractRegex("id=['\"]plstatus['\"][^>]*class=['\"]status-([a-zA-Z]+)['\"]", "off").equals("on", ignoreCase = true)

            // 7. Network Status
            val wifiSsid = extractRegex("Connected:\\s*<strong>(.*?)</strong>", extractRegex("id=['\"]wifissid['\"][^>]*value=['\"](.*?)['\"]", ""))
            val ipAddress = extractRegex("IP:\\s*([0-9.]+)", host)
            val isWifiConn = wifiSsid.isNotBlank() && !html.contains("Wi-Fi disconnected", ignoreCase = true)
            val isApMode = html.contains("SoftAP", ignoreCase = true) || host.contains("192.168.4.1")

            // 8. 12/24 Hour format
            val is12Hour = hasChecked("fmt12")

            // 9. Dual Alarms
            val a0Time = extractRegex("id=['\"]alarm0['\"][^>]*value=['\"]([0-9:]+)['\"]", "06:30").split(":")
            val a0H = a0Time.getOrNull(0)?.toIntOrNull() ?: 6
            val a0M = a0Time.getOrNull(1)?.toIntOrNull() ?: 30
            val a0En = hasChecked("en0")
            val a0Track = extractRegex("id=['\"]al0['\"][^>]*value=['\"]([0-9]+)['\"]", "1").toIntOrNull() ?: 1

            val a1Time = extractRegex("id=['\"]alarm1['\"][^>]*value=['\"]([0-9:]+)['\"]", "18:30").split(":")
            val a1H = a1Time.getOrNull(0)?.toIntOrNull() ?: 18
            val a1M = a1Time.getOrNull(1)?.toIntOrNull() ?: 30
            val a1En = hasChecked("en1")
            val a1Track = extractRegex("id=['\"]al1['\"][^>]*value=['\"]([0-9]+)['\"]", "2").toIntOrNull() ?: 2

            val alarms = listOf(
                ClockAlarmStatus(hour = a0H, minute = a0M, enabled = a0En, track = a0Track),
                ClockAlarmStatus(hour = a1H, minute = a1M, enabled = a1En, track = a1Track)
            )

            // 10. Azan Tracks
            val azanTracks = (0..4).map { i ->
                extractRegex("id=['\"]az$i['\"][^>]*value=['\"]([0-9]+)['\"]", "${i + 1}").toIntOrNull() ?: (i + 1)
            }

            ClockDashboardData(
                currentTimeStr = rawTime,
                currentDateStr = rawDate,
                banglaDate = bangla,
                temperatureC = tempVal,
                is12Hour = is12Hour,
                isDisplayOn = isDisplayOn,
                isLightOn = isLightOn,
                isPrayerAlarmOn = isPrayerAlarmOn,
                isTempSensorOn = isTempSensorOn,
                ldrRaw = ldrRaw,
                appliedBrightness = appliedBrightness,
                autoLdr = autoLdr,
                colorMode = colorMode,
                playlistEnabled = playlistOn,
                hourlyChimeEnabled = hourlyChimeEnabled,
                hourlyChimeMode = hourlyChimeMode,
                dfConnected = dfConnected,
                dfVolume = dfVol,
                wifiConnected = isWifiConn,
                apMode = isApMode,
                wifiSsid = wifiSsid,
                ipAddress = ipAddress,
                connectionType = if (isApMode) "Clock Hotspot (AP)" else if (isWifiConn) "Wi-Fi LAN" else "Disconnected",
                firmwareVersion = "v5.0-ESP32",
                prayerTimes = prayerTimes,
                azanTrack = azanTracks,
                alarms = alarms
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse HTML status: ${e.message}")
            ClockDashboardData(ipAddress = host)
        }
    }

    /**
     * Fetch dashboard status (delegates to /api/status JSON endpoint)
     */
    suspend fun fetchDashboardData(
        host: String,
        user: String = "admin",
        pass: String = ""
    ): ClockDashboardData = getStatus(host, user, pass)

    /**
     * Helper to send GET command to ESP32 with body-aware success detection
     */
    suspend fun sendGet(
        host: String,
        endpoint: String,
        params: Map<String, String> = emptyMap(),
        user: String = "admin",
        pass: String = ""
    ): ActionResponse = withContext(Dispatchers.IO) {
        try {
            val baseUrl = formatBaseUrl(host)
            val urlString = baseUrl + if (endpoint.startsWith("/")) endpoint else "/$endpoint"
            val parsed = urlString.toHttpUrlOrNull() ?: return@withContext ActionResponse(false, "Invalid URL: $urlString")
            val httpUrlBuilder = parsed.newBuilder()

            params.forEach { (k, v) ->
                httpUrlBuilder.addQueryParameter(k, v)
            }

            val fullUrl = httpUrlBuilder.build()
            val client = getClientWithAuth(user, pass)
            val request = Request.Builder()
                .url(fullUrl)
                .addHeader("Authorization", Credentials.basic(user, pass))
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()?.trim() ?: ""
                when {
                    response.code == 401 -> ActionResponse(false, "Authentication failed (401)")
                    !response.isSuccessful -> ActionResponse(false, "ESP32 returned HTTP ${response.code}", responseBody)
                    responseBody == "OK" || responseBody == "ON" || responseBody == "OFF" ->
                        ActionResponse(true, responseBody, responseBody)
                    responseBody == "CONFLICT" ->
                        ActionResponse(true, "Saved, but Alarm 1 and Alarm 2 are set to the same time — only one will ring", responseBody)
                    else ->
                        // Firmware returns HTTP 200 with error message body on validation failures
                        ActionResponse(false, responseBody, responseBody)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendGet failed on $endpoint", e)
            ActionResponse(false, e.localizedMessage ?: "Failed to reach ESP32")
        }
    }

    // 1. Phone Time Synchronization
    suspend fun syncTimeFromPhone(host: String, user: String, pass: String): ActionResponse {
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val s = cal.get(Calendar.SECOND)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        val epoch = (System.currentTimeMillis() / 1000).toString()

        val params = mapOf(
            "epoch" to epoch,
            "h" to h.toString(),
            "m" to m.toString(),
            "s" to s.toString(),
            "d" to day.toString(),
            "mo" to month.toString(),
            "y" to year.toString()
        )
        return sendGet(host, "/sync", params, user, pass)
    }

    // 2. NTP Time Synchronization
    suspend fun syncFromNtp(host: String, user: String, pass: String): ActionResponse {
        return sendGet(host, "/ntpsync", emptyMap(), user, pass)
    }

    // 3. Quick Toggles
    suspend fun toggleDisplay(host: String, user: String, pass: String): ActionResponse {
        return sendGet(host, "/toggledisplay", emptyMap(), user, pass)
    }

    suspend fun toggleLight(host: String, user: String, pass: String): ActionResponse {
        return sendGet(host, "/togglelight", emptyMap(), user, pass)
    }

    suspend fun togglePrayerAlarm(host: String, user: String, pass: String): ActionResponse {
        return sendGet(host, "/toggleprayeralarm", emptyMap(), user, pass)
    }

    suspend fun toggleTempSensor(host: String, user: String, pass: String): ActionResponse {
        return sendGet(host, "/toggletempsensor", emptyMap(), user, pass)
    }

    // 4. Date Settings
    suspend fun saveDateSettings(
        host: String,
        config: DateSettingsConfig,
        user: String,
        pass: String
    ): ActionResponse {
        val params = mapOf(
            "eng" to if (config.isDateEnabled) "1" else "0",
            "bangla" to if (config.isBanglaDate) "1" else "0"
        )
        return sendGet(host, "/savedatesettings", params, user, pass)
    }

    // 5. Alarm Settings (TWO separate calls, one per alarm, with i,h,m,e)
    suspend fun saveAlarm(
        host: String,
        config: AlarmConfig,
        user: String,
        pass: String
    ): ActionResponse {
        val r1 = sendGet(
            host, "/savealarm", mapOf(
                "i" to "0",
                "h" to config.alarm1Hour.toString(),
                "m" to config.alarm1Minute.toString(),
                "e" to if (config.alarm1Enabled) "1" else "0"
            ), user, pass
        )
        if (!r1.isSuccess) return r1
        return sendGet(
            host, "/savealarm", mapOf(
                "i" to "1",
                "h" to config.alarm2Hour.toString(),
                "m" to config.alarm2Minute.toString(),
                "e" to if (config.alarm2Enabled) "1" else "0"
            ), user, pass
        )
    }

    // 6. Brightness Control
    suspend fun saveBrightness(
        host: String,
        config: BrightnessConfig,
        user: String,
        pass: String
    ): ActionResponse {
        val params = mapOf(
            "b" to config.manualBrightness.toString(),
            "a" to if (config.autoLdr) "1" else "0",
            "lc" to config.ldrLowCut.toString(),
            "hc" to config.ldrHighCut.toString()
        )
        return sendGet(host, "/savebright", params, user, pass)
    }

    // 7. Color Control
    suspend fun saveColor(
        host: String,
        config: ColorConfig,
        user: String,
        pass: String
    ): ActionResponse {
        val params = mapOf(
            "m" to config.mode.toString(),
            "sc" to config.staticColorIndex.toString(),
            "ci" to config.colorIntervalSec.toString(),
            "r" to config.red.toString(),
            "g" to config.green.toString(),
            "b" to config.blue.toString(),
            "spd" to config.animSpeed.toString()
        )
        return sendGet(host, "/savecolor", params, user, pass)
    }

    // 8. Display Schedule
    suspend fun saveDisplaySchedule(
        host: String,
        config: DisplayScheduleConfig,
        user: String,
        pass: String
    ): ActionResponse {
        val params = mapOf(
            "auto" to if (config.isScheduleEnabled) "1" else "0",
            "offh" to config.startHour.toString(),
            "offm" to config.startMinute.toString(),
            "onh" to config.endHour.toString(),
            "onm" to config.endMinute.toString()
        )
        return sendGet(host, "/savedisplayschedule", params, user, pass)
    }

    // 9. DFPlayer Volume & Track Test
    suspend fun saveDfVolume(host: String, volume: Int, user: String, pass: String): ActionResponse {
        val params = mapOf("vol" to volume.coerceIn(0, 30).toString())
        return sendGet(host, "/savedfvolume", params, user, pass)
    }

    suspend fun testDfTrack(host: String, track: Int, user: String, pass: String): ActionResponse {
        val params = mapOf("idx" to track.toString())
        return sendGet(host, "/testdftrack", params, user, pass)
    }

    // 10. Hourly Chime
    suspend fun saveHourlyMode(
        host: String,
        config: HourlyChimeConfig,
        user: String,
        pass: String
    ): ActionResponse {
        var mask = 0
        config.poolTracks.forEach { track -> mask = mask or (1 shl (track - 1)) }
        val params = mapOf(
            "mode" to config.mode.toString(),
            "fixed" to config.fixedTrack.toString(),
            "mask" to mask.toString(),
            "byhour" to config.hourlyTracks.joinToString(",")
        )
        return sendGet(host, "/savehourlymode", params, user, pass)
    }

    suspend fun saveToneRange(
        host: String,
        enabled: Boolean,
        startHour: Int,
        endHour: Int,
        user: String,
        pass: String
    ): ActionResponse {
        val params = mapOf(
            "en" to if (enabled) "1" else "0",
            "sh" to startHour.toString(),
            "eh" to endHour.toString()
        )
        return sendGet(host, "/savetonerange", params, user, pass)
    }

    // 11. Azan & Alarm Track Assignments and Waqt Toggles
    suspend fun saveTrackAssignments(
        host: String,
        config: TrackAssignmentsConfig,
        user: String,
        pass: String
    ): ActionResponse {
        val params = mapOf(
            "az0" to config.fajrTrack.toString(),
            "az1" to config.dhuhrTrack.toString(),
            "az2" to config.asrTrack.toString(),
            "az3" to config.maghribTrack.toString(),
            "az4" to config.ishaTrack.toString(),
            "al0" to config.alarm1Track.toString(),
            "al1" to config.alarm2Track.toString()
        )
        val trackRes = sendGet(host, "/savetracks", params, user, pass)
        if (!trackRes.isSuccess) return trackRes

        return saveWaqtAzan(
            host = host,
            fajr = config.fajrEnabled,
            dhuhr = config.dhuhrEnabled,
            asr = config.asrEnabled,
            maghrib = config.maghribEnabled,
            isha = config.ishaEnabled,
            user = user,
            pass = pass
        )
    }

    suspend fun saveWaqtAzan(
        host: String,
        fajr: Boolean,
        dhuhr: Boolean,
        asr: Boolean,
        maghrib: Boolean,
        isha: Boolean,
        user: String,
        pass: String
    ): ActionResponse {
        val params = mapOf(
            "fajr" to if (fajr) "1" else "0",
            "dhuhr" to if (dhuhr) "1" else "0",
            "asr" to if (asr) "1" else "0",
            "maghrib" to if (maghrib) "1" else "0",
            "isha" to if (isha) "1" else "0"
        )
        return sendGet(host, "/savewaqtazan", params, user, pass)
    }

    suspend fun saveDisplaySettings(
        host: String,
        is12Hour: Boolean,
        showDate: Boolean,
        colonBlink: Boolean,
        hourlyBeep: Boolean,
        user: String,
        pass: String
    ): ActionResponse {
        val params = mapOf(
            "f" to if (is12Hour) "1" else "0",
            "sd" to if (showDate) "1" else "0",
            "cb" to if (colonBlink) "1" else "0",
            "hb" to if (hourlyBeep) "1" else "0"
        )
        return sendGet(host, "/savesettings", params, user, pass)
    }

    // 12. Weekly Playlist (Slots 0 and 1, Sunday to Saturday t0..t6)
    suspend fun saveWeeklyPlaylist(
        host: String,
        slots: List<WeeklyPlaylistSlot>,
        user: String,
        pass: String
    ): ActionResponse {
        val params = mutableMapOf<String, String>()
        slots.take(2).forEachIndexed { index, slot ->
            val p = "wp${index}_"
            params[p + "en"] = if (slot.enabled) "1" else "0"
            params[p + "h"] = slot.hour.toString()
            params[p + "m"] = slot.minute.toString()
            // t0=Sun t1=Mon t2=Tue t3=Wed t4=Thu t5=Fri t6=Sat
            params[p + "t0"] = slot.sunTrack.toString()
            params[p + "t1"] = slot.monTrack.toString()
            params[p + "t2"] = slot.tueTrack.toString()
            params[p + "t3"] = slot.wedTrack.toString()
            params[p + "t4"] = slot.thuTrack.toString()
            params[p + "t5"] = slot.friTrack.toString()
            params[p + "t6"] = slot.satTrack.toString()
        }
        return sendGet(host, "/saveweeklyplaylist", params, user, pass)
    }

    // 13. Color Playlist
    suspend fun saveColorPlaylist(
        host: String,
        config: ColorPlaylistConfig,
        user: String,
        pass: String
    ): ActionResponse {
        val params = mutableMapOf("en" to "1", "cnt" to config.steps.size.coerceAtMost(5).toString())
        config.steps.take(5).forEachIndexed { i, step ->
            params["m$i"] = step.mode.toString()
            params["ci$i"] = step.colorIndex.toString()
            params["r$i"] = step.red.toString()
            params["g$i"] = step.green.toString()
            params["b$i"] = step.blue.toString()
            params["d$i"] = step.durationSec.toString()
            params["spd$i"] = step.speed.coerceIn(1, 10).toString()
        }
        return sendGet(host, "/saveplaylist", params, user, pass)
    }

    suspend fun toggleColorPlaylist(host: String, user: String, pass: String): ActionResponse {
        return sendGet(host, "/toggleplaylist", emptyMap(), user, pass)
    }

    // 14. Wi-Fi Scan & Configuration
    suspend fun scanWifi(host: String, user: String, pass: String): List<WifiScanResult> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<WifiScanResult>()
            try {
                val longClient = baseClient.newBuilder()
                    .connectTimeout(LONG_TIMEOUT_SEC, TimeUnit.SECONDS)
                    .readTimeout(LONG_TIMEOUT_SEC, TimeUnit.SECONDS)
                    .build()

                val url = "${formatBaseUrl(host)}/scanwifi"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", Credentials.basic(user, pass))
                    .get()
                    .build()

                longClient.newCall(request).execute().use { response ->
                    val text = response.body?.string() ?: "[]"
                    val arr = JSONArray(text)
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        list.add(
                            WifiScanResult(
                                ssid = o.optString("ssid"),
                                rssi = o.optInt("rssi", -70),
                                isSecure = o.optInt("enc", 1) == 1
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "scanWifi error: ${e.message}")
            }
            list
        }

    suspend fun saveWifi(
        host: String,
        config: WifiConfig,
        user: String,
        pass: String
    ): ActionResponse {
        val params = mutableMapOf(
            "en" to if (config.enabled) "1" else "0",
            "ssid" to config.ssid,
            "haspass" to if (config.isOpen) "0" else "1",
            "pass" to config.password,
            "staticen" to if (config.isStatic) "1" else "0",
            "sip" to config.ip,
            "gip" to config.gateway,
            "snip" to config.subnet
        )
        return sendGet(host, "/savewifi", params, user, pass)
    }

    // 15. Security Passwords
    suspend fun changeApPassword(host: String, newPass: String, user: String, pass: String): ActionResponse {
        if (newPass.length < 8 || newPass.length > 19) {
            return ActionResponse(false, "AP Password must be 8-19 characters (WPA2 requirement).")
        }
        return sendGet(host, "/changeappass", mapOf("appass" to newPass), user, pass)
    }

    suspend fun changeWebPassword(
        host: String,
        oldPass: String,
        newPass: String,
        keepEnabled: Boolean = true,
        user: String,
        currentPass: String
    ): ActionResponse {
        if (newPass.length < 6) {
            return ActionResponse(false, "Password must be at least 6 characters.")
        }
        val params = mapOf(
            "old" to oldPass,
            "new" to newPass,
            "en" to if (keepEnabled) "1" else "0"
        )
        return sendGet(host, "/changepassword", params, user, currentPass)
    }

    // 16. OTA Firmware Update
    suspend fun uploadOtaFirmware(
        host: String,
        binFile: File,
        user: String,
        pass: String,
        onProgress: (Int) -> Unit
    ): ActionResponse = withContext(Dispatchers.IO) {
        try {
            val otaClient = baseClient.newBuilder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()

            val fileBody = binFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("update", binFile.name, fileBody)
                .build()

            val url = "${formatBaseUrl(host)}/update"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", Credentials.basic(user, pass))
                .post(multipartBody)
                .build()

            onProgress(30)
            otaClient.newCall(request).execute().use { response ->
                onProgress(100)
                val responseBody = response.body?.string()?.trim() ?: ""
                when {
                    response.code == 401 -> ActionResponse(false, "Authentication failed (401)")
                    response.isSuccessful -> ActionResponse(true, "Firmware uploaded successfully! ESP32 is rebooting...", responseBody)
                    else -> ActionResponse(false, "OTA update failed with code: ${response.code}", responseBody)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OTA Upload error: ${e.message}")
            ActionResponse(false, e.localizedMessage ?: "OTA upload failed. Please try again.")
        }
    }
}

class SultanClockApi : Esp32Api()
