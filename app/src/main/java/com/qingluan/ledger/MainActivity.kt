package com.qingluan.ledger

import android.app.*
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import dev.chrisbanes.haze.*
import java.time.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pendingQuickEntry = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private var launchReady by mutableStateOf(false)
    private var launchMotion by mutableStateOf(true)
    private var launchAnimation: android.animation.Animator? = null
    private var pendingSplashExit: (() -> Unit)? = null
    private var pendingSplashRemove: (() -> Unit)? = null
    private var appliedShortcutState: Boolean? = null

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        pendingQuickEntry.value = null
        enableEdgeToEdge()
        val coldStart = b == null
        if (Build.VERSION.SDK_INT >= 31) {
            splashScreen.setOnExitAnimationListener { splash ->
                pendingSplashRemove = {
                    runCatching { splash.remove() }
                    Unit
                }
                val exit: () -> Unit = {
                    launchAnimation =
                        animateStartupExit(splash, launchMotion) {
                            runCatching { splash.remove() }
                            launchAnimation = null
                            pendingSplashRemove = null
                        }
                }
                if (launchReady) exit() else pendingSplashExit = exit
            }
        }
        setContent {
            val screen: @Composable () -> Unit = {
                SecurityGate(
                    onReady = { prefs ->
                        val shortcutEnabled = prefs[DesktopEntry.enabledKey] ?: false
                        if (appliedShortcutState == null && shortcutEnabled)
                            pendingQuickEntry.value = DesktopEntry.parse(intent)
                        if (!shortcutEnabled) pendingQuickEntry.value = null
                        if (appliedShortcutState != shortcutEnabled) {
                            appliedShortcutState = shortcutEnabled
                            runCatching {
                                DesktopEntry.configure(this@MainActivity, shortcutEnabled)
                            }
                        }
                        launchMotion =
                            prefs[
                                androidx.datastore.preferences.core.booleanPreferencesKey("motion")]
                                ?: true
                        launchReady = true
                        pendingSplashExit?.let { exit ->
                            pendingSplashExit = null
                            exit()
                        }
                    }
                ) {
                    val quickType by pendingQuickEntry.collectAsStateWithLifecycle()
                    LedgerApp(
                        quickType = quickType,
                        consumeQuickEntry = {
                            pendingQuickEntry.value = null
                            intent.action = android.content.Intent.ACTION_MAIN
                        },
                    )
                }
            }
            if (coldStart && Build.VERSION.SDK_INT < 31)
                LegacyStartupReveal(launchReady, launchMotion, screen)
            else screen()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        lifecycleScope.launch {
            val enabled = preferences.data.first()[DesktopEntry.enabledKey] ?: false
            pendingQuickEntry.value = if (enabled) DesktopEntry.parse(intent) else null
        }
    }

    override fun onDestroy() {
        try {
            pendingSplashExit = null
            runCatching { launchAnimation?.cancel() }
            runCatching { pendingSplashRemove?.invoke() }
        } finally {
            launchAnimation = null
            pendingSplashRemove = null
            super.onDestroy()
        }
    }
}

val LocalCategoryIndex = staticCompositionLocalOf<Map<Long, Category>> { emptyMap() }

val expenseColor = Color(0xFFB25643)

@Composable
fun spendingColor() =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFFFB4A3) else expenseColor

fun icon(k: String): ImageVector = CategoryIcons.vector(k)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun LedgerApp(
    vm: LedgerViewModel = viewModel(),
    quickType: String? = null,
    consumeQuickEntry: () -> Unit = {},
) {
    val busy by vm.saving.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vm) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) vm.refreshDueDate()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val currentView = androidx.compose.ui.platform.LocalView.current
    SideEffect {
        val activity = context as? android.app.Activity
        if (activity != null) {
            androidx.core.view.WindowCompat.getInsetsController(activity.window, currentView)
                .apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
        }
    }
    val appearance by vm.appearance.collectAsStateWithLifecycle()
    val scheme = appearanceColors(context, dark, appearance)
    val typography =
        remember(appearance.fontSize, appearance.customFontPercent) {
            appearanceTypography(appearance)
        }
    MaterialTheme(colorScheme = scheme, typography = typography) {
        val rows by vm.entries.collectAsStateWithLifecycle()
        val cats by vm.categories.collectAsStateWithLifecycle()
        val message by vm.message.collectAsStateWithLifecycle()
        val undoBatch by vm.undoBatch.collectAsStateWithLifecycle()
        val effects by vm.effects.collectAsStateWithLifecycle()
        val selecting by vm.multiSelecting.collectAsStateWithLifecycle()
        val experience by vm.experience.collectAsStateWithLifecycle()
        val mainCurrency by vm.mainCurrency.collectAsStateWithLifecycle()
        val managedTags by vm.managedTags.collectAsStateWithLifecycle()
        var temporaryHide by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(experience.hideAmounts) { temporaryHide = null }
        val visibleExperience =
            if (temporaryHide == null) experience
            else experience.copy(hideAmounts = temporaryHide!!)
        val toggleAmounts: () -> Unit = { temporaryHide = !visibleExperience.hideAmounts }
        val nav = rememberNavController()
        val navigationFocus = androidx.compose.ui.platform.LocalFocusManager.current
        val navigationKeyboard =
            androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
        fun openPage(page: String) {
            if (nav.currentDestination?.route != page) {
                navigationFocus.clearFocus()
                navigationKeyboard?.hide()
                nav.navigate(page) { launchSingleTop = true }
            }
        }
        val back by nav.currentBackStackEntryAsState()
        val route = back?.destination?.route ?: "明细"
        val secondaryRoute = route in secondaryPages || route in legacySettingsRoutes
        var editId by rememberSaveable { mutableLongStateOf(0L) }
        val edit =
            remember(rows, editId) { if (editId == 0L) null else rows.find { it.id == editId } }
        var sheetOpen by rememberSaveable { mutableStateOf(false) }
        var entryInitialType by rememberSaveable { mutableStateOf("EXPENSE") }
        val entryState = rememberSaveableStateHolder()
        // Large image drafts stay in memory across management pages, outside saved-state Bundles.
        var draftReceipt by remember { mutableStateOf<String?>(null) }
        var initialDate by remember { mutableStateOf(LocalDate.now()) }
        LaunchedEffect(quickType, sheetOpen, busy) {
            if (quickType != null && !sheetOpen && !busy) {
                entryInitialType = quickType
                editId = 0L
                initialDate = LocalDate.now()
                entryState.removeState("entry")
                draftReceipt = null
                if (nav.currentDestination?.route != "明细")
                    nav.navigate("明细") {
                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                    }
                sheetOpen = true
                consumeQuickEntry()
            }
        }

        val haze = remember { HazeState() }
        val snack = remember { SnackbarHostState() }
        LaunchedEffect(message, undoBatch?.token) {
            message?.let { text ->
                val batch =
                    undoBatch?.takeIf { vm.undo.value.isNotEmpty() && text.startsWith("已删除") }
                val undoable = batch != null
                val result =
                    snack.showSnackbar(
                        text,
                        actionLabel = if (undoable) "撤销" else null,
                        withDismissAction = undoable,
                    )
                if (undoable) {
                    if (result == SnackbarResult.ActionPerformed) vm.undoDelete(batch)
                    else vm.clearUndo(batch?.token)
                }
                if (vm.message.value == text) vm.message.value = null
            }
        }
        val nativeHaptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        val silentHaptic = remember {
            object : androidx.compose.ui.hapticfeedback.HapticFeedback {
                override fun performHapticFeedback(
                    hapticFeedbackType: androidx.compose.ui.hapticfeedback.HapticFeedbackType
                ) {}
            }
        }
        CompositionLocalProvider(
            androidx.compose.ui.platform.LocalHapticFeedback provides
                if (effects.haptics) nativeHaptic else silentHaptic,
            LocalEffects provides effects,
            LocalExperience provides visibleExperience,
            LocalMainCurrency provides mainCurrency,
            LocalTagCatalog provides managedTags,
            LocalCurrency provides mainCurrency,
            LocalToggleAmounts provides toggleAmounts,
            LocalCurrencyFrequency provides
                remember(rows) { rows.groupingBy { it.currencyCode }.eachCount() },
            LocalAppearance provides appearance,
            LocalCategoryIndex provides remember(cats) { cats.associateBy { it.id } },
        ) {
            SideEffect { currentView.isHapticFeedbackEnabled = effects.haptics }
            val feedback = rememberAppFeedback()
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.TopCenter,
            ) {
                Scaffold(
                    modifier = Modifier.widthIn(max = 1100.dp).fillMaxSize(),
                    snackbarHost = { SnackbarHost(snack) },
                    containerColor = MaterialTheme.colorScheme.background,
                    floatingActionButton = {
                        if (!secondaryRoute && !selecting)
                            FloatingActionButton(
                                onClick = {
                                    feedback()
                                    editId = 0L
                                    entryInitialType = "EXPENSE"
                                    initialDate = LocalDate.now()
                                    entryState.removeState("entry")
                                    draftReceipt = null
                                    sheetOpen = true
                                },
                                modifier = Modifier.size(72.dp),
                                shape = CircleShape,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Icon(Icons.Outlined.Add, "记账", Modifier.size(32.dp))
                            }
                    },
                    bottomBar = {
                        if (!secondaryRoute)
                            LiquidNavigation(route, haze) { label ->
                                if (label != nav.currentDestination?.route) {
                                    navigationFocus.clearFocus()
                                    navigationKeyboard?.hide()
                                    nav.navigate(label) {
                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                    },
                ) { padding ->
                    val floating = route in listOf("明细", "洞察", "我的")
                    CompositionLocalProvider(
                        LocalFloatingBottomInset provides
                            if (floating) padding.calculateBottomPadding() else 0.dp
                    ) {
                        Box(
                            Modifier.fillMaxSize()
                                .haze(haze)
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            NavHost(
                                nav,
                                startDestination = "明细",
                                enterTransition = { pageEnter(motionAllowed(effects)) },
                                exitTransition = { pageExit(motionAllowed(effects)) },
                                popEnterTransition = {
                                    pageEnter(motionAllowed(effects), pop = true)
                                },
                                popExitTransition = {
                                    pageExit(motionAllowed(effects), pop = true)
                                },
                                modifier =
                                    Modifier.fillMaxSize()
                                        .padding(
                                            top = padding.calculateTopPadding(),
                                            bottom =
                                                if (floating) 0.dp
                                                else padding.calculateBottomPadding(),
                                            start =
                                                padding.calculateStartPadding(
                                                    androidx.compose.ui.platform
                                                        .LocalLayoutDirection
                                                        .current
                                                ),
                                            end =
                                                padding.calculateEndPadding(
                                                    androidx.compose.ui.platform
                                                        .LocalLayoutDirection
                                                        .current
                                                ),
                                        ),
                            ) {
                                composable("明细") {
                                    RouteSurface(route == "明细") {
                                        Details(
                                            rows,
                                            cats,
                                            onEdit = {
                                                editId = it.id
                                                entryState.removeState("entry")
                                                draftReceipt = null
                                                sheetOpen = true
                                            },
                                            onDelete = { vm.deleteEntries(listOf(it)) },
                                            vm = vm,
                                            openRecurring = { openPage("周期账单") },
                                        )
                                    }
                                }
                                composable("洞察") {
                                    RouteSurface(route == "洞察") {
                                        Insights(rows, cats, persist = vm::experience) { d ->
                                            initialDate = d
                                            entryInitialType = "EXPENSE"
                                            editId = 0L
                                            entryState.removeState("entry")
                                            draftReceipt = null
                                            sheetOpen = true
                                        }
                                    }
                                }
                                composable("我的") {
                                    RouteSurface(route == "我的") {
                                        SettingsHome(vm, rows, ::openPage)
                                    }
                                }
                                secondaryPages.forEach { page ->
                                    composable(page) {
                                        RouteSurface(route == page) {
                                            Settings(
                                                vm,
                                                cats,
                                                page,
                                                back = {
                                                    if (nav.currentDestination?.route == page)
                                                        nav.popBackStack()
                                                },
                                                open = ::openPage,
                                            )
                                        }
                                    }
                                }
                                legacySettingsRoutes.forEach { (oldRoute, targetRoute) ->
                                    composable(oldRoute) {
                                        LaunchedEffect(oldRoute) {
                                            nav.navigate(targetRoute) {
                                                popUpTo(oldRoute) { inclusive = true }
                                                launchSingleTop = true
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            BackupReminder(vm)
            if (sheetOpen && !secondaryRoute) {
                EntrySheet(
                    dismiss = {
                        sheetOpen = false
                        draftReceipt = null
                    }
                ) {
                    entryState.SaveableStateProvider("entry") {
                        EntryPanel(
                            edit,
                            initialDate,
                            cats,
                            busy,
                            initialCurrency = mainCurrency,
                            initialType = entryInitialType,
                            onCancel = {
                                sheetOpen = false
                                draftReceipt = null
                            },
                            onManage = { openPage("分类管理") },
                            onManageTags = { openPage("标签管理") },
                            draftReceipt = draftReceipt,
                            updateDraftReceipt = { draftReceipt = it },
                            save = { e ->
                                vm.save(e) {
                                    feedback()
                                    sheetOpen = false
                                    entryState.removeState("entry")
                                    draftReceipt = null
                                    editId = 0L
                                    nav.navigate("明细") { popUpTo("明细") { inclusive = true } }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Summary(rows: List<Entry>) {
    val fallback = LocalCurrency.current
    val groups = remember(rows, fallback) { currencyGroups(rows, fallback) }
    Column {
        groups.forEach { (code, entries) ->
            CompositionLocalProvider(LocalCurrency provides code) {
                if (code != "CNY" || groups.size > 1)
                    Text(code, style = MaterialTheme.typography.labelSmall)
                CurrencySummary(entries)
            }
        }
    }
}

@Composable
private fun CurrencySummary(rows: List<Entry>) {
    val currency = LocalCurrency.current
    val t = totals(rows)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
                Triple("收入", displayMoney(t.income, currency), MaterialTheme.colorScheme.primary),
                Triple("支出", displayMoney(t.expense, currency), spendingColor()),
                Triple("结余", displayMoney(t.balance, currency), MaterialTheme.colorScheme.onSurface),
            )
            .forEach { (label, value, color) ->
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.titleMedium,
                        color = color,
                        maxLines =
                            if (
                                androidx.compose.ui.platform.LocalDensity.current.fontScale *
                                    LocalAppearance.current.scale >= 1.4f
                            )
                                Int.MAX_VALUE
                            else 1,
                    )
                }
            }
    }
}

@Composable
fun DateButton(date: LocalDate, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Text(date.toString())
        Icon(Icons.Outlined.ExpandMore, null)
    }
    if (open) AppDateDialog(date, { open = false }, onChange)
}

@Composable
fun EntryRow(e: Entry, cats: List<Category>, onClick: (() -> Unit)?) =
    EntryRow(e, cats, onClick, null)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryRow(
    e: Entry,
    cats: List<Category>,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null,
) {
    val display = LocalExperience.current
    val feedback = rememberAppFeedback()
    val c = LocalCategoryIndex.current[e.categoryId] ?: cats.find { it.id == e.categoryId }
    Surface(
        modifier =
            Modifier.fillMaxWidth()
                .testTag("bill-row-${e.id}")
                .padding(vertical = 3.dp)
                .then(
                    if (onClick != null)
                        Modifier.combinedClickable(
                            onClick = {
                                feedback()
                                onClick()
                            },
                            onLongClick =
                                onLongClick?.let { action ->
                                    {
                                        feedback()
                                        action()
                                    }
                                },
                        )
                    else Modifier
                ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)),
    ) {
        val largeText =
            androidx.compose.ui.platform.LocalDensity.current.fontScale *
                LocalAppearance.current.scale >= 1.4f
        val amountText =
            (if (e.currencyCode != "CNY") e.currencyCode + " " else "") +
                (if (e.type == "INCOME") "+" else "−") +
                visibleMoney(e.amount, e.currencyCode, display.hideAmounts)
        Column(
            Modifier.heightIn(min = if (display.density == "comfortable") 72.dp else 52.dp)
                .padding(
                    horizontal = 10.dp,
                    vertical = if (display.density == "comfortable") 12.dp else 6.dp,
                ),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(icon(c?.iconKey ?: "circle"), null, Modifier.padding(7.dp).size(18.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        c?.name ?: "分类",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    val subtitle = entrySubtitle(e, display)
                    if (subtitle.isNotBlank())
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (largeText) 2 else 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                }
                if (!largeText)
                    Text(
                        amountText,
                        style = MaterialTheme.typography.titleSmall,
                        color =
                            if (e.type == "INCOME") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                    )
                trailing?.invoke()
            }
            if (largeText)
                Text(
                    amountText,
                    Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.titleSmall,
                    color =
                        if (e.type == "INCOME") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                )
        }
    }
}

@Composable
fun MonthHeader(month: LocalDate, rows: List<Entry>, change: (LocalDate) -> Unit) {
    val defaultCurrency = LocalMainCurrency.current
    val groups = remember(rows, defaultCurrency) { currencyGroups(rows, defaultCurrency) }
    Column {
        groups.entries.forEachIndexed { index, (code, entries) ->
            CompositionLocalProvider(LocalCurrency provides code) {
                MonthCurrencyHeader(month, entries, index == 0, groups.size > 1, change)
            }
        }
    }
}

@Composable
private fun MonthCurrencyHeader(
    month: LocalDate,
    rows: List<Entry>,
    showDate: Boolean,
    showCode: Boolean,
    change: (LocalDate) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val currency = LocalCurrency.current
    val t = totals(rows)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showDate)
                TextButton(
                    onClick = { open = true },
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.testTag("detail-period"),
                ) {
                    Column {
                        Text("${month.year}年", style = MaterialTheme.typography.labelSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${month.monthValue}月",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(16.dp))
                        }
                    }
                }
            else Spacer(Modifier.width(64.dp))
            if (showCode)
                Text(
                    currency,
                    Modifier.padding(end = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                )
            Spacer(Modifier.width(8.dp))
            listOf(
                    Triple(
                        "收入",
                        displayMoney(t.income, currency),
                        MaterialTheme.colorScheme.primary,
                    ),
                    Triple("支出", displayMoney(t.expense, currency), spendingColor()),
                    Triple(
                        "结余",
                        displayMoney(t.balance, currency),
                        MaterialTheme.colorScheme.onSurface,
                    ),
                )
                .forEach { (name, value, color) ->
                    Column(Modifier.weight(1f)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.labelLarge,
                            color = color,
                            maxLines =
                                if (
                                    androidx.compose.ui.platform.LocalDensity.current.fontScale *
                                        LocalAppearance.current.scale >= 1.4f
                                )
                                    Int.MAX_VALUE
                                else 1,
                        )
                    }
                }
        }
    }
    if (open)
        PeriodPicker(month, "month", { open = false }) {
            change(it)
            open = false
        }
}

@Composable
fun Details(
    rows: List<Entry>,
    cats: List<Category>,
    onEdit: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
    vm: LedgerViewModel? = null,
    openRecurring: () -> Unit = {},
) {
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf<Set<Long>>(emptySet()) }
    fun leaveSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }
    val selectionBackProgress =
        rememberPredictiveDismiss(selectionMode && LocalRouteActive.current, ::leaveSelection)
    DisposableEffect(Unit) { onDispose { vm?.multiSelecting?.value = false } }
    val routeActive = LocalRouteActive.current
    LaunchedEffect(selectionMode, routeActive) {
        vm?.multiSelecting?.value = selectionMode && routeActive
    }
    val recurring by
        (vm?.recurring ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList<RecurringBill>()))
            .collectAsState()
    val today by
        (vm?.today ?: kotlinx.coroutines.flow.MutableStateFlow(LocalDate.now())).collectAsState()
    val due = recurring.count { it.enabled && !LocalDate.parse(it.nextDate).isAfter(today) }
    var month by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("EXPENSE") }
    var category by remember { mutableLongStateOf(0L) }
    var from by remember { mutableStateOf<LocalDate?>(null) }
    var to by remember { mutableStateOf<LocalDate?>(null) }
    var filter by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(BillSearch()) }
    var advancedAllDates by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Entry?>(null) }
    var swipedId by remember { mutableStateOf<Long?>(null) }
    var search by remember { mutableStateOf(false) }
    var searchBounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    fun closeSearch() {
        search = false
        query = ""
        focusManager.clearFocus()
        keyboard?.hide()
    }
    val period = DateSpan.month(month)
    val zone = ZoneId.systemDefault()
    val advancedMatch = remember(advanced) { advanced.predicate() }
    val searchCategories = remember(cats) { cats.associateBy { it.id } }
    val scoped =
        remember(rows, cats, period, category, query, from, to, zone, advanced, advancedAllDates) {
            rows.filter { e ->
                (if (advancedAllDates) true
                else if (from != null || to != null)
                    (from == null || !e.date().isBefore(from)) &&
                        (to == null || !e.date().isAfter(to))
                else period.contains(e)) &&
                    (category == 0L || e.categoryId == category) &&
                    advancedMatch(e) &&
                    (query.isBlank() ||
                        e.note.contains(query, true) ||
                        e.tags.contains(query, true) ||
                        searchCategories[e.categoryId]?.name?.contains(query, true) == true)
            }
        }
    val filtered = remember(scoped, type) { scoped.filter { it.type == type } }
    LaunchedEffect(filtered) { vm?.currentExportIds?.value = filtered.map { it.id }.toSet() }
    val grouped = remember(filtered, zone) { filtered.groupBy { it.date() } }
    var visibleCount by
        remember(month, type, category, from, to, query, advanced, advancedAllDates) {
            mutableIntStateOf(50)
        }
    val listState = rememberLazyListState()
    LaunchedEffect(listState, filtered.size) {
        snapshotFlow {
                listState.layoutInfo.let {
                    it.visibleItemsInfo.lastOrNull()?.index to it.totalItemsCount
                }
            }
            .collect { (last, total) ->
                if (last != null && last >= total - 8 && visibleCount < filtered.size)
                    visibleCount = minOf(visibleCount + 50, filtered.size)
            }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize()
                .testTag("details-page")
                .dismissSearchOnBlankTap(search, searchBounds, ::closeSearch),
            state = listState,
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = LocalFloatingBottomInset.current + 88.dp,
                ),
        ) {
            item {
                HomeToolbar(
                    query,
                    { query = it },
                    search,
                    { search = true },
                    ::closeSearch,
                    { searchBounds = it },
                    { filter = true },
                    filtered =
                        advanced != BillSearch() ||
                            advancedAllDates ||
                            category != 0L ||
                            from != null ||
                            to != null,
                )
            }
            item {
                MonthHeader(month, scoped) {
                    month = it
                    from = null
                    to = null
                    advancedAllDates = false
                }
            }
            if (advancedAllDates || from != null || to != null)
                item {
                    Text(
                        if (advancedAllDates) "筛选范围：全部日期"
                        else "筛选范围：${from ?: "不限"} 至 ${to ?: "不限"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            if (due > 0)
                item {
                    TextButton(onClick = openRecurring, modifier = Modifier.fillMaxWidth()) {
                        Text("$due 项周期账单待确认")
                    }
                }
            if (filtered.isEmpty()) item { Empty() }
            var remaining = visibleCount
            for ((day, dayEntries) in grouped) {
                if (remaining <= 0) break
                val list = dayEntries.take(remaining)
                remaining -= list.size
                item {
                    Text(
                        day.toString(),
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                items(list, key = { it.id }, contentType = { "bill" }) { e ->
                    if (selectionMode)
                        EntryRow(
                            e,
                            cats,
                            onClick = {
                                selectedIds =
                                    if (e.id in selectedIds) selectedIds - e.id
                                    else selectedIds + e.id
                            },
                            onLongClick = null,
                            trailing = {
                                CompositionLocalProvider(
                                    LocalMinimumInteractiveComponentSize provides
                                        androidx.compose.ui.unit.Dp.Unspecified
                                ) {
                                    Checkbox(
                                        e.id in selectedIds,
                                        onCheckedChange = null,
                                        modifier =
                                            Modifier.size(24.dp)
                                                .testTag("bill-checkbox-${e.id}")
                                                .semantics {
                                                    stateDescription =
                                                        if (e.id in selectedIds) "已选" else "未选"
                                                    role =
                                                        androidx.compose.ui.semantics.Role.Checkbox
                                                },
                                    )
                                }
                            },
                        )
                    else
                        SwipeEntryRow(
                            e,
                            cats,
                            swipedId == e.id,
                            { swipedId = if (it) e.id else null },
                            { onEdit(e) },
                            { deleting = e },
                            longPress =
                                if (vm != null)
                                    ({
                                        selectionMode = true
                                        selectedIds = setOf(e.id)
                                        swipedId = null
                                        focusManager.clearFocus()
                                        keyboard?.hide()
                                    })
                                else null,
                        )
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = selectionMode && vm != null,
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = -16.dp.toPx() * selectionBackProgress
                        alpha = 1f - selectionBackProgress * .4f
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            enter =
                androidx.compose.animation.slideInVertically(
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            if (motionAllowed(LocalEffects.current)) UiMotion.ShortMillis else 0
                        ),
                    initialOffsetY = { -it },
                ) +
                    androidx.compose.animation.fadeIn(
                        androidx.compose.animation.core.tween(
                            if (motionAllowed(LocalEffects.current)) 120 else 0
                        )
                    ),
            exit =
                androidx.compose.animation.slideOutVertically(
                    animationSpec =
                        androidx.compose.animation.core.tween(
                            if (motionAllowed(LocalEffects.current)) UiMotion.ShortMillis else 0
                        ),
                    targetOffsetY = { -it },
                ) +
                    androidx.compose.animation.fadeOut(
                        androidx.compose.animation.core.tween(
                            if (motionAllowed(LocalEffects.current)) 120 else 0
                        )
                    ),
        ) {
            if (vm != null)
                BulkBillActions(
                    vm,
                    filtered.map { it.id }.toSet(),
                    selectedIds,
                    { selectedIds = it },
                    ::leaveSelection,
                )
        }
    }
    if (filter && vm != null)
        AdvancedSearchDialog(
            vm,
            cats,
            advanced.copy(
                keyword = query,
                type = type,
                categoryId = category,
                from = if (advancedAllDates) null else from ?: month,
                to = if (advancedAllDates) null else to ?: month.plusMonths(1).minusDays(1),
            ),
            { filter = false },
            { next ->
                query = next.keyword
                type = next.type
                category = next.categoryId
                from = next.from
                to = next.to
                advancedAllDates = next.from == null && next.to == null
                advanced =
                    next.copy(
                        keyword = "",
                        type = "EXPENSE",
                        categoryId = 0,
                        from = null,
                        to = null,
                    )
            },
        )
    deleting?.let { e ->
        GlassAlertDialog(
            onDismissRequest = {
                deleting = null
                swipedId = null
            },
            title = { Text("删除这笔账单？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(e)
                        deleting = null
                        swipedId = null
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        swipedId = null
                    }
                ) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
fun Empty(title: String = "暂无账单") {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            androidx.compose.ui.res.painterResource(R.drawable.brand_logo),
            "青鸾记账",
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
