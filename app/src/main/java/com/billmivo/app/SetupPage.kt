package com.billmivo.app

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun SetupPage(currency: String, chooseCurrency: (String) -> Unit, add: () -> Unit,
                       reminders: () -> Unit, household: () -> Unit, privacy: () -> Unit, finish: () -> Unit) {
    val locale = displayLocale()
    val currencies = remember(locale, currency) {
        (listOf(defaultCurrency(locale), currency, "GBP", "USD", "EUR", "AUD", "BRL", "CAD", "CNY", "EGP", "HKD",
            "IDR", "INR", "JPY", "KRW", "MXN", "NZD", "PLN", "SAR", "SEK", "TRY", "TWD", "AED", "ZAR")).distinct()
    }
    PageLayout {
        Panel {
            Icon(painterResource(R.drawable.billmivo_mark), null, Modifier.size(52.dp), tint = Color.Unspecified)
            SectionTitle(stringResource(R.string.setup_title), stringResource(R.string.setup_detail))
            LanguageMenu()
            MenuField(stringResource(R.string.setup_currency), currency, currencies, { it }, chooseCurrency)
            Button(add, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add_bill)) }
        }
        Panel {
            SectionTitle(stringResource(R.string.setup_optional), stringResource(R.string.setup_optional_detail))
            OutlinedButton(reminders) { Text(stringResource(R.string.reminders)) }
            OutlinedButton(household) { Text(stringResource(R.string.household)) }
            OutlinedButton(privacy) { Text(stringResource(R.string.privacy_title)) }
        }
        OutlinedButton(finish, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.setup_finish)) }
    }
}
