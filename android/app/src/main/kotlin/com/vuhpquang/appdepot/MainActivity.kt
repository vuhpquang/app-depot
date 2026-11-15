package com.vuhpquang.appdepot

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vuhpquang.appdepot.ui.AppDepotViewModel
import com.vuhpquang.appdepot.ui.AppDetailScreen
import com.vuhpquang.appdepot.ui.HistoryScreen
import com.vuhpquang.appdepot.ui.HomeScreen
import com.vuhpquang.appdepot.ui.ReleaseNotes
import com.vuhpquang.appdepot.ui.SettingsScreen
import com.vuhpquang.appdepot.ui.theme.AppDepotTheme

class MainActivity : ComponentActivity() {
    private val vm: AppDepotViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* denied: checks still run, just silently */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askForNotificationsOnce()
        enableEdgeToEdge()
        setContent {
            AppDepotTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    NavHost(nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                vm,
                                onOpenApp = { nav.navigate("app/$it") },
                                onSettings = { nav.navigate("settings") },
                                onHistory = { nav.navigate("history") },
                            )
                        }
                        composable("app/{slug}", arguments = listOf(navArgument("slug") { type = NavType.StringType })) { entry ->
                            AppDetailScreen(vm, entry.arguments?.getString("slug").orEmpty(), onBack = { nav.popBackStack() })
                        }
                        composable("history") { HistoryScreen(vm, onBack = { nav.popBackStack() }) }
                        composable("settings") { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
                    }
                    val whatsNew by vm.whatsNew.collectAsStateWithLifecycle()
                    whatsNew?.let { w ->
                        AlertDialog(
                            onDismissRequest = vm::dismissWhatsNew,
                            title = { Text(stringResource(R.string.whats_new_title, w.version)) },
                            text = { ReleaseNotes(w.notes, Modifier.verticalScroll(rememberScrollState())) },
                            confirmButton = { TextButton(vm::dismissWhatsNew) { Text(stringResource(R.string.action_ok)) } },
                        )
                    }
                }
            }
        }
    }

    private fun askForNotificationsOnce() {
        val settings = (application as AppDepotApplication).settings
        if (settings.notificationsAsked) return
        settings.notificationsAsked = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }
}
