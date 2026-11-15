package com.billmivo.app

import android.app.Application
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[BillsModel::class.java]
        setContent { BillmivoApp(model) }
    }
}

class BillsModel(application: Application) : AndroidViewModel(application) {
    private val repo = (application as BillmivoApplication).repository
    val records = repo.records.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun mutate(onError: () -> Unit, onSuccess: () -> Unit, action: suspend BillRepository.() -> Unit) {
        viewModelScope.launch {
            try {
                repo.action()
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onError()
            }
        }
    }
}

private enum class Page(val title: Int, val icon: Int) {
    Dashboard(R.string.dashboard, R.drawable.ic_home),
    Bills(R.string.bills, R.drawable.ic_bills),
    Calendar(R.string.calendar, R.drawable.ic_calendar),
    Insights(R.string.insights, R.drawable.ic_chart),
    More(R.string.more, R.drawable.ic_more)
}

@Composable
private fun BillmivoApp(model: BillsModel) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("display", 0) }
    var appearance by rememberSaveable { mutableStateOf(preferences.getString("appearance", "system") ?: "system") }
    var spoken by rememberSaveable { mutableStateOf(preferences.getBoolean("spoken", true)) }
    val systemDark = isSystemInDarkTheme()
    var page by rememberSaveable { mutableStateOf(Page.Dashboard) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now()
            delay(15_000)
        }
    }

    val dark = appearance == "dark" || appearance == "system" && systemDark
    val window = (context as? MainActivity)?.window
    SideEffect {
        window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    BillmivoTheme(dark) {
        val data by model.records.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        val error = stringResource(R.string.action_error)
        var failure by remember { mutableStateOf(0) }
        LaunchedEffect(failure) { if (failure > 0) snackbar.showSnackbar(error) }
        val save: (suspend BillRepository.() -> Unit, () -> Unit, () -> Unit) -> Unit =
            { action, success, failed -> model.mutate({ failed(); failure++ }, success, action) }

        var showEditor by rememberSaveable { mutableStateOf(false) }
        var editing by rememberSaveable { mutableStateOf<Long?>(null) }
        var selectedSchedule by rememberSaveable { mutableStateOf<Long?>(null) }
        var selectedDay by rememberSaveable { mutableStateOf<Long?>(null) }
        var showVoice by rememberSaveable { mutableStateOf(false) }
        var period by rememberSaveable { mutableStateOf(30) }
        var customStart by rememberSaveable { mutableStateOf<Long?>(null) }
        var customEnd by rememberSaveable { mutableStateOf<Long?>(null) }
        var requestedCurrency by rememberSaveable { mutableStateOf<String?>(null) }
        val locale = displayLocale()
        val currencies = data?.schedules?.map { it.currency }?.distinct()?.sorted().orEmpty()
        val currency = requestedCurrency?.takeIf { it in currencies } ?: currencies.firstOrNull() ?: defaultCurrency(locale)
        val start = if (period == 0) maxOf(today, LocalDate.ofEpochDay(customStart ?: today.toEpochDay())) else today
        val end = if (period == 0) maxOf(start, LocalDate.ofEpochDay(customEnd ?: start.toEpochDay()))
            else start.plusDays(period.toLong() - 1)
        val forecast = remember(data, today, start, end) { data?.forecast(today, start, end) }
        val spend = remember(data, today, currency) { data?.spending(today, currency) }

        val add = { editing = null; showEditor = true }
        val select: (Occurrence) -> Unit = { selectedSchedule = it.schedule.id; selectedDay = it.date.toEpochDay() }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 760.dp
            Scaffold(
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (!wide) NavigationBar(containerColor = palette.paper) {
                        Page.entries.forEach { item ->
                            NavigationBarItem(page == item, { page = item },
                                icon = { Icon(painterResource(item.icon), null) }, label = { Text(stringResource(item.title), maxLines = 1) })
                        }
                    }
                }
            ) { insets ->
                Row(Modifier.fillMaxSize().padding(insets)) {
                    if (wide) NavigationRail(containerColor = palette.paper) {
                        Page.entries.forEach { item ->
                            NavigationRailItem(page == item, { page = item },
                                icon = { Icon(painterResource(item.icon), null) }, label = { Text(stringResource(item.title)) })
                        }
                    }
                    val records = data
                    if (records == null || forecast == null || spend == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Column(Modifier.weight(1f)) {
                            BrandBar(onAdd = add, onVoice = { showVoice = true })
                            when (page) {
                                Page.Dashboard -> Dashboard(
                                    records, forecast, spend, currency, currencies, period, start, end, today,
                                    onCurrency = { requestedCurrency = it },
                                    onRange = { days, from, until ->
                                        period = days
                                        customStart = from.toEpochDay()
                                        customEnd = until.toEpochDay()
                                    },
                                    onAdd = add, onSelect = select
                                )
                                Page.Bills -> BillsPage(records, onEdit = { editing = it.id; showEditor = true }, onAdd = add)
                                Page.Calendar -> CalendarPage(records, today, select)
                                Page.Insights -> InsightsPage(records, spend, currency, currencies, today) { requestedCurrency = it }
                                Page.More -> SettingsPage(appearance, spoken,
                                    onTheme = { appearance = it; preferences.edit().putString("appearance", it).apply() },
                                    onSpoken = { spoken = it; preferences.edit().putBoolean("spoken", it).apply() })
                            }
                        }
                        if (showEditor) {
                            BillEditor(records.schedules.find { it.id == editing }, today, currency,
                                onClose = { showEditor = false }, save = save)
                        }
                        val date = selectedDay?.let(LocalDate::ofEpochDay)
                        val occurrence = remember(records, date, selectedSchedule) {
                            date?.let { records.occurrences(it, it).find { candidate -> candidate.schedule.id == selectedSchedule } }
                        }
                        occurrence?.let {
                            OccurrenceDialog(it, today, { selectedSchedule = null; selectedDay = null }, save)
                        }
                        if (showVoice) {
                            VoiceDialog(records, currency, today, spoken, onClose = { showVoice = false }) {
                                period = it
                                page = Page.Dashboard
                                showVoice = false
                            }
                        }
                    }
                }
            }
        }
    }
}
