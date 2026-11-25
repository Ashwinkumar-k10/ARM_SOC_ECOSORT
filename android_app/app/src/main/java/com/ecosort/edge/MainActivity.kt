package com.ecosort.edge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ecosort.edge.ui.EcoSortViewModel
import com.ecosort.edge.ui.screens.AssistantScreen
import com.ecosort.edge.ui.screens.DetailScreen
import com.ecosort.edge.ui.screens.HomeScreen
import com.ecosort.edge.ui.screens.ScannerScreen
import com.ecosort.edge.ui.theme.EcoColors
import com.ecosort.edge.ui.theme.EcoSortTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EcoSortTheme {
                val vm: EcoSortViewModel = viewModel()
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route ?: "home"

                val history by vm.history.collectAsState()
                val activeScan by vm.activeScan.collectAsState()
                val isProcessing by vm.isProcessing.collectAsState()
                val messages by vm.chatMessages.collectAsState()
                val isGenerating by vm.isGenerating.collectAsState()
                val llamaAvailable by vm.llamaAvailable.collectAsState()
                val telemetry by vm.telemetry.collectAsState()

                Scaffold(
                    containerColor = EcoColors.Canvas,
                    bottomBar = {
                        if (route != "detail") {
                            NavigationBar(containerColor = EcoColors.Surface) {
                                NavigationBarItem(
                                    selected = route == "home",
                                    onClick = { nav.navigate("home") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Outlined.Home, contentDescription = "Home") },
                                    label = { Text("Home") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EcoColors.ForestPrimary,
                                        selectedTextColor = EcoColors.ForestPrimary,
                                        indicatorColor = Color(0xFFBEECDD)
                                    )
                                )
                                NavigationBarItem(
                                    selected = route == "scanner",
                                    onClick = { nav.navigate("scanner") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Outlined.CameraAlt, contentDescription = "Scan") },
                                    label = { Text("Scan") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EcoColors.ForestPrimary,
                                        selectedTextColor = EcoColors.ForestPrimary,
                                        indicatorColor = Color(0xFFBEECDD)
                                    )
                                )
                                NavigationBarItem(
                                    selected = route == "assistant",
                                    onClick = { nav.navigate("assistant") { launchSingleTop = true } },
                                    icon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = "Assistant") },
                                    label = { Text("Assistant") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EcoColors.ForestPrimary,
                                        selectedTextColor = EcoColors.ForestPrimary,
                                        indicatorColor = Color(0xFFBEECDD)
                                    )
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = nav,
                        startDestination = "home",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("home") {
                            HomeScreen(
                                history = history,
                                telemetry = telemetry,
                                onScanClick = { nav.navigate("scanner") },
                                onSanitaryClick = {
                                    vm.triggerSanitaryOverride { nav.navigate("detail") }
                                },
                                onAssistantClick = { nav.navigate("assistant") }
                            )
                        }
                        composable("scanner") {
                            ScannerScreen(
                                isProcessing = isProcessing,
                                onImageCaptured = { bmp ->
                                    vm.classifyBitmap(bmp) { nav.navigate("detail") }
                                },
                                onPickUri = { uri ->
                                    vm.classifyUri(uri) { nav.navigate("detail") }
                                },
                                onPickSample = { sampleName ->
                                    vm.classifySampleAsset(sampleName) { nav.navigate("detail") }
                                }
                            )
                        }
                        composable("detail") {
                            DetailScreen(
                                activeScan = activeScan,
                                onBack = { nav.popBackStack() },
                                onAskAssistant = { nav.navigate("assistant") }
                            )
                        }
                        composable("assistant") {
                            AssistantScreen(
                                activeScan = activeScan,
                                messages = messages,
                                isGenerating = isGenerating,
                                llamaAvailable = llamaAvailable,
                                onAskLlama = { q -> vm.askLlama(q) },
                                onImportModelUri = { uri -> vm.importLlamaModel(uri) {} }
                            )
                        }
                    }
                }
            }
        }
    }
}
