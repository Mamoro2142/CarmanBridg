package com.mamoro.karman

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private fun values(vararg pairs: Pair<String, Any?>): ContentValues = ContentValues().apply {
    for ((k,v) in pairs) when(v) {
        null -> putNull(k)
        is String -> put(k,v)
        is Int -> put(k,v)
        is Long -> put(k,v)
        is Boolean -> put(k,if(v) 1 else 0)
        else -> error("Unsupported database value")
    }
}
private fun Cursor.str(k: String): String = getString(getColumnIndexOrThrow(k)) ?: ""
private fun Cursor.lng(k: String): Long = getLong(getColumnIndexOrThrow(k))
private fun Cursor.flag(k: String): Boolean = lng(k) == 1L
private fun Cursor.nullLong(k: String): Long? = getColumnIndexOrThrow(k).let { i -> if(isNull(i)) null else getLong(i) }

/** Brand-new schema: no old app database or package is accessed. */
class LedgerDb(ctx: Context): SQLiteOpenHelper(ctx.applicationContext, "karman_v1.db", null, 1) {
    override fun onConfigure(db: SQLiteDatabase) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE accounts(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, currency TEXT NOT NULL,
            opening_minor INTEGER NOT NULL, opening_base_minor INTEGER NOT NULL, rate_micros INTEGER NOT NULL,
            included INTEGER NOT NULL DEFAULT 1)""")
        db.execSQL("""CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, day TEXT NOT NULL, kind TEXT NOT NULL,
            from_account INTEGER NOT NULL REFERENCES accounts(id), amount_minor INTEGER NOT NULL,
            base_minor INTEGER NOT NULL, category TEXT NOT NULL, note TEXT NOT NULL,
            to_account INTEGER REFERENCES accounts(id), to_amount_minor INTEGER, source_key TEXT UNIQUE)""")
        db.execSQL("CREATE INDEX idx_entries_day ON entries(day DESC,id DESC)")
        db.execSQL("""CREATE TABLE goals(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL,
            target_minor INTEGER NOT NULL, saved_minor INTEGER NOT NULL, reserve INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE plans(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, amount_minor INTEGER NOT NULL,
            account_id INTEGER NOT NULL REFERENCES accounts(id), due TEXT NOT NULL, frequency TEXT NOT NULL,
            auto INTEGER NOT NULL, reserve INTEGER NOT NULL, anchor_day INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE budgets(id INTEGER PRIMARY KEY AUTOINCREMENT, category TEXT NOT NULL UNIQUE, limit_minor INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE templates(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, kind TEXT NOT NULL,
            category TEXT NOT NULL, account_id INTEGER NOT NULL REFERENCES accounts(id), amount_minor INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE bonds(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, nominal_minor INTEGER NOT NULL,
            quote_minor INTEGER NOT NULL, coupon_bps INTEGER NOT NULL, account_id INTEGER NOT NULL REFERENCES accounts(id))""")
        db.execSQL("""CREATE TABLE lots(id INTEGER PRIMARY KEY AUTOINCREMENT, bond_id INTEGER NOT NULL REFERENCES bonds(id),
            quantity INTEGER NOT NULL, cost_minor INTEGER NOT NULL, day TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE coupons(id INTEGER PRIMARY KEY AUTOINCREMENT, bond_id INTEGER NOT NULL REFERENCES bonds(id),
            day TEXT NOT NULL, amount_minor INTEGER NOT NULL, posted INTEGER NOT NULL DEFAULT 0,
            UNIQUE(bond_id, day))""")
        db.execSQL("CREATE TABLE preferences(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.insertOrThrow("accounts",null,values("name" to "Наличные", "currency" to "RUB",
            "opening_minor" to 0L, "opening_base_minor" to 0L, "rate_micros" to 1000000L, "included" to 1))
    }
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
        // No destructive fallback: every future schema change must get an explicit migration.
        throw IllegalStateException("Missing migration from $old to $new")
    }
    fun snapshot(): Snapshot {
        val db = readableDatabase
        fun <T> read(sql:String, transform:(Cursor)->T):List<T> {
            val list=ArrayList<T>();db.rawQuery(sql,null).use { c -> while(c.moveToNext()) list.add(transform(c)) }
            return list
        }
        val prefs = read("SELECT key,value FROM preferences") { it.str("key") to it.str("value") }.toMap()
        fun num(k:String,default:Long=0L)=prefs[k]?.toLongOrNull()?:default
        fun bool(k:String)=prefs[k]=="1"
        val settings = Settings(
            baseCurrency="RUB", displayCurrency=prefs["displayCurrency"]?.takeIf { it in listOf("RUB","EUR","USD","BYN") }?:"RUB",
            usdPerBaseMicros=num("usdPerBaseMicros",11000).coerceAtLeast(1),
            eurPerBaseMicros=num("eurPerBaseMicros",10000).coerceAtLeast(1),
            bynPerBaseMicros=num("bynPerBaseMicros",35000).coerceAtLeast(1),
            extraReserveMinor=num("extraReserveMinor"), sharedReserveMinor=num("sharedReserveMinor"),
            coveredPlansMinor=num("coveredPlansMinor"),includeInvestments=bool("includeInvestments"), seasonal=bool("seasonal")
        )
        return Snapshot(
            accounts=read("SELECT * FROM accounts ORDER BY id") { Account(it.lng("id"),it.str("name"),it.str("currency"),
                it.lng("opening_minor"),it.lng("opening_base_minor"),it.lng("rate_micros"),it.flag("included")) },
            entries=read("SELECT * FROM entries ORDER BY day DESC,id DESC") { Entry(it.lng("id"),it.str("day"),it.str("kind"),
                it.lng("from_account"),it.lng("amount_minor"),it.lng("base_minor"),it.str("category"),
                it.str("note"),it.nullLong("to_account"),it.nullLong("to_amount_minor"),
                it.getString(it.getColumnIndexOrThrow("source_key"))) },
            goals=read("SELECT * FROM goals ORDER BY id") { Goal(it.lng("id"),it.str("title"),it.lng("target_minor"),it.lng("saved_minor"),it.flag("reserve")) },
            plans=read("SELECT * FROM plans ORDER BY due,id") { Plan(it.lng("id"),it.str("title"),it.lng("amount_minor"),
                it.lng("account_id"),it.str("due"),it.str("frequency"),it.flag("auto"),it.flag("reserve"),it.lng("anchor_day").toInt()) },
            budgets=read("SELECT * FROM budgets ORDER BY category") { Budget(it.lng("id"),it.str("category"),it.lng("limit_minor")) },
            templates=read("SELECT * FROM templates ORDER BY id DESC") { Template(it.lng("id"),it.str("title"),it.str("kind"),
                it.str("category"),it.lng("account_id"),it.lng("amount_minor")) },
            bonds=read("SELECT * FROM bonds ORDER BY id DESC") { Bond(it.lng("id"),it.str("name"),it.lng("nominal_minor"),
                it.lng("quote_minor"),it.lng("coupon_bps").toInt(),it.lng("account_id")) },
            lots=read("SELECT * FROM lots ORDER BY day,id") { Lot(it.lng("id"),it.lng("bond_id"),it.lng("quantity"),it.lng("cost_minor"),it.str("day")) },
            coupons=read("SELECT * FROM coupons ORDER BY day,id") { Coupon(it.lng("id"),it.lng("bond_id"),it.str("day"),it.lng("amount_minor"),it.flag("posted")) },
            settings=settings
        )
    }
    private inline fun <T> mutation(block:(SQLiteDatabase)->T):T {
        val db=writableDatabase
        db.beginTransaction()
        try { val value=block(db);db.setTransactionSuccessful();return value }
        finally { db.endTransaction() }
    }
    private fun insertEntry(db:SQLiteDatabase, kind:String, accountId:Long, local:Long, base:Long,
        category:String, note:String, day:String, destination:Long?=null, credited:Long?=null, source:String?=null):Long {
        require(local in 1..Money.MAX_MINOR && base in 0..Money.MAX_MINOR)
        require(category.length<=80 && note.length<=500)
        LocalDate.parse(day)
        return db.insertOrThrow("entries",null,values("day" to day,"kind" to kind,"from_account" to accountId,
            "amount_minor" to local,"base_minor" to base,"category" to category,"note" to note,
            "to_account" to destination,"to_amount_minor" to credited,"source_key" to source))
    }
    fun account(name:String,currency:String,opening:Long,rateMicros:Long):Long=mutation { db ->
        require(name.isNotBlank()&&name.length<=80&&currency in listOf("RUB","USD","EUR","BYN"))
        require(opening in 0..Money.MAX_MINOR && rateMicros in 1..100000000000L)
        require(currency!="RUB"||rateMicros==1000000L)
        db.insertOrThrow("accounts",null,values("name" to name,"currency" to currency,"opening_minor" to opening,
            "opening_base_minor" to Money.toBase(opening,rateMicros),"rate_micros" to rateMicros,"included" to 1))
    }
    fun includeAccount(id:Long,include:Boolean)=mutation { db ->
        require(db.update("accounts",values("included" to include),"id=?",arrayOf(id.toString()))==1)
    }
    fun updateAccountRate(id:Long,rate:Long)=mutation { db ->
        require(rate in 1..100000000000L)
        val a=snapshot().accounts.first{it.id==id};require(a.currency!="RUB"||rate==1000000L)
        // Past entries and openingBaseMinor remain untouched.
        db.update("accounts",values("rate_micros" to rate),"id=?",arrayOf(id.toString()))
    }
    fun entry(kind:String,accountId:Long,amount:Long,category:String,note:String="",
        date:LocalDate=LocalDate.now(),toId:Long?=null,toAmount:Long?=null):Long=mutation { db ->
        require(kind in listOf(Kind.INCOME,Kind.EXPENSE,Kind.TRANSFER))
        val accounts=snapshot().accounts
        val a=accounts.first { it.id==accountId }
        val base=Money.toBase(amount,a.rateMicros)
        if(kind==Kind.TRANSFER) {
            val dest=accounts.first{it.id==toId};require(dest.id!=accountId)
            val credit=toAmount?:Money.nativeFromBase(base,dest.rateMicros)
            require(credit in 1..Money.MAX_MINOR)
            insertEntry(db,kind,accountId,amount,base,"Перевод",note,date.toString(),toId,credit)
        } else insertEntry(db,kind,accountId,amount,base,category,note,date.toString())
    }
    fun editEntryDetails(id:Long,category:String,note:String,day:LocalDate)=mutation { db ->
        val e=snapshot().entries.first{it.id==id}
        require(e.kind in listOf(Kind.EXPENSE,Kind.INCOME) && e.sourceKey==null)
        require(category.isNotBlank() && category.length<=80 && note.length<=500)
        db.update("entries",values("category" to category,"note" to note,"day" to day.toString()),"id=?",arrayOf(id.toString()))
    }
    fun deleteEntry(id:Long)=mutation { db ->
        val e=snapshot().entries.first {it.id==id}
        // Special transactions have corresponding lot or coupon state and cannot be removed independently.
        require(e.kind in listOf(Kind.INCOME,Kind.EXPENSE,Kind.TRANSFER) && e.sourceKey==null) { "Системную запись удалять нельзя" }
        db.delete("entries","id=?",arrayOf(id.toString()))
    }
    fun goal(title:String,target:Long,reserve:Boolean)=mutation { db ->
        require(title.isNotBlank() && target in 1..Money.MAX_MINOR)
        db.insertOrThrow("goals",null,values("title" to title,"target_minor" to target,"saved_minor" to 0L,"reserve" to reserve))
    }
    fun saved(goalId:Long,value:Long)=mutation { db ->
        require(value in 0..Money.MAX_MINOR)
        require(db.update("goals",values("saved_minor" to value),"id=?",arrayOf(goalId.toString()))==1)
    }
    fun plan(title:String,amount:Long,accountId:Long,due:LocalDate,frequency:String,auto:Boolean,reserve:Boolean)=mutation { db ->
        require(title.isNotBlank() && amount in 1..Money.MAX_MINOR && frequency in listOf("ONCE","MONTHLY","WEEKLY"))
        require(snapshot().accounts.any { it.id==accountId })
        db.insertOrThrow("plans",null,values("title" to title,"amount_minor" to amount,"account_id" to accountId,
            "due" to due.toString(),"frequency" to frequency,"auto" to auto,"reserve" to reserve,"anchor_day" to due.dayOfMonth))
    }
    fun postPlan(id:Long,today:LocalDate=LocalDate.now()):Boolean=mutation { db ->
        val plan=snapshot().plans.first{it.id==id}
        val due=LocalDate.parse(plan.due)
        require(!due.isAfter(today))
        val account=snapshot().accounts.first { it.id==plan.accountId }
        val key="plan:$id:$due"
        if(db.rawQuery("SELECT 1 FROM entries WHERE source_key=?",arrayOf(key)).use{it.moveToFirst()}) false
        else {
            insertEntry(db,Kind.EXPENSE,plan.accountId,plan.amountMinor,Money.toBase(plan.amountMinor,account.rateMicros),
                "Платежи",plan.title,due.toString(),source=key)
            if(plan.frequency=="ONCE") db.update("plans",values("auto" to false,"reserve" to false),"id=?",arrayOf(id.toString()))
            else db.update("plans",values("due" to LedgerMath.nextDate(due,plan.frequency,plan.anchorDay).toString()),"id=?",arrayOf(id.toString()))
            true
        }
    }
    fun processAutoPlans(today:LocalDate=LocalDate.now()) {
        // Invoked on app start; per-occurrence source_key prevents duplicate debits.
        var count=0
        while(count++<120) {
            val next=snapshot().plans.firstOrNull { it.auto && !LocalDate.parse(it.due).isAfter(today) } ?:break
            postPlan(next.id,today)
        }
    }
    fun budget(category:String,limitMinor:Long)=mutation { db ->
        require(category.isNotBlank() && category.length<=80 && limitMinor in 1..Money.MAX_MINOR)
        db.insertWithOnConflict("budgets",null,values("category" to category,"limit_minor" to limitMinor),SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun template(title:String,kind:String,category:String,accountId:Long,amount:Long)=mutation { db ->
        require(title.isNotBlank()&&kind in listOf(Kind.EXPENSE,Kind.INCOME)&&amount in 0..Money.MAX_MINOR)
        db.insertOrThrow("templates",null,values("title" to title,"kind" to kind,"category" to category,
            "account_id" to accountId,"amount_minor" to amount))
    }
    fun bond(name:String,accountId:Long,nominal:Long,quote:Long,couponBps:Int):Long=mutation { db ->
        require(name.isNotBlank()&&nominal in 1..Money.MAX_MINOR&&quote in 0..Money.MAX_MINOR&&couponBps in 0..100000)
        require(snapshot().accounts.first{it.id==accountId}.currency=="RUB")
        db.insertOrThrow("bonds",null,values("name" to name,"nominal_minor" to nominal,"quote_minor" to quote,
            "coupon_bps" to couponBps,"account_id" to accountId))
    }
    fun updateQuote(id:Long,newQuote:Long)=mutation { db ->
        require(newQuote in 0..Money.MAX_MINOR)
        require(Math.multiplyExact(LedgerMath.bondQuantity(snapshot(),id),newQuote)<=Money.MAX_MINOR)
        require(db.update("bonds",values("quote_minor" to newQuote),"id=?",arrayOf(id.toString()))==1)
    }
    fun buyBond(bondId:Long,quantity:Long,priceEach:Long,fee:Long=0,date:LocalDate=LocalDate.now())=mutation { db ->
        require(quantity in 1..100000000 && priceEach in 1..Money.MAX_MINOR && fee in 0..Money.MAX_MINOR)
        val current=snapshot()
        val b=current.bonds.first{it.id==bondId}
        val newCount=Math.addExact(LedgerMath.bondQuantity(current,bondId),quantity)
        require(Math.multiplyExact(newCount,b.nominalMinor) <= Money.MAX_MINOR)
        require(Math.multiplyExact(newCount,b.quoteMinor) <= Money.MAX_MINOR)
        val amount=Math.addExact(Math.multiplyExact(quantity,priceEach),fee)
        require(amount in 1..Money.MAX_MINOR)
        insertEntry(db,Kind.BUY,b.accountId,amount,amount,"Инвестиции","Покупка ${b.name}: $quantity шт.",date.toString())
        db.insertOrThrow("lots",null,values("bond_id" to bondId,"quantity" to quantity,"cost_minor" to amount,"day" to date.toString()))
    }
    fun sellBond(bondId:Long,quantity:Long,priceEach:Long,fee:Long=0,date:LocalDate=LocalDate.now()):Long=mutation { db ->
        require(quantity in 1..100000000 && priceEach in 1..Money.MAX_MINOR && fee>=0)
        val s=snapshot();val bond=s.bonds.first{it.id==bondId};require(LedgerMath.bondQuantity(s,bondId)>=quantity)
        val proceeds=Math.subtractExact(Math.multiplyExact(quantity,priceEach),fee);require(proceeds in 1..Money.MAX_MINOR)
        var pending=quantity;var basis=0L
        s.lots.filter{it.bondId==bondId}.forEach { lot ->
            if(pending>0) {
                val sold=minOf(pending,lot.quantity)
                val consumed=Money.proportions(lot.costMinor,sold,lot.quantity)
                basis=Math.addExact(basis,consumed)
                if(sold==lot.quantity) db.delete("lots","id=?",arrayOf(lot.id.toString()))
                else db.update("lots",values("quantity" to lot.quantity-sold,"cost_minor" to lot.costMinor-consumed),
                    "id=?",arrayOf(lot.id.toString()))
                pending-=sold
            }
        }
        check(pending==0L)
        insertEntry(db,Kind.SELL,bond.accountId,proceeds,proceeds,"Инвестиции","Продажа ${bond.name}: $quantity шт.; FIFO база ${basis}",date.toString())
        proceeds-basis
    }
    fun coupon(bondId:Long,day:LocalDate,amount:Long):Long=mutation { db ->
        require(amount in 1..Money.MAX_MINOR);require(snapshot().bonds.any{it.id==bondId})
        db.insertOrThrow("coupons",null,values("bond_id" to bondId,"day" to day.toString(),
            "amount_minor" to amount,"posted" to false))
    }
    fun postCoupon(id:Long,today:LocalDate=LocalDate.now())=mutation { db ->
        val s=snapshot();val c=s.coupons.first{it.id==id};require(!c.posted&& !LocalDate.parse(c.day).isAfter(today))
        val b=s.bonds.first{it.id==c.bondId}
        insertEntry(db,Kind.COUPON,b.accountId,c.amountMinor,c.amountMinor,"Купоны",b.name,c.day,source="coupon:$id")
        db.update("coupons",values("posted" to true),"id=?",arrayOf(id.toString()))
    }
    fun setPreference(key:String,value:String)=mutation { db ->
        require(key in setOf("displayCurrency","usdPerBaseMicros","eurPerBaseMicros","bynPerBaseMicros",
            "extraReserveMinor","sharedReserveMinor","coveredPlansMinor","includeInvestments","seasonal"))
        require(value.length<=50)
        db.insertWithOnConflict("preferences",null,values("key" to key,"value" to value),SQLiteDatabase.CONFLICT_REPLACE)
    }
    private fun validateImportedData(s:Snapshot) {
        require(s.accounts.isNotEmpty() && s.accounts.size<100000)
        require(s.accounts.all { a ->
            a.name.isNotBlank() && a.name.length<=80 && a.currency in listOf("RUB","USD","EUR","BYN") &&
                a.openingMinor in 0..Money.MAX_MINOR && a.openingBaseMinor in 0..Money.MAX_MINOR &&
                a.rateMicros in 1..100000000000L && (a.currency!="RUB"||a.rateMicros==1000000L)
        })
        val accIds=s.accounts.map{it.id}.toSet()
        require(s.entries.all { e ->
            e.fromAccount in accIds && e.toAccount?.let{it in accIds}!=false &&
                e.kind in listOf(Kind.INCOME,Kind.EXPENSE,Kind.TRANSFER,Kind.BUY,Kind.SELL,Kind.COUPON) &&
                e.amountMinor in 1..Money.MAX_MINOR && e.baseMinor in 0..Money.MAX_MINOR &&
                e.category.length<=80 && e.note.length<=500 &&
                runCatching{LocalDate.parse(e.day)}.isSuccess &&
                (e.kind!=Kind.TRANSFER||(e.toAccount!=null&&e.toAmountMinor in 1..Money.MAX_MINOR))
        })
        require(s.plans.all {it.amountMinor in 1..Money.MAX_MINOR && it.accountId in accIds &&
            it.frequency in listOf("ONCE","MONTHLY","WEEKLY") && it.anchorDay in 1..31 &&
            runCatching{LocalDate.parse(it.due)}.isSuccess})
        require(s.goals.all {it.targetMinor in 1..Money.MAX_MINOR && it.savedMinor in 0..Money.MAX_MINOR})
        require(s.budgets.all{it.monthlyLimitMinor in 1..Money.MAX_MINOR})
        val bondIds=s.bonds.map{it.id}.toSet()
        require(s.bonds.all{it.accountId in accIds && it.nominalMinor in 1..Money.MAX_MINOR &&
            it.quoteMinor in 0..Money.MAX_MINOR && it.couponBps in 0..100000})
        require(s.lots.all{it.bondId in bondIds && it.quantity in 1..100000000 &&
            it.costMinor in 1..Money.MAX_MINOR && runCatching{LocalDate.parse(it.day)}.isSuccess})
        require(s.coupons.all{it.bondId in bondIds && it.amountMinor in 1..Money.MAX_MINOR &&
            runCatching{LocalDate.parse(it.day)}.isSuccess})
        require(s.templates.all{it.accountId in accIds&&it.kind in listOf(Kind.EXPENSE,Kind.INCOME)&&
            it.amountMinor in 0..Money.MAX_MINOR})
        s.accounts.forEach{LedgerMath.accountBase(it,s.entries);LedgerMath.accountLocal(it,s.entries)}
        s.bonds.forEach{LedgerMath.bondMarketValue(s,it)}
    }
    private val backupTables=listOf("accounts","entries","goals","plans","budgets","templates","bonds","lots","coupons","preferences")
    fun exportJson():String {
        val result=JSONObject().put("format","karman-v1").put("schema",1)
        val db=readableDatabase
        backupTables.forEach { table ->
            val rows=JSONArray()
            db.query(table,null,null,null,null,null,null).use { c ->
                while(c.moveToNext()) {
                    val row=JSONObject()
                    for(i in 0 until c.columnCount) {
                        if(c.isNull(i)) row.put(c.getColumnName(i),JSONObject.NULL)
                        else when(c.getType(i)) {
                            Cursor.FIELD_TYPE_INTEGER-> row.put(c.getColumnName(i),c.getLong(i))
                            Cursor.FIELD_TYPE_STRING->row.put(c.getColumnName(i),c.getString(i))
                            else ->error("Unexpected column type in $table")
                        }
                    }
                    rows.put(row)
                }
            }
            result.put(table,rows)
        }
        return result.toString(2)
    }
    fun importJson(json:String) {
        require(json.length<=30_000_000) { "Файл слишком велик" }
        val root=JSONObject(json);require(root.optString("format")=="karman-v1"&&root.optInt("schema")==1)
        val allowedColumns=mapOf(
            "accounts" to setOf("id","name","currency","opening_minor","opening_base_minor","rate_micros","included"),
            "entries" to setOf("id","day","kind","from_account","amount_minor","base_minor","category","note","to_account","to_amount_minor","source_key"),
            "goals" to setOf("id","title","target_minor","saved_minor","reserve"),
            "plans" to setOf("id","title","amount_minor","account_id","due","frequency","auto","reserve","anchor_day"),
            "budgets" to setOf("id","category","limit_minor"),
            "templates" to setOf("id","title","kind","category","account_id","amount_minor"),
            "bonds" to setOf("id","name","nominal_minor","quote_minor","coupon_bps","account_id"),
            "lots" to setOf("id","bond_id","quantity","cost_minor","day"),
            "coupons" to setOf("id","bond_id","day","amount_minor","posted"),
            "preferences" to setOf("key","value")
        )
        backupTables.forEach { name -> require(root.has(name)&&root.getJSONArray(name).length()<=100000) }
        mutation { db ->
            backupTables.asReversed().forEach { db.delete(it,null,null) }
            for(name in backupTables) {
                val a=root.getJSONArray(name)
                for(i in 0 until a.length()) {
                    val o=a.getJSONObject(i); val cv=ContentValues()
                    val keys=o.keys()
                    while(keys.hasNext()) {
                        val key=keys.next();require(key in allowedColumns.getValue(name))
                        val v=o.get(key)
                        when(v) {
                            JSONObject.NULL -> cv.putNull(key)
                            is String -> {require(v.length<=2000);cv.put(key,v)}
                            is Number ->cv.put(key,v.toLong())
                            else ->error("Некорректное значение резервной копии")
                        }
                    }
                    db.insertOrThrow(name,null,cv)
                }
            }
            validateImportedData(snapshot())
        }
    }
}
