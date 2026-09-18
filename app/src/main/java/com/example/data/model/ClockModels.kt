package com.example.data.model

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTH_REQUIRED,
    ERROR
}

data class ClockAlarmStatus(
    val hour: Int = 0,
    val minute: Int = 0,
    val enabled: Boolean = false,
    val track: Int = 1
)

data class WeeklyPlaylistTodayStatus(
    val enabled: Boolean = false,
    val time: String = "08:00",
    val todayTrack: Int = 10,
    val tracks: List<Int> = listOf(10, 10, 10, 11, 11, 15, 10)
)

data class ClockDashboardData(
    val currentTimeStr: String = "12:00:00",
    val currentDateStr: String = "2026-08-20",
    val banglaDate: String? = null,
    val dayOfWeekStr: String = "Thursday",
    val temperatureC: Float = 28.5f,
    val is12Hour: Boolean = false,
    val isDisplayOn: Boolean = true,
    val isLightOn: Boolean = true,
    val isPrayerAlarmOn: Boolean = true,
    val isTempSensorOn: Boolean = true,
    val ldrRaw: Int = 450,
    val appliedBrightness: Int = 128,
    val autoLdr: Boolean = false,
    val colorMode: Int = 0,
    val playlistEnabled: Boolean = false,
    val playlistStep: Int = 0,
    val playlistCount: Int = 0,
    val hourlyChimeEnabled: Boolean = true,
    val hourlyChimeMode: Int = 0,
    val dfConnected: Boolean = true,
    val dfVolume: Int = 20,
    val currentPlayingTrack: Int = 0,
    val wifiConnected: Boolean = true,
    val apMode: Boolean = false,
    val wifiSsid: String = "Sultan_Clock_AP",
    val ipAddress: String = "192.168.4.1",
    val connectionType: String = "Wi-Fi LAN",
    val firmwareVersion: String = "v5.0-ESP32",
    val prayerTimes: PrayerTimes? = null,
    val azanWaqtEnabled: List<Boolean> = listOf(true, true, true, true, true),
    val azanTrack: List<Int> = listOf(1, 2, 3, 4, 5),
    val alarms: List<ClockAlarmStatus> = listOf(
        ClockAlarmStatus(6, 30, false, 1),
        ClockAlarmStatus(18, 30, false, 2)
    ),
    val weeklyPlaylist: List<WeeklyPlaylistTodayStatus> = listOf(
        WeeklyPlaylistTodayStatus(true, "08:00", 10),
        WeeklyPlaylistTodayStatus(false, "14:00", 12)
    )
)

data class AlarmConfig(
    val alarm1Enabled: Boolean = false,
    val alarm1Hour: Int = 6,
    val alarm1Minute: Int = 30,
    val alarm1Track: Int = 1,
    val alarm2Enabled: Boolean = false,
    val alarm2Hour: Int = 18,
    val alarm2Minute: Int = 30,
    val alarm2Track: Int = 2
)

data class BrightnessConfig(
    val manualBrightness: Int = 120, // 1..255
    val autoLdr: Boolean = false,
    val ldrLowCut: Int = 100,
    val ldrHighCut: Int = 900
)

data class ColorConfig(
    val mode: Int = 0, // 0: Static Color, 1: Smooth Fade, 2: Rainbow, 3: Custom RGB, 4: Sweep Random
    val staticColorIndex: Int = 0,
    val colorIntervalSec: Int = 5,
    val red: Int = 255,
    val green: Int = 200,
    val blue: Int = 50,
    val animSpeed: Int = 5
) {
    companion object {
        val MODES = listOf(
            "Static Color",
            "Smooth Fade",
            "Rainbow Wave",
            "Custom RGB",
            "Sweep Random"
        )
    }
}

data class DisplayScheduleConfig(
    val isScheduleEnabled: Boolean = false,
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 6,
    val endMinute: Int = 0
)

data class DateSettingsConfig(
    val isDateEnabled: Boolean = true,
    val isBanglaDate: Boolean = false,
    val dateFormat: Int = 0 // 0: DD-MM-YYYY, 1: MM-DD-YYYY, 2: YYYY-MM-DD
)

data class PrayerTimes(
    val fajr: String = "04:12",
    val sunrise: String = "05:28",
    val dhuhr: String = "12:05",
    val asr: String = "16:35",
    val maghrib: String = "18:32",
    val isha: String = "19:48",
    val isAzanAlarmEnabled: Boolean = true,
    val calculationLocation: String = "Dhaka, Bangladesh",
    val calculationMethod: String = "University of Islamic Sciences, Karachi"
)

data class HourlyChimeConfig(
    val enabled: Boolean = true,
    val startHour: Int = 6,
    val endHour: Int = 22,
    val mode: Int = 0, // 0: Fixed, 1: Random, 2: Sequential, 3: By Hour
    val fixedTrack: Int = 1,
    val poolTracks: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7, 8),
    val hourlyTracks: List<Int> = List(24) { (it % 12) + 1 }
) {
    companion object {
        val MODES = listOf("Fixed Track", "Random Pool", "Sequential Pool", "By-Hour (24 Tracks)")
    }
}

data class TrackAssignmentsConfig(
    val fajrEnabled: Boolean = true,
    val fajrTrack: Int = 1,
    val dhuhrEnabled: Boolean = true,
    val dhuhrTrack: Int = 2,
    val asrEnabled: Boolean = true,
    val asrTrack: Int = 3,
    val maghribEnabled: Boolean = true,
    val maghribTrack: Int = 4,
    val ishaEnabled: Boolean = true,
    val ishaTrack: Int = 5,
    val alarm1Track: Int = 6,
    val alarm2Track: Int = 7
)

data class WeeklyPlaylistSlot(
    val id: Int = 1,
    val enabled: Boolean = true,
    val hour: Int = 8,
    val minute: Int = 0,
    val sunTrack: Int = 10,
    val monTrack: Int = 10,
    val tueTrack: Int = 10,
    val wedTrack: Int = 11,
    val thuTrack: Int = 11,
    val friTrack: Int = 15,
    val satTrack: Int = 10
)

data class ColorPlaylistStep(
    val stepId: Int = 1,
    val mode: Int = 0,
    val colorIndex: Int = 0,
    val red: Int = 255,
    val green: Int = 100,
    val blue: Int = 50,
    val durationSec: Int = 10,
    val speed: Int = 5
)

data class ColorPlaylistConfig(
    val enabled: Boolean = false,
    val steps: List<ColorPlaylistStep> = listOf(
        ColorPlaylistStep(1, 0, 0, 255, 200, 50, 15, 5),
        ColorPlaylistStep(2, 1, 1, 0, 242, 254, 20, 5),
        ColorPlaylistStep(3, 2, 2, 255, 0, 128, 25, 6)
    )
)

data class WifiScanResult(
    val ssid: String,
    val rssi: Int,
    val isSecure: Boolean
)

data class WifiConfig(
    val enabled: Boolean = true,
    val ssid: String = "",
    val password: String = "",
    val isOpen: Boolean = false,
    val isStatic: Boolean = false,
    val ip: String = "192.168.1.150",
    val gateway: String = "192.168.1.1",
    val subnet: String = "255.255.255.0"
)

data class ActionResponse(
    val isSuccess: Boolean,
    val message: String,
    val rawResponse: String? = null
)

data class TrackInfo(
    val trackNumber: Int,
    val name: String,
    val category: String = "General"
)

data class ColorPreset(
    val id: String,
    val nameBn: String,
    val nameEn: String,
    val description: String,
    val mode: Int, // 0: Static, 1: Fade, 2: Rainbow, 3: Custom RGB, 4: Sweep
    val red: Int,
    val green: Int,
    val blue: Int,
    val brightness: Int? = null,
    val previewHex: Long
) {
    companion object {
        val PRESETS = listOf(
            ColorPreset(
                id = "islamic_emerald",
                nameBn = "ইসলামিক মোড",
                nameEn = "Islamic Emerald",
                description = "শান্ত ও মনোরম ডিপ এমারেল্ড গ্রিন",
                mode = 0,
                red = 0,
                green = 230,
                blue = 118,
                previewHex = 0xFF00E676
            ),
            ColorPreset(
                id = "royal_sultan",
                nameBn = "রয়েল সুলতান",
                nameEn = "Royal Sultan Gold",
                description = "উজ্জ্বল প্রিমিয়াম গোল্ডেন গ্লো",
                mode = 0,
                red = 255,
                green = 215,
                blue = 0,
                previewHex = 0xFFFFD700
            ),
            ColorPreset(
                id = "night_amber",
                nameBn = "নাইট মোড",
                nameEn = "Cozy Warm Amber",
                description = "চোখের জন্য আরামদায়ক হালকা মোমবাতি অ্যাম্বার",
                mode = 0,
                red = 255,
                green = 140,
                blue = 0,
                brightness = 35,
                previewHex = 0xFFFF8F00
            ),
            ColorPreset(
                id = "cyberpunk_neon",
                nameBn = "সাইবারপাঙ্ক",
                nameEn = "Cyberpunk Neon",
                description = "নিয়ন সায়ান ও পিংক ডায়নামিক ভাইব",
                mode = 2,
                red = 0,
                green = 229,
                blue = 255,
                previewHex = 0xFF00E5FF
            ),
            ColorPreset(
                id = "ocean_breeze",
                nameBn = "ওশান ব্রিজ",
                nameEn = "Ocean Breeze",
                description = "শান্ত স্নিগ্ধ সমুদ্রের অ্যাকোয়া ব্লু",
                mode = 0,
                red = 0,
                green = 176,
                blue = 255,
                previewHex = 0xFF00B0FF
            ),
            ColorPreset(
                id = "sunset_crimson",
                nameBn = "সানসেট ফায়ার",
                nameEn = "Sunset Crimson",
                description = "মনোমুগ্ধকর লালচে সূর্যাস্তের আভা",
                mode = 1,
                red = 255,
                green = 61,
                blue = 0,
                previewHex = 0xFFFF3D00
            )
        )
    }
}

data class ClockProfileBackup(
    val id: String,
    val name: String,
    val createdAt: String,
    val alarms: AlarmConfig = AlarmConfig(),
    val hourlyChime: HourlyChimeConfig = HourlyChimeConfig(),
    val trackAssignments: TrackAssignmentsConfig = TrackAssignmentsConfig(),
    val brightness: BrightnessConfig = BrightnessConfig(),
    val color: ColorConfig = ColorConfig(),
    val displaySchedule: DisplayScheduleConfig = DisplayScheduleConfig(),
    val is12Hour: Boolean = false,
    val showDate: Boolean = true,
    val colonBlink: Boolean = true,
    val trackNames: Map<String, String> = emptyMap()
)
