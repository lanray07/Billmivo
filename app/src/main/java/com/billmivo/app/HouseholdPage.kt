package com.billmivo.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@Composable
fun HouseholdPage(records: Records, today: LocalDate, save: SaveBillAction, back: () -> Unit) {
    val locale = displayLocale()
    var showMember by rememberSaveable { mutableStateOf(false) }
    var memberId by rememberSaveable { mutableStateOf<String?>(null) }
    var series by rememberSaveable { mutableStateOf<String?>(null) }
    val active = records.schedules.filter { it.until == null }.sortedBy { it.name }
    val memberNames = records.members.associate { it.id to it.name }
    val upcoming = remember(records, today) {
        records.occurrences(today, today.plusDays(29)).filter { it.open }
    }
    val actualPayments = remember(records, today) {
        val currencies = records.schedules.associate { it.id to it.currency }
        records.payments.filter { it.payerId != null && it.date in today.minusDays(29)..today }
            .groupBy { it.payerId }.mapValues { (_, payments) ->
                payments.groupBy { currencies.getValue(it.key.scheduleId) }.mapValues { (_, entries) ->
                    entries.sumOf { it.amount }
                }
            }
    }
    PageLayout {
        TextButton(back) { Text(stringResource(R.string.settings)) }
        SectionTitle(stringResource(R.string.household), stringResource(R.string.household_local))
        Panel(Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.members))
            if (records.members.isEmpty()) Text(stringResource(R.string.members_empty), color = palette.muted)
            records.members.forEach { member ->
                OutlinedButton({ memberId = member.id; showMember = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (member.archived) stringResource(R.string.chart_value, member.name, stringResource(R.string.archived))
                        else member.name)
                }
                if (member.relationship.isNotBlank()) Text(member.relationship, color = palette.muted)
                actualPayments[member.id].orEmpty().toSortedMap().forEach { (currency, amount) ->
                    Text(stringResource(R.string.chart_value, stringResource(R.string.household_paid_recent),
                        money(amount, currency, locale)), color = palette.muted)
                }
            }
            Button({ memberId = null; showMember = true }) { Text(stringResource(R.string.member_add)) }
        }
        Panel(Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.household_bills), stringResource(R.string.sharing_changes))
            if (active.isEmpty()) Text(stringResource(R.string.empty_bills), color = palette.muted)
            active.forEach { bill ->
                val rule = records.sharingFor(bill.series, today)
                val label = rule?.responsibleId?.let(memberNames::get)
                OutlinedButton({ series = bill.series }, enabled = records.members.isNotEmpty(),
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(bill.name)
                }
                if (label != null) Text(stringResource(R.string.chart_value, stringResource(R.string.responsible), label))
                Text(stringResource(splitLabel(rule?.mode)), color = palette.muted)
            }
        }
        val plans = upcoming.mapNotNull { bill ->
            val rule = records.sharingFor(bill.schedule.series, bill.date)?.takeIf { it.mode != null }
                ?: return@mapNotNull null
            bill to rule.allocate(bill.amount, bill.schedule.currency)
        }
        Panel(Modifier.fillMaxWidth()) {
            SectionTitle(stringResource(R.string.shares_30), stringResource(R.string.shares_note))
            if (plans.isEmpty()) Text(stringResource(R.string.shares_empty), color = palette.muted)
            plans.flatMap { (bill, allocations) ->
                allocations.orEmpty().map { (member, amount) ->
                    Triple(member, bill.schedule.currency, amount)
                }
            }.groupBy { it.first to it.second }.entries
                .sortedWith(compareBy({ memberNames[it.key.first] }, { it.key.second }))
                .forEach { (key, values) ->
                    Text(stringResource(R.string.chart_value, memberNames[key.first].orEmpty(),
                        money(values.sumOf { it.third }, key.second, locale)))
                }
            plans.filter { it.second == null }.map { it.first.schedule.name }.distinct().forEach {
                Text(stringResource(R.string.split_review, it), color = palette.red)
            }
        }
    }
    if (showMember) MemberDialog(records.members.find { it.id == memberId }, { showMember = false }, save)
    active.find { it.series == series }?.let { bill ->
        SplitDialog(bill, records.members, records.sharingFor(bill.series, today), today, { series = null }, save)
    }
}

private fun splitLabel(mode: SplitMode?) = when (mode) {
    null -> R.string.split_none
    SplitMode.EQUAL -> R.string.split_equal
    SplitMode.PERCENT -> R.string.split_percent
    SplitMode.FIXED -> R.string.split_fixed
    SplitMode.WEIGHTS -> R.string.split_weights
}

@Composable
private fun MemberDialog(member: Member?, close: () -> Unit, save: SaveBillAction) {
    val id = rememberSaveable { member?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(member?.name.orEmpty()) }
    var relationship by rememberSaveable { mutableStateOf(member?.relationship.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(member?.notes.orEmpty()) }
    var archived by rememberSaveable { mutableStateOf(member?.archived ?: false) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog({ if (!busy) close() },
        title = { Text(stringResource(if (member == null) R.string.member_add else R.string.member_edit)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text(stringResource(R.string.member_name)) }, singleLine = true)
                OutlinedTextField(relationship, { relationship = it.take(60) },
                    label = { Text(stringResource(R.string.member_relationship)) }, singleLine = true)
                OutlinedTextField(notes, { notes = it.take(500) }, label = { Text(stringResource(R.string.member_notes)) })
                if (member != null) {
                    SettingSwitch(stringResource(R.string.member_archive), archived) { archived = it }
                    if (archived) Text(stringResource(R.string.member_archive_note), color = palette.muted)
                }
            }
        }, dismissButton = { TextButton(close, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { Button({
            busy = true
            save({ saveMember(Member(id, name, relationship, notes, archived)) }, close) { busy = false }
        }, enabled = name.isNotBlank() && !busy) { Text(stringResource(R.string.save)) } })
}

@Composable
private fun SplitDialog(bill: Schedule, members: List<Member>, existing: SharingRule?, today: LocalDate,
                        close: () -> Unit, save: SaveBillAction) {
    val locale = displayLocale()
    val names = members.associate { it.id to it.name }
    val enabledMembers = members.filter { !it.archived || it.id == existing?.responsibleId ||
        existing?.parts?.any { part -> part.memberId == it.id } == true }
    var mode by rememberSaveable { mutableStateOf(existing?.mode?.name.orEmpty()) }
    var owner by rememberSaveable { mutableStateOf(existing?.responsibleId.orEmpty()) }
    var selected by rememberSaveable { mutableStateOf(ArrayList(existing?.parts?.map { it.memberId }.orEmpty())) }
    val inputs = remember(existing) { mutableStateMapOf<String, String>().also { map ->
        existing?.parts?.forEach { map[it.memberId] = inputAmount(it.value, locale) }
    } }
    val type = mode.takeIf(String::isNotEmpty)?.let(SplitMode::valueOf)
    val parts = if (type == null) emptyList() else selected.map { id ->
        val value = when (type) {
            SplitMode.EQUAL -> BigDecimal.ONE
            SplitMode.FIXED -> parseAmount(inputs[id].orEmpty(), bill.currency, locale)
            else -> parseDecimal(inputs[id].orEmpty(), locale)
        }
        SplitPart(id, value ?: BigDecimal.ZERO)
    }
    val rule = SharingRule(bill.series, today, type, owner.takeIf(String::isNotBlank), parts)
    val allocation = rule.allocate(bill.amount, bill.currency)
    var busy by remember { mutableStateOf(false) }
    AlertDialog({ if (!busy) close() }, title = { Text(bill.name) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.sharing_changes), color = palette.muted)
            Text(stringResource(R.string.chart_value, stringResource(R.string.amount), money(bill.amount, bill.currency, locale)))
            MenuField(stringResource(R.string.responsible), owner, listOf("") + enabledMembers.map { it.id },
                { names[it] ?: stringResource(R.string.unassigned) }, { owner = it })
            val modes = listOf("") + SplitMode.entries.map { it.name }
            MenuField(stringResource(R.string.split_type), mode, modes,
                { stringResource(splitLabel(it.takeIf(String::isNotBlank)?.let(SplitMode::valueOf))) }, { mode = it })
            if (type != null) {
                val help = when (type) {
                    SplitMode.EQUAL -> R.string.split_equal_help
                    SplitMode.PERCENT -> R.string.split_percent_help
                    SplitMode.FIXED -> R.string.split_fixed_help
                    SplitMode.WEIGHTS -> R.string.split_weight_help
                }
                Text(stringResource(help), color = palette.muted)
                enabledMembers.forEach {
                    SettingSwitch(it.name, it.id in selected) { checked ->
                        selected = ArrayList(if (checked) selected + it.id else selected - it.id)
                    }
                    if (it.id in selected && type != SplitMode.EQUAL) OutlinedTextField(
                        inputs[it.id].orEmpty(), { value -> inputs[it.id] = value.take(24) },
                        label = { Text(stringResource(if (type == SplitMode.PERCENT) R.string.split_percent_input
                            else if (type == SplitMode.WEIGHTS) R.string.split_weight_input else R.string.amount)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true
                    )
                }
                allocation.orEmpty().forEach { (id, amount) ->
                    Text(stringResource(R.string.chart_value, names[id].orEmpty(), money(amount, bill.currency, locale)))
                }
                if (allocation == null) Text(stringResource(R.string.split_invalid), color = palette.red)
            }
        }
    }, dismissButton = { TextButton(close, enabled = !busy) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { Button({
            busy = true
            save({ saveSharing(rule, today) }, close) { busy = false }
        }, enabled = allocation != null && !busy) { Text(stringResource(R.string.save)) } })
}

@Composable
fun BillAllocation(item: Occurrence, rule: SharingRule?, members: List<Member>, payments: List<Payment>) {
    val locale = displayLocale()
    val names = members.associate { it.id to it.name }
    rule?.responsibleId?.let(names::get)?.let {
        Text(stringResource(R.string.chart_value, stringResource(R.string.responsible), it))
    }
    if (rule?.mode != null) {
        val allocation = rule.allocate(item.amount, item.schedule.currency)
        Text(stringResource(R.string.shares_bill), style = MaterialTheme.typography.titleMedium)
        if (allocation == null) Text(stringResource(R.string.split_review, item.schedule.name), color = palette.red)
        allocation.orEmpty().forEach { (id, amount) ->
            Text(stringResource(R.string.chart_value, names[id].orEmpty(), money(amount, item.schedule.currency, locale)))
        }
    }
    payments.filter { it.payerId != null }.groupBy { it.payerId }.forEach { (id, entries) ->
        names[id]?.let {
            Text(stringResource(R.string.household_paid_by, it,
                money(entries.sumOf { entry -> entry.amount }, item.schedule.currency, locale)))
        }
    }
}
