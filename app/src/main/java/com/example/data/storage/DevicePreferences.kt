package com.example.data.storage

import android.content.Context
import android.content.SharedPreferences

class DevicePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "sultan_clock_prefs"
        private const val KEY_IP = "key_clock_ip"
        private const val KEY_USERNAME = "key_username"
        private const val KEY_PASSWORD = "key_password"
        private const val KEY_REMEMBER_PASS = "key_remember_pass"
        private const val KEY_AUTO_CONNECT = "key_auto_connect"
        private const val KEY_SAVED_IPS = "key_saved_ips"
        private const val KEY_LAST_CONN_TYPE = "key_last_conn_type"

        const val DEFAULT_MDNS_HOST = "sultanclock.local"
        const val DEFAULT_AP_IP = "192.168.4.1"
        const val DEFAULT_ADMIN_USER = "admin"
        const val DEFAULT_ADMIN_PASS = "sultan88"

        const val KEY_TRACK_NAMES = "key_track_names_json"
        const val KEY_BACKUP_PROFILES = "key_backup_profiles_json"

        val DEFAULT_TRACK_NAMES: Map<Int, String> = mapOf(
            1 to "স্বাগতম সুলতান ক্লক (Welcome Voice)",
            2 to "বিগ বেন ঘণ্টা চিম (Big Ben Chime)",
            3 to "ফজর আযান - মক্কা শরীফ (Fajr Azan)",
            4 to "যোহর আযান - মদিনা শরীফ (Dhuhr Azan)",
            5 to "আসর আযান - মিশরীয় সুর (Asr Azan)",
            6 to "মাগরিব আযান - আল-আকসা (Maghrib Azan)",
            7 to "ইশা আযান - হারামাইন (Isha Azan)",
            8 to "সকালের মিষ্টি অ্যালার্ম (Morning Alarm)",
            9 to "ডিজিটাল বিপ অ্যালার্ম (Digital Beep)",
            10 to "পাখির কলকাকলি (Nature Birds)",
            11 to "শান্ত বাঁশির সুর (Flute Tone)",
            12 to "সুইট ক্রিস্টাল চিম (Crystal Bell)",
            13 to "মক্কার মিষ্টি আযান (Alternative Azan)",
            14 to "মদিনার মনোমুগ্ধকর আযান (Alternative Azan 2)",
            15 to "জুমার বিশেষ বার্তা (Jummah Special)",
            16 to "কোরআন তিলাওয়াত - সূরা ফাতিহা",
            17 to "দরূদ শরীফ (Salawat)",
            18 to "নাসিদ রিংটোন (Islamic Nasheed)",
            19 to "জেন্টল পিয়ানো চিম (Gentle Piano)",
            20 to "টুইংকল বেল রিং (Twinkle Bell)"
        )
    }

    var ipAddress: String
        get() = prefs.getString(KEY_IP, DEFAULT_MDNS_HOST) ?: DEFAULT_MDNS_HOST
        set(value) = prefs.edit().putString(KEY_IP, value.trim()).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, DEFAULT_ADMIN_USER) ?: DEFAULT_ADMIN_USER
        set(value) = prefs.edit().putString(KEY_USERNAME, value.trim()).apply()

    var password: String
        get() = prefs.getString(KEY_PASSWORD, DEFAULT_ADMIN_PASS) ?: DEFAULT_ADMIN_PASS
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    var rememberPassword: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER_PASS, true)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER_PASS, value).apply()

    var autoConnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CONNECT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_CONNECT, value).apply()

    var lastConnectionType: String
        get() = prefs.getString(KEY_LAST_CONN_TYPE, "mDNS") ?: "mDNS"
        set(value) = prefs.edit().putString(KEY_LAST_CONN_TYPE, value).apply()

    fun getSavedIps(): Set<String> {
        val defaultSet = setOf(DEFAULT_MDNS_HOST, DEFAULT_AP_IP, "192.168.1.120")
        return prefs.getStringSet(KEY_SAVED_IPS, defaultSet) ?: defaultSet
    }

    fun addSavedIp(ip: String) {
        val current = getSavedIps().toMutableSet()
        current.add(ip.trim())
        prefs.edit().putStringSet(KEY_SAVED_IPS, current).apply()
    }

    fun removeSavedIp(ip: String) {
        val current = getSavedIps().toMutableSet()
        current.remove(ip.trim())
        prefs.edit().putStringSet(KEY_SAVED_IPS, current).apply()
    }

    fun clearAuth() {
        prefs.edit().remove(KEY_PASSWORD).apply()
    }

    // --- DFPlayer Track Catalog Storage ---

    fun getTrackNames(): Map<Int, String> {
        val jsonStr = prefs.getString(KEY_TRACK_NAMES, null)
        val result = DEFAULT_TRACK_NAMES.toMutableMap()
        if (!jsonStr.isNullOrBlank()) {
            try {
                val json = org.json.JSONObject(jsonStr)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val trackNum = k.toIntOrNull()
                    if (trackNum != null) {
                        result[trackNum] = json.getString(k)
                    }
                }
            } catch (e: Exception) {
                // fallback to defaults
            }
        }
        return result
    }

    fun saveTrackName(trackNumber: Int, name: String) {
        val current = getTrackNames().toMutableMap()
        current[trackNumber] = name.trim()
        saveAllTrackNames(current)
    }

    fun saveAllTrackNames(map: Map<Int, String>) {
        val json = org.json.JSONObject()
        for ((k, v) in map) {
            json.put(k.toString(), v)
        }
        prefs.edit().putString(KEY_TRACK_NAMES, json.toString()).apply()
    }

    fun resetTrackNamesToDefault() {
        saveAllTrackNames(DEFAULT_TRACK_NAMES)
    }

    // --- Profile Backups Storage ---
    fun getSavedProfiles(): List<com.example.data.model.ClockProfileBackup> {
        val jsonStr = prefs.getString(KEY_BACKUP_PROFILES, null) ?: return getDefaultProfiles()
        return try {
            val jsonArray = org.json.JSONArray(jsonStr)
            val list = mutableListOf<com.example.data.model.ClockProfileBackup>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(deserializeProfile(obj))
            }
            if (list.isEmpty()) getDefaultProfiles() else list
        } catch (e: Exception) {
            getDefaultProfiles()
        }
    }

    fun saveProfile(profile: com.example.data.model.ClockProfileBackup) {
        val current = getSavedProfiles().filter { it.id != profile.id }.toMutableList()
        current.add(0, profile) // add newest first
        val jsonArray = org.json.JSONArray()
        for (p in current) {
            jsonArray.put(serializeProfile(p))
        }
        prefs.edit().putString(KEY_BACKUP_PROFILES, jsonArray.toString()).apply()
    }

    fun deleteProfile(profileId: String) {
        val current = getSavedProfiles().filter { it.id != profileId }
        val jsonArray = org.json.JSONArray()
        for (p in current) {
            jsonArray.put(serializeProfile(p))
        }
        prefs.edit().putString(KEY_BACKUP_PROFILES, jsonArray.toString()).apply()
    }

    private fun serializeProfile(p: com.example.data.model.ClockProfileBackup): org.json.JSONObject {
        val json = org.json.JSONObject()
        json.put("id", p.id)
        json.put("name", p.name)
        json.put("createdAt", p.createdAt)
        json.put("is12Hour", p.is12Hour)
        json.put("showDate", p.showDate)
        json.put("colonBlink", p.colonBlink)

        // Alarms
        val alJson = org.json.JSONObject()
        alJson.put("a1En", p.alarms.alarm1Enabled)
        alJson.put("a1H", p.alarms.alarm1Hour)
        alJson.put("a1M", p.alarms.alarm1Minute)
        alJson.put("a1Tr", p.alarms.alarm1Track)
        alJson.put("a2En", p.alarms.alarm2Enabled)
        alJson.put("a2H", p.alarms.alarm2Hour)
        alJson.put("a2M", p.alarms.alarm2Minute)
        alJson.put("a2Tr", p.alarms.alarm2Track)
        json.put("alarms", alJson)

        // Hourly Chime
        val chJson = org.json.JSONObject()
        chJson.put("en", p.hourlyChime.enabled)
        chJson.put("start", p.hourlyChime.startHour)
        chJson.put("end", p.hourlyChime.endHour)
        chJson.put("mode", p.hourlyChime.mode)
        chJson.put("fixed", p.hourlyChime.fixedTrack)
        json.put("chime", chJson)

        // Brightness & Color
        val brJson = org.json.JSONObject()
        brJson.put("bright", p.brightness.manualBrightness)
        brJson.put("autoLdr", p.brightness.autoLdr)
        json.put("brightness", brJson)

        val clJson = org.json.JSONObject()
        clJson.put("mode", p.color.mode)
        clJson.put("r", p.color.red)
        clJson.put("g", p.color.green)
        clJson.put("b", p.color.blue)
        clJson.put("speed", p.color.animSpeed)
        json.put("color", clJson)

        // Track assignments
        val trJson = org.json.JSONObject()
        trJson.put("az0", p.trackAssignments.fajrTrack)
        trJson.put("az1", p.trackAssignments.dhuhrTrack)
        trJson.put("az2", p.trackAssignments.asrTrack)
        trJson.put("az3", p.trackAssignments.maghribTrack)
        trJson.put("az4", p.trackAssignments.ishaTrack)
        trJson.put("fajrEn", p.trackAssignments.fajrEnabled)
        trJson.put("dhuhrEn", p.trackAssignments.dhuhrEnabled)
        trJson.put("asrEn", p.trackAssignments.asrEnabled)
        trJson.put("maghribEn", p.trackAssignments.maghribEnabled)
        trJson.put("ishaEn", p.trackAssignments.ishaEnabled)
        json.put("tracks", trJson)

        return json
    }

    private fun deserializeProfile(json: org.json.JSONObject): com.example.data.model.ClockProfileBackup {
        val id = json.optString("id", System.currentTimeMillis().toString())
        val name = json.optString("name", "Backup Profile")
        val createdAt = json.optString("createdAt", "Just now")
        val is12Hour = json.optBoolean("is12Hour", false)
        val showDate = json.optBoolean("showDate", true)
        val colonBlink = json.optBoolean("colonBlink", true)

        val alJson = json.optJSONObject("alarms")
        val alarms = if (alJson != null) {
            com.example.data.model.AlarmConfig(
                alarm1Enabled = alJson.optBoolean("a1En", false),
                alarm1Hour = alJson.optInt("a1H", 6),
                alarm1Minute = alJson.optInt("a1M", 30),
                alarm1Track = alJson.optInt("a1Tr", 1),
                alarm2Enabled = alJson.optBoolean("a2En", false),
                alarm2Hour = alJson.optInt("a2H", 18),
                alarm2Minute = alJson.optInt("a2M", 30),
                alarm2Track = alJson.optInt("a2Tr", 2)
            )
        } else com.example.data.model.AlarmConfig()

        val chJson = json.optJSONObject("chime")
        val chime = if (chJson != null) {
            com.example.data.model.HourlyChimeConfig(
                enabled = chJson.optBoolean("en", true),
                startHour = chJson.optInt("start", 6),
                endHour = chJson.optInt("end", 22),
                mode = chJson.optInt("mode", 0),
                fixedTrack = chJson.optInt("fixed", 1)
            )
        } else com.example.data.model.HourlyChimeConfig()

        val brJson = json.optJSONObject("brightness")
        val brightness = if (brJson != null) {
            com.example.data.model.BrightnessConfig(
                manualBrightness = brJson.optInt("bright", 128),
                autoLdr = brJson.optBoolean("autoLdr", false)
            )
        } else com.example.data.model.BrightnessConfig()

        val clJson = json.optJSONObject("color")
        val color = if (clJson != null) {
            com.example.data.model.ColorConfig(
                mode = clJson.optInt("mode", 0),
                red = clJson.optInt("r", 255),
                green = clJson.optInt("g", 215),
                blue = clJson.optInt("b", 0),
                animSpeed = clJson.optInt("speed", 5)
            )
        } else com.example.data.model.ColorConfig()

        val trJson = json.optJSONObject("tracks")
        val tracks = if (trJson != null) {
            com.example.data.model.TrackAssignmentsConfig(
                fajrTrack = trJson.optInt("az0", 1),
                dhuhrTrack = trJson.optInt("az1", 2),
                asrTrack = trJson.optInt("az2", 3),
                maghribTrack = trJson.optInt("az3", 4),
                ishaTrack = trJson.optInt("az4", 5),
                fajrEnabled = trJson.optBoolean("fajrEn", true),
                dhuhrEnabled = trJson.optBoolean("dhuhrEn", true),
                asrEnabled = trJson.optBoolean("asrEn", true),
                maghribEnabled = trJson.optBoolean("maghribEn", true),
                ishaEnabled = trJson.optBoolean("ishaEn", true)
            )
        } else com.example.data.model.TrackAssignmentsConfig()

        return com.example.data.model.ClockProfileBackup(
            id = id,
            name = name,
            createdAt = createdAt,
            alarms = alarms,
            hourlyChime = chime,
            trackAssignments = tracks,
            brightness = brightness,
            color = color,
            is12Hour = is12Hour,
            showDate = showDate,
            colonBlink = colonBlink
        )
    }

    private fun getDefaultProfiles(): List<com.example.data.model.ClockProfileBackup> {
        return listOf(
            com.example.data.model.ClockProfileBackup(
                id = "default_sultan",
                name = "সুলতান ক্লাসিক প্রোফাইল (Default)",
                createdAt = "Factory Default",
                color = com.example.data.model.ColorConfig(mode = 0, red = 255, green = 215, blue = 0),
                hourlyChime = com.example.data.model.HourlyChimeConfig(enabled = true, startHour = 6, endHour = 22, mode = 0, fixedTrack = 2),
                brightness = com.example.data.model.BrightnessConfig(manualBrightness = 140, autoLdr = false)
            ),
            com.example.data.model.ClockProfileBackup(
                id = "ramadan_profile",
                name = "রমজান ও ইবাদত প্রোফাইল (Ramadan)",
                createdAt = "Preset",
                color = com.example.data.model.ColorConfig(mode = 0, red = 0, green = 230, blue = 118),
                alarms = com.example.data.model.AlarmConfig(alarm1Enabled = true, alarm1Hour = 4, alarm1Minute = 0, alarm1Track = 8),
                trackAssignments = com.example.data.model.TrackAssignmentsConfig(
                    fajrEnabled = true, fajrTrack = 3,
                    dhuhrEnabled = true, dhuhrTrack = 4,
                    asrEnabled = true, asrTrack = 5,
                    maghribEnabled = true, maghribTrack = 6,
                    ishaEnabled = true, ishaTrack = 7
                ),
                hourlyChime = com.example.data.model.HourlyChimeConfig(enabled = true, startHour = 5, endHour = 23, mode = 0, fixedTrack = 10)
            )
        )
    }
}
