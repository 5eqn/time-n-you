package com.seqn.timewatcher.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.seqn.timewatcher.R
import com.seqn.timewatcher.ui.home.HomeScreen
import com.seqn.timewatcher.ui.settings.SettingsScreen
import com.seqn.timewatcher.ui.stats.StatsScreen
import com.seqn.timewatcher.ui.usage.UsageScreen

sealed class Dest(val route: String, val labelRes: Int, val icon: ImageVector) {
    data object Home : Dest("home", R.string.nav_home, Icons.Filled.Home)
    data object Stats : Dest("stats", R.string.nav_stats, Icons.Filled.BarChart)
    data object Usage : Dest("usage", R.string.nav_usage, Icons.Filled.MenuBook)
    data object Settings : Dest("settings", R.string.nav_settings, Icons.Filled.Settings)
}

private val dests = listOf(Dest.Home, Dest.Stats, Dest.Usage, Dest.Settings)

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                dests.forEach { d ->
                    NavigationBarItem(
                        selected = current == d.route,
                        onClick = {
                            nav.navigate(d.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(d.icon, contentDescription = null) },
                        label = { Text(stringResource(d.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Dest.Home.route,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable(Dest.Home.route) { HomeScreen() }
            composable(Dest.Stats.route) { StatsScreen() }
            composable(Dest.Usage.route) { UsageScreen() }
            composable(Dest.Settings.route) { SettingsScreen() }
        }
    }
}
