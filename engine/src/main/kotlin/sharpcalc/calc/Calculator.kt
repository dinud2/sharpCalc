package sharpcalc.calc

import sharpcalc.engine.*

enum class Mode(val title: String) {
    NORMAL("NORMAL"), STAT("STAT"), TABLE("TABLE"), COMPLEX("COMPLEX"), EQUATION("EQUATION"),
    MATRIX("MATRIX"), VECTOR("VECTOR"), DISTRIBUTION("DISTRIBUTION"), DRILL("DRILL")
}
enum class Shift { NONE, SECOND, ALPHA }

/** One frame of the LCD: the dot matrix, the annunciators (L-2) and the cursor to blink. */
class Display(
    val px: BooleanArray, val ann: Set<String>, val lines: List<String>,
    val cursor: Boolean, val curX: Int, val curTop: Int, val curBot: Int,
    val off: Boolean, val contrast: Int,
)

class Cur(val x: Int, val top: Int, val bot: Int)

abstract class Screen {
    abstract fun key(a: String)
    abstract fun draw(l: Lcd): Cur?
}

class ResultView(val value: V, var forms: List<List<Row>>) {
    var k = 0
    var eng: Int? = null
    var prime = false
}

/**
 * The whole calculator behind the keys: no Android, no Compose (P-5).
 * Feed it key presses with [press]; read the LCD with [display]; persist with [save] and [load].
 */
class Calculator {
    val ctx = Ctx()
    val set = Settings()
    var on = true
    var shift = Shift.NONE
    var hyp = 0            // 1 = hyp, 2 = arc hyp
    var pend = ""          // "sto" or "rcl" while waiting for a memory key
    var mode = Mode.NORMAL
    val ed = Editor()
    var res: ResultView? = null
    var err: CalcError? = null
    var msg: String? = null
    val history = ArrayList<Pair<Row, ResultView?>>()
    var hidx = -1
    val screens = ArrayList<Screen>()
    val stat = StatData()
    val dmem = arrayOfNulls<String>(3)
    var defPending = -1
    var cplxPolar = false
    var matBuf: Mat? = null
    var vecBuf: Mat? = null
    var tblF1 = Row()
    var tblF2 = Row()
    var scrollX = 0
    val arrows = HashSet<String>()
    @Volatile var busy = false
    private var kOp: String? = null
    private var kPart: List<Node> = emptyList()

    companion object {
        val ERR = mapOf(1 to "SYNTAX", 2 to "CALCULATION", 3 to "NESTING", 4 to "DATA OVER", 7 to "DEFINITION", 8 to "DIM UNMATCHED", 10 to "UNDEFINED")
        private val DIRECT: Set<String> = (("0123456789.+-*/(),E°!%".map { it.toString() }) + listOf(
            "neg", "π", "e", "Ans", "nCr", "nPr", "GCD", "LCM", "AND", "OR", "XOR", "XNOR", "i", "∠", "x′", "y′",
            "hA", "hB", "hC", "hD", "hE", "hF",
        ) + Parser.PREFIX + Parser.VARS).toSet()
        private val NEEDS_ANS = setOf("+", "-", "*", "/", "pow", "sq", "inv", "cube", "!", "%", "nCr", "nPr", "GCD", "LCM", "intdiv", "AND", "OR", "XOR", "XNOR")
        private val HEXKEYS = mapOf("pow" to "hA", "sqrt" to "hB", "sq" to "hC", "log" to "hD", "ln" to "hE", "," to "hF")

        /** Plain text as a display row. 'E' and A-F go through the hex ids so that 'E' is not drawn as the exponent mark. */
        fun textRow(s: String) = Row(s.map { Tok(if (it in 'A'..'F') "h$it" else it.toString()) as Node }.toMutableList())
        fun flat(r: Row): String = r.items.joinToString("") { if (it is Tok) Disp.text(it.id) else "?" }
        fun hasTmpl(r: Row) = r.items.any { it is Tmpl }
    }

    fun wvActive() = set.wv && (mode == Mode.NORMAL || mode == Mode.TABLE) && ctx.base == Base.DEC

    // ------------------------------------------------------------------ keys

    fun cancel() { ctx.cancel = true }

    fun press(k: Key) {
        if (!on) { if (k == Key.ONC) on = true; return }
        ctx.cancel = false
        if (k == Key.SECOND) { shift = if (shift == Shift.SECOND) Shift.NONE else Shift.SECOND; return }
        if (k == Key.ALPHA) {
            if (shift == Shift.SECOND) { shift = Shift.NONE; act("mclr") }
            else shift = if (shift == Shift.ALPHA) Shift.NONE else Shift.ALPHA
            return
        }
        val sh = shift
        shift = Shift.NONE
        if (pend != "") {
            val p = pend
            pend = ""
            if (sh != Shift.SECOND) {
                Key.VAR[k]?.let { memKey(p, it); return }
                val d = listOf(Key.D1, Key.D2, Key.D3).indexOf(k)
                if (d >= 0) { defKey(p, d); return }
            }
        }
        var a = (when (sh) { Shift.NONE -> k.a1; Shift.SECOND -> k.a2; Shift.ALPHA -> k.a3 }) ?: return
        if (a == "hyp") { hyp = if (hyp == 1) 0 else 1; return }
        if (a == "archyp") { hyp = if (hyp == 2) 0 else 2; return }
        if (hyp != 0) {
            val h = hyp
            hyp = 0
            val base = a.removeSuffix("⁻¹")
            if (base == "sin" || base == "cos" || base == "tan") a = base + "h" + (if (h == 2 || a.endsWith("⁻¹")) "⁻¹" else "")
        }
        if (sh == Shift.NONE && ctx.base == Base.HEX) HEXKEYS[a]?.let { a = it }
        if (mode == Mode.COMPLEX) { if (a == ",") a = "i"; if (a == "ang") a = "∠" } else if (a == "ang") return
        act(a)
    }

    /** Performs one resolved action (the names in [Key]). */
    fun act(a: String) {
        ed.wv = wvActive(); ed.insert = set.insert
        try {
            busy = true
            dispatch(a)
        } catch (e: CalcError) {
            err = e
        } catch (e: Cancelled) {
        } catch (e: StackOverflowError) {
            err = CalcError(3)
        } finally { busy = false }
    }

    private fun dispatch(a: String) {
        if (defPending >= 0) {
            val d = defPending
            defPending = -1
            msg = null
            if (isInsertable(a)) { dmem[d] = "act:$a"; return }
            if (a == "onc") return
        }
        when (a) {
            "off" -> { on = false; screens.clear(); enterMode(); return }
            "home" -> { setMode(Mode.NORMAL); return }
            "mode" -> { err = null; closeMenus(); Menus.mode(this); return }
            "setup" -> { err = null; closeMenus(); Menus.setup(this); return }
            "mclr" -> { err = null; closeMenus(); Menus.mclr(this); return }
            "ca" -> { clearAll(); return }
        }
        if (err != null && screens.isNotEmpty()) { err = null; return }
        val s = screens.lastOrNull()
        if (s != null) s.key(a) else mainKey(a)
    }

    fun push(s: Screen) { screens.add(s) }
    fun pop() { if (screens.isNotEmpty()) screens.removeAt(screens.size - 1) }
    /** Closes menus and lists, keeping the root screen of modes that have one. */
    fun closeMenus() { while (screens.isNotEmpty() && (screens.last() as? Menu)?.root != true && screens.last() !is RootScreen) pop() }

    fun isInsertable(a: String) = a in DIRECT || a in setOf("intdiv", "sq", "inv", "cube", "pow", "10x", "ex", "sqrt", "cbrt", "root", "frac", "mixed", "logn", "abs", "sum", "prod", "integ", "diff")

    /** Types the function of action [a] into editor [e]. False if [a] is not something that can be typed. */
    fun insertAct(e: Editor, a: String): Boolean {
        val wv = e.wv
        fun t(vararg ids: String) { for (id in ids) if (!e.tok(id)) msg = "Buffer full!" }
        fun tm(k: K, pre: String? = null) { if (!e.tmpl(k, pre)) msg = "Buffer full!" }
        when (a) {
            "intdiv" -> t("int÷")
            "sq" -> t("²")
            "inv" -> t("⁻¹")
            "cube" -> t("³")
            "pow" -> if (wv) tm(K.POW) else t("^")
            "10x" -> { t("1", "0"); if (wv) tm(K.POW) else t("^") }
            "ex" -> { t("e"); if (wv) tm(K.POW) else t("^") }
            "sqrt" -> if (wv) tm(K.SQRT) else t("√")
            "cbrt" -> if (wv) tm(K.ROOT, "3") else t("³√")
            "root" -> if (wv) tm(K.ROOT) else t("x√")
            "frac" -> if (wv) tm(K.FRAC) else t("⌟")
            "mixed" -> if (wv) tm(K.MIXED) else t("⌟")
            "logn" -> if (wv) tm(K.LOGN) else t("logn(")
            "abs" -> if (wv) tm(K.ABS) else t("abs")
            "sum" -> if (wv) tm(K.SUM) else t("Σ(")
            "prod" -> if (wv) tm(K.PROD) else t("Π(")
            "integ" -> if (wv) tm(K.INT) else t("∫(")
            "diff" -> if (wv) tm(K.DIFF) else t("d/dx(")
            in DIRECT -> t(a)
            else -> return false
        }
        return true
    }

    /** Starts a new entry if a result is showing, then types [a]; operators continue from ANS (K-10). */
    private fun beginEntry(a: String) {
        if (res != null) { ed.clear(); res = null; scrollX = 0 }
        hidx = -1
        if (ed.isEmpty && a in NEEDS_ANS) ed.tok("Ans")
    }

    fun typeToken(id: String) { beginEntry(id); if (!ed.tok(id)) msg = "Buffer full!" }

    private fun mainKey(a: String) {
        if (msg != null) { msg = null; if (a == "onc") return }
        if (err != null) {
            val e = err!!
            when (a) {
                "left", "right" -> { err = null; res = null; e.pos?.let { ed.goTo(it) } }
                "onc" -> { err = null; ed.clear(); res = null }
            }
            return
        }
        when (a) {
            "onc" -> { ed.clear(); res = null; scrollX = 0; hidx = -1; kOp = null }
            "=" -> execute()
            "left" -> if (res != null) { res = null; ed.end() } else if (!ed.left()) ed.end()
            "right" -> if (res != null) { res = null; ed.home() } else if (!ed.right()) ed.home()
            "start" -> { res = null; ed.home() }
            "end" -> { res = null; ed.end() }
            "up" -> if (res != null || !ed.vert(true)) histMove(-1)
            "down" -> if (res != null || !ed.vert(false)) histMove(1)
            "top", "bottom" -> {}
            "bs", "del" -> {
                if (res != null) { res = null; ed.end() }
                if ((if (a == "bs") ed.bs() else ed.del()) == Editor.Del.CANNOT) msg = "Cannot delete!"
            }
            "m+", "m-" -> if (ensureResult(true)) {
                val v = res!!.value
                if (v is Num || v is Cx) ctx.vars["M"] = if (a == "m+") Ops.add(ctx.getVar("M"), v) else Ops.sub(ctx.getVar("M"), v)
            }
            "sto", "rcl" -> pend = a
            "change" -> res?.let { it.k = (it.k + 1) % it.forms.size }
            "todeg", "drg", "tohex", "tobin", "topen", "tooct", "todec", "pfact", "mdf", "eng<", "eng>", "torθ", "toxy" -> instruct(a)
            "math" -> Menus.math(this)
            "random" -> if (ctx.base == Base.DEC) Menus.random(this)
            "const" -> if (mode != Mode.TABLE && ctx.base == Base.DEC) Menus.constants(this)
            "conv" -> if (mode != Mode.TABLE && mode != Mode.COMPLEX && ctx.base == Base.DEC) Menus.conversions(this)
            "memory" -> Menus.memory(this)
            "stat" -> if (mode == Mode.STAT) Menus.stat(this)
            "data" -> if (mode == Mode.STAT) push(StatTable(this))
            "insd" -> {}
            "algb" -> if (mode == Mode.NORMAL) Tools.simulation(this)
            "solver" -> if (mode == Mode.NORMAL) Tools.solver(this)
            "d1", "d2", "d3" -> defKey("", a[1] - '1')
            "x′", "y′" -> if (mode == Mode.STAT) { beginEntry(a); insertAct(ed, a) }
            else -> { beginEntry(a); insertAct(ed, a) }
        }
    }

    // ------------------------------------------------------------------ evaluation

    private fun topOps(r: Row): List<Int> {
        val out = ArrayList<Int>()
        var depth = 0
        for ((k, n) in r.items.withIndex()) {
            val t = (n as? Tok)?.id ?: continue
            if (t.endsWith("(")) depth++ else if (t == ")") depth-- else if (depth == 0 && (t == "+" || t == "-" || t == "*" || t == "/")) out.add(k)
        }
        return out
    }
    private fun isLiteral(r: Row) = r.items.isNotEmpty() && r.items.all { it is Tok && (it.id.length == 1 && (it.id[0].isDigit() || it.id == "." || it.id == "E") || it.id == "neg") }

    /** Evaluates the entry line (=). Returns false on error or empty entry. */
    fun execute(): Boolean {
        if (ed.isEmpty) return false
        // constant calculation: after a+b=, a bare number followed by = reuses the operator and constant
        val ops = topOps(ed.root)
        if (kOp != null && ops.isEmpty() && isLiteral(ed.root) && res == null && (mode == Mode.NORMAL || mode == Mode.STAT) && ctx.base == Base.DEC) {
            val r = Row()
            if (kOp == "*") { r.items.addAll(kPart); r.items.add(Tok("*")); r.items.addAll(ed.root.items) }
            else { r.items.addAll(ed.root.items); r.items.add(Tok(kOp!!)); r.items.addAll(kPart) }
            ed.set(r)
        } else if (ops.size == 1 && ops[0] > 0 && ops[0] < ed.root.items.size - 1) {
            val o = ops[0]
            kOp = (ed.root.items[o] as Tok).id
            kPart = if (kOp == "*") ed.root.copy().items.subList(0, o).toList() else ed.root.copy().items.let { it.subList(o + 1, it.size).toList() }
        } else if (ops.isNotEmpty() || !isLiteral(ed.root)) kOp = null
        return try {
            val v = Parser.eval(ed.root, ctx)
            finish(v)
            true
        } catch (e: CalcError) { err = e; res = null; false }
    }

    private fun ansOf(v: V): V = when (v) { is QR -> Num(v.q); is Mat -> Num(v.a[0][0]); else -> v }

    fun finish(v: V) {
        ctx.ans = ansOf(v)
        res = view(v)
        scrollX = 0
        history.add(Pair(ed.root.copy(), res))
        while (history.size > 20) history.removeAt(0)
        hidx = -1
        if (v is Mat) { if (v.vec) vecBuf = v else matBuf = v; push(MatGrid(this, v.vec)) }
    }

    fun view(v: V) = ResultView(v, formsOf(v))

    fun numText(d: Double) = Fmt.num(d, set).let { if (set.comma) it.replace('.', ',') else it }

    fun cxText(z: Cx): List<String> {
        if (cplxPolar) {
            val r = numText(fin(Math.hypot(z.re, z.im)))
            val th = numText((Ops.arg(ctx, z) as Num).d)
            return if (r.length + th.length + 1 <= 16) listOf("$r∠$th") else listOf(r, "∠$th")
        }
        val re = numText(z.re)
        val im = (if (z.im < 0) "-" else "+") + numText(Math.abs(z.im)) + "i"
        return if (re.length + im.length <= 16) listOf(re + im) else listOf(re, im)
    }

    fun formsOf(v: V): List<List<Row>> = when (v) {
        is Num -> if (ctx.base != Base.DEC) listOf(listOf(textRow(R.toBaseString(R.baseFix(v.d, ctx.base), ctx.base))))
        else if (mode == Mode.COMPLEX && cplxPolar) listOf(cxText(Cx(v.d, 0.0)).map { textRow(it) })
        else Result.forms(v, set, wvActive(), ctx.fracUsed).map { listOf(it) }
        is Cx -> listOf(cxText(v).map { textRow(it) })
        is QR -> listOf(listOf(textRow("Q=" + numText(v.q).padStart(14)), textRow("R=" + numText(v.r).padStart(14))))
        is Mat -> listOf(listOf(textRow(if (v.vec) "VECTOR" else "MATRIX")))
    }

    /** Makes sure there is a result to work on: the shown one, the entry evaluated, or ANS. */
    private fun ensureResult(evalOnly: Boolean = false): Boolean {
        if (res != null && !evalOnly) return true
        if (res != null) return true
        if (!ed.isEmpty) return execute()
        res = view(ctx.ans)
        return true
    }

    private fun setValue(v: V) { ctx.ans = ansOf(v); res = view(v) }

    private fun instruct(a: String) {
        when (a) {
            "torθ", "toxy" -> {
                if (mode == Mode.COMPLEX) {
                    cplxPolar = a == "torθ"
                    res?.let { res = view(it.value) }
                    return
                }
                if (ed.isEmpty) return
                val l = Parser.evalList(ed.root, ctx)
                if (l.size != 2) throw CalcError(1, Pos(ed.root, ed.root.items.size))
                val p = Ops.real(l[0]).d; val q = Ops.real(l[1]).d
                val x: Double; val y: Double
                if (a == "torθ") { x = fin(Math.hypot(p, q)); y = fin(R.convertAngle(Math.atan2(q, p), Angle.RAD, ctx.angle)) }
                else { x = R.mul(Num(p), R.cos(Num(q), ctx.angle)).d; y = R.mul(Num(p), R.sin(Num(q), ctx.angle)).d }
                ctx.vars["X"] = Num(x); ctx.vars["Y"] = Num(y); ctx.ans = Num(x)
                val names = if (a == "torθ") listOf("r=", "θ=") else listOf("x=", "y=")
                res = ResultView(Num(x), listOf(listOf(textRow(names[0] + numText(x).padStart(14)), textRow(names[1] + numText(y).padStart(14)))))
                history.add(Pair(ed.root.copy(), res))
                return
            }
            "tohex", "tobin", "topen", "tooct", "todec" -> {
                if (mode != Mode.NORMAL) return
                val nb = when (a) { "tohex" -> Base.HEX; "tobin" -> Base.BIN; "topen" -> Base.PEN; "tooct" -> Base.OCT; else -> Base.DEC }
                if (res == null && !ed.isEmpty && !execute()) return
                val v = Ops.real(res?.value ?: ctx.ans).d
                val t = if (nb == Base.DEC) (if (v < 0) Math.ceil(v) else Math.floor(v)) else R.baseFix(v, nb)
                ctx.base = nb
                history.clear()
                ed.wv = wvActive()
                if (res != null || nb != Base.DEC) setValue(Num(t))
                return
            }
        }
        if (!ensureResult()) return
        val rv = res!!
        val v = rv.value as? Num ?: return
        when (a) {
            "todeg" -> setValue(Num(v.d, v.ex, !v.dms))
            "drg" -> {
                val next = Angle.values()[(ctx.angle.ordinal + 1) % 3]
                val c = fin(R.convertAngle(v.d, ctx.angle, next))
                ctx.angle = next
                history.clear()
                setValue(Num(c))
            }
            "pfact" -> {
                if (rv.prime) { res = view(v); return }
                if (ctx.base != Base.DEC || !isInt(v.d) || v.d < 2 || v.d > 9999999999.0) throw CalcError(2)
                res = ResultView(v, listOf(listOf(Result.primeRow(v.d.toLong(), wvActive())))).also { it.prime = true }
            }
            "mdf" -> if (ctx.base == Base.DEC) setValue(Num(Fmt.parse(Fmt.num(v.d, set))))
            "eng<", "eng>" -> if (ctx.base == Base.DEC) {
                val e = rv.eng?.let { it + (if (a == "eng>") 3 else -3) } ?: Fmt.engExp(v.d)
                if (e < -99 || e > 99) return
                res = ResultView(v, listOf(listOf(textRow(Fmt.engAt(v.d, e).let { if (set.comma) it.replace('.', ',') else it })))).also { it.eng = e }
            }
        }
    }

    private fun histMove(d: Int) {
        if (history.isEmpty()) return
        val cur = if (hidx >= 0) hidx else if (res != null) history.size - 1 else history.size
        val n = cur + d
        if (n < 0 || n >= history.size) return
        hidx = n
        ed.set(history[n].first.copy())
        res = history[n].second
        scrollX = 0
    }

    // ------------------------------------------------------------------ memories

    private fun memKey(p: String, name: String) {
        if (mode == Mode.COMPLEX && name != "M") return
        if (p == "rcl") { beginEntry(name); ed.tok(name); return }
        if (screens.isNotEmpty()) return
        if (res == null && !ed.isEmpty && !execute()) return
        val v = res?.value ?: ctx.ans
        if (v is Num || v is Cx) ctx.vars[name] = if (v is Num) Num(v.d, v.ex) else v
        if (res == null) res = view(v)
    }

    /** D1-D3: STO stores the entry line (or, with an empty line, the next function key); the key replays it. */
    private fun defKey(p: String, d: Int) {
        if (p == "sto") {
            if (mode == Mode.NORMAL && screens.isNotEmpty()) return
            if (!ed.isEmpty) dmem[d] = "row:" + ed.root.ser()
            else { defPending = d; msg = "D${d + 1}=?" }
            return
        }
        val m = dmem[d] ?: return
        if (m.startsWith("act:")) {
            val a = m.substring(4)
            if ((a == "x′" || a == "y′" || a.startsWith("st:")) && mode != Mode.STAT) { msg = "Cannot call!"; return }
            dispatch(a)
        } else {
            val s = screens.lastOrNull()
            if (s != null) return
            val r = Row.parse(m.substring(4))
            if (hasTmpl(r) && !wvActive()) { msg = "Cannot call!"; return }
            if (res != null) { ed.clear(); res = null }
            ed.insertRow(r)
        }
    }

    // ------------------------------------------------------------------ modes and clearing

    fun setMode(m: Mode, sub: Int = -1) {
        if (mode == Mode.COMPLEX && m != Mode.COMPLEX) {
            (ctx.vars["M"] as? Cx)?.let { ctx.vars["M"] = Num(it.re) }
            (ctx.ans as? Cx)?.let { ctx.ans = Num(it.re) }
        }
        mode = m
        ctx.base = Base.DEC
        for (k in 0 until 4) { ctx.mats[k] = null; ctx.vecs[k] = null }
        matBuf = null; vecBuf = null
        tblF1 = Row(); tblF2 = Row()
        if (m == Mode.STAT && sub >= 0) { if (sub != stat.sub) stat.rows.clear(); stat.sub = sub }
        enterMode()
        if (m == Mode.STAT && sub >= 0) push(StatTable(this))
    }

    /** Resets the entry and puts up the first screen of the current mode (M-12). */
    fun enterMode() {
        screens.clear()
        ed.clear(); res = null; err = null; msg = null; scrollX = 0
        history.clear(); hidx = -1
        kOp = null; pend = ""; hyp = 0; defPending = -1
        ctx.domain = when (mode) { Mode.COMPLEX -> Domain.COMPLEX; Mode.MATRIX -> Domain.MATRIX; Mode.VECTOR -> Domain.VECTOR; else -> Domain.REAL }
        ctx.stat = if (mode == Mode.STAT) stat else null
        ed.wv = wvActive()
        when (mode) {
            Mode.TABLE -> push(TableEntry(this, 1))
            Mode.EQUATION -> Menus.equation(this)
            Mode.DISTRIBUTION -> Menus.distribution(this)
            Mode.DRILL -> Menus.drill(this)
            else -> {}
        }
    }

    /** CA (2ndF MODE): clears the entry, ANS, statistics and matrices; A-F, X, Y, M and D1-D3 are kept, as in the manual. */
    fun clearAll() {
        ctx.ans = Num(0.0, Exact.ZERO)
        stat.rows.clear()
        for (k in 0 until 4) { ctx.mats[k] = null; ctx.vecs[k] = null }
        matBuf = null; vecBuf = null
        tblF1 = Row(); tblF2 = Row()
        enterMode()
    }

    fun clearMemory() {
        ctx.vars.clear()
        for (k in 0 until 3) dmem[k] = null
        clearAll()
    }

    fun initDisplay() {
        ctx.angle = Angle.DEG; set.fse = FSE.NORM1; set.tab = 0; set.recurring = false
        ctx.base = Base.DEC
        res?.let { res = view(it.value) }
    }

    fun reset() {
        initDisplay()
        set.wv = true; set.exact = true; set.insert = true; set.comma = false; set.contrast = 4; set.name = ""
        cplxPolar = false
        stat.sub = 0
        mode = Mode.NORMAL
        clearMemory()
    }

    // ------------------------------------------------------------------ display

    /** Draws editor [e] with its top at dot row [y]; returns where the cursor is. */
    fun drawEditor(l: Lcd, e: Editor, y: Int, showCursor: Boolean, reserve: Int = 0): Cur? {
        val height = Lcd.H - y - reserve
        if (e.wv || hasTmpl(e.root)) {
            val cp = CurPos()
            val box = Layout(if (showCursor) e.pos else null, cp).row(e.root, false, true)
            box.draw(Lcd(), 0, 0)
            if (!showCursor || box.w <= Lcd.W) scrollX = 0
            else {
                if (cp.x - scrollX > Lcd.W - 2) scrollX = cp.x - (Lcd.W - 8)
                if (cp.x - scrollX < 1) scrollX = Math.max(0, cp.x - 6)
            }
            var y0 = -box.top
            if (showCursor && cp.found) {
                if (y0 + cp.bot > height - 1) y0 -= (y0 + cp.bot) - (height - 1)
                if (y0 + cp.top < 0) y0 -= (y0 + cp.top)
            }
            val t = Lcd()
            box.draw(t, -scrollX, y0)
            for (yy in 0 until height) for (xx in 0 until Lcd.W) if (t.get(xx, yy)) l.set(xx, yy + y)
            if (y == 0) for (r in 0 until 4) for (c in 0 until 16) if (t.chars[r][c] != ' ' && r * 8 + 7 < height) l.chars[r][c] = t.chars[r][c]
            if (scrollX > 0) arrows.add("←")
            if (box.w - scrollX > Lcd.W) arrows.add("→")
            if (y0 + box.top < 0) arrows.add("↑")
            if (y0 + box.bot > height - 1) arrows.add("↓")
            return if (showCursor && cp.found) Cur(Math.max(0, cp.x - 1), cp.top + y, cp.bot + y) else null
        }
        // Line editor: text wraps over the lines
        val sb = StringBuilder()
        var curChar = 0
        for ((k, n) in e.root.items.withIndex()) {
            if (k == e.idx && e.row === e.root) curChar = sb.length
            sb.append(Disp.text((n as Tok).id))
        }
        if (e.idx >= e.root.items.size) curChar = sb.length
        val avail = Math.max(1, height / 8)
        val total = Math.max(1, (sb.length + 16) / 16)
        val cl = curChar / 16
        var first = 0
        if (showCursor && cl >= avail) first = cl - avail + 1
        for (k in 0 until avail) {
            val s = (first + k) * 16
            if (s >= sb.length) break
            l.text(sb.substring(s, Math.min(sb.length, s + 16)), 0, y + k * 8 + 1)
        }
        if (first > 0) arrows.add("↑")
        if (first + avail < total && sb.length > (first + avail) * 16) arrows.add("↓")
        return if (showCursor) Cur(Math.max(0, (curChar % 16) * 6 - 1), y + (cl - first) * 8 + 1, y + (cl - first) * 8 + 7) else null
    }

    fun drawResult(l: Lcd, rv: ResultView) {
        val boxes = rv.forms[rv.k].map { Layout(null).row(it, false, true) }
        val total = boxes.sumOf { it.bot - it.top + 1 }
        var y = Lcd.H - total
        l.clearRect(0, Math.max(0, y), Lcd.W - 1, Lcd.H - 1)
        for (b in boxes) {
            b.draw(l, Math.max(0, Lcd.W - b.w), y - b.top)
            if (b.w - 1 > Lcd.W) arrows.add("→")
            y += b.bot - b.top + 1
        }
    }

    private fun drawMain(l: Lcd): Cur? {
        msg?.let { l.line(0, it); return null }
        val rv = res
        val reserve = rv?.let { r -> Layout(null).let { lay -> r.forms[r.k].sumOf { f -> lay.row(f, false, true).let { it.bot - it.top + 1 } } } } ?: 0
        val cur = drawEditor(l, ed, 0, rv == null, 0)
        if (rv != null) { if (reserve > 0) drawResult(l, rv) }
        return cur
    }

    fun display(): Display {
        val l = Lcd()
        arrows.clear()
        var cur: Cur? = null
        if (on) {
            val e = err
            if (e != null) {
                l.line(0, "ERROR " + e.code.toString().padStart(2, '0'))
                l.line(1, ERR[e.code] ?: "")
            } else cur = screens.lastOrNull()?.draw(l) ?: if (screens.isEmpty()) drawMain(l) else null
        } else if (set.name.isNotEmpty()) {
            l.line(1, set.name.take(16)); if (set.name.length > 16) l.line(2, set.name.drop(16).take(16))
        }
        val ann = HashSet<String>(arrows)
        if (on) {
            if (shift == Shift.SECOND) ann.add("2ndF")
            if (shift == Shift.ALPHA) ann.add("ALPHA")
            if (hyp != 0) { ann.add("HYP"); if (hyp == 2) ann.add("2ndF") }
            if (pend == "sto") ann.add("STO")
            if (pend == "rcl") ann.add("RCL")
            ann.add(when (set.fse) { FSE.FIX -> "FIX"; FSE.SCI -> "SCI"; FSE.ENG -> "ENG"; FSE.NORM1 -> "N1"; FSE.NORM2 -> "N2" })
            ann.add(ctx.angle.name)
            if (busy) ann.add("BUSY")
            if (wvActive()) ann.add("W-VIEW")
            val m = ctx.vars["M"]
            if (m is Num && m.d != 0.0 || m is Cx) ann.add("M")
            if (ctx.base != Base.DEC) ann.add(ctx.base.name)
            if (mode == Mode.COMPLEX) ann.add(if (cplxPolar) "rθ" else "xy")
            if (mode != Mode.NORMAL) ann.add(mode.title)
        }
        return Display(l.px, ann, l.textLines(), on && cur != null, cur?.x ?: 0, cur?.top ?: 0, cur?.bot ?: 0, !on, set.contrast)
    }

    // ------------------------------------------------------------------ state (N-2, D-6)

    private fun enc(v: V?) = SavedValue.encode(v)
    private fun dec(s: String) = SavedValue.decode(s)
    private fun encMat(m: Mat?) = if (m == null) "" else (if (m.vec) "v" else "m") + m.a.joinToString(";") { it.joinToString(",") }
    private fun decMat(s: String): Mat? = if (s.isEmpty()) null else Mat(s.substring(1).split(";").map { r -> r.split(",").map { it.toDouble() }.toDoubleArray() }.toTypedArray(), s[0] == 'v')

    fun save(): String {
        val m = LinkedHashMap<String, String>()
        m["v"] = "1"
        m["on"] = on.toString(); m["mode"] = mode.name; m["sub"] = stat.sub.toString()
        m["angle"] = ctx.angle.name; m["base"] = ctx.base.name
        m["fse"] = set.fse.name; m["tab"] = set.tab.toString(); m["wv"] = set.wv.toString(); m["exact"] = set.exact.toString()
        m["insert"] = set.insert.toString(); m["recurring"] = set.recurring.toString(); m["comma"] = set.comma.toString()
        m["contrast"] = set.contrast.toString(); m["name"] = set.name; m["polar"] = cplxPolar.toString()
        for ((k, v) in ctx.vars) m["var.$k"] = enc(v)
        m["ans"] = enc(ctx.ans)
        for (k in 0 until 3) dmem[k]?.let { m["d$k"] = it }
        m["entry"] = ed.root.ser(); m["cursor"] = ed.cursorPath(); m["shown"] = (res != null).toString()
        SavedValue.putResult(m, "result", res)
        m["hist"] = history.size.toString()
        for ((k, h) in history.withIndex()) {
            m["hist.$k"] = h.first.ser()
            SavedValue.putResult(m, "hist.$k.result", h.second)
        }
        m["stat"] = stat.rows.joinToString(";") { it.joinToString(",") }
        for (k in 0 until 4) { m["mat$k"] = encMat(ctx.mats[k]); m["vec$k"] = encMat(ctx.vecs[k]) }
        m["matbuf"] = encMat(matBuf); m["vecbuf"] = encMat(vecBuf)
        m["tf1"] = tblF1.ser(); m["tf2"] = tblF2.ser()
        return m.entries.joinToString("\n") { it.key + "=" + it.value.replace("\n", " ") }
    }

    fun load(s: String) {
        try {
            val m = HashMap<String, String>()
            for (line in s.split("\n")) { val i = line.indexOf('='); if (i > 0) m[line.substring(0, i)] = line.substring(i + 1) }
            if (m["v"] != "1") return
            fun b(k: String, d: Boolean) = m[k]?.toBoolean() ?: d
            mode = Mode.valueOf(m["mode"] ?: "NORMAL")
            stat.sub = m["sub"]?.toInt() ?: 0
            set.fse = FSE.valueOf(m["fse"] ?: "NORM1"); set.tab = m["tab"]?.toInt() ?: 0
            set.wv = b("wv", true); set.exact = b("exact", true); set.insert = b("insert", true)
            set.recurring = b("recurring", false); set.comma = b("comma", false)
            set.contrast = m["contrast"]?.toInt() ?: 4; set.name = m["name"] ?: ""
            cplxPolar = b("polar", false)
            ctx.vars.clear()
            for ((k, v) in m) if (k.startsWith("var.")) ctx.vars[k.substring(4)] = dec(v)
            for (k in 0 until 3) dmem[k] = m["d$k"]
            stat.rows.clear()
            m["stat"]?.takeIf { it.isNotEmpty() }?.split(";")?.forEach { r -> stat.rows.add(r.split(",").map { it.toDouble() }.toDoubleArray()) }
            enterMode()
            on = b("on", true)
            ctx.angle = Angle.valueOf(m["angle"] ?: "DEG")
            ctx.base = Base.valueOf(m["base"] ?: "DEC")
            ctx.ans = dec(m["ans"] ?: "n:0.0")
            for (k in 0 until 4) { ctx.mats[k] = decMat(m["mat$k"] ?: ""); ctx.vecs[k] = decMat(m["vec$k"] ?: "") }
            matBuf = decMat(m["matbuf"] ?: ""); vecBuf = decMat(m["vecbuf"] ?: "")
            tblF1 = Row.parse(m["tf1"] ?: ""); tblF2 = Row.parse(m["tf2"] ?: "")
            val n = m["hist"]?.toInt() ?: 0
            for (k in 0 until n) m["hist.$k"]?.let { history.add(Pair(Row.parse(it), SavedValue.getResult(m, "hist.$k.result"))) }
            ed.wv = wvActive()
            ed.set(Row.parse(m["entry"] ?: ""))
            ed.restoreCursor(m["cursor"] ?: "")
            if (b("shown", false) && !ed.isEmpty && screens.isEmpty()) {
                // Older saves contain ANS but no result snapshot. Restore that value without
                // re-running expressions that depend on ANS, memories, or random numbers.
                res = SavedValue.getResult(m, "result") ?: view(ctx.ans)
            }
            if (mode == Mode.TABLE && !tblF1.isEmpty) (screens.lastOrNull() as? TableEntry)?.ed?.set(tblF1.copy())
        } catch (e: Exception) {
            reset()
        }
    }
}
