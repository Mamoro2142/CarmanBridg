package com.mamoro.karman

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LedgerMathTest {
    private val rub=Account(1,"Рубли","RUB",20_000_00,20_000_00,1_000_000)
    private val usd=Account(2,"Доллары","USD",0,0,90_000_000)
    @Test fun parseMoneyIgnoresGroupedSpaces() {
        assertEquals(1234567L,Money.parse("12 345,67"))
        assertEquals(1L,Money.parse("0.01"))
    }
    @Test fun baseBalanceDoesNotChangeOnFxDisplay() {
        val s=Snapshot(accounts=listOf(rub),settings=Settings(displayCurrency="USD",usdPerBaseMicros=11111))
        assertEquals(2_000_000L,LedgerMath.cashBase(s))
        assertEquals(2_000_000L,LedgerMath.cashBase(s.copy(settings=s.settings.copy(usdPerBaseMicros=12300))))
    }
    @Test fun transferCannotDuplicateBaseCash() {
        val tx=Entry(1,"2026-11-10",Kind.TRANSFER,1,9000,9000,"Перевод",toAccount=2,toAmountMinor=100)
        val s=Snapshot(accounts=listOf(rub,usd),entries=listOf(tx))
        assertEquals(2_000_000L,LedgerMath.cashBase(s))
        assertEquals(100L,LedgerMath.accountLocal(usd,s.entries))
    }
    @Test fun independentBondLotsKeepActualInvestment() {
        val bond=Bond(1,"Номинал 1000",100000,100000,1000,1)
        val s=Snapshot(bonds=listOf(bond),lots=listOf(Lot(1,1,1,95000,"2026-11-10"),Lot(2,1,5,375000,"2026-11-10")))
        assertEquals(6L,LedgerMath.bondQuantity(s,1))
        assertEquals(470000L,LedgerMath.bondInvested(s,1))
        assertEquals(600000L,LedgerMath.bondMarketValue(s,bond))
    }
    @Test fun investmentCanBeIncludedOnlyOnceInTotal() {
        val bond=Bond(1,"Бумага",100000,100000,800,1)
        val buy=Entry(1,"2026-11-10",Kind.BUY,1,95000,95000,"Инвестиции")
        val s=Snapshot(accounts=listOf(rub),entries=listOf(buy),bonds=listOf(bond),
            lots=listOf(Lot(1,1,1,95000,"2026-11-10")),settings=Settings(includeInvestments=true))
        assertEquals(2005000L,LedgerMath.totalBase(s))
        assertEquals(1905000L,LedgerMath.cashBase(s))
    }
    @Test fun fifoCostPreservesLastRemainder() {
        assertEquals(951L,Money.proportions(1901,1,2))
        assertEquals(950L,1901-Money.proportions(1901,1,2))
    }
    @Test fun paidAndSharedReservesDeductOnlyOnce() {
        val plan=Plan(1,"Интернет",10000,1,"2026-11-10","MONTHLY",false,true,10)
        val setting=Settings(extraReserveMinor=20000,sharedReserveMinor=0,coveredPlansMinor=5000)
        val snap=Snapshot(accounts=listOf(rub),goals=listOf(Goal(1,"Цель",50000,30000,true)),plans=listOf(plan),settings=setting)
        assertEquals(1945000L,LedgerMath.availableBase(snap,LocalDate.of(2026,11,10)))
    }
    @Test fun monthlyRecurrenceDoesNotDriftAfterFebruary() {
        val jan=LocalDate.parse("2026-01-31")
        val feb=LedgerMath.nextDate(jan,"MONTHLY",31)
        val mar=LedgerMath.nextDate(feb,"MONTHLY",31)
        assertEquals(LocalDate.parse("2026-02-28"),feb)
        assertEquals(LocalDate.parse("2026-03-31"),mar)
    }
}
