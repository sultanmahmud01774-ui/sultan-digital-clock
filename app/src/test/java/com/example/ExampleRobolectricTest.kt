package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.network.Esp32Api
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Sultan Digital Clock", appName)
  }

  @Test
  fun `parseStatusJson correctly parses json response from ESP32 api status`() {
    val api = Esp32Api()
    val jsonString = """
      {
        "time": "15:45:30",
        "date": "2026-08-20",
        "banglaDate": "৫ ভাদ্র ১৪৩৩",
        "temperature": 29.4,
        "hourFormat": 0,
        "displayOn": true,
        "lightOn": true,
        "prayerAlarmEnabled": true,
        "tempSensorEnabled": true,
        "ldrRaw": 512,
        "brightness": 128,
        "autoLdr": true,
        "colorMode": 2,
        "playlistEnabled": true,
        "playlistStep": 1,
        "playlistCount": 3,
        "hourlyChimeEnabled": true,
        "hourlyChimeMode": 1,
        "dfPlayerOk": true,
        "dfVolume": 22,
        "wifiConnected": true,
        "wifiSsid": "Home_WiFi",
        "ip": "192.168.1.100",
        "apMode": false,
        "prayerTimes": {
          "fajr": "04:15",
          "sunrise": "05:30",
          "dhuhr": "12:08",
          "asr": "16:40",
          "maghrib": "18:35",
          "isha": "19:50"
        },
        "azanWaqtEnabled": [true, true, false, true, true],
        "azanTrack": [1, 2, 3, 4, 5],
        "alarms": [
          {"hour": 6, "minute": 15, "enabled": true, "track": 2},
          {"hour": 18, "minute": 45, "enabled": false, "track": 4}
        ],
        "weeklyPlaylist": [
          {"enabled": true, "time": "08:30", "todayTrack": 11, "tracks": [10, 10, 11, 11, 12, 15, 10]},
          {"enabled": false, "time": "14:00", "todayTrack": 12, "tracks": [12, 12, 12, 12, 12, 12, 12]}
        ]
      }
    """.trimIndent()

    val parsed = api.parseStatusJson(jsonString, "192.168.1.100")

    assertEquals("15:45:30", parsed.currentTimeStr)
    assertEquals("2026-08-20", parsed.currentDateStr)
    assertEquals("৫ ভাদ্র ১৪৩৩", parsed.banglaDate)
    assertEquals(29.4f, parsed.temperatureC, 0.01f)
    assertFalse(parsed.is12Hour)
    assertTrue(parsed.isDisplayOn)
    assertTrue(parsed.isLightOn)
    assertTrue(parsed.isPrayerAlarmOn)
    assertTrue(parsed.isTempSensorOn)
    assertEquals(512, parsed.ldrRaw)
    assertEquals(128, parsed.appliedBrightness)
    assertTrue(parsed.autoLdr)
    assertEquals(2, parsed.colorMode)
    assertTrue(parsed.playlistEnabled)
    assertEquals(1, parsed.playlistStep)
    assertEquals(3, parsed.playlistCount)
    assertTrue(parsed.hourlyChimeEnabled)
    assertEquals(1, parsed.hourlyChimeMode)
    assertTrue(parsed.dfConnected)
    assertEquals(22, parsed.dfVolume)
    assertTrue(parsed.wifiConnected)
    assertFalse(parsed.apMode)
    assertEquals("Home_WiFi", parsed.wifiSsid)
    assertEquals("192.168.1.100", parsed.ipAddress)
    assertEquals("Connected via Home WiFi: Home_WiFi", parsed.connectionType)
    assertEquals("v5.0-ESP32", parsed.firmwareVersion)

    assertNotNull(parsed.prayerTimes)
    assertEquals("04:15", parsed.prayerTimes?.fajr)
    assertEquals("05:30", parsed.prayerTimes?.sunrise)
    assertEquals("12:08", parsed.prayerTimes?.dhuhr)

    assertEquals(listOf(true, true, false, true, true), parsed.azanWaqtEnabled)
    assertEquals(listOf(1, 2, 3, 4, 5), parsed.azanTrack)

    assertEquals(2, parsed.alarms.size)
    assertEquals(6, parsed.alarms[0].hour)
    assertEquals(15, parsed.alarms[0].minute)
    assertTrue(parsed.alarms[0].enabled)
    assertEquals(2, parsed.alarms[0].track)

    assertEquals(2, parsed.weeklyPlaylist.size)
    assertTrue(parsed.weeklyPlaylist[0].enabled)
    assertEquals("08:30", parsed.weeklyPlaylist[0].time)
    assertEquals(11, parsed.weeklyPlaylist[0].todayTrack)
    assertEquals(listOf(10, 10, 11, 11, 12, 15, 10), parsed.weeklyPlaylist[0].tracks)
    assertEquals(10, parsed.weeklyPlaylist[0].tracks[0]) // Sun
    assertEquals(15, parsed.weeklyPlaylist[0].tracks[5]) // Fri
  }
}

