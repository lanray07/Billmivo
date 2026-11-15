package com.billmivo.app

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

@Composable
fun PremiumPage(store: PremiumAccess, state: PremiumState, back: () -> Unit) {
    LaunchedEffect(store) { if (store.available && state.price == null && !state.busy) store.refresh() }
    PageLayout {
        TextButton(back) { Text(stringResource(R.string.settings)) }
        SectionTitle(stringResource(R.string.premium_title), stringResource(R.string.premium_one_time))
        Panel(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.premium_benefits))
            if (state.busy) {
                CircularProgressIndicator()
                Text(stringResource(R.string.premium_checking))
            }
            state.message?.let { Text(stringResource(it), color = palette.muted) }
            if (state.entitled && state.message != R.string.premium_owned) Text(stringResource(R.string.premium_owned))
            if (!state.entitled) state.price?.let { price ->
                Button(store::buy, enabled = store.available && !state.busy) {
                    Text(stringResource(R.string.premium_buy, price))
                }
            }
            TextButton(store::refresh, enabled = store.available && !state.busy) {
                Text(stringResource(R.string.premium_restore))
            }
            if (!store.available || state.price == null && !state.busy) {
                Text(stringResource(R.string.premium_unavailable), color = palette.muted)
            }
        }
    }
}
