package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.VideoEditorViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: VideoEditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                var currentRoute by remember { mutableStateOf("dashboard") } // "dashboard", "editor"
                var currentTab by remember { mutableStateOf("home") } // "home", "templates", "ai", "profile"

                val isPremium = remember { mutableStateOf(true) } // PRO unlocked out of the box
                val removeWatermark = remember { mutableStateOf(true) }

                var showGalleryDrawer by remember { mutableStateOf(false) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (currentRoute == "dashboard") {
                            NavigationBar(
                                containerColor = Color(0xFF0F0F0F),
                                contentColor = LightText,
                                modifier = Modifier
                                    .windowInsetsPadding(WindowInsets.navigationBars)
                                    .testTag("app_navigation_bar")
                            ) {
                                NavigationBarItem(
                                    selected = currentTab == "home",
                                    onClick = { currentTab = "home" },
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                    label = { Text("Home", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AccentCyan,
                                        selectedTextColor = AccentCyan,
                                        indicatorColor = AccentCyan.copy(alpha = 0.20f),
                                        unselectedIconColor = MutedText,
                                        unselectedTextColor = MutedText
                                    ),
                                    modifier = Modifier.testTag("nav_item_home")
                                )

                                NavigationBarItem(
                                    selected = currentTab == "templates",
                                    onClick = { currentTab = "templates" },
                                    icon = { Icon(Icons.Default.MovieCreation, contentDescription = "Templates") },
                                    label = { Text("Templates", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AccentCyan,
                                        selectedTextColor = AccentCyan,
                                        indicatorColor = AccentCyan.copy(alpha = 0.20f),
                                        unselectedIconColor = MutedText,
                                        unselectedTextColor = MutedText
                                    ),
                                    modifier = Modifier.testTag("nav_item_templates")
                                )

                                NavigationBarItem(
                                    selected = currentTab == "ai",
                                    onClick = { currentTab = "ai" },
                                    icon = { Icon(Icons.Default.AutoAwesome, contentDescription = "AI Tools") },
                                    label = { Text("AI Tools", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AccentCyan,
                                        selectedTextColor = AccentCyan,
                                        indicatorColor = AccentCyan.copy(alpha = 0.20f),
                                        unselectedIconColor = MutedText,
                                        unselectedTextColor = MutedText
                                    ),
                                    modifier = Modifier.testTag("nav_item_ai")
                                )

                                NavigationBarItem(
                                    selected = currentTab == "profile",
                                    onClick = { currentTab = "profile" },
                                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                                    label = { Text("Profile", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = AccentCyan,
                                        selectedTextColor = AccentCyan,
                                        indicatorColor = AccentCyan.copy(alpha = 0.20f),
                                        unselectedIconColor = MutedText,
                                        unselectedTextColor = MutedText
                                    ),
                                    modifier = Modifier.testTag("nav_item_profile")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(BackCarbon)
                            .padding(
                                bottom = if (currentRoute == "dashboard") innerPadding.calculateBottomPadding() else 0.dp
                            )
                    ) {
                        if (currentRoute == "dashboard") {
                            when (currentTab) {
                                "home" -> {
                                    HomeScreen(
                                        viewModel = viewModel,
                                        onNewProjectClicked = { showGalleryDrawer = true },
                                        onProjectLoaded = { id ->
                                            viewModel.loadProject(id)
                                            currentRoute = "editor"
                                        },
                                        onNavigateToAiTools = { currentTab = "ai" },
                                        onNavigateToTemplates = { currentTab = "templates" }
                                    )
                                }
                                "templates" -> {
                                    TemplatesScreen(
                                        viewModel = viewModel,
                                        onTemplateApplied = { id ->
                                            currentRoute = "editor"
                                        }
                                    )
                                }
                                "ai" -> {
                                    AiToolsScreen(
                                        viewModel = viewModel,
                                        onOpenTimeline = { id ->
                                            viewModel.loadProject(id)
                                            currentRoute = "editor"
                                        }
                                    )
                                }
                                "profile" -> {
                                    ProfileScreen(
                                        isPremiumState = isPremium,
                                        removeWatermark = removeWatermark
                                    )
                                }
                            }
                        } else if (currentRoute == "editor") {
                            EditorScreen(
                                viewModel = viewModel,
                                onBackClicked = {
                                    currentRoute = "dashboard"
                                    currentTab = "home"
                                },
                                onAddMediaClicked = { showGalleryDrawer = true },
                                removeWatermark = removeWatermark.value
                            )
                        }

                        // Sliding visual media selector overlay drawer
                        if (showGalleryDrawer) {
                            MediaSelectorScreen(
                                onDismiss = { showGalleryDrawer = false },
                                onMediaImported = { selectedMedia ->
                                    if (selectedMedia.isNotEmpty()) {
                                        if (currentRoute == "dashboard") {
                                            // Initialize a new dynamic project sequence
                                            viewModel.createAndLoadNewProject("Project #${System.currentTimeMillis() / 1000 % 1000}")
                                            currentRoute = "editor"
                                        }
                                        viewModel.importSelectedMedia(selectedMedia)
                                        Toast.makeText(this@MainActivity, "Imported ${selectedMedia.size} Clips to Timeline", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
