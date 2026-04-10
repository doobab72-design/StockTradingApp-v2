package com.stocktrading

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.stocktrading.presentation.ui.DashboardScreen
import com.stocktrading.presentation.ui.PortfolioScreen
import com.stocktrading.presentation.ui.RecommendationScreen
import com.stocktrading.presentation.ui.SettingsScreen
import com.stocktrading.ui.theme.StockTradingAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val navigateTo = intent?.getStringExtra("navigate_to")

        setContent {
            StockTradingAppTheme {
                StockTradingApp(initialRoute = navigateTo)
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "대시보드", Icons.Default.Dashboard)
    object Portfolio : Screen("portfolio", "포트폴리오", Icons.Default.ShowChart)
    object Recommendations : Screen("recommendations", "추천 종목", Icons.Default.Recommend)
    object Settings : Screen("settings", "설정", Icons.Default.Settings)
}

@Composable
fun StockTradingApp(initialRoute: String? = null) {
    val navController = rememberNavController()
    val screens = listOf(
        Screen.Dashboard,
        Screen.Portfolio,
        Screen.Recommendations,
        Screen.Settings
    )

    LaunchedEffect(initialRoute) {
        if (initialRoute != null) {
            navController.navigate(initialRoute) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Portfolio.route) { PortfolioScreen() }
            composable(Screen.Recommendations.route) { RecommendationScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
