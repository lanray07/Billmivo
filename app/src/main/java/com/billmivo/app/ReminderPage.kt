package com.billmivo.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun SettingSwitch(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = null)
    }
}

@Composable
fun RemindersPage(records: Records, today: LocalDate, service: BillReminders, save: SaveBillAction, back: () -> Unit) {
    val context = LocalContext.current
    val locale = displayLocale()
    var preferences by remember { mutableStateOf(service.preferences()) }
    var permission by remember { mutableStateOf(service.canNotify()) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permission = service.canNotify()
        service.refresh()
    }
    LifecycleResumeEffect(service) {
        permission = service.canNotify()
        onPauseOrDispose { }
    }
    fun update(options: ReminderPreferences) {
        preferences = options
        service.save(options)
    }
    val hours = preferences.hours
    val hourFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val bills = records.schedules.filter { it.until == null }.sortedBy { it.name }
    PageLayout {
        TextButton(back) { Text(stringResource(R.string.settings)) }
        SectionTitle(stringResource(R.string.reminders), stringResource(R.string.reminder_intro))
        Panel(Modifier.fillMaxWidth()) {
            SettingSwitch(stringResource(R.string.reminder_enable), preferences.enabled) {
                update(preferences.copy(enabled = it))
                if (it && Build.VERSION.SDK_INT >= 33 && !permission) request.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (preferences.enabled && !permission) {
                Text(stringResource(R.string.reminder_blocked), color = palette.red)
                TextButton({
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }) { Text(stringResource(R.string.reminder_system_settings)) }
            }
            MenuField(stringResource(R.string.reminder_hour), hours.hour, (0..23).toList(),
                { LocalTime.of(it, 0).format(hourFormat) }, { update(preferences.copy(hours = hours.copy(hour = it))) })
            SettingSwitch(stringResource(R.string.reminder_quiet), hours.quietStart != hours.quietEnd) {
                update(preferences.copy(hours = if (it) hours.copy(quietStart = 22, quietEnd = 7)
                    else hours.copy(quietStart = 0, quietEnd = 0)))
            }
            if (hours.quietStart != hours.quietEnd) {
                MenuField(stringResource(R.string.reminder_quiet_start), hours.quietStart, (0..23).filter { it != hours.quietEnd },
                    { LocalTime.of(it, 0).format(hourFormat) },
                    { update(preferences.copy(hours = hours.copy(quietStart = it))) })
                MenuField(stringResource(R.string.reminder_quiet_end), hours.quietEnd, (0..23).filter { it != hours.quietStart },
                    { LocalTime.of(it, 0).format(hourFormat) },
                    { update(preferences.copy(hours = hours.copy(quietEnd = it))) })
            }
            SettingSwitch(stringResource(R.string.reminder_previews), preferences.previews) {
                update(preferences.copy(previews = it))
            }
        }
        val todayEvents = remember(records, today) { records.reminderEvents(today) }
        Panel(Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.reminder_today_title))
            if (todayEvents.isEmpty()) Text(stringResource(R.string.reminder_today_empty), color = palette.muted)
            todayEvents.take(20).forEach { event ->
                val title = when (event.kind) {
                    ReminderKind.UPCOMING -> R.string.reminder_before
                    ReminderKind.DUE -> R.string.reminder_due
                    ReminderKind.OVERDUE -> R.string.reminder_overdue
                    ReminderKind.INCREASE -> R.string.reminder_increase
                    ReminderKind.ONE_OFF -> R.string.one_off_reminder
                }
                Text(stringResource(R.string.chart_value, event.bill.schedule.name, stringResource(title)))
            }
        }
        val scheduled = records.oneOffReminders.filter { it.notifyOn >= today }.sortedBy { it.notifyOn }
        if (scheduled.isNotEmpty()) Panel(Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.one_off_reminders))
            val byId = records.schedules.associateBy { it.id }
            scheduled.forEach { reminder ->
                val name = byId[reminder.key.scheduleId]?.name.orEmpty()
                Text(stringResource(R.string.chart_value, name, dateLabel(reminder.notifyOn, locale)))
                TextButton({ save({ removeOneOffReminder(reminder.key) }, {}, {}) }) {
                    Text(stringResource(R.string.one_off_remove_for, name))
                }
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.reminder_per_bill))
            if (bills.isEmpty()) Text(stringResource(R.string.empty_bills), color = palette.muted)
            val active = records.reminders.map { it.series }.toSet()
            bills.forEach { bill ->
                OutlinedButton({ selected = bill.series }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.chart_value, bill.name,
                        stringResource(if (bill.series in active) R.string.reminder_active else R.string.reminder_off)))
                }
            }
        }
    }
    bills.find { it.series == selected }?.let {
        ReminderRuleDialog(it, records.reminders.find { rule -> rule.series == it.series }, { selected = null }, save)
    }
}

@Composable
internal fun OneOffReminderDialog(item: Occurrence, requestedDay: LocalDate?, today: LocalDate, records: Records,
                                 service: BillReminders, save: SaveBillAction, close: () -> Unit) {
    val current = records.oneOffReminders.find { it.key == item.key }?.notifyOn
    val latest = today.plusDays(90)
    val initial = (requestedDay ?: current ?: minOf(item.date, today.plusDays(1))).coerceIn(today, latest)
    val locale = displayLocale()
    var day by rememberSaveable(item.key.scheduleId, item.date.toEpochDay(), initial.toEpochDay()) {
        mutableLongStateOf(initial.toEpochDay())
    }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!busy) close() },
        title = { Text(stringResource(R.string.one_off_reminder)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.chart_value, item.schedule.name, dateLabel(item.date, locale)))
                DateField(R.string.one_off_reminder_date, LocalDate.ofEpochDay(day), latest, earliest = today) {
                    day = it.toEpochDay()
                }
                Text(stringResource(R.string.one_off_reminder_time), color = palette.muted)
                if (!service.preferences().enabled || !service.canNotify()) {
                    Text(stringResource(R.string.one_off_disabled), color = palette.muted)
                }
            }
        },
        confirmButton = { Button({
            busy = true
            save({ saveOneOffReminder(item.key, LocalDate.ofEpochDay(day), today) }, close) { busy = false }
        }, enabled = !busy) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(close, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun ReminderRuleDialog(bill: Schedule, current: ReminderRule?, close: () -> Unit, save: SaveBillAction) {
    var lead by rememberSaveable { mutableIntStateOf(current?.leadDays ?: 0) }
    var due by rememberSaveable { mutableStateOf(current?.due ?: false) }
    var overdue by rememberSaveable { mutableStateOf(current?.overdue ?: false) }
    var increase by rememberSaveable { mutableStateOf(current?.increase ?: false) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog({ if (!busy) close() }, title = { Text(bill.name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MenuField(stringResource(R.string.reminder_before), lead, (0..30).toList(), {
                if (it == 0) stringResource(R.string.reminder_off) else androidx.compose.ui.res.pluralStringResource(
                    R.plurals.reminder_days, it, it)
            }, { lead = it })
            SettingSwitch(stringResource(R.string.reminder_due), due) { due = it }
            SettingSwitch(stringResource(R.string.reminder_overdue), overdue) { overdue = it }
            if (bill.cadence != Cadence.ONCE) SettingSwitch(stringResource(R.string.reminder_increase), increase) { increase = it }
        }
    }, dismissButton = { TextButton(close, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { Button({
            val rule = ReminderRule(bill.series, lead.takeIf { it > 0 }, due, overdue, increase && bill.cadence != Cadence.ONCE)
            busy = true
            save({ saveReminder(rule) }, close) { busy = false }
        }, enabled = !busy) { Text(stringResource(R.string.save)) } })
}
