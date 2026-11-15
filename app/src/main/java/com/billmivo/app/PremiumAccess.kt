package com.billmivo.app

import kotlinx.coroutines.flow.StateFlow

data class PremiumState(
    val price: String? = null, val entitled: Boolean = false,
    val busy: Boolean = false, val message: Int? = null
)

interface PremiumAccess {
    val available: Boolean
    val state: StateFlow<PremiumState>
    fun initialize()
    fun refresh()
    fun buy()
    fun restore()
}

class RecurringBillLimit : IllegalStateException()
