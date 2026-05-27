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
import com.example.ui.navigation.ScreenAuth
import com.example.ui.navigation.ScreenConnect
import com.example.ui.navigation.ScreenServers
import com.example.ui.navigation.ScreenSubscription
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ConnectScreen
import com.example.ui.screens.ServerListScreen
import com.example.ui.screens.SubscriptionScreen
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
            // Still set empty content or error content so it doesn't crash UI immediately
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isAuthenticated) {
                NavigationBar(
                    containerColor = com.example.ui.theme.DarkNav,
                    contentColor = Color.White,
                    modifier = Modifier
                        .padding(16.dp)
                        .clip(RoundedCornerShape(40.dp))
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

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
                if (!isAuthenticated) {
                    navController.navigate(ScreenAuth.route) {
                        try {
                            val startId = navController.graph.findStartDestination().id
                            popUpTo(startId) { inclusive = true }
                        } catch (e: Exception) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                } else {
                    if (navController.currentDestination?.route == ScreenAuth.route) {
                        navController.navigate(ScreenConnect.route) {
                            popUpTo(ScreenAuth.route) { inclusive = true }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        NavHost(
            navController = navController,
            startDestination = ScreenAuth.route,
            modifier = Modifier.padding(innerPadding)
        ) {
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
                    onNavigateToServers = { navController.navigate(ScreenServers.route) }
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
        }
    }
}
