package com.mamoro.karman

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode

private fun pricePerCurrency(perBase:Long):String = BigDecimal(1000000)
    .divide(BigDecimal.valueOf(perBase),2,RoundingMode.HALF_UP).toPlainString()
private fun currencyToBaseMicro(price:String):Long {
    val numeric=BigDecimal(price.replace(" ","").replace(',','.'))
    require(numeric>BigDecimal.ZERO && numeric<=BigDecimal(100000))
    return BigDecimal.ONE.divide(numeric,12,RoundingMode.HALF_UP)
        .multiply(BigDecimal(1000000)).setScale(0,RoundingMode.HALF_UP).longValueExact().also{require(it>0)}
}
@Composable
fun SettingsTab(s:Snapshot,vm:KarmanViewModel) {
    var dialog by remember {mutableStateOf("")}
    var amount by remember {mutableStateOf("")}
    var name by remember {mutableStateOf("")}
    var category by remember {mutableStateOf("Продукты")}
    var accountId by remember(s.accounts) {mutableLongStateOf(s.accounts.firstOrNull()?.id?:0)}
    var kind by remember {mutableStateOf(Kind.EXPENSE)}
    var rateCode by remember {mutableStateOf("USD")}
    var error by remember {mutableStateOf<String?>(null)}
    var feedback by remember {mutableStateOf("")}
    var importCandidate by remember {mutableStateOf<String?>(null)}
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val exportLauncher=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri:Uri? ->
        if(uri!=null) scope.launch {
            try {
                val data=vm.export()
                withContext(Dispatchers.IO) {context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(data)}
                    ?:error("Не удалось создать файл")}
                feedback="Копия сохранена. Не публикуйте её: файл не зашифрован."
            }catch(e:Exception){feedback=e.message?:"Ошибка экспорта"}
        }
    }
    val importLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri:Uri? ->
        if(uri!=null) scope.launch {
            try {
                val data=withContext(Dispatchers.IO) {context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {it.readText()}
                    ?:error("Не удалось прочитать файл")}
                importCandidate=data;feedback="Копия прочитана. Подтвердите замену данных."
            }catch(e:Exception){feedback=e.message?:"Ошибка импорта"}
        }
    }
    fun start(d:String,value:String="") {dialog=d;amount=value;error=null}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=18.dp,vertical=8.dp,bottom=30.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {TitleBar("Ещё","Настройки и данные")}
        item {
            KCard {
                Text("База расчёта — российский рубль",fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(7.dp))
                Text("Базовые суммы зафиксированы при внесении операций. Курсы нужны только для новых операций и пересчёта отображения.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                listOf("USD","EUR","BYN").forEach { cur ->
                    val perBase=LedgerMath.rateForDisplay(s.settings,cur)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("1 $cur = ${pricePerCurrency(perBase)} ₽",modifier=Modifier.weight(1f))
                        TextButton(onClick={rateCode=cur;start("fx",pricePerCurrency(perBase))}) {Text("Курс")}
                    }
                }
            }
        }
        item {
            KCard {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Инвестиции в общем балансе",fontWeight=FontWeight.Medium)
                        Text("Добавлять рыночную оценку портфеля один раз; доступные деньги считать отдельно.",
                            color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked=s.settings.includeInvestments,onCheckedChange={vm.pref("includeInvestments",if(it)"1" else "0")})
                }
            }
        }
        item {
            KCard {
                Text("Расчёт доступного остатка",fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                val labels=listOf(
                    Triple("extraReserveMinor","Дополнительный резерв",s.settings.extraReserveMinor),
                    Triple("sharedReserveMinor","Общие деньги целей и резервов",s.settings.sharedReserveMinor),
                    Triple("coveredPlansMinor","Платежи, покрытые резервом",s.settings.coveredPlansMinor)
                )
                labels.forEach { (key,title,sum) ->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(title)
                            Text(Money.text(sum,"RUB"),color=MaterialTheme.colorScheme.onSurfaceVariant,
                                style=MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick={start(key,moneyValue(sum))}) {Text("Изменить")}
                    }
                }
                Text("Перекрытия указываются вручную, чтобы общие деньги и покрытые платежи не вычитались дважды.",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {TinySection("Быстрые шаблоны","+ Шаблон"){name="";amount="";start("template")}}
        if(s.templates.isEmpty()) item {EmptyText("Шаблон с суммой: быстрое проведение. Без суммы: готовая форма.")}
        items(s.templates,key={it.id}) {t ->
            KCard {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {Text(t.title);Text(t.category,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    MoneyLine(t.amountMinor,s.accounts.firstOrNull{it.id==t.accountId}?.currency?:"RUB")
                }
            }
        }
        item {
            KCard {
                Text("Резервная копия",fontWeight=FontWeight.SemiBold)
                Text("JSON, локально на устройстве. Импорт заменяет текущие данные; сначала сохраните отдельную копию.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={exportLauncher.launch("Karman-backup.json")},modifier=Modifier.weight(1f)) {Text("Экспорт")}
                    OutlinedButton(onClick={importLauncher.launch(arrayOf("application/json","text/plain","*/*"))},
                        modifier=Modifier.weight(1f)) {Text("Импорт")}
                }
                if(feedback.isNotEmpty()) Text(feedback,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            KCard {
                Text("Карман · версия 1.0.0",fontWeight=FontWeight.SemiBold)
                Text("Android 8.0+ · данные хранятся на устройстве · без рекламы и аналитических SDK",
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Сезонное оформление отключено ради простого интерфейса.",
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if(importCandidate!=null) AlertDialog(onDismissRequest={importCandidate=null},
        title={Text("Восстановить резервную копию?")},
        text={Text("Текущие счета, операции, планы и инвестиции будут заменены. Перед продолжением рекомендуется экспортировать копию текущих данных.")},
        confirmButton={TextButton(onClick={vm.import(importCandidate.orEmpty());importCandidate=null}) {Text("Заменить данные")}},
        dismissButton={TextButton(onClick={importCandidate=null}){Text("Отмена")}})
    if(dialog.isNotBlank()) ActionDialog(title=when(dialog) {
        "fx"->"Курс $rateCode";"template"->"Новый шаблон";else->"Настройка резерва"
    },onClose={dialog=""},submit={
        try {
            when(dialog) {
                "fx"->vm.pref(when(rateCode){"USD"->"usdPerBaseMicros";"EUR"->"eurPerBaseMicros";else->"bynPerBaseMicros"},
                    currencyToBaseMicro(amount).toString())
                "template"->{vm.addTemplate(name,kind,category,accountId,
                    if(amount.isBlank())0 else Money.parse(amount))}
                else ->{val value=Money.parse(amount);require(value>=0);vm.pref(dialog,value.toString())}
            }
            dialog=""
        } catch(e:Exception){error=e.message?:"Проверьте значения"}
    }) {
        if(dialog=="template") {
            SimpleText("Название",name,{name=it})
            Row(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                listOf(Kind.EXPENSE to "Расход",Kind.INCOME to "Доход").forEach {(k,l)->
                    FilterChip(selected=kind==k,onClick={kind=k},label={Text(l)})
                }
            }
            SimpleText("Категория",category,{category=it})
            Text("Счёт")
            AccountChoices(s.accounts,accountId,{accountId=it})
            Text("Оставьте сумму пустой — шаблон будет без суммы.")
            AmountInput("Сумма (необязательно)",amount,{amount=it})
        } else {
            if(dialog=="fx") SimpleText("Рублей за 1 $rateCode",amount,{amount=it})
            else AmountInput("Сумма в ₽",amount,{amount=it})
        }
        if(error!=null)Text(error.orEmpty(),color=MaterialTheme.colorScheme.error)
    }
}
