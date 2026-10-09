package com.example.jenkinslearning.presentation.navigation

import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.jenkinslearning.R
import com.example.jenkinslearning.feature.build.BuildDetailsRoute
import com.example.jenkinslearning.feature.credentials.CredentialsRoute
import com.example.jenkinslearning.feature.dashboard.DashboardRoute
import com.example.jenkinslearning.feature.jenkins.JobDetailsRoute
import com.example.jenkinslearning.feature.jenkins.JobsRoute
import com.example.jenkinslearning.feature.learning.LearningRoute
import com.example.jenkinslearning.feature.settings.SettingsRoute

/**
 * Routes. Job names can contain "/" (folders) and spaces, so they're passed
 * as URL-encoded query arguments rather than path segments.
 */
object Routes {
    const val DASHBOARD = "dashboard"
    const val JOBS = "jobs"
    const val CREDENTIALS = "credentials"
    const val LEARN = "learn"
    const val SETTINGS = "settings"

    const val ARG_JOB = "job"
    const val ARG_BUILD = "build"
    const val JOB_DETAILS = "job_details?$ARG_JOB={$ARG_JOB}"
    const val BUILD_DETAILS = "build_details?$ARG_JOB={$ARG_JOB}&$ARG_BUILD={$ARG_BUILD}"

    fun jobDetails(jobFullName: String) = "job_details?$ARG_JOB=${Uri.encode(jobFullName)}"
    fun buildDetails(jobFullName: String, buildNumber: Int) =
        "build_details?$ARG_JOB=${Uri.encode(jobFullName)}&$ARG_BUILD=$buildNumber"
}

private enum class TopLevelTab(val route: String, val label: String, @param:DrawableRes val icon: Int) {
    Dashboard(Routes.DASHBOARD, "Dashboard", R.drawable.ic_home),
    Jobs(Routes.JOBS, "Jobs", R.drawable.ic_list),
    Credentials(Routes.CREDENTIALS, "Credentials", R.drawable.ic_lock),
    Learn(Routes.LEARN, "Learn", R.drawable.ic_info),
    Settings(Routes.SETTINGS, "Settings", R.drawable.ic_tune),
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        // Screens draw their own top bars; see ScreenScaffold.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                TopLevelTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = { navController.navigateToTab(tab.route) },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.DASHBOARD) {
                DashboardRoute(
                    onOpenSettings = { navController.navigateToTab(Routes.SETTINGS) },
                    onOpenJob = { navController.navigate(Routes.jobDetails(it)) },
                )
            }
            composable(Routes.JOBS) {
                JobsRoute(onOpenJob = { navController.navigate(Routes.jobDetails(it)) })
            }
            composable(Routes.CREDENTIALS) { CredentialsRoute() }
            composable(Routes.LEARN) { LearningRoute() }
            composable(Routes.SETTINGS) { SettingsRoute() }

            composable(
                route = Routes.JOB_DETAILS,
                arguments = listOf(navArgument(Routes.ARG_JOB) { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                JobDetailsRoute(
                    jobFullName = entry.arguments?.getString(Routes.ARG_JOB).orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenBuild = { job, number -> navController.navigate(Routes.buildDetails(job, number)) },
                )
            }
            composable(
                route = Routes.BUILD_DETAILS,
                arguments = listOf(
                    navArgument(Routes.ARG_JOB) { type = NavType.StringType; defaultValue = "" },
                    navArgument(Routes.ARG_BUILD) { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { entry ->
                BuildDetailsRoute(
                    jobFullName = entry.arguments?.getString(Routes.ARG_JOB).orEmpty(),
                    buildNumber = entry.arguments?.getInt(Routes.ARG_BUILD) ?: 0,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

/** Standard bottom-navigation behavior: one copy of each tab, state saved and restored. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
