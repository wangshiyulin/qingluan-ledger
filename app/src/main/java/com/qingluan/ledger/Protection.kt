package com.qingluan.ledger

import android.app.Activity
import android.app.KeyguardManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun rememberAuthenticate(result: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(result)
    val fallback =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            callback(it.resultCode == Activity.RESULT_OK)
        }
    var signal by remember { mutableStateOf<android.os.CancellationSignal?>(null) }
    DisposableEffect(Unit) { onDispose { signal?.cancel() } }
    fun credential() {
        try {
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            @Suppress("DEPRECATION")
            val intent = keyguard?.createConfirmDeviceCredentialIntent("青鸾记账", "验证后继续")
            if (intent != null) fallback.launch(intent) else callback(false)
        } catch (_: Exception) {
            callback(false)
        }
    }
    return {
        val manager = context.getSystemService(KeyguardManager::class.java)
        if (manager?.isDeviceSecure != true) callback(false)
        else if (Build.VERSION.SDK_INT >= 30) {
            signal?.cancel()
            val current = android.os.CancellationSignal()
            signal = current
            try {
                val prompt =
                    android.hardware.biometrics.BiometricPrompt.Builder(context)
                        .setTitle("验证青鸾记账身份")
                        .setAllowedAuthenticators(
                            android.hardware.biometrics.BiometricManager.Authenticators
                                .BIOMETRIC_STRONG or
                                android.hardware.biometrics.BiometricManager.Authenticators
                                    .DEVICE_CREDENTIAL
                        )
                        .build()
                prompt.authenticate(
                    current,
                    context.mainExecutor,
                    object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult
                        ) {
                            callback(true)
                        }

                        override fun onAuthenticationError(code: Int, message: CharSequence) {
                            if (code in setOf(1, 11, 12, 15)) credential() else callback(false)
                        }
                    },
                )
            } catch (_: Exception) {
                credential()
            }
        } else credential()
    }
}

@Composable
fun SecurityGate(onReady: (Preferences) -> Unit = {}, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val savedContent = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var prefs by remember { mutableStateOf<Preferences?>(null) }
    var unlocked by remember { mutableStateOf(false) }
    var stopped by remember { mutableLongStateOf(0L) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { context.preferences.data.collect { prefs = it } }
    SideEffect { prefs?.let(onReady) }
    val locked = prefs?.get(booleanPreferencesKey("appLock")) ?: false
    val hide = prefs?.get(booleanPreferencesKey("hidePreview")) ?: false
    val timeout = (prefs?.get(stringPreferencesKey("lockTimeout")) ?: "60").toLongOrNull() ?: 60L
    val authenticate = rememberAuthenticate { ok ->
        unlocked = ok
        if (!ok) error = "验证未完成"
    }
    LaunchedEffect(locked) { if (locked) unlocked = false }
    DisposableEffect(hide) {
        val window = (context as Activity).window
        if (hide) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {}
    }
    DisposableEffect(owner, locked, timeout) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stopped = android.os.SystemClock.elapsedRealtime()
            if (event == Lifecycle.Event.ON_START && locked && stopped != 0L) {
                if (
                    android.os.SystemClock.elapsedRealtime() - stopped >= timeout * 1000 ||
                        context.getSystemService(KeyguardManager::class.java).isDeviceLocked
                )
                    unlocked = false
                stopped = 0L
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    if (prefs == null)
        Surface(
            Modifier.fillMaxSize(),
            color = androidx.compose.ui.res.colorResource(R.color.startup_background),
        ) {}
    else if (locked && !unlocked) {
        val mode = prefs?.get(stringPreferencesKey("theme")) ?: "system"
        val dark =
            mode == "dark" || (mode != "light" && androidx.compose.foundation.isSystemInDarkTheme())
        val style =
            appearanceFromPreferences(
                prefs ?: androidx.datastore.preferences.core.emptyPreferences()
            )
        val view = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            androidx.core.view.WindowCompat.getInsetsController((context as Activity).window, view)
                .apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
        }
        MaterialTheme(
            colorScheme = appearanceColors(context, dark, style),
            typography = appearanceTypography(style, rememberAppFont(style)),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("青鸾记账", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            try {
                                authenticate()
                            } catch (_: Exception) {
                                error = "请检查系统锁屏与身份验证设置"
                            }
                        }
                    ) {
                        Text("验证解锁")
                    }
                    if (error.isNotBlank()) Text(error)
                }
            }
        }
    } else content()
}

@Composable
fun ProtectionControls(vm: LedgerViewModel) {
    val context = LocalContext.current
    val e by vm.experience.collectAsState()
    val prefs by context.preferences.data.collectAsState(initial = emptyPreferences())
    var authChange by remember { mutableStateOf(false) }
    val authenticate = rememberAuthenticate { ok ->
        if (ok)
            vm.action {
                context.preferences.edit { it[booleanPreferencesKey("appLock")] = authChange }
            }
        else vm.message.value = "验证未完成；请先设置系统锁屏密码"
    }
    ConfigSwitch("应用锁", prefs[booleanPreferencesKey("appLock")] ?: false) { value ->
        authChange = value
        try {
            authenticate()
        } catch (_: Exception) {
            vm.message.value = "无法启动系统身份验证"
        }
    }
    ConfigChoice(
        "离开后锁定",
        prefs[stringPreferencesKey("lockTimeout")] ?: "60",
        linkedMapOf("0" to "立即", "60" to "1分钟", "300" to "5分钟"),
    ) { value ->
        vm.action { context.preferences.edit { it[stringPreferencesKey("lockTimeout")] = value } }
    }
    ConfigSwitch("隐藏后台预览", prefs[booleanPreferencesKey("hidePreview")] ?: false) { value ->
        vm.action { context.preferences.edit { it[booleanPreferencesKey("hidePreview")] = value } }
    }
    Text("隐藏后台预览时也会禁止系统截图。", style = MaterialTheme.typography.labelSmall)
    ConfigChoice("备份提醒", e.backupDays, linkedMapOf("off" to "关闭", "7" to "每7天", "30" to "每30天")) {
        vm.experience("backupDays", it)
    }
    Text("打开应用时提醒；完整 JSON 或加密备份成功后重新计时。", style = MaterialTheme.typography.labelSmall)
}

@Composable
fun BackupReminder(vm: LedgerViewModel) {
    val context = LocalContext.current
    val e by vm.experience.collectAsState()
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var export by remember { mutableStateOf(false) }
    LaunchedEffect(e.backupDays) {
        if (e.backupDays != "off") {
            val p = context.preferences.data.first()
            val now = System.currentTimeMillis()
            val baseline =
                p[longPreferencesKey("lastBackup")] ?: p[longPreferencesKey("reminderStart")]
            if (baseline == null)
                context.preferences.edit { it[longPreferencesKey("reminderStart")] = now }
            else if (
                now - baseline >= e.backupDays.toLong() * 86400000L &&
                    now - (p[longPreferencesKey("backupSnooze")] ?: 0L) >= 86400000L
            )
                open = true
        }
    }
    fun snooze() {
        open = false
        scope.launch {
            context.preferences.edit {
                it[longPreferencesKey("backupSnooze")] = System.currentTimeMillis()
            }
        }
    }
    if (open)
        GlassAlertDialog(
            onDismissRequest = { snooze() },
            title = { Text("备份提醒") },
            text = { Text("建议导出一份完整本地备份。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        snooze()
                        export = true
                    }
                ) {
                    Text("导出")
                }
            },
            dismissButton = { TextButton(onClick = { snooze() }) { Text("稍后") } },
        )
    if (export) ExportDialog(vm, dismiss = { export = false })
}
