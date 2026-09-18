package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.network.Esp32Api
import com.example.data.network.SultanClockApi
import com.example.data.storage.DevicePreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class ActionFeedback(
    val inProgress: Boolean = false,
    val actionName: String = "",
    val isSuccess: Boolean? = null,
    val message: String = "",
    val timestamp: Long = 0L
)

data class SultanClockUiState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionMessage: String = "Ready to connect",
    val activeHost: String = "sultanclock.local",
    val username: String = "admin",
    val passwordInput: String = "",
    val rememberPassword: Boolean = true,
    val autoConnect: Boolean = true,
    val isManualIpDialogOpen: Boolean = false,
    val manualIpInput: String = "",
    val savedIps: Set<String> = emptySet(),
    
    // Live Dashboard Telemetry
    val dashboard: ClockDashboardData = ClockDashboardData(),
    val isPolling: Boolean = false,
    
    // Configurations
    val alarmConfig: AlarmConfig = AlarmConfig(),
    val brightnessConfig: BrightnessConfig = BrightnessConfig(),
    val colorConfig: ColorConfig = ColorConfig(),
    val displaySchedule: DisplayScheduleConfig = DisplayScheduleConfig(),
    val dateSettings: DateSettingsConfig = DateSettingsConfig(),
    val prayerTimes: PrayerTimes = PrayerTimes(),
    val hourlyChime: HourlyChimeConfig = HourlyChimeConfig(),
    val trackAssignments: TrackAssignmentsConfig = TrackAssignmentsConfig(),
    val weeklyPlaylist: List<WeeklyPlaylistSlot> = listOf(
        WeeklyPlaylistSlot(1, true, 8, 0, 10, 10, 11, 11, 15, 10, 10),
        WeeklyPlaylistSlot(2, false, 14, 0, 12, 12, 12, 12, 12, 12, 12)
    ),
    val colorPlaylist: ColorPlaylistConfig = ColorPlaylistConfig(),
    
    // Wi-Fi & Security
    val wifiScanList: List<WifiScanResult> = emptyList(),
    val isWifiScanning: Boolean = false,
    val wifiConfig: WifiConfig = WifiConfig(),
    val apPasswordInput: String = "",
    val oldAdminPasswordInput: String = "",
    val newAdminPasswordInput: String = "",
    
    // OTA Update
    val otaProgress: Int = 0,
    val isOtaUploading: Boolean = false,
    val selectedOtaFileName: String? = null,
    
    // Action Feedback
    val feedback: ActionFeedback = ActionFeedback(),

    // DFPlayer Track Catalog & Custom Names
    val trackNames: Map<Int, String> = DevicePreferences.DEFAULT_TRACK_NAMES,
    val isTrackManagerOpen: Boolean = false,

    // Configuration Profile Backups
    val savedProfiles: List<ClockProfileBackup> = emptyList(),
    val isProfileBackupOpen: Boolean = false
)

class ClockViewModel(application: Application) : AndroidViewModel(application) {

    private val api: Esp32Api = Esp32Api()
    private val prefs = DevicePreferences(application)

    private val _uiState = MutableStateFlow(
        SultanClockUiState(
            activeHost = prefs.ipAddress,
            username = prefs.username,
            passwordInput = prefs.password,
            rememberPassword = prefs.rememberPassword,
            autoConnect = prefs.autoConnect,
            manualIpInput = prefs.ipAddress,
            savedIps = prefs.getSavedIps(),
            trackNames = prefs.getTrackNames(),
            savedProfiles = prefs.getSavedProfiles()
        )
    )
    val uiState: StateFlow<SultanClockUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var reconnectJob: Job? = null
    private var isAppForeground: Boolean = true
    private var isHomeOrControlsVisible: Boolean = true

    init {
        if (_uiState.value.autoConnect) {
            connectToClock()
        }
    }

    // --- CONNECTION MANAGEMENT ---

    fun setHost(host: String) {
        _uiState.update { it.copy(activeHost = host.trim()) }
    }

    fun setUsername(user: String) {
        _uiState.update { it.copy(username = user.trim()) }
    }

    fun setPassword(pass: String) {
        _uiState.update { it.copy(passwordInput = pass) }
    }

    fun setRememberPassword(remember: Boolean) {
        _uiState.update { it.copy(rememberPassword = remember) }
        prefs.rememberPassword = remember
    }

    fun setAutoConnect(auto: Boolean) {
        _uiState.update { it.copy(autoConnect = auto) }
        prefs.autoConnect = auto
    }

    fun toggleManualIpDialog(show: Boolean) {
        _uiState.update { it.copy(isManualIpDialogOpen = show) }
    }

    fun updateManualIpInput(ip: String) {
        _uiState.update { it.copy(manualIpInput = ip) }
    }

    fun saveManualIp() {
        val ip = _uiState.value.manualIpInput.trim()
        if (ip.isNotEmpty()) {
            prefs.addSavedIp(ip)
            prefs.ipAddress = ip
            _uiState.update {
                it.copy(
                    activeHost = ip,
                    savedIps = prefs.getSavedIps(),
                    isManualIpDialogOpen = false
                )
            }
            connectToClock(ip)
        }
    }

    fun removeSavedIp(ip: String) {
        prefs.removeSavedIp(ip)
        _uiState.update { it.copy(savedIps = prefs.getSavedIps()) }
    }

    fun connectToClock(targetHost: String? = null) {
        viewModelScope.launch {
            val hostToTry = targetHost ?: _uiState.value.activeHost
            _uiState.update {
                it.copy(
                    connectionStatus = ConnectionStatus.CONNECTING,
                    connectionMessage = "Connecting to $hostToTry...",
                    activeHost = hostToTry
                )
            }

            val user = _uiState.value.username
            val pass = _uiState.value.passwordInput

            // Try candidate hosts in order:
            // 1) Last-successfully-connected saved IP first (from DevicePreferences)
            // 2) User-entered host
            // 3) 192.168.4.1 (AP fallback)
            // 4) sultanclock.local LAST (low-priority fallback)
            val candidateHosts = mutableListOf<String>()
            val savedIp = prefs.ipAddress.trim()
            if (savedIp.isNotEmpty() && savedIp != DevicePreferences.DEFAULT_MDNS_HOST) {
                candidateHosts.add(savedIp)
            }
            if (hostToTry.isNotBlank()) {
                candidateHosts.add(hostToTry.trim())
            }
            candidateHosts.add(DevicePreferences.DEFAULT_AP_IP)
            candidateHosts.add(DevicePreferences.DEFAULT_MDNS_HOST)
            val finalCandidates = candidateHosts.distinct()

            var connectedHost: String? = null
            var lastStatus = ConnectionStatus.DISCONNECTED
            var lastMsg = "ESP32 not found"

            for (cand in finalCandidates) {
                _uiState.update { it.copy(connectionMessage = "Probing $cand...") }
                val (status, msg) = api.checkConnection(cand, user, pass)
                if (status == ConnectionStatus.CONNECTED) {
                    connectedHost = cand
                    lastStatus = status
                    lastMsg = msg
                    break
                } else if (status == ConnectionStatus.AUTH_REQUIRED) {
                    connectedHost = cand
                    lastStatus = status
                    lastMsg = "Password required for admin@$cand"
                    break
                } else {
                    lastStatus = status
                    lastMsg = msg
                }
            }

            if (connectedHost != null && lastStatus == ConnectionStatus.CONNECTED) {
                prefs.ipAddress = connectedHost
                prefs.addSavedIp(connectedHost)
                if (_uiState.value.rememberPassword) {
                    prefs.password = pass
                    prefs.username = user
                }
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.CONNECTED,
                        connectionMessage = "Connected to $connectedHost",
                        activeHost = connectedHost,
                        savedIps = prefs.getSavedIps()
                    )
                }
                evaluatePolling()
            } else if (lastStatus == ConnectionStatus.AUTH_REQUIRED) {
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.AUTH_REQUIRED,
                        connectionMessage = lastMsg,
                        activeHost = connectedHost ?: hostToTry
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        connectionStatus = ConnectionStatus.DISCONNECTED,
                        connectionMessage = "Clock not found. Check Wi-Fi / AP connection."
                    )
                }
            }
        }
    }

    fun disconnect() {
        stopPolling()
        _uiState.update {
            it.copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                connectionMessage = "Disconnected by user"
            )
        }
    }

    // --- TELEMETRY POLLING VIA /api/status ---

    fun updateVisibility(isHomeOrControls: Boolean, isForeground: Boolean) {
        isHomeOrControlsVisible = isHomeOrControls
        isAppForeground = isForeground
        evaluatePolling()
    }

    private fun evaluatePolling() {
        val shouldPoll = isAppForeground &&
                isHomeOrControlsVisible &&
                _uiState.value.connectionStatus == ConnectionStatus.CONNECTED

        if (shouldPoll) {
            if (pollingJob == null || pollingJob?.isActive != true) {
                startLiveStatusPolling(_uiState.value.activeHost)
            }
        } else {
            stopPolling()
        }
    }

    private fun startLiveStatusPolling(host: String) {
        stopPolling()
        pollingJob = viewModelScope.launch {
            _uiState.update { it.copy(isPolling = true) }
            while (isActive) {
                try {
                    val user = _uiState.value.username
                    val pass = _uiState.value.passwordInput
                    val data = api.getStatus(host, user, pass)
                    _uiState.update { current ->
                        val updatedAlarmConfig = current.alarmConfig.copy(
                            alarm1Hour = data.alarms.getOrNull(0)?.hour ?: current.alarmConfig.alarm1Hour,
                            alarm1Minute = data.alarms.getOrNull(0)?.minute ?: current.alarmConfig.alarm1Minute,
                            alarm1Enabled = data.alarms.getOrNull(0)?.enabled ?: current.alarmConfig.alarm1Enabled,
                            alarm1Track = data.alarms.getOrNull(0)?.track ?: current.alarmConfig.alarm1Track,
                            alarm2Hour = data.alarms.getOrNull(1)?.hour ?: current.alarmConfig.alarm2Hour,
                            alarm2Minute = data.alarms.getOrNull(1)?.minute ?: current.alarmConfig.alarm2Minute,
                            alarm2Enabled = data.alarms.getOrNull(1)?.enabled ?: current.alarmConfig.alarm2Enabled,
                            alarm2Track = data.alarms.getOrNull(1)?.track ?: current.alarmConfig.alarm2Track
                        )

                        val updatedTrackAssignments = current.trackAssignments.copy(
                            fajrEnabled = data.azanWaqtEnabled.getOrElse(0) { current.trackAssignments.fajrEnabled },
                            dhuhrEnabled = data.azanWaqtEnabled.getOrElse(1) { current.trackAssignments.dhuhrEnabled },
                            asrEnabled = data.azanWaqtEnabled.getOrElse(2) { current.trackAssignments.asrEnabled },
                            maghribEnabled = data.azanWaqtEnabled.getOrElse(3) { current.trackAssignments.maghribEnabled },
                            ishaEnabled = data.azanWaqtEnabled.getOrElse(4) { current.trackAssignments.ishaEnabled },
                            fajrTrack = data.azanTrack.getOrElse(0) { current.trackAssignments.fajrTrack },
                            dhuhrTrack = data.azanTrack.getOrElse(1) { current.trackAssignments.dhuhrTrack },
                            asrTrack = data.azanTrack.getOrElse(2) { current.trackAssignments.asrTrack },
                            maghribTrack = data.azanTrack.getOrElse(3) { current.trackAssignments.maghribTrack },
                            ishaTrack = data.azanTrack.getOrElse(4) { current.trackAssignments.ishaTrack },
                            alarm1Track = data.alarms.getOrNull(0)?.track ?: current.trackAssignments.alarm1Track,
                            alarm2Track = data.alarms.getOrNull(1)?.track ?: current.trackAssignments.alarm2Track
                        )

                        val updatedPrayerTimes = data.prayerTimes?.copy(
                            isAzanAlarmEnabled = data.isPrayerAlarmOn,
                            calculationLocation = current.prayerTimes.calculationLocation,
                            calculationMethod = current.prayerTimes.calculationMethod
                        ) ?: current.prayerTimes.copy(isAzanAlarmEnabled = data.isPrayerAlarmOn)

                        val updatedWeeklyPlaylist = if (data.weeklyPlaylist.isNotEmpty()) {
                            data.weeklyPlaylist.mapIndexed { index, item ->
                                val timeParts = item.time.split(":")
                                val h = timeParts.getOrNull(0)?.toIntOrNull() ?: (if (index == 0) 8 else 14)
                                val m = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
                                val t = item.tracks
                                WeeklyPlaylistSlot(
                                    id = index + 1,
                                    enabled = item.enabled,
                                    hour = h,
                                    minute = m,
                                    sunTrack = t.getOrElse(0) { item.todayTrack },
                                    monTrack = t.getOrElse(1) { item.todayTrack },
                                    tueTrack = t.getOrElse(2) { item.todayTrack },
                                    wedTrack = t.getOrElse(3) { item.todayTrack },
                                    thuTrack = t.getOrElse(4) { item.todayTrack },
                                    friTrack = t.getOrElse(5) { item.todayTrack },
                                    satTrack = t.getOrElse(6) { item.todayTrack }
                                )
                            }
                        } else {
                            current.weeklyPlaylist
                        }

                        current.copy(
                            dashboard = data,
                            alarmConfig = updatedAlarmConfig,
                            trackAssignments = updatedTrackAssignments,
                            prayerTimes = updatedPrayerTimes,
                            weeklyPlaylist = updatedWeeklyPlaylist,
                            colorConfig = current.colorConfig.copy(mode = data.colorMode),
                            brightnessConfig = current.brightnessConfig.copy(
                                autoLdr = data.autoLdr,
                                manualBrightness = data.appliedBrightness
                            ),
                            hourlyChime = current.hourlyChime.copy(
                                enabled = data.hourlyChimeEnabled,
                                mode = data.hourlyChimeMode
                            )
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("ClockViewModel", "Live status polling error: ${e.message}")
                }
                delay(3500) // Poll every 3.5 seconds while Home/Controls screen is visible
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        _uiState.update { it.copy(isPolling = false) }
    }

    // --- QUICK ACTION HANDLERS ---

    private fun executeAction(name: String, actionBlock: suspend () -> ActionResponse) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    feedback = ActionFeedback(
                        inProgress = true,
                        actionName = name,
                        message = "Sending command...",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            val result = try {
                actionBlock()
            } catch (e: Exception) {
                ActionResponse(false, e.localizedMessage ?: "Action failed")
            }

            _uiState.update {
                it.copy(
                    feedback = ActionFeedback(
                        inProgress = false,
                        actionName = name,
                        isSuccess = result.isSuccess,
                        message = if (result.isSuccess) "$name applied successfully" else result.message,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun syncTimeFromPhone() {
        executeAction("Phone Time Sync") {
            api.syncTimeFromPhone(
                _uiState.value.activeHost,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    fun syncFromNtp() {
        executeAction("NTP Time Sync") {
            api.syncFromNtp(
                _uiState.value.activeHost,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    fun toggleDisplay() {
        executeAction("Display Toggle") {
            val result = api.toggleDisplay(
                _uiState.value.activeHost,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
            // FIX: apply the state the ESP32 actually confirmed (rawResponse = "ON"/"OFF"),
            // instead of guessing beforehand. Guessing before the network call caused the
            // background /api/status poller (every 3.5s) to race with the toggle request and
            // occasionally overwrite the optimistic guess with a stale value — looking like
            // the switch "turned itself back on".
            if (result.isSuccess && (result.rawResponse == "ON" || result.rawResponse == "OFF")) {
                val newState = result.rawResponse == "ON"
                _uiState.update { it.copy(dashboard = it.dashboard.copy(isDisplayOn = newState)) }
            }
            result
        }
    }

    fun toggleLight() {
        executeAction("Light Toggle") {
            val result = api.toggleLight(
                _uiState.value.activeHost,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
            if (result.isSuccess && (result.rawResponse == "ON" || result.rawResponse == "OFF")) {
                val newState = result.rawResponse == "ON"
                _uiState.update { it.copy(dashboard = it.dashboard.copy(isLightOn = newState)) }
            }
            result
        }
    }

    fun togglePrayerAlarm() {
        executeAction("Prayer Alarm Toggle") {
            val result = api.togglePrayerAlarm(
                _uiState.value.activeHost,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
            if (result.isSuccess && (result.rawResponse == "ON" || result.rawResponse == "OFF")) {
                val newState = result.rawResponse == "ON"
                _uiState.update { it.copy(dashboard = it.dashboard.copy(isPrayerAlarmOn = newState)) }
            }
            result
        }
    }

    fun toggleTempSensor() {
        executeAction("Temp Sensor Toggle") {
            val result = api.toggleTempSensor(
                _uiState.value.activeHost,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
            if (result.isSuccess && (result.rawResponse == "ON" || result.rawResponse == "OFF")) {
                val newState = result.rawResponse == "ON"
                _uiState.update { it.copy(dashboard = it.dashboard.copy(isTempSensorOn = newState)) }
            }
            result
        }
    }

    // --- BRIGHTNESS CONTROLS ---

    fun updateManualBrightness(value: Int) {
        _uiState.update { it.copy(brightnessConfig = it.brightnessConfig.copy(manualBrightness = value)) }
    }

    fun updateAutoLdr(enabled: Boolean) {
        _uiState.update { it.copy(brightnessConfig = it.brightnessConfig.copy(autoLdr = enabled)) }
    }

    fun updateLdrCuts(low: Int, high: Int) {
        _uiState.update { it.copy(brightnessConfig = it.brightnessConfig.copy(ldrLowCut = low, ldrHighCut = high)) }
    }

    fun saveBrightness() {
        executeAction("Save Brightness") {
            api.saveBrightness(
                _uiState.value.activeHost,
                _uiState.value.brightnessConfig,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    // --- COLOR CONTROLS ---

    fun updateColorMode(mode: Int) {
        _uiState.update { it.copy(colorConfig = it.colorConfig.copy(mode = mode)) }
    }

    fun updateRgb(r: Int, g: Int, b: Int) {
        // FIX: the ESP32 firmware only applies r/g/b when colorMode == 3 (Custom RGB).
        // Picking a color/slider without switching mode meant the picked color was sent
        // but silently ignored server-side (Static/Fade modes use a preset index instead).
        // Auto-switch to Custom RGB the moment the user touches a color control.
        _uiState.update {
            it.copy(colorConfig = it.colorConfig.copy(red = r, green = g, blue = b, mode = 3))
        }
    }

    fun saveColor() {
        executeAction("Save Color Mode") {
            api.saveColor(
                _uiState.value.activeHost,
                _uiState.value.colorConfig,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    // --- DISPLAY SCHEDULE ---

    fun updateDisplaySchedule(config: DisplayScheduleConfig) {
        _uiState.update { it.copy(displaySchedule = config) }
    }

    fun saveDisplaySchedule() {
        executeAction("Save Display Schedule") {
            api.saveDisplaySchedule(
                _uiState.value.activeHost,
                _uiState.value.displaySchedule,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    // --- DATE SETTINGS ---

    fun updateDateSettings(config: DateSettingsConfig) {
        _uiState.update { it.copy(dateSettings = config) }
    }

    fun saveDateSettings() {
        executeAction("Save Date Settings") {
            api.saveDateSettings(
                _uiState.value.activeHost,
                _uiState.value.dateSettings,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    // --- ALARM SETTINGS ---

    fun updateAlarmConfig(config: AlarmConfig) {
        _uiState.update { it.copy(alarmConfig = config) }
    }

    fun saveAlarmConfig() {
        executeAction("Save Alarms") {
            api.saveAlarm(
                _uiState.value.activeHost,
                _uiState.value.alarmConfig,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    // --- DFPLAYER MP3 & HOURLY CHIME ---

    fun updateDfVolume(vol: Int) {
        _uiState.update { it.copy(dashboard = it.dashboard.copy(dfVolume = vol)) }
        executeAction("DFPlayer Volume") {
            api.saveDfVolume(
                _uiState.value.activeHost,
                vol,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    fun testDfTrack(track: Int) {
        _uiState.update { it.copy(dashboard = it.dashboard.copy(currentPlayingTrack = track)) }
        executeAction("Test Track $track") {
            api.testDfTrack(
                _uiState.value.activeHost,
                track,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    fun updateHourlyChime(config: HourlyChimeConfig) {
        _uiState.update { it.copy(hourlyChime = config) }
    }

    fun saveHourlyChime() {
        executeAction("Save Hourly Chime") {
            api.saveHourlyMode(
                _uiState.value.activeHost,
                _uiState.value.hourlyChime,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    fun saveToneRange() {
        val conf = _uiState.value.hourlyChime
        executeAction("Save Chime Active Hours") {
            api.saveToneRange(
                _uiState.value.activeHost,
                conf.enabled,
                conf.startHour,
                conf.endHour,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    // --- TRACK ASSIGNMENTS ---

    fun updateTrackAssignments(config: TrackAssignmentsConfig) {
        _uiState.update { it.copy(trackAssignments = config) }
    }

    fun saveTrackAssignments() {
        executeAction("Save Track Assignments") {
            api.saveTrackAssignments(
                _uiState.value.activeHost,
                _uiState.value.trackAssignments,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    // --- WEEKLY PLAYLIST ---

    fun updateWeeklySlot(slot: WeeklyPlaylistSlot) {
        val updated = _uiState.value.weeklyPlaylist.map {
            if (it.id == slot.id) slot else it
        }
        _uiState.update { it.copy(weeklyPlaylist = updated) }
    }

    fun addWeeklySlot() {
        val current = _uiState.value.weeklyPlaylist
        // Firmware intentionally supports two persistent weekly slots.
        if (current.size >= 2) return
        val newId = (current.maxOfOrNull { it.id } ?: 0) + 1
        _uiState.update {
            it.copy(weeklyPlaylist = current + WeeklyPlaylistSlot(id = newId, enabled = true, hour = 12, minute = 0))
        }
    }

    fun removeWeeklySlot(id: Int) {
        _uiState.update {
            it.copy(weeklyPlaylist = it.weeklyPlaylist.filter { slot -> slot.id != id })
        }
    }

    fun saveWeeklyPlaylist() {
        executeAction("Save Weekly Playlist") {
            api.saveWeeklyPlaylist(
                _uiState.value.activeHost,
                _uiState.value.weeklyPlaylist,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    // --- COLOR PLAYLIST ---

    fun updateColorPlaylist(config: ColorPlaylistConfig) {
        _uiState.update { it.copy(colorPlaylist = config) }
    }

    fun saveColorPlaylist() {
        executeAction("Save Color Playlist") {
            api.saveColorPlaylist(
                _uiState.value.activeHost,
                _uiState.value.colorPlaylist,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    fun toggleColorPlaylist(enabled: Boolean) {
        _uiState.update { it.copy(colorPlaylist = it.colorPlaylist.copy(enabled = enabled)) }
        executeAction("Toggle Color Playlist") {
            api.toggleColorPlaylist(
                _uiState.value.activeHost,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    // --- WI-FI & SECURITY ---

    fun scanWifiNetworks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWifiScanning = true) }
            val results = api.scanWifi(
                _uiState.value.activeHost,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
            _uiState.update {
                it.copy(
                    wifiScanList = results,
                    isWifiScanning = false
                )
            }
        }
    }

    fun updateWifiConfig(config: WifiConfig) {
        _uiState.update { it.copy(wifiConfig = config) }
    }

    fun saveWifiConfig() {
        executeAction("Save Wi-Fi Network") {
            api.saveWifi(
                _uiState.value.activeHost,
                _uiState.value.wifiConfig,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    fun updateApPasswordInput(pass: String) {
        _uiState.update { it.copy(apPasswordInput = pass) }
    }

    fun saveApPassword() {
        val pass = _uiState.value.apPasswordInput
        executeAction("Update AP Hotspot Password") {
            api.changeApPassword(
                _uiState.value.activeHost,
                pass,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    fun updateOldAdminPassword(pass: String) {
        _uiState.update { it.copy(oldAdminPasswordInput = pass) }
    }

    fun updateNewAdminPassword(pass: String) {
        _uiState.update { it.copy(newAdminPasswordInput = pass) }
    }

    fun saveWebPassword() {
        val oldPass = _uiState.value.oldAdminPasswordInput
        val newPass = _uiState.value.newAdminPasswordInput
        executeAction("Update Web UI Password") {
            val res = api.changeWebPassword(
                _uiState.value.activeHost,
                oldPass,
                newPass,
                keepEnabled = true,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
            if (res.isSuccess) {
                _uiState.update { it.copy(passwordInput = newPass) }
                prefs.password = newPass
            }
            res
        }
    }

    // --- OTA UPDATE ---

    fun onOtaFileSelected(uri: Uri, fileName: String) {
        _uiState.update { it.copy(selectedOtaFileName = fileName) }
    }

    fun uploadOtaFirmware(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isOtaUploading = true, otaProgress = 10) }
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                val tempFile = File(context.cacheResolverOrDir(), "firmware_update.bin")
                FileOutputStream(tempFile).use { output ->
                    inputStream?.copyTo(output)
                }

                _uiState.update { it.copy(otaProgress = 30) }
                val result = api.uploadOtaFirmware(
                    _uiState.value.activeHost,
                    tempFile,
                    _uiState.value.username,
                    _uiState.value.passwordInput
                ) { progress ->
                    _uiState.update { it.copy(otaProgress = progress) }
                }

                _uiState.update {
                    it.copy(
                        isOtaUploading = false,
                        feedback = ActionFeedback(
                            inProgress = false,
                            actionName = "OTA Update",
                            isSuccess = result.isSuccess,
                            message = result.message,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isOtaUploading = false,
                        feedback = ActionFeedback(
                            inProgress = false,
                            actionName = "OTA Update",
                            isSuccess = false,
                            message = e.localizedMessage ?: "Failed to upload firmware",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    // --- ONE-TAP COLOR PRESETS ---

    fun applyColorPreset(preset: ColorPreset) {
        viewModelScope.launch {
            val updatedConfig = _uiState.value.colorConfig.copy(
                mode = preset.mode,
                red = preset.red,
                green = preset.green,
                blue = preset.blue
            )
            _uiState.update { it.copy(colorConfig = updatedConfig) }

            executeAction("${preset.nameBn} প্রিসেট") {
                // Apply color config
                val colorRes = api.saveColor(
                    _uiState.value.activeHost,
                    updatedConfig,
                    _uiState.value.username,
                    _uiState.value.passwordInput
                )
                // If preset defines specific brightness (e.g. Night Mode), apply it as well
                if (preset.brightness != null) {
                    val brightConfig = _uiState.value.brightnessConfig.copy(
                        manualBrightness = preset.brightness,
                        autoLdr = false
                    )
                    _uiState.update { it.copy(brightnessConfig = brightConfig) }
                    api.saveBrightness(
                        _uiState.value.activeHost,
                        brightConfig,
                        _uiState.value.username,
                        _uiState.value.passwordInput
                    )
                }
                colorRes
            }
        }
    }

    // --- DFPLAYER SD CARD TRACK MANAGER ---

    fun openTrackManager(open: Boolean) {
        _uiState.update { it.copy(isTrackManagerOpen = open) }
    }

    fun saveTrackName(trackNumber: Int, name: String) {
        prefs.saveTrackName(trackNumber, name)
        _uiState.update { it.copy(trackNames = prefs.getTrackNames()) }
    }

    fun resetTrackNames() {
        prefs.resetTrackNamesToDefault()
        _uiState.update { it.copy(trackNames = prefs.getTrackNames()) }
    }

    fun getTrackDisplayName(trackNumber: Int): String {
        val custom = _uiState.value.trackNames[trackNumber]
        return if (!custom.isNullOrBlank()) "Track $trackNumber: $custom" else "Track $trackNumber"
    }

    // --- CONFIGURATION PROFILE BACKUP & RESTORE ---

    fun openProfileBackup(open: Boolean) {
        _uiState.update { it.copy(isProfileBackupOpen = open) }
    }

    fun saveCurrentProfile(name: String) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        val profile = ClockProfileBackup(
            id = "profile_${System.currentTimeMillis()}",
            name = name,
            createdAt = sdf.format(Date()),
            alarms = _uiState.value.alarmConfig,
            hourlyChime = _uiState.value.hourlyChime,
            trackAssignments = _uiState.value.trackAssignments,
            brightness = _uiState.value.brightnessConfig,
            color = _uiState.value.colorConfig,
            displaySchedule = _uiState.value.displaySchedule,
            is12Hour = _uiState.value.dateSettings.dateFormat == 0,
            showDate = _uiState.value.dateSettings.isDateEnabled,
            trackNames = _uiState.value.trackNames.mapKeys { it.key.toString() }
        )
        prefs.saveProfile(profile)
        _uiState.update {
            it.copy(
                savedProfiles = prefs.getSavedProfiles(),
                feedback = ActionFeedback(
                    isSuccess = true,
                    actionName = "Profile Backup",
                    message = "'$name' ব্যাকআপ সংরক্ষিত হয়েছে",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteProfile(profileId: String) {
        prefs.deleteProfile(profileId)
        _uiState.update { it.copy(savedProfiles = prefs.getSavedProfiles()) }
    }

    fun restoreProfile(profile: ClockProfileBackup) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    alarmConfig = profile.alarms,
                    hourlyChime = profile.hourlyChime,
                    trackAssignments = profile.trackAssignments,
                    brightnessConfig = profile.brightness,
                    colorConfig = profile.color,
                    displaySchedule = profile.displaySchedule,
                    feedback = ActionFeedback(
                        inProgress = true,
                        actionName = "Restore Profile",
                        message = "প্রোফাইল '${profile.name}' ক্লকে পাঠানো হচ্ছে...",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            val host = _uiState.value.activeHost
            val user = _uiState.value.username
            val pass = _uiState.value.passwordInput

            try {
                // 1. Send Color & Brightness
                api.saveColor(host, profile.color, user, pass)
                api.saveBrightness(host, profile.brightness, user, pass)

                // 2. Send Alarms
                api.saveAlarm(host, profile.alarms, user, pass)

                // 3. Send Hourly Chime
                api.saveHourlyMode(
                    host = host,
                    config = profile.hourlyChime,
                    user = user,
                    pass = pass
                )
                api.saveToneRange(
                    host = host,
                    enabled = profile.hourlyChime.enabled,
                    startHour = profile.hourlyChime.startHour,
                    endHour = profile.hourlyChime.endHour,
                    user = user,
                    pass = pass
                )

                // 4. Send Track Assignments
                api.saveTrackAssignments(host, profile.trackAssignments, user, pass)

                // 5. Send Display Settings
                api.saveDisplaySettings(
                    host = host,
                    is12Hour = profile.is12Hour,
                    showDate = profile.showDate,
                    colonBlink = profile.colonBlink,
                    hourlyBeep = profile.hourlyChime.enabled,
                    user = user,
                    pass = pass
                )

                _uiState.update {
                    it.copy(
                        feedback = ActionFeedback(
                            inProgress = false,
                            actionName = "Restore Profile",
                            isSuccess = true,
                            message = "'${profile.name}' সফলভাবে ক্লকে রিস্টোর হয়েছে!",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        feedback = ActionFeedback(
                            inProgress = false,
                            actionName = "Restore Profile",
                            isSuccess = false,
                            message = "রিস্টোর ব্যর্থ: ${e.localizedMessage}",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    private fun android.content.Context.cacheResolverOrDir(): File = cacheDir

    override fun onCleared() {
        super.onCleared()
        stopPolling()
        reconnectJob?.cancel()
    }
}
