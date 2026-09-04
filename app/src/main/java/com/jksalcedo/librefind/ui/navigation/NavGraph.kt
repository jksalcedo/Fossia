package com.jksalcedo.librefind.ui.navigation

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.jksalcedo.librefind.ui.auth.AuthScreen
import com.jksalcedo.librefind.ui.auth.AuthViewModel
import com.jksalcedo.librefind.ui.auth.ProfileSetupScreen
import com.jksalcedo.librefind.ui.community.CommunitySubmissionsScreen
import com.jksalcedo.librefind.ui.community.LeaderboardScreen
import com.jksalcedo.librefind.ui.dashboard.DashboardScreen
import org.koin.androidx.compose.koinViewModel
import com.jksalcedo.librefind.ui.details.AlternativeDetailScreen
import com.jksalcedo.librefind.ui.details.DetailsScreen
import com.jksalcedo.librefind.ui.discover.DiscoverScreen
import com.jksalcedo.librefind.ui.mysubmissions.MySubmissionsScreen
import com.jksalcedo.librefind.ui.reports.MyReportsScreen
import com.jksalcedo.librefind.ui.reports.ReportScreen
import com.jksalcedo.librefind.ui.correction.SuggestCorrectionScreen
import com.jksalcedo.librefind.ui.profile.ProfileScreen
import com.jksalcedo.librefind.ui.settings.IgnoredAppsScreen
import com.jksalcedo.librefind.ui.signingkey.SigningKeyVoteScreen
import com.jksalcedo.librefind.ui.submit.SubmitScreen

private const val NAV_DURATION = 300

@Composable
fun NavGraph(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Route.Dashboard.route,
        enterTransition = { slideInHorizontally(tween(NAV_DURATION)) { it / 4 } + fadeIn(tween(NAV_DURATION)) },
        exitTransition = { slideOutHorizontally(tween(NAV_DURATION)) { -it / 4 } + fadeOut(tween(NAV_DURATION)) },
        popEnterTransition = { slideInHorizontally(tween(NAV_DURATION)) { -it / 4 } + fadeIn(tween(NAV_DURATION)) },
        popExitTransition = { slideOutHorizontally(tween(NAV_DURATION)) { it / 4 } + fadeOut(tween(NAV_DURATION)) }
    ) {
        composable(Route.Dashboard.route) {
            val authViewModel: AuthViewModel = koinViewModel()
            val authState by authViewModel.uiState.collectAsStateWithLifecycle()

            DashboardScreen(
                onAppClick = { appName, packageName ->
                    navController.navigate(Route.Details.createRoute(appName, packageName))
                },
                onSubmitClick = {
                    if (authState.isSignedIn) {
                        navController.navigate(Route.Submit.createRoute())
                    } else {
                        navController.navigate(Route.Auth.route)
                    }
                },
                onMySubmissionsClick = {
                    navController.navigate(Route.MySubmissions.route)
                },
                onIgnoredAppsClick = {
                    navController.navigate(Route.IgnoredApps.route)
                },
                onSettingsClick = {
                    navController.navigate(Route.Settings.route)
                },
                onProfileClick = { userId ->
                    navController.navigate(Route.Profile.createRoute(userId))
                }
            )
        }

        composable(
            route = Route.Profile.route,
            arguments = listOf(navArgument("userId") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")?.let { Uri.decode(it) }
            ProfileScreen(
                userId = userId,
                onBackClick = { navController.navigateUp() },
                onSubmissionClick = { submissionId ->
                    navController.navigate(Route.Submit.createRoute(submissionId = submissionId))
                },
                onSignOutSuccess = {
                    navController.navigate(Route.Dashboard.route) {
                        popUpTo(Route.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.Discover.route) {
            DiscoverScreen(
                onBackClick = { navController.navigateUp() },
                onAlternativeClick = { altId ->
                    navController.navigate(Route.AlternativeDetail.createRoute(altId))
                },
                onProprietaryClick = { appName, packageName ->
                    navController.navigate(Route.Details.createRoute(appName, packageName))
                }
            )
        }

        composable(
            route = Route.Details.route,
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType },
                navArgument("appName") { type = NavType.StringType })
        ) { backStackEntry ->
            val authViewModel: AuthViewModel = koinViewModel()
            val authState by authViewModel.uiState.collectAsStateWithLifecycle()
            val packageName =
                backStackEntry.arguments?.getString("packageName") ?: return@composable
            val appName = backStackEntry.arguments?.getString("appName") ?: return@composable
            DetailsScreen(
                appName = appName,
                packageName = packageName,
                onBackClick = { navController.navigateUp() },
                onAlternativeClick = { altId ->
                    navController.navigate(Route.AlternativeDetail.createRoute(altId))
                },
                onSuggestAsFoss = { name, pkg ->
                    navController.navigate(Route.Submit.createRoute(name, pkg, "foss"))
                },
                onSuggestAsProprietary = { name, pkg ->
                    navController.navigate(Route.Submit.createRoute(name, pkg, "proprietary"))
                },
                onAddAlternativeClick = { _, pkg ->
                    navController.navigate(
                        Route.Submit.createRoute(type = "link", proprietaryTarget = pkg)
                    )
                },
                onSuggestCorrection = { pkg ->
                    navController.navigate(Route.SuggestCorrection.createRoute(pkg))
                },
                onSubmitSigningKey = { name, pkg ->
                    if (authState.isSignedIn) {
                        navController.navigate(Route.SigningKeyVote.createRoute(pkg, name))
                    } else {
                        navController.navigate(Route.Auth.route)
                    }
                },
                onViewPendingSubmission = { submissionId ->
                    navController.navigate(Route.SubmissionDetail.createRoute(submissionId))
                }
            )
        }

        composable(
            route = Route.AlternativeDetail.route,
            arguments = listOf(navArgument("altId") { type = NavType.StringType })
        ) { backStackEntry ->
            val altId = backStackEntry.arguments?.getString("altId")?.let { Uri.decode(it) } ?: return@composable
            AlternativeDetailScreen(
                altId = altId,
                onBackClick = { navController.navigateUp() }
            )
        }

        composable(Route.Auth.route) {
            AuthScreen(
                onAuthSuccess = { needsProfile ->
                    if (needsProfile) {
                        navController.navigate(Route.ProfileSetup.route) {
                            popUpTo(Route.Auth.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Route.Submit.createRoute()) {
                            popUpTo(Route.Auth.route) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Route.ProfileSetup.route) {
            ProfileSetupScreen(
                onProfileComplete = {
                    navController.navigate(Route.Submit.createRoute()) {
                        popUpTo(Route.ProfileSetup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Route.Submit.route,
            arguments = listOf(
                navArgument("appName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("packageName") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("type") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("submissionId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("proprietaryTarget") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val prefilledAppName = backStackEntry.arguments?.getString("appName")
            val prefilledPackageName = backStackEntry.arguments?.getString("packageName")
            val prefilledType = backStackEntry.arguments?.getString("type")
            val submissionId = backStackEntry.arguments?.getString("submissionId")
            val proprietaryTarget = backStackEntry.arguments?.getString("proprietaryTarget")

            SubmitScreen(
                onBackClick = { navController.navigateUp() },
                onSuccess = {
                    navController.navigate(Route.Dashboard.route) {
                        popUpTo(Route.Dashboard.route) { inclusive = true }
                    }
                },
                onNavigateToTargetSubmission = { appName, packageName ->
                    navController.navigate(Route.Submit.createRoute(appName, packageName)) {
                        popUpTo(Route.Submit.route) { inclusive = true }
                    }
                },
                prefilledAppName = prefilledAppName,
                prefilledPackageName = prefilledPackageName,
                prefilledType = prefilledType,
                submissionId = submissionId,
                prefilledProprietaryTarget = proprietaryTarget
            )
        }

        composable(Route.MySubmissions.route) {
            MySubmissionsScreen(
                onBackClick = { navController.navigateUp() },
                onSubmissionClick = { submissionId ->
                    navController.navigate(Route.Submit.createRoute(submissionId = submissionId))
                }
            )
        }

        composable(Route.IgnoredApps.route) {
            IgnoredAppsScreen(
                onBackClick = { navController.navigateUp() }
            )
        }

        composable(Route.Settings.route) {
            val authViewModel: AuthViewModel = koinViewModel()
            val authState by authViewModel.uiState.collectAsStateWithLifecycle()

            com.jksalcedo.librefind.ui.settings.SettingsScreen(
                onBackClick = { navController.navigateUp() },
                onReportClick = {
                    navController.navigate(Route.Report.route)
                },
                onMyReportsClick = {
                    if (authState.isSignedIn) {
                        navController.navigate(Route.MyReports.route)
                    } else {
                        navController.navigate(Route.Auth.route)
                    }
                },
                onCommunityClick = {
                    navController.navigate(Route.Community.route)
                },
                onPrivacyPolicyClick = {
                    navController.navigate(Route.PrivacyPolicy.route)
                }
            )
        }

        composable(Route.PrivacyPolicy.route) {
            com.jksalcedo.librefind.ui.settings.PrivacyPolicyScreen(
                onBackClick = { navController.navigateUp() }
            )
        }

        composable(Route.Report.route) {
            ReportScreen(
                onBackClick = { navController.navigateUp() },
                onSuccess = {
                    navController.navigate(Route.Dashboard.route) {
                        popUpTo(Route.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Route.MyReports.route) {
            MyReportsScreen(
                onBackClick = { navController.navigateUp() }
            )
        }

        composable(Route.Community.route) {
            CommunitySubmissionsScreen(
                onBackClick = { navController.navigateUp() },
                onSubmissionClick = { submissionId ->
                    navController.navigate(Route.SubmissionDetail.createRoute(submissionId))
                },
                onKeyVoteClick = { packageName, appName, sha256 ->
                    navController.navigate(Route.SigningKeyVote.createViewRoute(packageName, appName, sha256))
                },
                onUserClick = { userId ->
                    navController.navigate(Route.Profile.createRoute(userId))
                },
                onLeaderboardClick = {
                    navController.navigate(Route.Leaderboard.route)
                }
            )
        }

        composable(Route.Leaderboard.route) {
            LeaderboardScreen(
                onBackClick = { navController.navigateUp() },
                onUserClick = { userId ->
                    navController.navigate(Route.Profile.createRoute(userId))
                }
            )
        }

        composable(
            route = Route.SubmissionDetail.route,
            arguments = listOf(navArgument("submissionId") { type = NavType.StringType })
        ) { backStackEntry ->
            val submissionId = backStackEntry.arguments?.getString("submissionId")?.let { Uri.decode(it) }
                ?: return@composable
            com.jksalcedo.librefind.ui.community.SubmissionDetailScreen(
                submissionId = submissionId,
                onBackClick = { navController.navigateUp() },
                onEditClick = { id ->
                    navController.navigate(Route.Submit.createRoute(submissionId = id))
                }
            )
        }

        composable(
            route = Route.SuggestCorrection.route,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val packageName =
                backStackEntry.arguments?.getString("packageName")?.let { Uri.decode(it) }
                    ?: return@composable
            SuggestCorrectionScreen(
                packageName = packageName,
                onBackClick = { navController.navigateUp() }
            )
        }

        composable(
            route = Route.SigningKeyVote.route,
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType },
                navArgument("appName") { type = NavType.StringType },
                navArgument("sha256") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName")
                ?.let { Uri.decode(it) } ?: return@composable
            val appName = backStackEntry.arguments?.getString("appName")
                ?.let { Uri.decode(it) } ?: return@composable
            val sha256 = backStackEntry.arguments?.getString("sha256")
                ?.let { Uri.decode(it) }.orEmpty()
            SigningKeyVoteScreen(
                packageName = packageName,
                appName = appName,
                sha256Digest = sha256,
                onBackClick = { navController.navigateUp() }
            )
        }
    }
}
