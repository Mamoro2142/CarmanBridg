package com.mamoro.karman

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ActionDialog(title:String,onClose:()->Unit,submit:()->Unit,submitLabel:String="Сохранить",
    content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(onDismissRequest=onClose,title={Text(title)},
        text={Column(Modifier.fillMaxWidth().heightIn(max=460.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(10.dp),content=content)},
        confirmButton={TextButton(onClick=submit) {Text(submitLabel)}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}})
}
@Composable
fun TinySection(title:String,action:String,onAction:()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
        TextButton(onClick=onAction) {Text(action)}
    }
}
@Composable
fun PlansTab(s:Snapshot,vm:KarmanViewModel) {
    var dialog by remember {mutableStateOf("")}
    var selectedGoal by remember {mutableStateOf<Goal?>(null)}
    var name by remember {mutableStateOf("")}
    var amount by remember {mutableStateOf("")}
    var goalCategory by remember {mutableStateOf("Продукты")}
    var planDay by remember {mutableStateOf(LocalDate.now().plusMonths(1).toString())}
    var freq by remember {mutableStateOf("MONTHLY")}
    var auto by remember {mutableStateOf(false)}
    var accountId by remember(s.accounts) {mutableLongStateOf(s.accounts.firstOrNull()?.id?:0)}
    var error by remember {mutableStateOf<String?>(null)}
    fun start(type:String) {dialog=type;name="";amount="";error=null}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=18.dp,vertical=8.dp),
        verticalArrangement=Arrangement.spacedBy(11.dp)) {
        item { TitleBar("План","Бюджеты, цели, предстоящие платежи") }
        item {
            KCard {
                KTitle("Доступно после резервирования")
                Spacer(Modifier.height(8.dp))
                MoneyLine(LedgerMath.availableBase(s),"RUB",large=true)
            }
        }
        item {TinySection("Бюджеты на месяц","+ Лимит"){start("budget")} }
        if(s.budgets.isEmpty()) item {EmptyText("Задайте лимит для любой категории расходов")}
        items(s.budgets,key={it.id}) { b->
            val spent=s.entries.asSequence().filter{it.kind==Kind.EXPENSE&&it.category==b.category&&
                YearMonth.from(LocalDate.parse(it.day))==YearMonth.now()}.sumOf{it.baseMinor}
            KCard {
                Row {Text(b.category,Modifier.weight(1f));Text(Money.text(spent,"RUB"))}
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress={(spent.toFloat()/b.monthlyLimitMinor.coerceAtLeast(1)).coerceIn(0f,1f)},
                    modifier=Modifier.fillMaxWidth(),color=if(spent>b.monthlyLimitMinor)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                Text("Лимит: ${Money.text(b.monthlyLimitMinor,"RUB")}",
                    color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
            }
        }
        item {TinySection("Цели","+ Цель"){start("goal")} }
        if(s.goals.isEmpty()) item {EmptyText("Добавьте цель — накопления можно учитывать как резерв")}
        items(s.goals,key={it.id}) {g->
            KCard {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(g.title,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f))
                    TextButton(onClick={selectedGoal=g;amount=moneyValue(g.savedMinor);dialog="saved"}) {Text("Изменить")}
                }
                MoneyLine(g.savedMinor,"RUB")
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(progress={(g.savedMinor.toFloat()/g.targetMinor).coerceIn(0f,1f)},
                    modifier=Modifier.fillMaxWidth())
                Text("Цель: ${Money.text(g.targetMinor,"RUB")}",color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {TinySection("Предстоящие платежи","+ Платёж"){start("plan")} }
        if(s.plans.isEmpty()) item {EmptyText("Сюда можно добавить подписки и регулярные платежи")}
        items(s.plans,key={it.id}) {p->
            val account=s.accounts.firstOrNull{it.id==p.accountId}
            KCard {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.title,fontWeight=FontWeight.Medium)
                        Text("${p.due} · ${if(p.frequency=="MONTHLY")"ежемесячно" else if(p.frequency=="WEEKLY")"еженедельно" else "один раз"}",
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MoneyLine(p.amountMinor,account?.currency?:"RUB")
                }
                val paidOnce=p.frequency=="ONCE" && s.entries.any{it.sourceKey=="plan:${p.id}:${p.due}"}
                if(paidOnce) Text("Оплачено",color=MaterialTheme.colorScheme.onSurfaceVariant)
                else if(!LocalDate.parse(p.due).isAfter(LocalDate.now())) TextButton(onClick={vm.postPlan(p.id)}) {
                    Text(if(p.auto)"Провести сейчас" else "Отметить оплату")
                }
            }
        }
    }
    if(dialog.isNotBlank()) {
        ActionDialog(title=when(dialog){"budget"->"Бюджет категории";"goal"->"Новая цель";"saved"->"Накоплено";else->"Предстоящий платёж"},
            onClose={dialog=""},submit={
                try {
                    val sum=Money.parse(amount)
                    require(sum>=0 && (dialog=="saved" || sum>0))
                    when(dialog) {
                        "budget" -> vm.setBudget(goalCategory,sum)
                        "goal" -> vm.addGoal(name,sum)
                        "saved" -> vm.saveGoal(requireNotNull(selectedGoal).id,sum)
                        "plan" -> vm.addPlan(name,sum,accountId,LocalDate.parse(planDay),freq,auto)
                    }
                    dialog=""
                } catch(e:Exception){error=e.message?:"Проверьте данные"}
            }) {
            if(dialog=="budget") {
                Text("Категория")
                SimpleText("Название категории",goalCategory,{goalCategory=it})
            }
            if(dialog in listOf("goal","plan")) SimpleText("Название",name,{name=it})
            AmountInput("Сумма, ₽",amount,{amount=it})
            if(dialog=="plan") {
                Text("Счёт списания")
                AccountChoices(s.accounts,accountId,{accountId=it})
                SimpleText("Дата платежа (ГГГГ-ММ-ДД)",planDay,{planDay=it})
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("ONCE" to "Разово","MONTHLY" to "Месяц","WEEKLY" to "Неделя").forEach{(key,title)->
                        FilterChip(selected=freq==key,onClick={freq=key},label={Text(title)})
                    }
                }
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=auto,onCheckedChange={auto=it});Text("Автоматически проводить при открытии")
                }
            }
            if(error!=null) Text(error.orEmpty(),color=MaterialTheme.colorScheme.error)
        }
    }
}
fun moneyValue(minor:Long):String = java.math.BigDecimal.valueOf(minor,2).toPlainString()
