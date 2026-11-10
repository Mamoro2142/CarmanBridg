package com.mamoro.karman

/** Amounts are stored as signed, integer minor units (kopecks/cents). Never use Double for money. */
data class Account(
    val id: Long, val name: String, val currency: String,
    val openingMinor: Long, val openingBaseMinor: Long,
    val rateMicros: Long, val included: Boolean = true
)

data class Entry(
    val id: Long, val day: String, val kind: String, val fromAccount: Long,
    val amountMinor: Long, val baseMinor: Long, val category: String,
    val note: String = "", val toAccount: Long? = null,
    val toAmountMinor: Long? = null, val sourceKey: String? = null
)

data class Goal(val id: Long, val title: String, val targetMinor: Long, val savedMinor: Long, val reserve: Boolean)
data class Plan(val id: Long, val title: String, val amountMinor: Long, val accountId: Long,
    val due: String, val frequency: String, val auto: Boolean, val reserve: Boolean, val anchorDay: Int)
data class Budget(val id: Long, val category: String, val monthlyLimitMinor: Long)
data class Template(val id: Long, val title: String, val kind: String, val category: String,
    val accountId: Long, val amountMinor: Long)
data class Bond(val id: Long, val name: String, val nominalMinor: Long, val quoteMinor: Long,
    val couponBps: Int, val accountId: Long)
data class Lot(val id: Long, val bondId: Long, val quantity: Long, val costMinor: Long, val day: String)
data class Coupon(val id: Long, val bondId: Long, val day: String, val amountMinor: Long, val posted: Boolean)
data class Settings(
    val baseCurrency: String = "RUB", val displayCurrency: String = "RUB",
    val eurPerBaseMicros: Long = 10000L, val usdPerBaseMicros: Long = 11000L,
    val bynPerBaseMicros: Long = 35000L, val extraReserveMinor: Long = 0,
    val sharedReserveMinor: Long = 0, val coveredPlansMinor: Long = 0,
    val includeInvestments: Boolean = false, val seasonal: Boolean = false
)
data class Snapshot(
    val accounts: List<Account> = emptyList(), val entries: List<Entry> = emptyList(),
    val goals: List<Goal> = emptyList(), val plans: List<Plan> = emptyList(),
    val templates: List<Template> = emptyList(), val budgets: List<Budget> = emptyList(), val bonds: List<Bond> = emptyList(),
    val lots: List<Lot> = emptyList(), val coupons: List<Coupon> = emptyList(),
    val settings: Settings = Settings()
)
object Kind {
    const val EXPENSE = "EXPENSE"
    const val INCOME = "INCOME"
    const val TRANSFER = "TRANSFER"
    const val BUY = "BOND_BUY"
    const val SELL = "BOND_SELL"
    const val COUPON = "COUPON"
}
