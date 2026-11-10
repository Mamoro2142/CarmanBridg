package com.mamoro.karman

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class LedgerDbTest {
    private lateinit var db:LedgerDb
    @Before fun setup() {
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        ctx.deleteDatabase("karman_v1.db")
        db=LedgerDb(ctx)
    }
    @After fun teardown() {db.close()}
    @Test fun entriesPersistAndSourceValuesAreImmutable() {
        val a=db.snapshot().accounts.first()
        db.entry(Kind.INCOME,a.id,100000,"Зарплата")
        assertEquals(100000L,LedgerMath.cashBase(db.snapshot()))
    }
    @Test fun duplicateCouponCannotBePostedTwice() {
        val id=db.snapshot().accounts.first().id
        val bond=db.bond("Облигация",id,100000,96000,1000)
        val coupon=db.coupon(bond,LocalDate.parse("2026-01-01"),2000)
        db.postCoupon(coupon,LocalDate.parse("2026-01-01"))
        assertEquals(2000L,LedgerMath.cashBase(db.snapshot()))
        assertThrows(IllegalArgumentException::class.java) {db.postCoupon(coupon,LocalDate.parse("2026-01-01"))}
    }
    @Test fun automaticScheduledPaymentPostsExactlyOnce() {
        val acc=db.snapshot().accounts.first().id
        db.plan("Услуга",500,acc,LocalDate.parse("2026-10-05"),"MONTHLY",true,true)
        db.processAutoPlans(LocalDate.parse("2026-10-05"))
        db.processAutoPlans(LocalDate.parse("2026-10-05"))
        assertEquals(1,db.snapshot().entries.size)
        assertEquals(-500L,LedgerMath.cashBase(db.snapshot()))
    }
    @Test fun backupExportAndImportRoundTrip() {
        val a=db.snapshot().accounts.first().id
        db.entry(Kind.EXPENSE,a,500,"Продукты")
        val json=db.exportJson()
        db.importJson(json)
        assertEquals(1,db.snapshot().entries.size)
        assertEquals(-500L,LedgerMath.cashBase(db.snapshot()))
    }
    @Test fun bondSaleUsesFifoAndCreditsWallet() {
        val a=db.snapshot().accounts.first().id
        val b=db.bond("Бонд",a,100000,100000,800)
        db.buyBond(b,1,95000)
        db.buyBond(b,5,75000)
        assertEquals(-470000L,LedgerMath.cashBase(db.snapshot()))
        val gain=db.sellBond(b,1,110000)
        assertEquals(15000L,gain)
        assertEquals(375000L,LedgerMath.bondInvested(db.snapshot(),b))
    }
}
