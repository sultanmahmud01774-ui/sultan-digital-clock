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
    val selectedModel: ClockModel = ClockModel.ESP8266,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val connectionMessage: String = "Ready to connect",
    val activeHost: String = "192.168.4.1",
    val username: String = "admin",
    val passwordInput: String = "",
    val rememberPassword: Boolean = true,
    val autoConnect: Boolean = true,
    val isManualIpDialogOpen: Boolean = false,
    val manualIpInput: String = "",
    val savedIps: Set<String> = emptySet(),
    val wifiPasswordDisplay: String? = null,

    // Real-World ESP8266 Setup & Safe Reboot State
    val isRebooting: Boolean = false,
    val rebootCountdownSec: Int = 0,
    val rebootTargetIp: String = "",
    val esp8266RouterSsid: String = "",
    val esp8266RouterPass: String = "",
    val esp8266UseStaticIp: Boolean = true,
    val esp8266StaticIp: String = "192.168.0.108",
    val esp8266Gateway: String = "192.168.0.1",
    val esp8266Subnet: String = "255.255.255.0",
    
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
            selectedModel = prefs.clockModel,
            activeHost = prefs.getIpForModel(prefs.clockModel),
            username = prefs.username,
            passwordInput = prefs.password,
            rememberPassword = prefs.rememberPassword,
            autoConnect = prefs.autoConnect,
            manualIpInput = prefs.getIpForModel(prefs.clockModel),
            savedIps = prefs.getSavedIps(),
            trackNames = prefs.getTrackNames(),
            savedProfiles = prefs.getSavedProfiles(),
            esp8266RouterSsid = prefs.esp8266RouterSsid,
            esp8266UseStaticIp = prefs.esp8266UseStaticIp,
            esp8266StaticIp = prefs.esp8266StaticIp,
            esp8266Gateway = prefs.esp8266Gateway,
            esp8266Subnet = prefs.esp8266Subnet
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

    // --- CLOCK MODEL SELECTION (ESP32 vs ESP8266) ---

    fun setClockModel(model: ClockModel) {
        if (_uiState.value.selectedModel == model) return
        prefs.clockModel = model
        val targetIp = prefs.getIpForModel(model)
        stopPolling()
        _uiState.update {
            it.copy(
                selectedModel = model,
                activeHost = targetIp,
                manualIpInput = targetIp,
                connectionStatus = ConnectionStatus.CONNECTING,
                connectionMessage = "Switched to ${model.title}. Connecting to $targetIp..."
            )
        }
        connectToClock(targetIp)
    }

    // --- CONNECTION MANAGEMENT ---

    fun setHost(host: String) {
        val cleanHost = host.trim()
        prefs.setIpForModel(_uiState.value.selectedModel, cleanHost)
        _uiState.update { it.copy(activeHost = cleanHost, manualIpInput = cleanHost) }
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
            prefs.setIpForModel(_uiState.value.selectedModel, ip)
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

    // --- DEDICATED REAL-WORLD ESP8266 SETUP & REBOOT WORKFLOW ---

    fun connectToEsp8266Ap() {
        val apIp = DevicePreferences.DEFAULT_AP_IP
        setClockModel(ClockModel.ESP8266)
        setHost(apIp)
        connectToClock(apIp)
    }

    fun updateEsp8266SetupConfig(
        ssid: String? = null,
        pass: String? = null,
        useStaticIp: Boolean? = null,
        staticIp: String? = null,
        gateway: String? = null,
        subnet: String? = null
    ) {
        _uiState.update {
            it.copy(
                esp8266RouterSsid = ssid ?: it.esp8266RouterSsid,
                esp8266RouterPass = pass ?: it.esp8266RouterPass,
                esp8266UseStaticIp = useStaticIp ?: it.esp8266UseStaticIp,
                esp8266StaticIp = staticIp ?: it.esp8266StaticIp,
                esp8266Gateway = gateway ?: it.esp8266Gateway,
                esp8266Subnet = subnet ?: it.esp8266Subnet
            )
        }
    }

    fun saveEsp8266WifiAndReboot(
        ssid: String = _uiState.value.esp8266RouterSsid,
        pass: String = _uiState.value.esp8266RouterPass,
        useStaticIp: Boolean = _uiState.value.esp8266UseStaticIp,
        staticIp: String = _uiState.value.esp8266StaticIp,
        gateway: String = _uiState.value.esp8266Gateway,
        subnet: String = _uiState.value.esp8266Subnet
    ) {
        viewModelScope.launch {
            val cleanSsid = ssid.trim()
            if (cleanSsid.isEmpty()) {
                _uiState.update {
                    it.copy(
                        feedback = ActionFeedback(
                            inProgress = false,
                            actionName = "Save Wi-Fi",
                            message = "অনুগ্রহ করে আপনার রাউটারের Wi-Fi SSID দিন বা স্ক্যান করুন",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                }
                return@launch
            }

            val assignedIp = if (useStaticIp && staticIp.isNotBlank()) staticIp.trim() else "192.168.0.108"

            // Step 4 (Permanent Control IP): Once saved, automatically remember and set this assigned IP (e.g. 192.168.0.108) as the active host address in SharedPreferences for all future 24/7 controls without needing AP mode again.
            prefs.esp8266RouterSsid = cleanSsid
            prefs.esp8266UseStaticIp = useStaticIp
            prefs.esp8266StaticIp = assignedIp
            prefs.esp8266Gateway = gateway.trim()
            prefs.esp8266Subnet = subnet.trim()
            prefs.setIpForModel(ClockModel.ESP8266, assignedIp)
            prefs.addSavedIp(assignedIp)

            val currentHost = _uiState.value.activeHost
            val wifiConfig = WifiConfig(
                enabled = true,
                ssid = cleanSsid,
                isOpen = pass.isEmpty(),
                password = pass,
                isStatic = useStaticIp,
                ip = assignedIp,
                gateway = gateway.trim().ifBlank { "192.168.0.1" },
                subnet = subnet.trim().ifBlank { "255.255.255.0" }
            )

            _uiState.update {
                it.copy(
                    feedback = ActionFeedback(
                        inProgress = true,
                        actionName = "রাউটারে Wi-Fi ও IP ($assignedIp) পাঠানো হচ্ছে..."
                    )
                )
            }

            // Send /savewifi to current host (typically 192.168.4.1)
            api.saveWifi(currentHost, wifiConfig, _uiState.value.username, _uiState.value.passwordInput)

            // Step 5 (Safe Reboot Handling): When saving Wi-Fi & IP, pause background status polling for 15 seconds to prevent network socket drops or app crashes while the ESP8266 restarts.
            stopPolling()

            _uiState.update {
                it.copy(
                    isRebooting = true,
                    rebootCountdownSec = 15,
                    rebootTargetIp = assignedIp,
                    activeHost = assignedIp,
                    manualIpInput = assignedIp,
                    connectionStatus = ConnectionStatus.CONNECTING,
                    connectionMessage = "ESP8266 রিস্টার্ট হচ্ছে... অনুগ্রহ করে আপনার ফোনের ওয়াই-ফাই রাউটারে ($cleanSsid) যুক্ত করুন।",
                    feedback = ActionFeedback(
                        inProgress = true,
                        actionName = "ESP8266 রিবুট হচ্ছে ($assignedIp)..."
                    )
                )
            }

            // 15-second countdown with visual progress
            for (sec in 15 downTo 1) {
                _uiState.update { it.copy(rebootCountdownSec = sec) }
                delay(1000L)
            }

            _uiState.update {
                it.copy(
                    isRebooting = false,
                    rebootCountdownSec = 0,
                    connectionStatus = ConnectionStatus.CONNECTING,
                    connectionMessage = "স্থায়ী IP ($assignedIp) এ ঘড়ির সাথে যুক্ত হওয়া হচ্ছে...",
                    feedback = ActionFeedback(
                        inProgress = false,
                        isSuccess = true,
                        message = "Wi-Fi ও IP সংরক্ষিত হয়েছে! স্থায়ী IP $assignedIp এ কানেক্ট করা হচ্ছে।",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }

            // Connect automatically to the permanent IP
            connectToClock(assignedIp)
        }
    }

    fun resetEsp8266DefaultPassword() {
        executeAction("Reset Default Password (sultan88)") {
            api.resetDefaultPassword(
                _uiState.value.activeHost,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    fun testEsp8266Buzzer() {
        testBuzzerBeep()
    }

    fun saveEsp8266DateDisplay(englishDate: Boolean, banglaDate: Boolean) {
        val config = _uiState.value.dateSettings.copy(
            isDateEnabled = englishDate,
            isBanglaDate = banglaDate
        )
        _uiState.update { it.copy(dateSettings = config) }
        executeAction("Save Date Display Settings") {
            api.saveDateSettings(
                _uiState.value.activeHost,
                config,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
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
                _uiState.value.connectionStatus == ConnectionStatus.CONNECTED &&
                !_uiState.value.isRebooting

        if (shouldPoll) {
            if (pollingJob == null || pollingJob?.isActive != true) {
                startLiveStatusPolling(_uiState.value.activeHost)
            }
        } else {
            stopPolling()
        }
    }

    fun pausePolling(durationMillis: Long = 12000L) {
        stopPolling()
        viewModelScope.launch {
            delay(durationMillis)
            evaluatePolling()
        }
    }

    private fun startLiveStatusPolling(host: String) {
        stopPolling()
        pollingJob = viewModelScope.launch {
            _uiState.update { it.copy(isPolling = true) }
            var consecutiveErrors = 0
            while (isActive) {
                try {
                    val user = _uiState.value.username
                    val pass = _uiState.value.passwordInput
                    val data = api.getStatus(host, user, pass)
                    if (data == null) {
                        consecutiveErrors++
                        if (consecutiveErrors >= 3) {
                            delay(6000)
                        } else {
                            delay(2500)
                        }
                        continue
                    }
                    consecutiveErrors = 0

                    // Performance optimization: If clock data has not changed, do not trigger recomposition
                    if (data == _uiState.value.dashboard) {
                        delay(4000)
                        continue
                    }

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
                                enabled = data.hourlyBeepEnabled,
                                mode = data.hourlyChimeMode,
                                startHour = data.hourlyToneStartHour,
                                endHour = data.hourlyToneEndHour
                            ),
                            selectedModel = if (data.firmwareVersion.contains("ESP8266")) ClockModel.ESP8266 else current.selectedModel
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("ClockViewModel", "Live status polling error: ${e.message}")
                    consecutiveErrors++
                    if (consecutiveErrors >= 2) {
                        delay(7000) // Back off on connection errors
                        continue
                    }
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

            _uiState.update { current ->
                val isConnectionFailure = !result.isSuccess && (
                    result.message.contains("timed out", ignoreCase = true) ||
                    result.message.contains("unreachable", ignoreCase = true) ||
                    result.message.contains("offline", ignoreCase = true)
                )
                current.copy(
                    feedback = ActionFeedback(
                        inProgress = false,
                        actionName = name,
                        isSuccess = result.isSuccess,
                        message = if (result.isSuccess) "$name applied successfully" else result.message,
                        timestamp = System.currentTimeMillis()
                    ),
                    connectionStatus = if (isConnectionFailure && current.connectionStatus == ConnectionStatus.CONNECTED) {
                        ConnectionStatus.DISCONNECTED
                    } else {
                        current.connectionStatus
                    },
                    connectionMessage = if (isConnectionFailure && current.connectionStatus == ConnectionStatus.CONNECTED) {
                        "Clock unreachable at ${current.activeHost}. Please connect to ${current.selectedModel.defaultApSsid}."
                    } else {
                        current.connectionMessage
                    }
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

    fun updateStaticColorIndex(index: Int) {
        _uiState.update { it.copy(colorConfig = it.colorConfig.copy(staticColorIndex = index)) }
    }

    fun updateColorIntervalSec(seconds: Int) {
        _uiState.update { it.copy(colorConfig = it.colorConfig.copy(colorIntervalSec = seconds)) }
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
        _uiState.update {
            it.copy(
                dashboard = it.dashboard.copy(
                    colorMode = it.colorConfig.mode
                )
            )
        }
        executeAction("Save Color Mode") {
            api.saveColor(
                _uiState.value.activeHost,
                _uiState.value.colorConfig,
                _uiState.value.username,
                _uiState.value.passwordInput,
                _uiState.value.selectedModel
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

    fun stopAudio() {
        _uiState.update { it.copy(dashboard = it.dashboard.copy(currentPlayingTrack = 0)) }
        executeAction("Stop Audio") {
            api.testDfTrack(
                _uiState.value.activeHost,
                0,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    fun stopActiveAlarm() {
        executeAction("Stop Alarm") {
            api.testDfTrack(
                _uiState.value.activeHost,
                0,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    fun updateAnimSpeed(speed: Int) {
        _uiState.update { it.copy(colorConfig = it.colorConfig.copy(animSpeed = speed)) }
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

    fun toggleHourlyChime() {
        val newEnabled = !_uiState.value.hourlyChime.enabled
        _uiState.update { it.copy(hourlyChime = it.hourlyChime.copy(enabled = newEnabled)) }
        saveHourlyChime()
    }

    fun saveToneRange(
        enabled: Boolean = _uiState.value.hourlyChime.toneRangeEnabled,
        startHour: Int = _uiState.value.hourlyChime.toneStartHour,
        endHour: Int = _uiState.value.hourlyChime.toneEndHour
    ) {
        _uiState.update {
            it.copy(
                hourlyChime = it.hourlyChime.copy(
                    toneRangeEnabled = enabled,
                    toneStartHour = startHour,
                    toneEndHour = endHour
                )
            )
        }
        executeAction("Save Chime Active Hours") {
            api.saveToneRange(
                _uiState.value.activeHost,
                enabled,
                startHour,
                endHour,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    // --- ESP8266 SPECIFIC CONTROLS & UTILITIES ---

    fun testBuzzerBeep() {
        executeAction("Buzzer Beep Test (বিপ পরীক্ষা)") {
            api.testTone(
                _uiState.value.activeHost,
                0,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    fun testTone(idx: Int) {
        executeAction("Playing Tone #$idx") {
            api.testTone(
                _uiState.value.activeHost,
                idx,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    fun saveTone(mode: Int, idx: Int) {
        _uiState.update {
            it.copy(
                hourlyChime = it.hourlyChime.copy(
                    mode = mode,
                    fixedTrack = idx
                )
            )
        }
        executeAction("Save Tone Settings") {
            api.saveTone(
                _uiState.value.activeHost,
                mode,
                idx,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    fun saveSingleAlarm(
        index: Int,
        hour: Int,
        minute: Int,
        enabled: Boolean,
        toneIndex: Int
    ) {
        _uiState.update {
            val updated = if (index == 0) {
                it.alarmConfig.copy(alarm1Hour = hour, alarm1Minute = minute, alarm1Enabled = enabled, alarm1Track = toneIndex)
            } else {
                it.alarmConfig.copy(alarm2Hour = hour, alarm2Minute = minute, alarm2Enabled = enabled, alarm2Track = toneIndex)
            }
            it.copy(alarmConfig = updated)
        }
        executeAction("Save Alarm ${index + 1}") {
            api.saveSingleAlarm(
                _uiState.value.activeHost,
                index,
                hour,
                minute,
                enabled,
                toneIndex,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    fun resetDefaultAdminPassword() {
        executeAction("Reset Default Password") {
            val res = api.resetDefaultPassword(
                _uiState.value.activeHost,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
            if (res.isSuccess) {
                _uiState.update { it.copy(passwordInput = "sultan88") }
                prefs.password = "sultan88"
            }
            res
        }
    }

    fun fetchWifiPassword() {
        viewModelScope.launch {
            val res = api.showWifiPassword(
                _uiState.value.activeHost,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
            _uiState.update {
                it.copy(
                    wifiPasswordDisplay = if (res.isSuccess) res.rawResponse ?: res.message else "Error fetching password"
                )
            }
        }
    }

    fun saveDateDisplaySettings(englishDate: Boolean, banglaDate: Boolean) {
        _uiState.update {
            it.copy(
                dateSettings = it.dateSettings.copy(
                    isDateEnabled = englishDate,
                    isBanglaDate = banglaDate
                )
            )
        }
        executeAction("Save Date Settings (তারিখ প্রদর্শন)") {
            api.saveDateDisplaySettings(
                _uiState.value.activeHost,
                englishDate,
                banglaDate,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
        }
    }

    fun set12HourFormat(is12Hour: Boolean) {
        _uiState.update { it.copy(dashboard = it.dashboard.copy(is12Hour = is12Hour)) }
        executeAction(if (is12Hour) "Set 12-Hour Format" else "Set 24-Hour Format") {
            api.saveDisplaySettings(
                host = _uiState.value.activeHost,
                is12Hour = is12Hour,
                showDate = _uiState.value.dateSettings.isDateEnabled,
                colonBlink = _uiState.value.dashboard.isLightOn,
                hourlyBeep = _uiState.value.dashboard.hourlyBeepEnabled,
                user = _uiState.value.username,
                pass = _uiState.value.passwordInput
            )
        }
    }

    fun saveHourlyBeep(enabled: Boolean) {
        _uiState.update {
            it.copy(
                hourlyChime = it.hourlyChime.copy(enabled = enabled),
                dashboard = it.dashboard.copy(hourlyBeepEnabled = enabled)
            )
        }
        executeAction(if (enabled) "Enable Hourly Beep" else "Mute Hourly Beep") {
            api.saveDisplaySettings(
                host = _uiState.value.activeHost,
                is12Hour = _uiState.value.dashboard.is12Hour,
                showDate = _uiState.value.dateSettings.isDateEnabled,
                colonBlink = _uiState.value.dashboard.isLightOn,
                hourlyBeep = enabled,
                user = _uiState.value.username,
                pass = _uiState.value.passwordInput
            )
        }
    }

    fun updateBrightnessConfig(config: BrightnessConfig) {
        _uiState.update { it.copy(brightnessConfig = config) }
    }

    fun updateColorConfig(config: ColorConfig) {
        _uiState.update { it.copy(colorConfig = config) }
    }

    fun updateDisplaySettings(
        is12Hour: Boolean = _uiState.value.dashboard.is12Hour,
        showDate: Boolean = _uiState.value.dateSettings.isDateEnabled,
        colonBlink: Boolean = _uiState.value.dashboard.isLightOn,
        hourlyBeep: Boolean = _uiState.value.dashboard.hourlyBeepEnabled
    ) {
        _uiState.update {
            it.copy(
                dashboard = it.dashboard.copy(
                    is12Hour = is12Hour,
                    isLightOn = colonBlink,
                    enableEnglishDate = showDate,
                    hourlyBeepEnabled = hourlyBeep
                ),
                dateSettings = it.dateSettings.copy(
                    isDateEnabled = showDate
                )
            )
        }
    }

    fun saveDisplaySettings() {
        executeAction("Save Display Settings") {
            api.saveDisplaySettings(
                host = _uiState.value.activeHost,
                is12Hour = _uiState.value.dashboard.is12Hour,
                showDate = _uiState.value.dateSettings.isDateEnabled,
                colonBlink = _uiState.value.dashboard.isLightOn,
                hourlyBeep = _uiState.value.dashboard.hourlyBeepEnabled,
                user = _uiState.value.username,
                pass = _uiState.value.passwordInput
            )
        }
    }

    fun stopAlarmAudio() {
        stopActiveAlarm()
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
                _uiState.value.passwordInput,
                _uiState.value.selectedModel
            )
        }
    }

    fun toggleColorPlaylist(enabled: Boolean) {
        _uiState.update { it.copy(colorPlaylist = it.colorPlaylist.copy(enabled = enabled)) }
        executeAction("Toggle Color Playlist") {
            api.toggleColorPlaylist(
                _uiState.value.activeHost,
                _uiState.value.username,
                _uiState.value.passwordInput,
                enabled = enabled
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
        val ssid = _uiState.value.wifiConfig.ssid.trim()
        if (ssid.isEmpty()) {
            _uiState.update {
                it.copy(
                    feedback = ActionFeedback(
                        inProgress = false,
                        actionName = "Save Wi-Fi",
                        message = "Please enter or select a Wi-Fi SSID",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            return
        }

        executeAction("Save Wi-Fi Network") {
            pausePolling(15000L)
            val res = api.saveWifi(
                _uiState.value.activeHost,
                _uiState.value.wifiConfig,
                _uiState.value.username,
                _uiState.value.passwordInput
            )
            if (res.isSuccess || res.message.contains("reboot", ignoreCase = true) || res.message.contains("offline", ignoreCase = true) || res.message.contains("timed out", ignoreCase = true)) {
                ActionResponse(
                    true,
                    "Wi-Fi credentials saved! Digital clock is restarting to connect to '$ssid'. Reconnect phone to your Wi-Fi router."
                )
            } else {
                res
            }
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
