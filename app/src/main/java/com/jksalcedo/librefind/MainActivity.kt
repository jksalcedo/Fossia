package com.jksalcedo.librefind


import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jksalcedo.librefind.ui.auth.AuthViewModel
import com.jksalcedo.librefind.ui.navigation.NavGraph
import com.jksalcedo.librefind.ui.navigation.Route
import com.jksalcedo.librefind.ui.theme.LibreFindTheme
import com.jksalcedo.librefind.data.local.PreferencesManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jksalcedo.librefind.worker.NotificationWorker
import com.jksalcedo.librefind.worker.SignerFeedWorker
import java.util.concurrent.TimeUnit
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import org.woheller69.freeDroidWarn.FreeDroidWarn

class MainActivity : AppCompatActivity() {

    private val supabase: SupabaseClient by inject()

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _: Boolean ->
        // Handle if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        supabase.handleDeeplinks(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionCode
        }
        FreeDroidWarn.showWarningOnUpgrade(this, versionCode)

        setContent {
            LibreFindTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val authViewModel: AuthViewModel = koinViewModel()
                val authState by authViewModel.uiState.collectAsState()

                val showBottomBar =
                    currentRoute == Route.Dashboard.route || currentRoute == Route.Discover.route || currentRoute == Route.Community.route

                val prefs = remember { PreferencesManager(this@MainActivity) }
                var showConsentDialog by remember { mutableStateOf(!prefs.hasAskedNetworkConsent()) }

                if (showConsentDialog) {
                    AlertDialog(
                        onDismissRequest = {
                            // Force explicit decision
                        },
                        title = { Text(stringResource(R.string.network_consent_title)) },
                        text = { Text(stringResource(R.string.network_consent_message)) },
                        confirmButton = {
                            TextButton(onClick = {
                                prefs.setHasAskedNetworkConsent()
                                prefs.setNetworkConsentGranted(true)
                                showConsentDialog = false
                                
                                val workManager = WorkManager.getInstance(this@MainActivity)
                                val constraints = Constraints.Builder()
                                    .setRequiredNetworkType(NetworkType.CONNECTED)
                                    .build()

                                val signerRequest = PeriodicWorkRequestBuilder<SignerFeedWorker>(1, TimeUnit.DAYS)
                                    .setConstraints(constraints)
                                    .build()
                                workManager.enqueueUniquePeriodicWork("signer_feed_update", ExistingPeriodicWorkPolicy.UPDATE, signerRequest)

                                val notifRequest = PeriodicWorkRequestBuilder<NotificationWorker>(1, TimeUnit.HOURS)
                                    .setConstraints(constraints)
                                    .build()
                                workManager.enqueueUniquePeriodicWork("notification_check", ExistingPeriodicWorkPolicy.UPDATE, notifRequest)
                            }) {
                                Text(stringResource(R.string.network_consent_opt_in))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                prefs.setHasAskedNetworkConsent()
                                prefs.setNetworkConsentGranted(false)
                                showConsentDialog = false
                            }) {
                                Text(stringResource(R.string.network_consent_disable))
                            }
                        }
                    )
                }

                Scaffold(
                    contentWindowInsets = WindowInsets(0),
                    bottomBar = {
                        if (showBottomBar) {
                            NavigationBar {
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_home),
                                            contentDescription = stringResource(R.string.dashboard)
                                        )
                                    },
                                    label = { stringResource(R.string.dashboard) },
                                    selected = currentRoute == Route.Dashboard.route,
                                    onClick = {
                                        if (currentRoute != Route.Dashboard.route) {
                                            navController.navigate(Route.Dashboard.route) {
                                                popUpTo(Route.Dashboard.route) { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                )
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_discover),
                                            contentDescription = stringResource(R.string.discover)
                                        )
                                    },
                                    label = { stringResource(R.string.discover) },
                                    selected = currentRoute == Route.Discover.route,
                                    onClick = {
                                        if (currentRoute != Route.Discover.route) {
                                            navController.navigate(Route.Discover.route) {
                                                popUpTo(Route.Dashboard.route) { inclusive = false }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                )
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_world),
                                            contentDescription = stringResource(R.string.community)
                                        )
                                    },
                                    label = { stringResource(R.string.community) },
                                    selected = currentRoute == Route.Community.route,
                                    onClick = {
                                        if (currentRoute != Route.Community.route) {
                                            if (authState.isSignedIn) {
                                                navController.navigate(Route.Community.route) {
                                                    popUpTo(Route.Dashboard.route) {
                                                        inclusive = false
                                                    }
                                                    launchSingleTop = true
                                                }
                                            } else {
                                                navController.navigate(Route.Auth.route)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                        NavGraph(navController = navController)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        supabase.handleDeeplinks(intent)
    }

    override fun onResume() {
        super.onResume()
        supabase.handleDeeplinks(intent)
    }
}