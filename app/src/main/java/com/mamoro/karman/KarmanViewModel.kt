package com.mamoro.karman

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

class KarmanViewModel(app: Application): AndroidViewModel(app) {
    private val database=LedgerDb(app)
    private val writeMutex=Mutex()
    private val _snapshot=MutableStateFlow(database.snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot
    private val _issue=MutableStateFlow<String?>(null)
    val issue:StateFlow<String?> = _issue
    init { execute { database.processAutoPlans() } }
    fun execute(block:()->Unit) { viewModelScope.launch {
        withContext(Dispatchers.IO) { writeMutex.withLock {
            try {
                block()
                val fresh=database.snapshot()
                withContext(Dispatchers.Main) { _snapshot.value=fresh;_issue.value=null }
            } catch(ex:Exception) {
                withContext(Dispatchers.Main) { _issue.value=ex.message?:"Ошибка сохранения" }
            }
        } }
    } }
    fun refreshScheduled()=execute{database.processAutoPlans()}
    fun dismissIssue(){ _issue.value=null }
    fun income(kind:String,accountId:Long,amount:Long,category:String,note:String,day:LocalDate=LocalDate.now()) =
        execute { database.entry(kind,accountId,amount,category,note,day) }
    fun transfer(from:Long,to:Long,amount:Long,toAmount:Long?,date:LocalDate=LocalDate.now()) =
        execute { database.entry(Kind.TRANSFER,from,amount,"Перевод",date=date,toId=to,toAmount=toAmount) }
    fun editEntryDetails(id:Long,category:String,note:String,day:LocalDate)=execute{database.editEntryDetails(id,category,note,day)}
    fun deleteEntry(id:Long) = execute { database.deleteEntry(id) }
    fun createAccount(name:String,currency:String,opening:Long,rate:Long) = execute { database.account(name,currency,opening,rate) }
    fun changeAccountRate(id:Long,rate:Long)=execute{database.updateAccountRate(id,rate)}
    fun toggleAccount(id:Long,include:Boolean)=execute{database.includeAccount(id,include)}
    fun addGoal(title:String,goal:Long)=execute{database.goal(title,goal,true)}
    fun saveGoal(id:Long,value:Long)=execute{database.saved(id,value)}
    fun addPlan(name:String,amount:Long,accountId:Long,date:LocalDate,freq:String,auto:Boolean)=
        execute{database.plan(name,amount,accountId,date,freq,auto,true)}
    fun postPlan(id:Long)=execute{database.postPlan(id)}
    fun setBudget(cat:String,limit:Long)=execute{database.budget(cat,limit)}
    fun addTemplate(name:String,kind:String,cat:String,accountId:Long,amount:Long)=
        execute{database.template(name,kind,cat,accountId,amount)}
    fun addBond(name:String,accountId:Long,nominal:Long,quote:Long,yield:Int)=
        execute{database.bond(name,accountId,nominal,quote,yield)}
    fun buyBond(id:Long,qty:Long,price:Long,fee:Long)=execute{database.buyBond(id,qty,price,fee)}
    fun sellBond(id:Long,qty:Long,price:Long,fee:Long)=execute{database.sellBond(id,qty,price,fee)}
    fun quote(id:Long,quote:Long)=execute{database.updateQuote(id,quote)}
    fun addCoupon(bondId:Long,date:LocalDate,amount:Long)=execute{database.coupon(bondId,date,amount)}
    fun postCoupon(id:Long)=execute{database.postCoupon(id)}
    fun pref(key:String,value:String)=execute{database.setPreference(key,value)}
    suspend fun export():String=withContext(Dispatchers.IO){database.exportJson()}
    fun import(content:String)=execute{database.importJson(content)}
    override fun onCleared() { database.close();super.onCleared() }
}
