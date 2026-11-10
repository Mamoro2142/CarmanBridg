package com.mamoro.karman

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private val Ink=Color(0xFF101214)
private val Surface=Color(0xFF1B1F22)
private val Secondary=Color(0xFFADB2B5)
private val Accent=Color(0xFFF3A35D)
private val Light=Color(0xFFF2F1EF)
private val Green=Color(0xFF89C9AA)

@Composable
fun KarmanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme=darkColorScheme(primary=Accent, onPrimary=Ink, background=Ink,
            onBackground=Light, surface=Surface, onSurface=Light,
            surfaceVariant=Color(0xFF292E31), onSurfaceVariant=Secondary,
            error=Color(0xFFFF9393), outline=Color(0xFF4C5154)),
        typography=Typography(), content=content
    )
}

class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=android.graphics.Color.rgb(16,18,20)
        window.navigationBarColor=android.graphics.Color.rgb(16,18,20)
        setContent { KarmanTheme { KarmanApp() } }
    }
}

private val tabs=listOf("Главная","История","План","Счета","Ещё")
private val tabIcons=listOf(Icons.Default.Home,Icons.Default.ReceiptLong,Icons.Default.CalendarMonth,
    Icons.Default.AccountBalanceWallet,Icons.Default.MoreHoriz)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KarmanApp(vm:KarmanViewModel=viewModel()) {
    val s by vm.snapshot.collectAsState()
    val issue by vm.issue.collectAsState()
    val pager=rememberPagerState(pageCount={tabs.size})
    var quick by remember { mutableStateOf<Template?>(null) }
    var showQuick by remember { mutableStateOf(false) }
    val snackbar=remember { SnackbarHostState() }
    val scope=rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshScheduled() }
    LaunchedEffect(issue) { if(issue!=null) { snackbar.showSnackbar(issue?:"Ошибка"); vm.dismissIssue() } }
    BackHandler(enabled=true) {
        when { showQuick->{showQuick=false;quick=null}
            pager.currentPage!=0->pager.requestScrollToPage(0)
            else -> Unit // Never exit when already on root.
        }
    }
    Scaffold(
        containerColor=Ink,
        snackbarHost={SnackbarHost(snackbar)},
        floatingActionButton={
            if(pager.currentPage in 0..2) FloatingActionButton(
                onClick={quick=null;showQuick=true},
                containerColor=Accent, contentColor=Ink,
                shape=RoundedCornerShape(18.dp)) {Icon(Icons.Default.Add,"Новая операция")}
        },
        bottomBar={NavigationBar(containerColor=Surface) {
            tabs.forEachIndexed { i,name ->
                NavigationBarItem(selected=pager.currentPage==i,
                    onClick={pager.requestScrollToPage(i)},
                    label={Text(name,maxLines=1)},
                    icon={Icon(tabIcons[i],contentDescription=name)},
                    colors=NavigationBarItemDefaults.colors(selectedIconColor=Ink,selectedTextColor=Accent,
                        indicatorColor=Accent,unselectedIconColor=Secondary,unselectedTextColor=Secondary))
            }
        }}
    ) {padding ->
        HorizontalPager(state=pager,modifier=Modifier.fillMaxSize().padding(padding),
            beyondViewportPageCount=0) {page ->
            when(page) {
                0->HomeTab(s,vm,onQuick={ template->
                    if(template.amountMinor>0) vm.income(template.kind,template.accountId,template.amountMinor,template.category,"")
                    else {quick=template;showQuick=true}
                },onNavigate={pager.requestScrollToPage(it)})
                1->HistoryTab(s,vm)
                2->PlansTab(s,vm)
                3->AccountsTab(s,vm)
                else->SettingsTab(s,vm)
            }
        }
    }
    if(showQuick) QuickEntrySheet(s,vm,quick,onClose={showQuick=false;quick=null})
}

@Composable
fun TitleBar(title:String,subtitle:String?=null) {
    Column(Modifier.fillMaxWidth().padding(top=18.dp,bottom=12.dp)) {
        Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
        if(subtitle!=null) Text(subtitle,color=Secondary,style=MaterialTheme.typography.bodyMedium)
    }
}
@Composable
fun KCard(modifier:Modifier=Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier=modifier,colors=CardDefaults.cardColors(containerColor=Surface),
        shape=RoundedCornerShape(20.dp)) { Column(Modifier.fillMaxWidth().padding(17.dp),content=content) }
}
@Composable
fun KTitle(text:String,modifier:Modifier=Modifier) { Text(text,modifier,color=Secondary,
    style=MaterialTheme.typography.labelLarge) }
@Composable
fun MoneyLine(amount:Long,currency:String,large:Boolean=false,color:Color=Light) {
    Text(Money.text(amount,currency),color=color,fontWeight=FontWeight.SemiBold,
        style=if(large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium)
}
@Composable
fun EmptyText(text:String) { Text(text,color=Secondary,modifier=Modifier.padding(vertical=22.dp)) }

@Composable
fun HomeTab(s:Snapshot,vm:KarmanViewModel,onQuick:(Template)->Unit,onNavigate:(Int)->Unit) {
    val month=remember { YearMonth.now() }
    val mainBase=remember(s) { LedgerMath.totalBase(s) }
    val available=remember(s) { LedgerMath.availableBase(s) }
    val spent=remember(s) { LedgerMath.monthSpent(s,month) }
    val income=remember(s) { LedgerMath.monthIncome(s,month) }
    LazyColumn(modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=96.dp),
        verticalArrangement=Arrangement.spacedBy(13.dp)) {
        item { TitleBar("Карман","Бюджет без лишних действий") }
        item {
            KCard {
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()) {
                    KTitle("Общий баланс")
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.AccountBalanceWallet,null,tint=Accent)
                }
                Spacer(Modifier.height(9.dp))
                Crossfade(targetState=LedgerMath.shownBalance(s),label="balance") { shown ->
                    MoneyLine(shown,s.settings.displayCurrency,true)
                }
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    items(listOf("RUB" to "₽","USD" to "$","EUR" to "€","BYN" to "Br")) { (code,symbol) ->
                        FilterChip(selected=s.settings.displayCurrency==code,
                            onClick={vm.pref("displayCurrency",code)},label={Text(symbol)},
                            colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Accent,selectedLabelColor=Ink))
                    }
                }
                Text("Основа: ${Money.text(mainBase,"RUB")}. Переключение не меняет данные.",
                    color=Secondary,style=MaterialTheme.typography.labelSmall)
            }
        }
        item {
            KCard {
                KTitle("Доступно с учётом резервов и платежей")
                Spacer(Modifier.height(8.dp))
                MoneyLine(available,"RUB",large=true,color=if(available>=0) Green else MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(5.dp))
                Text("Доходы, расходы и планы — в одном месте",color=Secondary,
                    style=MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                KCard(Modifier.weight(1f)) { KTitle("Доходы · месяц");Spacer(Modifier.height(8.dp));MoneyLine(income,"RUB",color=Green) }
                KCard(Modifier.weight(1f)) { KTitle("Расходы · месяц");Spacer(Modifier.height(8.dp));MoneyLine(spent,"RUB",color=Accent) }
            }
        }
        if(s.templates.isNotEmpty()) {
            item { Text("Одно касание",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold) }
            item {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    items(s.templates,key={it.id}) { t->
                        AssistChip(onClick={onQuick(t)},label={Text(t.title)},leadingIcon={
                            Icon(Icons.Default.Bolt,null,modifier=Modifier.size(18.dp))
                        })
                    }
                }
            }
        }
        item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Последние операции",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick={onNavigate(1)}){Text("Все")}
            }
        }
        if(s.entries.isEmpty()) item { EmptyText("Операций пока нет. Нажмите + для первой записи.") }
        items(s.entries.take(5),key={it.id}) {e->TransactionRow(e,s)}
    }
}

@Composable
fun TransactionRow(e:Entry,s:Snapshot,modifier:Modifier=Modifier, trailing: (@Composable () -> Unit)?=null) {
    val incoming=e.kind in listOf(Kind.INCOME,Kind.COUPON,Kind.SELL)
    val account=s.accounts.find{it.id==e.fromAccount}
    KCard(modifier) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(40.dp),contentAlignment=Alignment.Center) {
                Icon(when(e.kind){Kind.TRANSFER->Icons.Default.SwapHoriz;Kind.BUY,Kind.SELL->Icons.Default.TrendingUp;
                    Kind.COUPON->Icons.Default.Payments;else->if(incoming)Icons.Default.ArrowDownward else Icons.Default.ArrowUpward},
                    null,tint=if(incoming) Green else Accent)
            }
            Column(Modifier.weight(1f)) {
                Text(e.category.ifEmpty {if(incoming) "Доход" else "Расход"},fontWeight=FontWeight.Medium)
                Text("${e.day} · ${account?.name?:"Счёт"}${if(e.note.isNotBlank()) " · ${e.note}" else ""}",
                    color=Secondary,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment=Alignment.End) {
                Text("${if(incoming)"+" else "−"}${Money.text(e.amountMinor,account?.currency?:"RUB")}",
                    color=if(incoming)Green else Light,fontWeight=FontWeight.SemiBold)
                if(account?.currency!="RUB") Text(Money.text(e.baseMinor,"RUB"),color=Secondary,
                    style=MaterialTheme.typography.labelSmall)
            }
            if(trailing!=null) trailing()
        }
    }
}

@Composable
fun HistoryTab(s:Snapshot,vm:KarmanViewModel) {
    var filter by remember {mutableStateOf("ALL")}
    var chosen by remember {mutableStateOf<Entry?>(null)}
    var editing by remember {mutableStateOf(false)}
    var pendingDelete by remember {mutableStateOf<Entry?>(null)}
    var editedCategory by remember {mutableStateOf("")}
    var editedNote by remember {mutableStateOf("")}
    var editedDate by remember {mutableStateOf("")}
    var editError by remember {mutableStateOf<String?>(null)}
    var category by remember {mutableStateOf<String?>(null)}
    val rows=remember(s,filter,category) {s.entries.filter {e ->
        (filter=="ALL" || filter=="IN"&&e.kind in listOf(Kind.INCOME,Kind.COUPON,Kind.SELL)
          ||filter=="OUT"&&e.kind in listOf(Kind.EXPENSE,Kind.BUY)) &&
            (category==null||e.category==category)
    }}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=18.dp,vertical=8.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { TitleBar("История","Операции и аналитика") }
        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to "Все","OUT" to "Расходы","IN" to "Доходы").forEach{ (k,v)->
                FilterChip(selected=filter==k,onClick={filter=k;category=null},label={Text(v)})
            }
        }}
        if(filter=="OUT") {
            item {
                val monthly=s.entries.filter{it.kind==Kind.EXPENSE&&YearMonth.from(LocalDate.parse(it.day))==YearMonth.now()}
                val groups=monthly.groupBy{it.category}.mapValues {it.value.sumOf {e->e.baseMinor}}.toList().sortedByDescending{it.second}
                KCard {
                    Text("Расходы по категориям · ${YearMonth.now()}",fontWeight=FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    if(groups.isEmpty()) EmptyText("Нет расходов за текущий месяц")
                    groups.take(8).forEach { (name,total)->
                        val max=groups.maxOf{it.second}.coerceAtLeast(1L)
                        Column(Modifier.fillMaxWidth().clickable {category=if(category==name)null else name}.padding(vertical=6.dp)) {
                            Row { Text(name,modifier=Modifier.weight(1f));Text(Money.text(total,"RUB")) }
                            Spacer(Modifier.height(5.dp))
                            LinearProgressIndicator(progress={total.toFloat()/max},modifier=Modifier.fillMaxWidth(),
                                color=Accent,trackColor=MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                    if(category!=null) TextButton(onClick={category=null}) {Text("Сбросить категорию")}
                }
            }
        }
        if(rows.isEmpty()) item {EmptyText("Записей не найдено")}
        items(rows,key={it.id}) {e->
            TransactionRow(e,s,Modifier.clickable{chosen=e;editing=false;editedCategory=e.category;editedNote=e.note;editedDate=e.day;editError=null})
        }
    }
    if(chosen!=null && !editing) AlertDialog(onDismissRequest={chosen=null},
        title={Text(chosen?.category?:"Операция")},
        text={Text("${chosen?.day}\n${chosen?.note.orEmpty()}")},
        confirmButton={
            Row {
                if(chosen?.kind in listOf(Kind.EXPENSE,Kind.INCOME)&&chosen?.sourceKey==null)
                    TextButton(onClick={editing=true}) {Text("Изменить")}
                if(chosen?.kind in listOf(Kind.EXPENSE,Kind.INCOME,Kind.TRANSFER)&&chosen?.sourceKey==null)
                    TextButton(onClick={pendingDelete=chosen;chosen=null}) {Text("Удалить",color=MaterialTheme.colorScheme.error)}
            }
        },dismissButton={TextButton(onClick={chosen=null}){Text("Закрыть")}})
    if(pendingDelete!=null) AlertDialog(onDismissRequest={pendingDelete=null},
        title={Text("Удалить операцию?")},
        text={Text("Запись исчезнет из истории, а баланс будет пересчитан.")},
        confirmButton={TextButton(onClick={pendingDelete?.let{vm.deleteEntry(it.id)};pendingDelete=null}) {
            Text("Удалить",color=MaterialTheme.colorScheme.error)
        }},dismissButton={TextButton(onClick={pendingDelete=null}){Text("Отмена")}})
    if(chosen!=null && editing) ActionDialog("Изменить сведения",onClose={chosen=null;editing=false},submit={
        try {
            vm.editEntryDetails(requireNotNull(chosen).id,editedCategory,editedNote,LocalDate.parse(editedDate))
            chosen=null;editing=false
        } catch(ex:Exception) {editError=ex.message?:"Ошибка"}
    }) {
        SimpleText("Категория",editedCategory,{editedCategory=it})
        SimpleText("Комментарий",editedNote,{editedNote=it})
        SimpleText("Дата (ГГГГ-ММ-ДД)",editedDate,{editedDate=it})
        if(editError!=null) Text(editError.orEmpty(),color=MaterialTheme.colorScheme.error)
    }
}

private val expenses=listOf("Продукты","Транспорт","Дом","Здоровье","Кафе","Покупки","Связь","Платежи","Другое")
private val incomes=listOf("Зарплата","Подработка","Подарки","Возврат","Другое")

private fun typing(text:String):String {
    val cleaned=text.filter {it.isDigit()||it==','||it=='.'}.replace(',','.')
    val pos=cleaned.indexOf('.')
    val whole=(if(pos>=0)cleaned.substring(0,pos) else cleaned).trimStart('0').ifEmpty {"0"}.take(13)
    val formatted=whole.reversed().chunked(3).joinToString(" ").reversed()
    return if(pos>=0) formatted+"."+cleaned.substring(pos+1).filter{it.isDigit()}.take(2) else formatted
}
private fun moneyRaw(value:Long):String = if(value==0L) "" else java.math.BigDecimal.valueOf(value,2).toPlainString()
@Composable
fun AmountInput(label:String,value:String,onValue:(String)->Unit,modifier:Modifier=Modifier) {
    OutlinedTextField(value=value,onValueChange={onValue(typing(it))},label={Text(label)},
        singleLine=true,modifier=modifier.fillMaxWidth(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
}
@Composable
fun SimpleText(label:String,value:String,onValue:(String)->Unit,modifier:Modifier=Modifier) {
    OutlinedTextField(value=value,onValueChange=onValue,label={Text(label)},singleLine=true,modifier=modifier.fillMaxWidth())
}
@Composable
fun AccountChoices(accounts:List<Account>,selected:Long,onChange:(Long)->Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),
        modifier=Modifier.fillMaxWidth()) {
        items(accounts,key={it.id}) { a->
            FilterChip(selected=selected==a.id,onClick={onChange(a.id)},label={Text("${a.name} · ${a.currency}")})
        }
    }
}
@Composable
fun PrimaryAction(label:String,click:()->Unit,enabled:Boolean=true) {
    Button(onClick=click,enabled=enabled,modifier=Modifier.fillMaxWidth().height(52.dp),
        shape=RoundedCornerShape(15.dp)) { Text(label,fontWeight=FontWeight.SemiBold) }
}
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun QuickEntrySheet(s:Snapshot,vm:KarmanViewModel,template:Template?,onClose:()->Unit) {
    val recent=s.entries.firstOrNull{it.kind in listOf(Kind.EXPENSE,Kind.INCOME)}
    var kind by remember(template?.id){mutableStateOf(template?.kind?:Kind.EXPENSE)}
    var amount by remember(template?.id){mutableStateOf(moneyRaw(template?.amountMinor?:0))}
    var accountId by remember(template?.id) {mutableLongStateOf(template?.accountId?:recent?.fromAccount?:s.accounts.firstOrNull()?.id?:0)}
    var category by remember(template?.id) { mutableStateOf(template?.category?:recent?.category?:"Продукты") }
    var note by remember {mutableStateOf("")}
    var selectedDay by remember {mutableStateOf(LocalDate.now().toString())}
    var extra by remember {mutableStateOf(false)}
    var saveTemplate by remember {mutableStateOf(false)}
    var templateName by remember {mutableStateOf("")}
    var noAmountTemplate by remember {mutableStateOf(false)}
    var toAccount by remember {mutableLongStateOf(s.accounts.firstOrNull{it.id!=accountId}?.id?:accountId)}
    var toAmount by remember {mutableStateOf("")}
    var error by remember {mutableStateOf<String?>(null)}
    val focusRequester=remember{FocusRequester()}
    LaunchedEffect(Unit){focusRequester.requestFocus()}
    ModalBottomSheet(onDismissRequest=onClose,containerColor=Surface,
        sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal=20.dp).padding(bottom=30.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(template!=null)template.title else "Новая операция",style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.Bold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf(Kind.EXPENSE to "Расход",Kind.INCOME to "Доход",Kind.TRANSFER to "Перевод").forEach{(k,name)->
                    FilterChip(selected=kind==k,onClick={kind=k;category=if(k==Kind.INCOME)"Зарплата" else "Продукты"},label={Text(name)})
                }
            }
            AmountInput("Сумма",amount,{amount=it},Modifier.focusRequester(focusRequester))
            Text("Счёт",color=Secondary)
            AccountChoices(s.accounts,accountId,{accountId=it})
            if(kind==Kind.TRANSFER) {
                Text("На счёт",color=Secondary)
                AccountChoices(s.accounts.filter{it.id!=accountId},toAccount,{toAccount=it})
                if(s.accounts.find{it.id==accountId}?.currency!=s.accounts.find{it.id==toAccount}?.currency)
                    AmountInput("Зачислится (необязательно)",toAmount,{toAmount=it})
            } else {
                Text("Категория",color=Secondary)
                val all=(if(kind==Kind.EXPENSE) expenses else incomes)+
                    s.entries.filter{it.kind==kind}.map{it.category}
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement=Arrangement.spacedBy(7.dp),
                    verticalArrangement=Arrangement.spacedBy(0.dp)) {
                    all.distinct().forEach { name->
                        FilterChip(selected=category==name,onClick={category=name},label={Text(name)})
                    }
                }
            }
            TextButton(onClick={extra=!extra}) {
                Icon(if(extra)Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)
                Text(if(extra)"Скрыть детали" else "Дополнительно: дата, заметка, шаблон")
            }
            if(extra) {
                SimpleText("Дата (ГГГГ-ММ-ДД)",selectedDay,{selectedDay=it})
                SimpleText("Комментарий",note,{note=it})
                if(kind!=Kind.TRANSFER) SimpleText("Своя категория",category,{category=it})
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=saveTemplate,onCheckedChange={saveTemplate=it})
                    Text("Сохранить как шаблон")
                }
                if(saveTemplate) {
                    SimpleText("Название шаблона",templateName,{templateName=it})
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked=noAmountTemplate,onCheckedChange={noAmountTemplate=it})
                        Text("Шаблон без суммы")
                    }
                }
            }
            if(error!=null) Text(error.orEmpty(),color=MaterialTheme.colorScheme.error)
            PrimaryAction("Сохранить") {
                try {
                    val sum=Money.parse(amount);require(sum>0){"Введите сумму больше нуля"}
                    require(accountId!=0L)
                    val date=LocalDate.parse(selectedDay)
                    if(kind==Kind.TRANSFER) {
                        require(s.accounts.size>1&&toAccount!=accountId){"Добавьте второй счёт"}
                        vm.transfer(accountId,toAccount,sum,toAmount.takeIf{it.isNotBlank()}?.let(Money::parse),date)
                    } else {
                        require(category.isNotBlank())
                        vm.income(kind,accountId,sum,category,note,date)
                        if(saveTemplate) vm.addTemplate(templateName.ifBlank{category},kind,category,accountId,
                            if(noAmountTemplate)0 else sum)
                    }
                    onClose()
                } catch(e:Exception) {error=e.message?:"Проверьте введённые данные"}
            }
        }
    }
}
