package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.ClockModel
import com.example.data.model.ConnectionStatus
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.ClockViewModel

enum class MainNavScreen(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Dashboard),
    CONTROLS("Controls", Icons.Default.Tune),
    SCHEDULE("Schedule", Icons.Default.AccessTime),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val viewModel: ClockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                SultanClockMainApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SultanClockMainApp(viewModel: ClockViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var currentScreen by remember { mutableStateOf(MainNavScreen.HOME) }
    var userBypassedConnection by remember { mutableStateOf(false) }
    var isForeground by remember { mutableStateOf(true) }

    // Lifecycle observer to stop polling when the app is paused / backgrounded
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, currentScreen) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    isForeground = true
                    val isHomeOrControls = currentScreen == MainNavScreen.HOME || currentScreen == MainNavScreen.CONTROLS
                    viewModel.updateVisibility(isHomeOrControls = isHomeOrControls, isForeground = true)
                }
                Lifecycle.Event.ON_PAUSE -> {
                    isForeground = false
                    val isHomeOrControls = currentScreen == MainNavScreen.HOME || currentScreen == MainNavScreen.CONTROLS
                    viewModel.updateVisibility(isHomeOrControls = isHomeOrControls, isForeground = false)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Update visibility state when current navigation tab changes
    LaunchedEffect(currentScreen, isForeground) {
        val isHomeOrControls = currentScreen == MainNavScreen.HOME || currentScreen == MainNavScreen.CONTROLS
        viewModel.updateVisibility(isHomeOrControls = isHomeOrControls, isForeground = isForeground)
    }

    // Show connection screen when not connected, unless user deliberately skipped to offline mode
    val shouldShowConnectionScreen = uiState.connectionStatus != ConnectionStatus.CONNECTED && !userBypassedConnection

    if (shouldShowConnectionScreen) {
        ConnectionScreen(
            viewModel = viewModel,
            uiState = uiState,
            onNavigateToDashboard = {
                userBypassedConnection = true
            }
        )
    } else {
        Scaffold(
            containerColor = DeepBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (uiState.connectionStatus) {
                                            ConnectionStatus.CONNECTED -> SuccessGreen
                                            ConnectionStatus.CONNECTING -> GoldPrimary
                                            else -> ErrorRed
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "SULTAN DIGITAL CLOCK",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = GoldPrimary
                                )
                                Text(
                                    text = uiState.activeHost,
                                    fontSize = 10.sp,
                                    color = CyanAccent
                                )
                            }
                        }
                    },
                    actions = {
                        // Module Badge / Switcher
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (uiState.selectedModel == ClockModel.ESP8266) AmberOrange.copy(alpha = 0.2f) else CyanAccent.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (uiState.selectedModel == ClockModel.ESP8266) AmberOrange else CyanAccent
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val nextModel = if (uiState.selectedModel == ClockModel.ESP32) ClockModel.ESP8266 else ClockModel.ESP32
                                    viewModel.setClockModel(nextModel)
                                }
                                .testTag("topbar_model_switcher")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.selectedModel == ClockModel.ESP8266) Icons.Default.Mosque else Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = if (uiState.selectedModel == ClockModel.ESP8266) AmberOrange else CyanAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = uiState.selectedModel.shortName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.selectedModel == ClockModel.ESP8266) AmberOrange else CyanAccent
                                )
                            }
                        }

                        IconButton(
                            onClick = { userBypassedConnection = false },
                            modifier = Modifier.testTag("open_connection_settings")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = "Connection Settings",
                                tint = if (uiState.connectionStatus == ConnectionStatus.CONNECTED) SuccessGreen else GoldPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DeepBackground,
                        titleContentColor = GoldPrimary
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = CardBackground,
                    contentColor = TextPrimary,
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = CardBorder,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                    )
                ) {
                    MainNavScreen.values().forEach { screen ->
                        val isSelected = currentScreen == screen
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentScreen = screen },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    tint = if (isSelected) GoldPrimary else TextMuted
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) GoldPrimary else TextSecondary
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = GoldPrimary.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("nav_${screen.name.lowercase()}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AnimatedContent(
                    targetState = currentScreen,
                    label = "screen_transition"
                ) { target ->
                    when (target) {
                        MainNavScreen.HOME -> DashboardScreen(
                            viewModel = viewModel,
                            uiState = uiState,
                            onNavigateToConnection = { userBypassedConnection = false }
                        )
                        MainNavScreen.CONTROLS -> ControlsScreen(
                            viewModel = viewModel,
                            uiState = uiState
                        )
                        MainNavScreen.SCHEDULE -> ScheduleScreen(
                            viewModel = viewModel,
                            uiState = uiState
                        )
                        MainNavScreen.SETTINGS -> SettingsScreen(
                            viewModel = viewModel,
                            uiState = uiState
                        )
                    }
                }
            }
        }
    }
}
