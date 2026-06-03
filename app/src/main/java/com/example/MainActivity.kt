package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.local.AppDatabase
import com.example.data.remote.ApiClient
import com.example.data.repository.VpnRepository
import com.example.ui.navigation.ScreenSplash
import com.example.ui.navigation.ScreenAuth
import com.example.ui.navigation.ScreenConnect
import com.example.ui.navigation.ScreenServers
import com.example.ui.navigation.ScreenSubscription
import com.example.ui.navigation.ScreenSettings
import com.example.ui.navigation.ScreenAccount
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ConnectScreen
import com.example.ui.screens.ServerListScreen
import com.example.ui.screens.SubscriptionScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.AccountScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.VpnViewModel
import com.example.ui.viewmodel.VpnViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("ShieldVPN", "MainActivity: onCreate started")
        
        try {
            enableEdgeToEdge()

            val database = AppDatabase.getDatabase(this)
            val repository = VpnRepository(ApiClient.apiService, database.serverDao())

            android.util.Log.i("ShieldVPN", "Dependencies initialized successfully")

            setContent {
                val viewModel: VpnViewModel = viewModel(factory = VpnViewModelFactory(repository))
                MyApplicationTheme {
                    ShieldVpnApp(viewModel)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ShieldVPN", "Critical error in onCreate", e)
            setContent {
                androidx.compose.material3.Text("A critical error occurred initializing the app. Check logs.")
            }
        }
    }
}

@Composable
fun ShieldVpnApp(viewModel: VpnViewModel) {
    val navController = rememberNavController()
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    // Define helper to hide bottom nav on Splash or Auth screens
    val isSplashOrAuth = currentRoute == ScreenSplash.route || currentRoute == ScreenAuth.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isAuthenticated && !isSplashOrAuth) {
                NavigationBar(
                    containerColor = com.example.ui.theme.DarkNav,
                    contentColor = Color.White,
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(40.dp))
                ) {
                    NavigationBarItem(
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            unselectedIconColor = com.example.ui.theme.Slate500,
                            selectedTextColor = Color.White,
                            unselectedTextColor = com.example.ui.theme.Slate500,
                            indicatorColor = Color.Transparent
                        ),
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = currentDestination?.hierarchy?.any { it.route == ScreenConnect.route } == true,
                        onClick = {
                            try {
                                navController.navigate(ScreenConnect.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    )
                    NavigationBarItem(
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            unselectedIconColor = com.example.ui.theme.Slate500,
                            selectedTextColor = Color.White,
                            unselectedTextColor = com.example.ui.theme.Slate500,
                            indicatorColor = Color.Transparent
                        ),
                        icon = { Icon(Icons.Default.Public, contentDescription = "Servers") },
                        label = { Text("Servers") },
                        selected = currentDestination?.hierarchy?.any { it.route == ScreenServers.route } == true,
                        onClick = {
                            try {
                                navController.navigate(ScreenServers.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    )
                    NavigationBarItem(
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            unselectedIconColor = com.example.ui.theme.Slate500,
                            selectedTextColor = Color.White,
                            unselectedTextColor = com.example.ui.theme.Slate500,
                            indicatorColor = Color.Transparent
                        ),
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        selected = currentDestination?.hierarchy?.any { it.route == ScreenSettings.route } == true,
                        onClick = {
                            try {
                                navController.navigate(ScreenSettings.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    )
                    NavigationBarItem(
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            unselectedIconColor = com.example.ui.theme.Slate500,
                            selectedTextColor = Color.White,
                            unselectedTextColor = com.example.ui.theme.Slate500,
                            indicatorColor = Color.Transparent
                        ),
                        icon = { Icon(Icons.Default.Person, contentDescription = "Account") },
                        label = { Text("Account") },
                        selected = currentDestination?.hierarchy?.any { it.route == ScreenAccount.route } == true,
                        onClick = {
                            try {
                                navController.navigate(ScreenAccount.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    )
                    NavigationBarItem(
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            unselectedIconColor = com.example.ui.theme.Slate500,
                            selectedTextColor = Color.White,
                            unselectedTextColor = com.example.ui.theme.Slate500,
                            indicatorColor = Color.Transparent
                        ),
                        icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = "Premium") },
                        label = { Text("Premium") },
                        selected = currentDestination?.hierarchy?.any { it.route == ScreenSubscription.route } == true,
                        onClick = {
                            try {
                                navController.navigate(ScreenSubscription.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } catch (e: Exception) { e.printStackTrace() }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        
        LaunchedEffect(isAuthenticated) {
            try {
                val currentR = navController.currentDestination?.route
                if (!isAuthenticated) {
                    if (currentR != null && currentR != ScreenAuth.route && currentR != ScreenSplash.route) {
                        navController.navigate(ScreenAuth.route) {
                            try {
                                val startId = navController.graph.findStartDestination().id
                                popUpTo(startId) { inclusive = true }
                            } catch (e: Exception) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                } else {
                    if (currentR == ScreenAuth.route) {
                        navController.navigate(ScreenConnect.route) {
                            popUpTo(ScreenAuth.route) { inclusive = true }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ShieldVPN", "Navigation exception caught safely", e)
            }
        }

        NavHost(
            navController = navController,
            startDestination = ScreenSplash.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ScreenSplash.route) {
                SplashScreen(
                    onSplashComplete = {
                        val startDest = if (viewModel.isAuthenticated.value) ScreenConnect.route else ScreenAuth.route
                        navController.navigate(startDest) {
                            popUpTo(ScreenSplash.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(ScreenAuth.route) {
                AuthScreen(
                    viewModel = viewModel,
                    onNavigateToHome = {
                        navController.navigate(ScreenConnect.route) {
                            popUpTo(ScreenAuth.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(ScreenConnect.route) {
                ConnectScreen(
                    viewModel = viewModel,
                    onNavigateToServers = { navController.navigate(ScreenServers.route) },
                    onNavigateToPremium = {
                        navController.navigate(ScreenSubscription.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(ScreenServers.route) {
                ServerListScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(ScreenSubscription.route) {
                SubscriptionScreen(viewModel = viewModel)
            }
            composable(ScreenSettings.route) {
                SettingsScreen(viewModel = viewModel)
            }
            composable(ScreenAccount.route) {
                AccountScreen(
                    viewModel = viewModel,
                    onNavigateToAuth = { navController.navigate(ScreenAuth.route) },
                    onNavigateToPremium = { navController.navigate(ScreenSubscription.route) }
                )
            }
        }
    }
}

