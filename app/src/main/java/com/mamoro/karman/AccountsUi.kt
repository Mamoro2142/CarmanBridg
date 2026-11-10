package com.mamoro.karman

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

fun rubRateMicro(text:String):Long {
    val rate=BigDecimal(text.replace(" ","").replace(',','.'))
    require(rate>BigDecimal.ZERO && rate<=BigDecimal(100000))
    return rate.multiply(BigDecimal(1000000)).setScale(0,RoundingMode.HALF_UP).longValueExact()
}
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AccountsTab(s:Snapshot,vm:KarmanViewModel) {
    var dialog by remember {mutableStateOf("")}
    var name by remember {mutableStateOf("")}
    var currency by remember {mutableStateOf("RUB")}
    var initial by remember {mutableStateOf("")}
    var rate by remember {mutableStateOf("1")}
    var chosenAccount by remember {mutableStateOf<Account?>(null)}
    var chosenBond by remember {mutableStateOf<Bond?>(null)}
    var nominal by remember {mutableStateOf("1000")}
    var market by remember {mutableStateOf("1000")}
    var couponYield by remember {mutableStateOf("0")}
    var qty by remember {mutableStateOf("1")}
    var fee by remember {mutableStateOf("")}
    var due by remember {mutableStateOf(LocalDate.now().toString())}
    var error by remember {mutableStateOf<String?>(null)}
    var accountId by remember(s.accounts) {mutableLongStateOf(s.accounts.firstOrNull{it.currency=="RUB"}?.id?:0)}
    fun start(d:String) {dialog=d;name="";initial="";fee="";error=null;qty="1"}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=18.dp,vertical=8.dp),
        verticalArrangement=Arrangement.spacedBy(11.dp)) {
        item {TitleBar("Счета","Деньги и инвестиции отдельно")}
        item {TinySection("Денежные счета","+ Счёт"){currency="RUB";rate="1";start("account")}}
        items(s.accounts,key={it.id}) {a->
            KCard {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(a.name,fontWeight=FontWeight.SemiBold)
                        Text("${a.currency} · ${if(a.included)"учитывается" else "скрыт из баланса"}",
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    MoneyLine(LedgerMath.accountLocal(a,s.entries),a.currency)
                }
                if(a.currency!="RUB") Text("Зафиксировано: ${Money.text(LedgerMath.accountBase(a,s.entries),"RUB")}",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=a.included,onCheckedChange={vm.toggleAccount(a.id,it)})
                    Text("В общем балансе",style=MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    if(a.currency!="RUB") TextButton(onClick={chosenAccount=a;rate=BigDecimal.valueOf(a.rateMicros,6).toPlainString();dialog="rate"}) {
                        Text("Курс")
                    }
                }
            }
        }
        item {
            KCard {
                KTitle("Инвестиции · текущая оценка")
                Spacer(Modifier.height(7.dp))
                MoneyLine(LedgerMath.portfolioValue(s),"RUB",large=true)
                Text("Оценка активов отдельно от денежных счетов — без двойного учёта",color=MaterialTheme.colorScheme.onSurfaceVariant,
                    style=MaterialTheme.typography.bodySmall)
            }
        }
        item {TinySection("Облигации","+ Облигация"){start("bond")}}
        if(s.bonds.isEmpty()) item {EmptyText("Создайте облигацию и добавьте партии покупки")}
        items(s.bonds,key={it.id}) {b->
            val count=LedgerMath.bondQuantity(s,b.id)
            KCard {
                Text(b.name,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text("Количество: $count · Купонная доходность: ${b.couponBps/100.0}%",color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Номинал: ${Money.text(b.nominalMinor*count,"RUB")}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Вложено: ${Money.text(LedgerMath.bondInvested(s,b.id),"RUB")}",color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Оценка: ${Money.text(LedgerMath.bondMarketValue(s,b),"RUB")}",fontWeight=FontWeight.Medium)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick={chosenBond=b;market=moneyValue(b.quoteMinor);start("buy")}){Text("Купить")}
                    TextButton(onClick={chosenBond=b;market=moneyValue(b.quoteMinor);start("sell")},enabled=count>0){Text("Продать")}
                    TextButton(onClick={chosenBond=b;market=moneyValue(b.quoteMinor);start("quote")}){Text("Оценка")}
                    TextButton(onClick={chosenBond=b;start("coupon");market="";due=LocalDate.now().toString()}){Text("Купон")}
                }
                s.coupons.filter{it.bondId==b.id}.forEach {c->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("Купон ${c.day} · ${Money.text(c.amountMinor,"RUB")}",
                            modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
                        if(!c.posted&&!LocalDate.parse(c.day).isAfter(LocalDate.now()))
                            TextButton(onClick={vm.postCoupon(c.id)}){Text("Получен")}
                    }
                }
            }
        }
    }
    if(dialog.isNotBlank()) ActionDialog(title=when(dialog){
        "account"->"Новый счёт";"rate"->"Курс счёта";"bond"->"Новая облигация";
        "buy"->"Докупка облигаций";"sell"->"Продажа облигаций";"quote"->"Рыночная цена";else->"Купон"
    },onClose={dialog=""},submit={
        try {
            when(dialog) {
                "account" -> vm.createAccount(name,currency,initial.takeIf{it.isNotBlank()}?.let(Money::parse)?:0,
                    if(currency=="RUB")1000000 else rubRateMicro(rate))
                "rate"->Unit
                "bond"->vm.addBond(name,accountId,Money.parse(nominal),Money.parse(market),
                    (BigDecimal(couponYield.replace(',','.'))*BigDecimal(100)).intValueExact())
                "buy"->vm.buyBond(requireNotNull(chosenBond).id,qty.toLong(),Money.parse(market),fee.takeIf{it.isNotBlank()}?.let(Money::parse)?:0)
                "sell"->vm.sellBond(requireNotNull(chosenBond).id,qty.toLong(),Money.parse(market),fee.takeIf{it.isNotBlank()}?.let(Money::parse)?:0)
                "quote"->vm.quote(requireNotNull(chosenBond).id,Money.parse(market))
                "coupon"->vm.addCoupon(requireNotNull(chosenBond).id,LocalDate.parse(due),Money.parse(market))
            }
            if(dialog=="rate") vm.changeAccountRate(requireNotNull(chosenAccount).id,rubRateMicro(rate))
            dialog=""
        } catch(ex:Exception) {error=ex.message?:"Проверьте данные"}
    }) {
        when(dialog) {
            "account"->{
                SimpleText("Название счёта",name,{name=it})
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    listOf("RUB","USD","EUR","BYN").forEach{cur->
                        FilterChip(selected=currency==cur,onClick={currency=cur},label={Text(cur)})
                    }
                }
                AmountInput("Начальный остаток",initial,{initial=it})
                if(currency!="RUB") SimpleText("Сколько ₽ стоит 1 $currency",rate,{rate=it})
            }
            "rate"->{SimpleText("Сколько ₽ стоит 1 ${chosenAccount?.currency}",rate,{rate=it})
                Text("Новый курс используется только для следующих операций. Прошлые суммы и основной баланс не меняются.")}
            "bond"->{
                SimpleText("Название",name,{name=it})
                Text("Счёт оплаты (RUB)")
                AccountChoices(s.accounts.filter{it.currency=="RUB"},accountId,{accountId=it})
                AmountInput("Номинал одной бумаги",nominal,{nominal=it})
                AmountInput("Текущая цена одной бумаги",market,{market=it})
                SimpleText("Купонная доходность, %",couponYield,{couponYield=it})
            }
            "buy","sell"->{
                SimpleText("Количество",qty,{qty=it})
                AmountInput("Цена одной бумаги",market,{market=it})
                AmountInput("Комиссия",fee,{fee=it})
                Text("Партии хранят себестоимость отдельно; продажи списывают старые партии первыми.")
            }
            "quote" -> AmountInput("Новая рыночная цена одной бумаги",market,{market=it})
            "coupon"->{
                SimpleText("Дата выплаты (ГГГГ-ММ-ДД)",due,{due=it})
                AmountInput("Сумма выплаты",market,{market=it})
                Text("До отметки «Получен» деньги не зачисляются на счёт.")
            }
        }
        if(error!=null) Text(error.orEmpty(),color=MaterialTheme.colorScheme.error)
    }
}
