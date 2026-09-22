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
        private const val DEFAULT_TIMEOUT_SEC = 2L
        private const val LONG_TIMEOUT_SEC = 8L

        // Precompiled Regexes to eliminate GC pressure and main-thread / polling thread stutters
        private val RAW_TIME_REGEX = Regex("<div[^>]*class=['\"][^'\"]*time-main[^'\"]*['\"][^>]*>(.*?)</div>", RegexOption.IGNORE_CASE)
        private val HTML_TAGS_REGEX = Regex("<[^>]*>")
        private val RAW_DATE_REGEX = Regex("<div[^>]*class=['\"][^'\"]*time-date[^'\"]*['\"][^>]*>([^<]+)</div>", RegexOption.IGNORE_CASE)
        private val BANGLA_REGEX = Regex("বাংলা:\\s*([0-9/]+|[^<\\s]+)", RegexOption.IGNORE_CASE)
        private val DISPLAY_STATUS_REGEX = Regex("id=['\"]displaystatus['\"][^>]*class=['\"]status-([a-zA-Z]+)['\"]", RegexOption.IGNORE_CASE)
        private val LIGHT_STATUS_REGEX = Regex("id=['\"]lightstatus['\"][^>]*class=['\"]status-([a-zA-Z]+)['\"]", RegexOption.IGNORE_CASE)
        private val TEMP_REGEX = Regex("([0-9.]+)\\s*&deg;C", RegexOption.IGNORE_CASE)
        private val TONE_START_REGEX = Regex("id=['\"]tonestarthr['\"][^>]*value=['\"]([0-9:]+)['\"]", RegexOption.IGNORE_CASE)
        private val TONE_END_REGEX = Regex("id=['\"]toneendhr['\"][^>]*value=['\"]([0-9:]+)['\"]", RegexOption.IGNORE_CASE)
        private val HMODE_REGEX = Regex("value=['\"]([0-3])['\"][^>]*name=['\"]hmode['\"][^>]*checked|name=['\"]hmode['\"][^>]*value=['\"]([0-3])['\"][^>]*checked", RegexOption.IGNORE_CASE)
        private val LDR_RAW_REGEX = Regex("Live LDR Raw:\\s*<strong>([0-9]+)</strong>", RegexOption.IGNORE_CASE)
        private val APPLIED_BRIGHT_REGEX1 = Regex("Applied Brightness:\\s*<strong>([0-9]+)</strong>", RegexOption.IGNORE_CASE)
        private val APPLIED_BRIGHT_REGEX2 = Regex("id=['\"]bright['\"][^>]*value=['\"]([0-9]+)['\"]", RegexOption.IGNORE_CASE)
        private val COLOR_MODE_REGEX = Regex("<select[^>]*id=['\"]colormode['\"][^>]*>.*?<option value=['\"]([0-4])['\"]\\s*selected", RegexOption.IGNORE_CASE)
        private val PLAYLIST_STATUS_REGEX = Regex("id=['\"]plstatus['\"][^>]*class=['\"]status-([a-zA-Z]+)['\"]", RegexOption.IGNORE_CASE)
        private val WIFI_SSID_CONNECTED_REGEX = Regex("Connected:\\s*<strong>(.*?)</strong>", RegexOption.IGNORE_CASE)
        private val WIFI_SSID_INPUT_REGEX = Regex("id=['\"]wifissid['\"][^>]*value=['\"](.*?)['\"]", RegexOption.IGNORE_CASE)
        private val IP_REGEX = Regex("IP:\\s*([0-9.]+)", RegexOption.IGNORE_CASE)
        private val ALARM0_TIME_REGEX = Regex("id=['\"]alarm0['\"][^>]*value=['\"]([0-9:]+)['\"]", RegexOption.IGNORE_CASE)
        private val ALARM0_TRACK_REGEX = Regex("id=['\"]al0['\"][^>]*value=['\"]([0-9]+)['\"]", RegexOption.IGNORE_CASE)
        private val ALARM1_TIME_REGEX = Regex("id=['\"]alarm1['\"][^>]*value=['\"]([0-9:]+)['\"]", RegexOption.IGNORE_CASE)
        private val ALARM1_TRACK_REGEX = Regex("id=['\"]al1['\"][^>]*value=['\"]([0-9]+)['\"]", RegexOption.IGNORE_CASE)
        private val AZAN_TRACK_REGEXES = (0..4).map { i ->
            Regex("id=['\"]az$i['\"][^>]*value=['\"]([0-9]+)['\"]", RegexOption.IGNORE_CASE)
        }
    }

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .readTimeout(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .writeTimeout(DEFAULT_TIMEOUT_SEC, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
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
    suspend fun pingHost(hostOrIp: String, port: Int = 80, timeoutMs: Int = 1500): Boolean =
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
     * Test connection and authentication directly against root / for instant response
     */
    suspend fun checkConnection(
        host: String,
        user: String = "admin",
        pass: String = ""
    ): Pair<ConnectionStatus, String> = withContext(Dispatchers.IO) {
        val rootUrl = "${formatBaseUrl(host)}/"
        try {
            val client = getClientWithAuth(user, pass)
            val request = Request.Builder()
                .url(rootUrl)
                .addHeader("Authorization", Credentials.basic(user, pass))
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> ConnectionStatus.CONNECTED to "Connected successfully"
                    401 -> ConnectionStatus.AUTH_REQUIRED to "Authentication required (401)"
                    else -> ConnectionStatus.ERROR to "HTTP status: ${response.code}"
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Connection failed to $rootUrl: ${e.message}")
            ConnectionStatus.DISCONNECTED to (e.localizedMessage ?: "ESP offline or unreachable")
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
    ): ClockDashboardData? = withContext(Dispatchers.IO) {
        val client = getClientWithAuth(user, pass)
        try {
            // Supplied ESP8266 firmware exposes its complete live state in the root HTML.
            // Do not depend on the ESP32-only /api/status route.
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
                }
                if (response.code == 401) {
                    Log.w(TAG, "getStatus 401 Unauthorized for $host")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getStatus unreachable or offline: ${e.message}")
        }
        null
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
            fun extractRegex(regex: Regex, default: String = ""): String {
                val match = regex.find(html)
                return match?.groups?.get(1)?.value?.trim() ?: default
            }

            fun hasChecked(id: String): Boolean {
                val idx = html.indexOf(id, ignoreCase = true)
                if (idx == -1) return false
                val start = (idx - 60).coerceAtLeast(0)
                val end = (idx + 60).coerceAtMost(html.length)
                val snippet = html.substring(start, end)
                return snippet.contains("checked", ignoreCase = true)
            }

            // 1. Live Time & Date
            val rawTimeMatch = RAW_TIME_REGEX.find(html)
            val rawTime = rawTimeMatch?.groups?.get(1)?.value?.replace(HTML_TAGS_REGEX, " ")?.trim() ?: "12:00:00"
            val rawDate = extractRegex(RAW_DATE_REGEX, "")
            val bangla = extractRegex(BANGLA_REGEX).takeIf { it.isNotBlank() }

            // Detect Model
            val isEsp8266 = html.contains("SULTAN CLOCK (FIXED VERSION)", ignoreCase = true) ||
                    html.contains("hourlybeep_range", ignoreCase = true) ||
                    html.contains("ESP8266", ignoreCase = true) ||
                    html.contains("Masjid Edition", ignoreCase = true) ||
                    (!html.contains("DFPlayer", ignoreCase = true) && !html.contains("prayerstatus", ignoreCase = true) && html.contains("sultan", ignoreCase = true))

            val detectedModel = if (isEsp8266) ClockModel.ESP8266 else ClockModel.ESP32
            val fwVersion = if (isEsp8266) "v8-ESP8266-ColorChangeSec" else "v5.0-ESP32"

            // 2. Hardware Switch States
            val isDisplayOn = extractRegex(DISPLAY_STATUS_REGEX, "on").equals("on", ignoreCase = true)
            val isLightOn = extractRegex(LIGHT_STATUS_REGEX, "off").equals("on", ignoreCase = true)
            val isTempSensorOn = false
            val tempVal = extractRegex(TEMP_REGEX, "28.5").toFloatOrNull() ?: 28.5f
            val isPrayerAlarmOn = false

            // 3. Prayer Times (ESP32)
            val prayerTimes: PrayerTimes? = null

            // 4. DFPlayer & Audio (ESP32 & ESP8266) & Buzzer Hourly Beep
            val dfConnected = false
            val dfVol = 0
            val hourlyChimeEnabled = hasChecked("hourlybeep_range") || hasChecked("hourlybeep2") || hasChecked("hourlybeep")
            val hourlyToneRangeEnabled = hasChecked("tonerangeen")
            val toneStartStr = extractRegex(TONE_START_REGEX, "07:00")
            val toneEndStr = extractRegex(TONE_END_REGEX, "22:00")
            val toneStartHour = toneStartStr.split(":").getOrNull(0)?.toIntOrNull() ?: 7
            val toneEndHour = toneEndStr.split(":").getOrNull(0)?.toIntOrNull() ?: 22
            val enableEngDate = hasChecked("showEnglishDate")
            val enableBanDate = hasChecked("showBanglaDate")

            val hourlyChimeMode = extractRegex(HMODE_REGEX, "0").toIntOrNull() ?: 0

            // 5. Brightness & LDR Telemetry
            val autoLdr = hasChecked("autoldr")
            val ldrRaw = extractRegex(LDR_RAW_REGEX, "450").toIntOrNull() ?: 450
            val appliedBrightness = extractRegex(APPLIED_BRIGHT_REGEX1, "").toIntOrNull()
                ?: extractRegex(APPLIED_BRIGHT_REGEX2, "128").toIntOrNull() ?: 128

            // 6. Color Mode & Playlist
            val colorMode = extractRegex(COLOR_MODE_REGEX, "0").toIntOrNull() ?: 0
            val playlistOn = extractRegex(PLAYLIST_STATUS_REGEX, "off").equals("on", ignoreCase = true)

            // 7. Network Status
            val wifiSsid = extractRegex(WIFI_SSID_CONNECTED_REGEX, extractRegex(WIFI_SSID_INPUT_REGEX, ""))
            val ipAddress = extractRegex(IP_REGEX, host)
            val isApMode = html.contains("SoftAP", ignoreCase = true) || host.contains("192.168.4.1")
            val isWifiConn = isApMode || (wifiSsid.isNotBlank() && !html.contains("Wi-Fi disconnected", ignoreCase = true))

            // 8. 12/24 Hour format
            val is12Hour = hasChecked("fmt12")

            // 9. Dual Alarms
            val a0Time = extractRegex(ALARM0_TIME_REGEX, "06:30").split(":")
            val a0H = a0Time.getOrNull(0)?.toIntOrNull() ?: 6
            val a0M = a0Time.getOrNull(1)?.toIntOrNull() ?: 30
            val a0En = hasChecked("en0")
            val a0Track = extractRegex(ALARM0_TRACK_REGEX, "1").toIntOrNull() ?: 1

            val a1Time = extractRegex(ALARM1_TIME_REGEX, "18:30").split(":")
            val a1H = a1Time.getOrNull(0)?.toIntOrNull() ?: 18
            val a1M = a1Time.getOrNull(1)?.toIntOrNull() ?: 30
            val a1En = hasChecked("en1")
            val a1Track = extractRegex(ALARM1_TRACK_REGEX, "2").toIntOrNull() ?: 2

            val alarms = listOf(
                ClockAlarmStatus(hour = a0H, minute = a0M, enabled = a0En, track = a0Track),
                ClockAlarmStatus(hour = a1H, minute = a1M, enabled = a1En, track = a1Track)
            )

            // 10. Azan Tracks
            val azanTracks = AZAN_TRACK_REGEXES.mapIndexed { i, regex ->
                extractRegex(regex, "${i + 1}").toIntOrNull() ?: (i + 1)
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
                firmwareVersion = fwVersion,
                detectedModel = detectedModel,
                hourlyBeepEnabled = hourlyChimeEnabled,
                hourlyToneRangeEnabled = hourlyToneRangeEnabled,
                hourlyToneStartHour = toneStartHour,
                hourlyToneEndHour = toneEndHour,
                enableEnglishDate = enableEngDate,
                enableBanglaDate = enableBanDate,
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
    ): ClockDashboardData? = getStatus(host, user, pass)

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
                    !response.isSuccessful -> ActionResponse(false, "Clock returned HTTP ${response.code}", responseBody)
                    // Robust JSON error checking
                    responseBody.startsWith("{") && responseBody.contains("\"error\":true", ignoreCase = true) ->
                        ActionResponse(false, responseBody.ifBlank { "Command rejected by clock" }, responseBody)
                    // Brief explicit error message from MCU
                    responseBody.length in 1..50 && (responseBody.startsWith("ERROR", ignoreCase = true) || responseBody.startsWith("FAIL", ignoreCase = true)) ->
                        ActionResponse(false, responseBody, responseBody)
                    responseBody == "CONFLICT" ->
                        ActionResponse(true, "Saved, but Alarm 1 and Alarm 2 are set to the same time — only one will ring", responseBody)
                    else ->
                        // Robust HTTP 200 parsing: If HTTP status is 200, it is considered a success even if returning HTML or simple text
                        ActionResponse(true, if (responseBody.isNotBlank()) responseBody else "Command executed successfully", responseBody)
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "sendGet timed out on $endpoint connecting to $host: ${e.message}")
            ActionResponse(false, "Clock connection timed out at $host. Please ensure you are connected to the clock's Wi-Fi network.")
        } catch (e: java.io.IOException) {
            Log.w(TAG, "sendGet I/O unreachable on $endpoint to $host: ${e.message}")
            ActionResponse(false, "Clock offline or unreachable at $host (${e.javaClass.simpleName})")
        } catch (e: Exception) {
            Log.w(TAG, "sendGet error on $endpoint: ${e.message}")
            ActionResponse(false, e.localizedMessage ?: "Failed to reach Clock")
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
        return ActionResponse(false, "Prayer/Azan control is not supported by the supplied ESP8266 firmware.")
    }

    suspend fun toggleTempSensor(host: String, user: String, pass: String): ActionResponse {
        return ActionResponse(false, "Temperature sensor control is not supported by the supplied ESP8266 firmware.")
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
            "english" to if (config.isDateEnabled) "1" else "0",
            "showEnglishDate" to if (config.isDateEnabled) "1" else "0",
            "bangla" to if (config.isBanglaDate) "1" else "0",
            "showBanglaDate" to if (config.isBanglaDate) "1" else "0"
        )
        return sendGet(host, "/savedatesettings", params, user, pass)
    }

    // 5. Alarm Settings (TWO separate calls, one per alarm, with i,h,m,e,t)
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
                "e" to if (config.alarm1Enabled) "1" else "0",
                "t" to config.alarm1Track.toString()
            ), user, pass
        )
        if (!r1.isSuccess) return r1
        return sendGet(
            host, "/savealarm", mapOf(
                "i" to "1",
                "h" to config.alarm2Hour.toString(),
                "m" to config.alarm2Minute.toString(),
                "e" to if (config.alarm2Enabled) "1" else "0",
                "t" to config.alarm2Track.toString()
            ), user, pass
        )
    }

    suspend fun saveSingleAlarm(
        host: String,
        index: Int,
        hour: Int,
        minute: Int,
        enabled: Boolean,
        toneIndex: Int,
        user: String,
        pass: String
    ): ActionResponse {
        return sendGet(
            host, "/savealarm", mapOf(
                "i" to index.toString(),
                "h" to hour.toString(),
                "m" to minute.toString(),
                "e" to if (enabled) "1" else "0",
                "t" to toneIndex.toString()
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

    // 7. Color Control - Strictly separated for ESP8266 vs ESP32
    suspend fun saveColorEsp8266(
        host: String,
        config: ColorConfig,
        user: String,
        pass: String
    ): ActionResponse {
        // ESP8266 firmware accepts strictly short codes: m, sc, ci, r, g, b, spd
        val params = mapOf(
            "m" to config.mode.toString(),
            "sc" to config.staticColorIndex.toString(),
            "ci" to config.colorIntervalSec.toString(),
            "r" to config.red.toString(),
            "g" to config.green.toString(),
            "b" to config.blue.toString(),
            "spd" to config.animSpeed.coerceIn(1, 10).toString()
        )
        return sendGet(host, "/savecolor", params, user, pass)
    }

    suspend fun saveColorEsp32(
        host: String,
        config: ColorConfig,
        user: String,
        pass: String
    ): ActionResponse {
        val params = mapOf(
            "mode" to config.mode.toString(),
            "sc" to config.staticColorIndex.toString(),
            "interval" to config.colorIntervalSec.toString(),
            "r" to config.red.toString(),
            "g" to config.green.toString(),
            "b" to config.blue.toString(),
            "speed" to config.animSpeed.coerceIn(1, 10).toString()
        )
        return sendGet(host, "/savecolor", params, user, pass)
    }

    suspend fun saveColor(
        host: String,
        config: ColorConfig,
        user: String,
        pass: String,
        model: ClockModel = ClockModel.ESP8266
    ): ActionResponse {
        return if (model == ClockModel.ESP8266) {
            saveColorEsp8266(host, config, user, pass)
        } else {
            saveColorEsp32(host, config, user, pass)
        }
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
        return ActionResponse(false, "DFPlayer volume is not supported by the supplied ESP8266 firmware.")
    }

    suspend fun testDfTrack(host: String, track: Int, user: String, pass: String): ActionResponse {
        return ActionResponse(false, "DFPlayer track control is not supported by the supplied ESP8266 firmware. Use Tone Test instead.")
    }

    // 10. Hourly Chime
    suspend fun saveHourlyMode(
        host: String,
        config: HourlyChimeConfig,
        user: String,
        pass: String
    ): ActionResponse {
        // The supplied ESP8266 firmware exposes hourly tone enable and its active
        // time range, but it does not expose the ESP32-style hourly track pool/mode API.
        val settings = sendGet(
            host, "/savesettings",
            mapOf(
                "f" to "0",
                "sd" to "1",
                "cb" to "0",
                "hb" to if (config.enabled) "1" else "0"
            ), user, pass
        )
        if (!settings.isSuccess) return settings
        return sendGet(
            host, "/savetonerange",
            mapOf(
                "en" to if (config.toneRangeEnabled) "1" else "0",
                "sh" to config.toneStartHour.coerceIn(0, 23).toString(),
                "eh" to config.toneEndHour.coerceIn(0, 23).toString()
            ), user, pass
        )
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
        return ActionResponse(false, "Azan/DFPlayer track assignment is not supported by the supplied ESP8266 firmware.")
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
        return ActionResponse(false, "Prayer/Waqt audio controls are not supported by the supplied ESP8266 firmware.")
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
        return ActionResponse(false, "Weekly audio playlist is not supported by the supplied ESP8266 firmware.")
    }

    // 13. Color Playlist - Strictly separated for ESP8266 vs ESP32
    suspend fun saveColorPlaylistEsp8266(
        host: String,
        config: ColorPlaylistConfig,
        user: String,
        pass: String
    ): ActionResponse {
        val count = config.steps.size.coerceIn(1, 8)
        val params = mutableMapOf(
            "en" to if (config.enabled) "1" else "0",
            "enabled" to if (config.enabled) "1" else "0",
            "cnt" to count.toString(),
            "count" to count.toString()
        )
        config.steps.take(count).forEachIndexed { i, step ->
            params["m$i"] = step.mode.toString()
            params["mode$i"] = step.mode.toString()
            params["ci$i"] = step.colorIndex.toString()
            params["color$i"] = step.colorIndex.toString()
            params["r$i"] = step.red.toString()
            params["g$i"] = step.green.toString()
            params["b$i"] = step.blue.toString()
            params["d$i"] = step.durationSec.toString()
            params["duration$i"] = step.durationSec.toString()
            params["spd$i"] = step.speed.coerceIn(1, 10).toString()
            params["speed$i"] = step.speed.coerceIn(1, 10).toString()
            params["cc$i"] = step.colorChangeSec.coerceIn(1, step.durationSec).toString()
        }
        return sendGet(host, "/saveplaylist", params, user, pass)
    }

    suspend fun saveColorPlaylistEsp32(
        host: String,
        config: ColorPlaylistConfig,
        user: String,
        pass: String
    ): ActionResponse {
        val count = config.steps.size.coerceIn(1, 8)
        val params = mutableMapOf(
            "en" to if (config.enabled) "1" else "0",
            "enabled" to if (config.enabled) "1" else "0",
            "count" to count.toString(),
            "cnt" to count.toString()
        )
        config.steps.take(count).forEachIndexed { i, step ->
            params["mode$i"] = step.mode.toString()
            params["m$i"] = step.mode.toString()
            params["color$i"] = step.colorIndex.toString()
            params["ci$i"] = step.colorIndex.toString()
            params["r$i"] = step.red.toString()
            params["g$i"] = step.green.toString()
            params["b$i"] = step.blue.toString()
            params["speed$i"] = step.speed.coerceIn(1, 10).toString()
            params["spd$i"] = step.speed.coerceIn(1, 10).toString()
            params["duration$i"] = step.durationSec.toString()
            params["d$i"] = step.durationSec.toString()
        }
        return sendGet(host, "/saveplaylist", params, user, pass)
    }

    suspend fun saveColorPlaylist(
        host: String,
        config: ColorPlaylistConfig,
        user: String,
        pass: String,
        model: ClockModel = ClockModel.ESP8266
    ): ActionResponse {
        return if (model == ClockModel.ESP8266) {
            saveColorPlaylistEsp8266(host, config, user, pass)
        } else {
            saveColorPlaylistEsp32(host, config, user, pass)
        }
    }

    suspend fun toggleColorPlaylist(host: String, user: String, pass: String, enabled: Boolean? = null): ActionResponse {
        val params = mutableMapOf<String, String>()
        if (enabled != null) {
            params["en"] = if (enabled) "1" else "0"
            params["enabled"] = if (enabled) "1" else "0"
        }
        val res = sendGet(host, "/toggleplaylist", params, user, pass)
        return if (!res.isSuccess && enabled != null) {
            sendGet(host, "/saveplaylist", params, user, pass)
        } else {
            res
        }
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
        return ActionResponse(false, "AP password change is not exposed by the supplied ESP8266 firmware API.")
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

    // 15b. ESP8266 Specific Utilities (Buzzer Test, Default Password Reset, Date Display)
    suspend fun testTone(host: String, idx: Int = 0, user: String, pass: String): ActionResponse {
        val params = mapOf("idx" to idx.toString())
        return sendGet(host, "/testtone", params, user, pass)
    }

    suspend fun saveTone(host: String, mode: Int, idx: Int, user: String, pass: String): ActionResponse {
        val params = mapOf(
            "mode" to mode.toString(),
            "idx" to idx.toString()
        )
        return sendGet(host, "/savetone", params, user, pass)
    }

    suspend fun resetDefaultPassword(host: String, user: String, pass: String): ActionResponse {
        return ActionResponse(false, "Default-password reset is not exposed by the supplied ESP8266 firmware API.")
    }

    suspend fun showWifiPassword(host: String, user: String, pass: String): ActionResponse {
        return ActionResponse(false, "Wi-Fi password display is not exposed by the supplied ESP8266 firmware API.")
    }

    suspend fun saveDateDisplaySettings(
        host: String,
        englishDate: Boolean,
        banglaDate: Boolean,
        user: String,
        pass: String
    ): ActionResponse {
        val params = mapOf(
            "eng" to if (englishDate) "1" else "0",
            "english" to if (englishDate) "1" else "0",
            "showEnglishDate" to if (englishDate) "1" else "0",
            "bangla" to if (banglaDate) "1" else "0",
            "showBanglaDate" to if (banglaDate) "1" else "0"
        )
        return sendGet(host, "/savedatesettings", params, user, pass)
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
            Log.w(TAG, "OTA Upload error: ${e.message}")
            ActionResponse(false, e.localizedMessage ?: "OTA upload failed. Please try again.")
        }
    }
}

class SultanClockApi : Esp32Api()
