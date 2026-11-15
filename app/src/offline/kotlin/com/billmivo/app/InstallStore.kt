package com.billmivo.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

@Suppress("UNUSED_PARAMETER")
fun installStore(context: Context): PremiumAccess = object : PremiumAccess {
    override val available = false
    override val state = MutableStateFlow(PremiumState())
    override fun initialize() = Unit
    override fun refresh() = Unit
    override fun buy() = Unit
    override fun restore() = Unit
}
