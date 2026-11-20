package dev.savar.regista

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect

class RegistaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.load(this)
        Reminders.createChannels(this)
        Sync.schedulePeriodic(this)
    }
}

enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    UP_NEXT("Up next", Icons.Outlined.Event, Icons.Rounded.Event),
    FOLLOWING("Following", Icons.Outlined.StarOutline, Icons.Rounded.Star),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
}

class MainActivity : ComponentActivity() {
    private var openMatch by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Store.load(this)
        openMatch = intent?.getStringExtra(Reminders.EXTRA_MATCH)
        setContent {
            RegistaTheme {
                RegistaRoot(openMatch, onMatchOpened = { openMatch = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Reminders.EXTRA_MATCH)?.let { openMatch = it }
    }
}

private fun readHealth(ctx: Context) = Health(
    notifications = Reminders.notificationsAllowed(ctx),
    exact = Reminders.canExact(ctx),
    battery = Reminders.ignoringBatteryOptimizations(ctx),
)

@SuppressLint("BatteryLife")
@Composable
fun RegistaRoot(
    openMatch: String?,
    onMatchOpened: () -> Unit,
    preview: Boolean = false,
    startTab: Tab = Tab.UP_NEXT,
    startBig: Boolean = false,
    previewHealth: Health? = null,
) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(startTab) }
    var bigPage by rememberSaveable { mutableStateOf(startBig) }
    var search by remember { mutableStateOf<SearchPurpose?>(null) }
    var health by remember { mutableStateOf(previewHealth ?: readHealth(ctx)) }
    val prefs = remember { ctx.getSharedPreferences("ui", Context.MODE_PRIVATE) }
    var batteryDismissed by remember { mutableStateOf(prefs.getBoolean("battery_dismissed", false)) }
    var askedNotifications by remember { mutableIntStateOf(0) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        health = readHealth(ctx)
        Reminders.reschedule(ctx)
    }

    // Every return to the app: re-read permissions, rebuild alarms (exact may have been granted), refresh if stale.
    if (!preview) LifecycleResumeEffect(Unit) {
        health = readHealth(ctx)
        Reminders.reschedule(ctx)
        Sync.syncIfStale(ctx)
        // First launch: notifications are the whole point, so ask straight away (Android 13+).
        if (Build.VERSION.SDK_INT >= 33 && !health.notifications && !prefs.getBoolean("asked_notifications", false)) {
            prefs.edit().putBoolean("asked_notifications", true).apply()
            askedNotifications++
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onPauseOrDispose { }
    }

    fun start(i: Intent) = runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    val fixNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("asked_notifications_twice", false)) {
            prefs.edit().putBoolean("asked_notifications_twice", true).apply()
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            start(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }
    }
    val fixExact: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            start(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))) ||
                start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
        }
    }
    val fixBattery: () -> Unit = {
        start(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))) ||
            start(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    BackHandler(enabled = bigPage) { bigPage = false }
    BackHandler(enabled = !bigPage && tab != Tab.UP_NEXT) { tab = Tab.UP_NEXT }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = {
                            if (tab == t && t == Tab.FOLLOWING) bigPage = false
                            tab = t
                        },
                        icon = { Icon(if (tab == t) t.selectedIcon else t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val page = if (tab == Tab.FOLLOWING && bigPage) "big" else tab.name
            AnimatedContent(page, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "tab") { p ->
                when (p) {
                    Tab.UP_NEXT.name -> UpNextScreen(
                        health = health,
                        batteryDismissed = batteryDismissed,
                        onFixNotifications = fixNotifications,
                        onFixExact = fixExact,
                        onFixBattery = fixBattery,
                        onDismissBattery = {
                            batteryDismissed = true
                            prefs.edit().putBoolean("battery_dismissed", true).apply()
                        },
                        onFollow = { tab = Tab.FOLLOWING; search = SearchPurpose.FOLLOW },
                        openMatch = openMatch,
                        onMatchOpened = onMatchOpened,
                    )
                    Tab.FOLLOWING.name -> FollowingScreen(
                        onSearch = { search = SearchPurpose.FOLLOW },
                        onBigSettings = { bigPage = true },
                    )
                    "big" -> BigSettingsPage(onBack = { bigPage = false }, onAddBig = { search = SearchPurpose.BIG })
                    else -> SettingsScreen(health, fixNotifications, fixExact, fixBattery)
                }
            }
        }
    }

    search?.let { purpose -> TeamSearchSheet(purpose) { search = null } }

    // A tap on a reminder opens Up next with that match's sheet.
    androidx.compose.runtime.LaunchedEffect(openMatch) { if (openMatch != null) tab = Tab.UP_NEXT }
}
