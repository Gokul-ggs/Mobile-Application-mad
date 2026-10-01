package com.example.myapplication

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.ui.theme.SuccessGreen
import com.example.myapplication.ui.theme.WarningAmber

sealed class NavItem(
    val title: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object Chat : NavItem("AI Tutor", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
    object Quiz : NavItem("Quiz", Icons.Filled.Quiz, Icons.Outlined.Quiz)
    object Flashcards : NavItem("Flashcards", Icons.Filled.Style, Icons.Outlined.Style)
    object History : NavItem("Progress", Icons.Filled.Assessment, Icons.Outlined.Assessment)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(viewModel: StudyViewModel = viewModel()) {
    val selectedIndex by viewModel.selectedTab.collectAsState()
    val navItems = listOf(NavItem.Chat, NavItem.Quiz, NavItem.Flashcards, NavItem.History)

    val showSettingsDialog by viewModel.showSettingsDialog.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val isKeyConfigured = apiKey.isNotBlank() || ApiClient.defaultApiKey.isNotBlank()

    if (showSettingsDialog) {
        SettingsDialog(
            currentApiKey = apiKey,
            onSaveApiKey = { newKey -> viewModel.saveApiKey(newKey) },
            onDismiss = { viewModel.setShowSettingsDialog(false) },
            onClearHistory = { viewModel.clearQuizHistory() }
        )
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 1.dp
            ) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.School,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "StudyGenius AI",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Gemini 2.0 Flash Tutor",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        // Key Status Chip & Settings
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isKeyConfigured) SuccessGreen.copy(alpha = 0.12f) else WarningAmber.copy(alpha = 0.15f),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isKeyConfigured) SuccessGreen else WarningAmber)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isKeyConfigured) "AI Online" else "Set Key",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isKeyConfigured) SuccessGreen else WarningAmber
                                )
                            }
                        }

                        IconButton(onClick = { viewModel.setShowSettingsDialog(true) }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    navItems.forEachIndexed { index, item ->
                        val isSelected = selectedIndex == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.navigateToTab(index) },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = selectedIndex,
                animationSpec = tween(250),
                label = "screenTransition"
            ) { screenIndex ->
                when (screenIndex) {
                    0 -> ChatScreen(viewModel)
                    1 -> QuizScreen(viewModel)
                    2 -> FlashcardScreen(viewModel)
                    3 -> HistoryScreen(viewModel)
                }
            }
        }
    }
}
