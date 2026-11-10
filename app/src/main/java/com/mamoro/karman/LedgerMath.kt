package com.mamoro.karman

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

object Money {
    private val million = BigDecimal(1_000_000)
    const val MAX_MINOR: Long = 90_000_000_000_000L
    fun parse(input: String): Long {
        val normalized = input.replace("\u00a0", "").replace("\u202f", "")
            .replace(" ", "").replace(',', '.').replace("₽", "").replace("$", "").replace("€", "")
        require(normalized.isNotEmpty()) { "Введите сумму" }
        val result = BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2).longValueExact()
        require(result in -MAX_MINOR..MAX_MINOR) { "Сумма слишком велика" }
        return result
    }
    fun text(minor: Long, currency: String): String {
        val n = NumberFormat.getNumberInstance(Locale.forLanguageTag("ru-RU"))
        n.minimumFractionDigits = 2; n.maximumFractionDigits = 2
        return n.format(BigDecimal.valueOf(minor, 2)) + " " + when(currency) {
            "RUB" -> "₽"; "USD" -> "$"; "EUR" -> "€"; "BYN" -> "Br"; else -> currency
        }
    }
    /** Locked base value of an entry. rateMicros = base minor per local minor * 1e6. */
    fun toBase(localMinor: Long, rateMicros: Long): Long {
        require(rateMicros > 0)
        return BigDecimal.valueOf(localMinor).multiply(BigDecimal.valueOf(rateMicros))
            .divide(million, 0, RoundingMode.HALF_UP).longValueExact()
    }
    fun fromBase(baseMinor: Long, localPerBaseMicros: Long): Long =
        toBase(baseMinor, localPerBaseMicros)
    fun nativeFromBase(baseMinor: Long, basePerNativeMicros: Long): Long {
        require(basePerNativeMicros > 0)
        return BigDecimal.valueOf(baseMinor).multiply(million)
            .divide(BigDecimal.valueOf(basePerNativeMicros), 0, RoundingMode.HALF_UP).longValueExact()
    }
    fun proportions(costMinor: Long, partQty: Long, totalQty: Long): Long {
        require(totalQty > 0 && partQty in 0..totalQty)
        if(partQty == totalQty) return costMinor
        return BigDecimal.valueOf(costMinor).multiply(BigDecimal.valueOf(partQty))
            .divide(BigDecimal.valueOf(totalQty), 0, RoundingMode.HALF_UP).longValueExact()
    }
}

object LedgerMath {
    fun accountLocal(account: Account, rows: List<Entry>): Long {
        var sum = account.openingMinor
        rows.forEach { e ->
            if(e.fromAccount == account.id) sum = Math.addExact(sum, when(e.kind) {
                Kind.INCOME, Kind.COUPON, Kind.SELL -> e.amountMinor
                else -> -e.amountMinor
            })
            if(e.kind == Kind.TRANSFER && e.toAccount == account.id)
                sum = Math.addExact(sum, e.toAmountMinor ?: 0)
        }
        return sum
    }
    /** Authoritative historical base value: changing display currency or rates never mutates it. */
    fun accountBase(account: Account, rows: List<Entry>): Long {
        var sum = account.openingBaseMinor
        rows.forEach { e ->
            if(e.fromAccount == account.id) sum = Math.addExact(sum, when(e.kind) {
                Kind.INCOME, Kind.COUPON, Kind.SELL -> e.baseMinor
                else -> -e.baseMinor
            })
            if(e.kind == Kind.TRANSFER && e.toAccount == account.id) sum = Math.addExact(sum, e.baseMinor)
        }
        return sum
    }
    fun cashBase(s: Snapshot): Long = s.accounts.filter { it.included }
        .sumOf { accountBase(it, s.entries) }
    /** Total wealth can include investments only once, as a separate portfolio valuation. */
    fun totalBase(s:Snapshot):Long = Math.addExact(cashBase(s), if(s.settings.includeInvestments) portfolioValue(s) else 0L)
    fun monthSpent(s: Snapshot, month: YearMonth): Long = s.entries.asSequence()
        .filter { it.kind == Kind.EXPENSE && YearMonth.from(LocalDate.parse(it.day)) == month }
        .sumOf { it.baseMinor }
    fun monthIncome(s: Snapshot, month: YearMonth): Long = s.entries.asSequence()
        .filter { it.kind == Kind.INCOME || it.kind == Kind.COUPON }
        .filter { YearMonth.from(LocalDate.parse(it.day)) == month }.sumOf { it.baseMinor }
    fun nextDate(due: LocalDate, frequency: String, anchorDay: Int): LocalDate = when(frequency) {
        "WEEKLY" -> due.plusWeeks(1)
        "MONTHLY" -> YearMonth.from(due).plusMonths(1).let { it.atDay(anchorDay.coerceIn(1, it.lengthOfMonth())) }
        else -> due
    }
    fun bondQuantity(s: Snapshot, bondId: Long): Long = s.lots.filter { it.bondId == bondId }.sumOf { it.quantity }
    fun bondInvested(s: Snapshot, bondId: Long): Long = s.lots.filter { it.bondId == bondId }.sumOf { it.costMinor }
    fun bondMarketValue(s: Snapshot, bond: Bond): Long = Math.multiplyExact(bondQuantity(s,bond.id), bond.quoteMinor)
    fun portfolioValue(s: Snapshot): Long = s.bonds.sumOf { bondMarketValue(s,it) }
    fun availableBase(s: Snapshot, today: LocalDate = LocalDate.now()): Long {
        val goalReserve = s.goals.filter { it.reserve }.sumOf { it.savedMinor }
        val baseReserve = Math.addExact(goalReserve, s.settings.extraReserveMinor)
        val overlap = s.settings.sharedReserveMinor.coerceIn(0, baseReserve)
        val distinctReserve = baseReserve - overlap
        val includedIds=s.accounts.filter{it.included}.map{it.id}.toSet()
        val due = s.plans.filter { it.reserve && it.accountId in includedIds && !LocalDate.parse(it.due).isAfter(today.plusMonths(1)) }
            .sumOf { plan ->
                s.accounts.firstOrNull { it.id == plan.accountId }
                    ?.let { Money.toBase(plan.amountMinor, it.rateMicros) } ?: 0L
            }
        val alreadyCovered = s.settings.coveredPlansMinor.coerceIn(0, minOf(due, distinctReserve))
        return cashBase(s) - distinctReserve - (due - alreadyCovered)
    }
    fun rateForDisplay(s: Settings, currency: String): Long = when(currency) {
        s.baseCurrency -> 1_000_000L
        "USD" -> s.usdPerBaseMicros
        "EUR" -> s.eurPerBaseMicros
        "BYN" -> s.bynPerBaseMicros
        else -> 1_000_000L
    }
    fun shownBalance(s: Snapshot): Long = Money.fromBase(totalBase(s), rateForDisplay(s.settings, s.settings.displayCurrency))
}
