package com.billmivo.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.util.UUID

@Composable
internal fun categoryName(id: String, records: Records): String =
    records.customCategories.find { it.id == id }?.name ?: stringResource(categories[id] ?: R.string.other)

@Composable
internal fun CategoryMenu(selected: String, records: Records, onSelect: (String) -> Unit) {
    MenuField(stringResource(R.string.category), selected, categories.keys.toList() + records.customCategories.map { it.id },
        { categoryName(it, records) }, onSelect)
}

@Composable
internal fun CategoriesPage(records: Records, save: SaveBillAction, back: () -> Unit) {
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    PageLayout {
        TextButton(back) { Text(stringResource(R.string.settings)) }
        SectionTitle(stringResource(R.string.custom_categories))
        Panel(Modifier.fillMaxWidth()) {
            if (records.customCategories.isEmpty()) Text(stringResource(R.string.custom_categories_empty), color = palette.muted)
            records.customCategories.forEach { category ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ name = category.name; error = false; editingId = category.id }, Modifier.weight(1f)) {
                        Text(category.name)
                    }
                    TextButton({ deletingId = category.id }) { Text(stringResource(R.string.delete)) }
                }
            }
            Button({ name = ""; error = false; editingId = "custom:" + UUID.randomUUID() }) {
                Text(stringResource(R.string.category_add))
            }
        }
    }
    editingId?.let { id ->
        val existing = records.customCategories.any { it.id == id }
        val duplicate = records.customCategories.any { it.id != id && it.name.equals(name.trim(), ignoreCase = true) }
        AlertDialog(onDismissRequest = { if (!busy) editingId = null },
            title = { Text(stringResource(if (existing) R.string.category_edit else R.string.category_add)) },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text(stringResource(R.string.name)) },
                        enabled = !busy, singleLine = true, isError = duplicate)
                    if (duplicate || error) Text(stringResource(if (duplicate) R.string.category_duplicate else R.string.action_error),
                        color = palette.red)
                }
            },
            confirmButton = { Button({
                busy = true
                save({ saveCategory(CustomCategory(id, name)) }, { busy = false; editingId = null }) { busy = false; error = true }
            }, enabled = name.isNotBlank() && !duplicate && !busy) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton({ editingId = null }, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
    }
    deletingId?.let { id ->
        ConfirmRecordAction(R.string.delete, R.string.category_delete_note, { deletingId = null }) {
            deletingId = null
            save({ deleteCategory(id) }, {}, {})
        }
    }
}
