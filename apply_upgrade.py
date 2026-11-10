#!/usr/bin/env python3
"""Karman v1.0.1 migration of source at build time (no database schema changes).

Requires the public v1.0.0 source tree from CarmanBridg (main, 2026-11-11).
Applies deterministic, verified substitutions; fails loudly on unexpected source.
The private signing certificate is never read by this script.
"""
from pathlib import Path

ROOT = Path.cwd()
APP = ROOT / 'app/src/main/java/com/mamoro/karman'
changed = []


def replace(path: Path, old: str, new: str, note: str):
    source = path.read_text(encoding='utf-8')
    if old == new:
        raise AssertionError('No-op patch: ' + note)
    occurrences = source.count(old)
    if occurrences == 1:
        path.write_text(source.replace(old, new, 1), encoding='utf-8')
        changed.append((str(path.relative_to(ROOT)), note))
    elif occurrences == 0 and new in source:
        print('Already applied:', note)
    else:
        raise RuntimeError(f'Unexpected source for {path} [{note}]: {occurrences} matches')


ui = APP / 'KarmanUi.kt'
settings = APP / 'SettingsUi.kt'
model = APP / 'KarmanViewModel.kt'
db = APP / 'LedgerDb.kt'

# 1. Persist original compiler fixes, even when the workflow already applied them.
replace(ui, 'PrimaryAction("Сохранить") {', 'PrimaryAction("Сохранить", click = {', 'save button callback')
replace(ui,
        '} catch(e:Exception) {error=e.message?:"Проверьте введённые данные"}\n            }\n        }\n    }\n}',
        '} catch(e:Exception) {error=e.message?:"Проверьте введённые данные"}\n            })\n        }\n    }\n}',
        'save button closing')
replace(settings,
        'PaddingValues(horizontal=18.dp,vertical=8.dp,bottom=30.dp)',
        'PaddingValues(start=18.dp,end=18.dp,top=8.dp,bottom=30.dp)',
        'settings padding')

# 2. History: search and one-touch month filters.
replace(ui,
'''    var category by remember {mutableStateOf<String?>(null)}
    val rows=remember(s,filter,category) {s.entries.filter {e ->
        (filter=="ALL" || filter=="IN"&&e.kind in listOf(Kind.INCOME,Kind.COUPON,Kind.SELL)
          ||filter=="OUT"&&e.kind in listOf(Kind.EXPENSE,Kind.BUY)) &&
            (category==null||e.category==category)
    }}''',
'''    var category by remember {mutableStateOf<String?>(null)}
    var search by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("ALL") }
    val rows=remember(s,filter,category,search,period) {
        val month=when(period) {
            "CURRENT" -> YearMonth.now().toString()
            "PREVIOUS" -> YearMonth.now().minusMonths(1).toString()
            else -> null
        }
        val needle=search.trim()
        val accountNames=s.accounts.associate { it.id to it.name }
        s.entries.filter { e ->
            (filter=="ALL" || filter=="IN"&&e.kind in listOf(Kind.INCOME,Kind.COUPON,Kind.SELL)
              ||filter=="OUT"&&e.kind in listOf(Kind.EXPENSE,Kind.BUY)) &&
                (category==null || e.category==category) &&
                (month==null || e.day.startsWith(month)) &&
                (needle.isEmpty() || e.category.contains(needle,ignoreCase=true) ||
                  e.note.contains(needle,ignoreCase=true) ||
                  e.day.contains(needle,ignoreCase=true) ||
                  (accountNames[e.fromAccount]?.contains(needle,ignoreCase=true)==true))
        }
    }''', 'history filtering')
replace(ui,
'''        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to "Все","OUT" to "Расходы","IN" to "Доходы").forEach{ (k,v)->
                FilterChip(selected=filter==k,onClick={filter=k;category=null},label={Text(v)})
            }
        }}
        if(filter=="OUT") {''',
'''        item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to "Все","OUT" to "Расходы","IN" to "Доходы").forEach{ (k,v)->
                FilterChip(selected=filter==k,onClick={filter=k;category=null},label={Text(v)})
            }
        }}
        item {
            OutlinedTextField(value=search,onValueChange={search=it},
                modifier=Modifier.fillMaxWidth(),singleLine=true,
                label={Text("Поиск по операциям")},
                leadingIcon={Icon(Icons.Default.Search,contentDescription=null)})
        }
        item {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                items(listOf("ALL" to "Всё время","CURRENT" to "Этот месяц","PREVIOUS" to "Прошлый месяц")) { (key,label) ->
                    FilterChip(selected=period==key,onClick={period=key},label={Text(label)})
                }
            }
        }
        if(search.isNotBlank() || period!="ALL") item {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Найдено: ${rows.size}",color=Secondary,modifier=Modifier.weight(1f))
                TextButton(onClick={search="";period="ALL"}) {Text("Сбросить")}
            }
        }
        if(filter=="OUT") {''', 'history search fields')

# 3. Quick entry: remember last category and account of the same operation kind.
replace(ui,
'''    val recent=s.entries.firstOrNull{it.kind in listOf(Kind.EXPENSE,Kind.INCOME)}
    var kind by remember(template?.id){mutableStateOf(template?.kind?:Kind.EXPENSE)}
    var amount by remember(template?.id){mutableStateOf(moneyRaw(template?.amountMinor?:0))}
    var accountId by remember(template?.id) {mutableLongStateOf(template?.accountId?:recent?.fromAccount?:s.accounts.firstOrNull()?.id?:0)}
    var category by remember(template?.id) { mutableStateOf(template?.category?:recent?.category?:"Продукты") }''',
'''    val lastExpense=s.entries.firstOrNull{it.kind==Kind.EXPENSE}
    val lastIncome=s.entries.firstOrNull{it.kind==Kind.INCOME}
    val initialKind=template?.kind?:Kind.EXPENSE
    val recentForKind=if(initialKind==Kind.INCOME) lastIncome else lastExpense
    var kind by remember(template?.id){mutableStateOf(initialKind)}
    var amount by remember(template?.id){mutableStateOf(moneyRaw(template?.amountMinor?:0))}
    var accountId by remember(template?.id) {mutableLongStateOf(template?.accountId?:recentForKind?.fromAccount?:s.accounts.firstOrNull()?.id?:0)}
    var category by remember(template?.id) {
        mutableStateOf(template?.category?:recentForKind?.category?:if(initialKind==Kind.INCOME)"Зарплата" else "Продукты")
    }''', 'quick entry defaults')
replace(ui,
'''                    FilterChip(selected=kind==k,onClick={kind=k;category=if(k==Kind.INCOME)"Зарплата" else "Продукты"},label={Text(name)})''',
'''                    FilterChip(selected=kind==k,onClick={
                        kind=k
                        val last=if(k==Kind.INCOME)lastIncome else lastExpense
                        category=last?.category?:if(k==Kind.INCOME)"Зарплата" else "Продукты"
                        if(last!=null) accountId=last.fromAccount
                        if(toAccount==accountId) toAccount=s.accounts.firstOrNull{it.id!=accountId}?.id?:accountId
                    },label={Text(name)})''', 'quick entry kind-switch')
replace(ui,
'''            AccountChoices(s.accounts,accountId,{accountId=it})''',
'''            AccountChoices(s.accounts,accountId,{newId ->
                accountId=newId
                if(toAccount==newId) toAccount=s.accounts.firstOrNull{it.id!=newId}?.id?:newId
            })''', 'transfer destination validation')

# 4. Saved templates: allow deletion without touching past transactions.
replace(db,
'''    fun bond(name:String,accountId:Long,nominal:Long,quote:Long,couponBps:Int):Long=mutation { db ->''',
'''    fun deleteTemplate(id:Long)=mutation { db ->
        require(db.delete("templates","id=?",arrayOf(id.toString()))==1) { "Шаблон не найден" }
    }
    fun bond(name:String,accountId:Long,nominal:Long,quote:Long,couponBps:Int):Long=mutation { db ->''',
    'database delete template')
replace(model,
'''    fun addBond(name:String,accountId:Long,nominal:Long,quote:Long,yield:Int)=''',
'''    fun deleteTemplate(id:Long)=execute{database.deleteTemplate(id)}
    fun addBond(name:String,accountId:Long,nominal:Long,quote:Long,yield:Int)=''',
    'viewmodel delete template')
replace(settings,
'''import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*''',
'''import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*''',
    'delete icon import')
replace(settings,
'''    var importCandidate by remember {mutableStateOf<String?>(null)}
    val context=LocalContext.current''',
'''    var importCandidate by remember {mutableStateOf<String?>(null)}
    var deleteTemplateCandidate by remember {mutableStateOf<Template?>(null)}
    val context=LocalContext.current''',
    'delete template state')
replace(settings,
'''                    MoneyLine(t.amountMinor,s.accounts.firstOrNull{it.id==t.accountId}?.currency?:"RUB")
                }
            }
        }
        item {
            KCard {
                Text("Резервная копия"''',
'''                    MoneyLine(t.amountMinor,s.accounts.firstOrNull{it.id==t.accountId}?.currency?:"RUB")
                    IconButton(onClick={deleteTemplateCandidate=t}) {
                        Icon(Icons.Default.DeleteOutline,contentDescription="Удалить шаблон",tint=MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item {
            KCard {
                Text("Резервная копия"''',
    'template delete action')
replace(settings,
'''    if(importCandidate!=null) AlertDialog(onDismissRequest={importCandidate=null},''',
'''    if(deleteTemplateCandidate!=null) AlertDialog(
        onDismissRequest={deleteTemplateCandidate=null},
        title={Text("Удалить шаблон?")},
        text={Text("Шаблон «${deleteTemplateCandidate?.title.orEmpty()}» будет удалён. История операций останется без изменений.")},
        confirmButton={TextButton(onClick={
            deleteTemplateCandidate?.let{vm.deleteTemplate(it.id)}
            deleteTemplateCandidate=null
        }) {Text("Удалить",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(onClick={deleteTemplateCandidate=null}) {Text("Отмена")}})
    if(importCandidate!=null) AlertDialog(onDismissRequest={importCandidate=null},''',
    'template delete confirmation')

# 5. Add a regression test for accounting after template deletion.
test = ROOT / 'app/src/test/java/com/mamoro/karman/LedgerDbTest.kt'
replace(test,
'''    @Test fun bondSaleUsesFifoAndCreditsWallet() {''',
'''    @Test fun deletingTemplateDoesNotDeletePastTransactions() {
        val account=db.snapshot().accounts.first().id
        db.entry(Kind.EXPENSE,account,2500,"Кафе")
        val templateId=db.template("Кофе",Kind.EXPENSE,"Кафе",account,2500)
        assertEquals(1,db.snapshot().templates.size)
        db.deleteTemplate(templateId)
        assertTrue(db.snapshot().templates.isEmpty())
        assertEquals(1,db.snapshot().entries.size)
        assertEquals(-2500L,LedgerMath.cashBase(db.snapshot()))
    }
    @Test fun bondSaleUsesFifoAndCreditsWallet() {''',
    'template deletion regression test')

# Version 1.0.1, consistent application ID and signing key.
gradle=ROOT/'app/build.gradle.kts'
replace(gradle,'versionCode = 1\n        versionName = "1.0.0"',
       'versionCode = 2\n        versionName = "1.0.1"', 'version bump')
replace(settings,'Карман · версия 1.0.0','Карман · версия 1.0.1','version label')

print(f'Karman 1.0.1: applied {len(changed)} source updates:')
for path, note in changed:
    print(f'- {path}: {note}')
