package sharpcalc.calc

import sharpcalc.engine.*

/** Marks the first screen of a mode that has no entry line of its own (TABLE, EQUATION, ...). */
interface RootScreen

class MenuItem(val label: String, val run: () -> Unit)

/**
 * Multi-level menu on the LCD (K-11): a digit picks an item, up/down turn pages, BS goes back, ON/C leaves.
 * Lists longer than ten items are either two-digit lists (CONST, CONV) or numbered per page.
 */
class Menu(val c: Calculator, val items: List<MenuItem>, val twoDigit: Boolean = false, val root: Boolean = false, val title: String? = null) : Screen() {
    private val global = twoDigit || items.size <= 10
    private val pages = ArrayList<List<List<Int>>>()
    var page = 0
    private var first = -1

    init {
        val perPage = if (title != null) 3 else 4
        val pre = if (twoDigit) 3 else 2
        var lines = ArrayList<List<Int>>()
        var line = ArrayList<Int>()
        var len = 0
        for (i in items.indices) {
            val w = pre + items[i].label.length
            if (line.isNotEmpty() && len + 1 + w > 16) {
                lines.add(line); line = ArrayList(); len = 0
                if (lines.size == perPage) { pages.add(lines); lines = ArrayList() }
            }
            len += (if (line.isEmpty()) 0 else 1) + w
            line.add(i)
        }
        if (line.isNotEmpty()) lines.add(line)
        if (lines.isNotEmpty()) pages.add(lines)
    }

    private fun label(i: Int, posInPage: Int) = when {
        twoDigit -> (i + 1).toString().padStart(2, '0') + ":" + items[i].label
        global -> "$i:" + items[i].label
        else -> "$posInPage:" + items[i].label
    }

    override fun key(a: String) {
        if (a.length == 1 && a[0].isDigit()) {
            val d = a[0] - '0'
            if (twoDigit) {
                if (first < 0) {
                    first = d
                    val target = pages.indexOfFirst { p -> p.flatten().any { (it + 1) / 10 == d } }
                    if (target >= 0) page = target
                } else {
                    val n = first * 10 + d
                    first = -1
                    if (n in 1..items.size) items[n - 1].run()
                }
            } else if (global) { if (d < items.size) items[d].run() }
            else { val f = pages[page].flatten(); if (d < f.size) items[f[d]].run() }
            return
        }
        first = -1
        when (a) {
            "down", "right" -> page = Math.min(page + 1, pages.size - 1)
            "up", "left" -> page = Math.max(page - 1, 0)
            "top", "start" -> page = 0
            "bottom", "end" -> page = pages.size - 1
            "bs" -> if (!root) c.pop()
            "onc" -> if (!root) c.closeMenus()
        }
    }

    override fun draw(l: Lcd): Cur? {
        var y = 0
        if (title != null) { l.line(0, title); y = 1 }
        var pos = 0
        for (line in pages[page]) {
            var col = 0
            for (i in line) { val s = label(i, pos); l.line(y, s, col); col += s.length + 1; pos++ }
            y++
        }
        if (page > 0) c.arrows.add("↑")
        if (page < pages.size - 1) c.arrows.add("↓")
        if (twoDigit && first >= 0) l.right(3, first.toString() + "_")
        return null
    }
}

class DigitPrompt(val c: Calculator, val text: String, val onDigit: (Int) -> Unit) : Screen() {
    override fun key(a: String) {
        if (a.length == 1 && a[0].isDigit()) onDigit(a[0] - '0')
        else if (a == "bs") c.pop() else if (a == "onc") c.closeMenus()
    }
    override fun draw(l: Lcd): Cur? { l.line(0, text); return Cur(text.length * 6, 1, 7) }
}

/** Scrollable list of "label value" lines (MEMORY list, statistics, equation results). */
class ListScreen(val c: Calculator, val rows: List<Pair<String, String>>, val onClose: () -> Unit = { c.pop() }) : Screen() {
    var top = 0
    override fun key(a: String) {
        val max = Math.max(0, rows.size - 4)
        when (a) {
            "down" -> top = Math.min(max, top + 1)
            "up" -> top = Math.max(0, top - 1)
            "top" -> top = 0
            "bottom" -> top = max
            "onc", "=", "bs" -> onClose()
        }
    }
    override fun draw(l: Lcd): Cur? {
        for (k in 0 until 4) {
            val r = rows.getOrNull(top + k) ?: break
            l.line(k, r.first)
            l.right(k, r.second.takeLast(16 - r.first.length))
        }
        if (top > 0) c.arrows.add("↑")
        if (top + 4 < rows.size) c.arrows.add("↓")
        return null
    }
}

class Field(val label: String, var value: Double?)

/** A column of numeric input fields; each accepts an expression and ENTER (equation coefficients, Solver, ...). */
class Form(val c: Calculator, val title: String?, val fields: List<Field>, val onDone: (Form) -> Unit, val onExit: () -> Unit) : Screen() {
    var cur = 0
    val ed = Editor().apply { wv = false }

    override fun key(a: String) {
        ed.insert = c.set.insert
        when (a) {
            "=" -> {
                if (!ed.isEmpty) {
                    fields[cur].value = Ops.real(Parser.eval(ed.root, c.ctx)).d
                    ed.clear()
                }
                if (fields[cur].value == null) return
                if (cur == fields.size - 1) onDone(this) else cur++
            }
            "up" -> if (ed.isEmpty && cur > 0) cur--
            "down" -> if (ed.isEmpty && cur < fields.size - 1 && fields[cur].value != null) cur++
            "top" -> if (ed.isEmpty) cur = 0
            "bottom" -> if (ed.isEmpty && fields.all { it.value != null }) cur = fields.size - 1
            "onc" -> if (!ed.isEmpty) ed.clear() else onExit()
            "left" -> ed.left()
            "right" -> ed.right()
            "bs" -> ed.bs()
            "del" -> ed.del()
            else -> c.insertAct(ed, a)
        }
    }

    override fun draw(l: Lcd): Cur? {
        var y = 0
        if (title != null) { l.line(0, title); y = 1 }
        val vis = 4 - y
        val first = Math.max(0, Math.min(cur - vis + 1, fields.size - vis)).coerceAtLeast(if (cur < vis) 0 else cur - vis + 1)
        var cursor: Cur? = null
        for (k in 0 until vis) {
            val f = fields.getOrNull(first + k) ?: break
            l.line(y + k, f.label)
            if (first + k == cur && !ed.isEmpty) cursor = Draw.entry(l, ed, y + k, f.label.length)
            else {
                f.value?.let { l.right(y + k, Fmt.cell(it, 16 - f.label.length - 1)) }
                if (first + k == cur) cursor = Cur(f.label.length * 6, (y + k) * 8 + 1, (y + k) * 8 + 7)
            }
        }
        if (first > 0) c.arrows.add("↑")
        if (first + vis < fields.size) c.arrows.add("↓")
        return cursor
    }
}

object Draw {
    /** One-line entry field starting at text column [col]; shows the part around the cursor. */
    fun entry(l: Lcd, e: Editor, line: Int, col: Int): Cur {
        val sb = StringBuilder()
        var curChar = -1
        for ((k, n) in e.root.items.withIndex()) {
            if (k == e.idx) curChar = sb.length
            sb.append(if (n is Tok) Disp.text(n.id) else "?")
        }
        if (curChar < 0) curChar = sb.length
        val avail = 16 - col
        val off = Math.max(0, curChar - (avail - 1))
        l.line(line, sb.substring(off, Math.min(sb.length, off + avail)), col)
        return Cur(Math.max(0, (col + curChar - off) * 6 - 1), line * 8 + 1, line * 8 + 7)
    }
    fun cellInvert(l: Lcd, line: Int, col: Int, width: Int) = l.invert(col * 6 - (if (col > 0) 1 else 0), line * 8, (col + width) * 6 - 1, line * 8 + 7)
}

// ---------------------------------------------------------------------------- menus

object Menus {
    private fun item(label: String, run: () -> Unit) = MenuItem(label, run)
    private fun done(c: Calculator) { c.closeMenus(); c.res?.let { c.res = c.view(it.value) } }
    private fun newEntry(c: Calculator) { c.closeMenus(); c.ed.clear(); c.res = null; c.history.clear(); c.ed.wv = c.wvActive() }
    private fun tok(c: Calculator, label: String, id: String = label) = item(label) { c.closeMenus(); c.typeToken(id) }
    private fun actItem(c: Calculator, label: String, a: String) = item(label) { c.closeMenus(); c.act(a) }

    fun mode(c: Calculator) {
        c.push(Menu(c, Mode.values().map { m ->
            item(m.title) {
                if (m == Mode.STAT) c.push(Menu(c, StatData.SUBS.mapIndexed { k, s -> item(s) { c.setMode(Mode.STAT, k) } }))
                else c.setMode(m)
            }
        }))
    }

    fun setup(c: Calculator) {
        fun tab(f: FSE, q: String) = c.push(DigitPrompt(c, q) { d -> c.set.fse = f; c.set.tab = d; done(c) })
        c.push(Menu(c, listOf(
            item("DRG") { c.push(Menu(c, Angle.values().map { u -> item(u.name) { c.ctx.angle = u; done(c) } })) },
            item("FSE") { c.push(Menu(c, listOf(
                item("FIX") { tab(FSE.FIX, "TAB(0-9)?") },
                item("SCI") { tab(FSE.SCI, "SIG(0-9)?") },
                item("ENG") { tab(FSE.ENG, "TAB(0-9)?") },
                item("NORM1") { c.set.fse = FSE.NORM1; done(c) },
                item("NORM2") { c.set.fse = FSE.NORM2; done(c) },
            ))) },
            item("EDITOR") { c.push(Menu(c, listOf(
                item("W-VIEW") { c.push(Menu(c, listOf(
                    item("EXACT(a/b,√,π)") { c.set.wv = true; c.set.exact = true; newEntry(c) },
                    item("APPROX.") { c.set.wv = true; c.set.exact = false; newEntry(c) },
                ))) },
                item("LINE") { c.set.wv = false; newEntry(c) },
            ))) },
            item("CTRST") { c.push(Contrast(c)) },
            item("INS/OVR") { c.push(Menu(c, listOf(
                item("INSERT") { c.set.insert = true; done(c) },
                item("OVERWRITE") { c.set.insert = false; done(c) },
            ))) },
            item("RECUR") { c.push(Menu(c, listOf(
                item("OFF") { c.set.recurring = false; done(c) },
                item("ON") { c.set.recurring = true; done(c) },
            ))) },
            item("DECIMAL") { c.push(Menu(c, listOf(
                item("DOT") { c.set.comma = false; done(c) },
                item("COMMA") { c.set.comma = true; done(c) },
            ))) },
            item("NAME") { c.push(NameEdit(c)) },
        )))
    }

    fun mclr(c: Calculator) {
        fun confirm(what: () -> Unit) = c.push(Menu(c, listOf(item("CLEAR") { what() }, item("CANCEL") { c.closeMenus() })))
        c.push(Menu(c, listOf(
            item("DISP") { c.initDisplay(); c.closeMenus() },
            item("MEMORY") { confirm { c.clearMemory() } },
            item("RESET") { confirm { c.reset() } },
        )))
    }

    fun random(c: Calculator) = c.push(Menu(c, listOf(tok(c, "RANDOM"), tok(c, "R.Dice"), tok(c, "R.Coin"), tok(c, "R.Int("))))

    fun constants(c: Calculator) = c.push(Menu(c, Consts.list.mapIndexed { k, k0 ->
        tok(c, k0.sym, "k" + (k + 1).toString().padStart(2, '0'))
    }, twoDigit = true))

    fun conversions(c: Calculator) = c.push(Menu(c, Consts.conv.mapIndexed { k, cv ->
        tok(c, cv.from + "►" + cv.to, "cv" + (k + 1).toString().padStart(2, '0'))
    }, twoDigit = true))

    fun memory(c: Calculator) {
        val names = if (c.mode == Mode.COMPLEX) listOf("M") else listOf("A", "B", "C", "D", "E", "F", "X", "Y", "M")
        c.push(ListScreen(c, names.map { n ->
            val v = c.ctx.getVar(n)
            Pair("$n=", if (v is Cx) c.cxText(v).joinToString("") else Fmt.cell((v as Num).d, 9).let { if (it.contains('.') || it.contains(Fmt.EXP)) it else "$it." })
        }))
    }

    private fun prefixes(c: Calculator) = Menu(c, listOf("k" to "pk", "M" to "pM", "G" to "pG", "T" to "pT", "m" to "pm", "μ" to "pμ", "n" to "pn", "p" to "pp", "f" to "pf").map { tok(c, it.first, it.second) })

    fun math(c: Calculator) {
        val common = listOf(actItem(c, "abs", "abs"), tok(c, "ipart"), tok(c, "int"), tok(c, "fpart"), tok(c, "(%)"))
        fun pick(label: String, vec: Boolean, f: (Int) -> Unit) = item(label) {
            c.push(Menu(c, (0 until 4).map { k -> item((if (vec) "vect" else "mat") + ('A' + k)) { f(k) } }))
        }
        fun lin(vec: Boolean): List<MenuItem> {
            val pre = if (vec) "vect" else "mat"
            return listOf(
                item("NAME") { c.push(Menu(c, (0 until 4).map { k -> tok(c, pre + ('A' + k)) })) },
                item("EDIT") { Tools.matEdit(c, vec) },
                pick("LOAD", vec) { k ->
                    val m = (if (vec) c.ctx.vecs[k] else c.ctx.mats[k]) ?: throw CalcError(10)
                    val copy = Mat(Array(m.rows) { m.a[it].clone() }, m.vec)
                    if (vec) c.vecBuf = copy else c.matBuf = copy
                    c.closeMenus(); c.push(MatGrid(c, vec))
                },
                pick("STORE", vec) { k ->
                    val m = (if (vec) c.vecBuf else c.matBuf) ?: throw CalcError(10)
                    val copy = Mat(Array(m.rows) { m.a[it].clone() }, m.vec)
                    if (vec) c.ctx.vecs[k] = copy else c.ctx.mats[k] = copy
                    c.closeMenus()
                },
            )
        }
        val items = when (c.mode) {
            Mode.NORMAL -> if (c.ctx.base != Base.DEC) return else common + item("ENG PREFIX") { c.push(prefixes(c)) }
            Mode.STAT -> common + listOf(tok(c, "→t"), tok(c, "P("), tok(c, "Q("), tok(c, "R("))
            Mode.COMPLEX -> listOf(tok(c, "abs"), tok(c, "conj("), tok(c, "arg("), tok(c, "real("), tok(c, "img("))
            Mode.MATRIX -> lin(false) + listOf(tok(c, "det"), tok(c, "trans"), tok(c, "identity"), tok(c, "dim("), tok(c, "fill("),
                item("MORE") { c.push(Menu(c, listOf(tok(c, "rand_mat("), tok(c, "ref("), tok(c, "rref("), tok(c, "abs"), tok(c, "ipart"), tok(c, "int"), tok(c, "fpart")))) })
            Mode.VECTOR -> lin(true) + listOf(tok(c, "DotPro("), tok(c, "CrossPro("), tok(c, "Angl("), tok(c, "Unit("), tok(c, "abs"))
            else -> return
        }
        c.push(Menu(c, items))
    }

    private fun statList(c: Calculator, ids: List<String>) = ListScreen(c, ids.map { id ->
        Pair("$id=", try { c.numText(c.stat.value(id)) } catch (e: CalcError) { "-------" })
    })

    fun stat(c: Calculator) {
        val two = c.stat.twoVar
        fun vars(ids: List<String>) = Menu(c, ids.map { tok(c, it, "st:$it") })
        val xv = listOf(StatData.XB, "sx", "s²x", "σx", "σ²x", "n")
        val yv = listOf(StatData.YB, "sy", "s²y", "σy", "σ²y")
        val sums = listOf("Σx", "Σx²") + if (two) listOf("Σy", "Σy²", "Σxy", "Σx²y", "Σx³", "Σx⁴") else emptyList()
        val mm = listOf("xmin", "xmax") + (if (two) listOf("ymin", "ymax") else emptyList()) + StatData.Q_VARS
        val reg = if (c.stat.sub == 2) listOf("a", "b", "c", "R²") else listOf("a", "b", "r", "r²")
        val all = StatData.X_VARS + (if (two) StatData.Y_VARS else emptyList()) + StatData.Q_VARS
        val items = arrayListOf(
            item("STAT VALUES") { c.closeMenus(); c.push(statList(c, all)) },
            item("REG. COEF") { if (two) { c.closeMenus(); c.push(statList(c, reg)) } },
            item("VAR") { c.push(vars(xv + if (two) yv else emptyList())) },
            item("Σ VAR") { c.push(vars(sums)) },
            item("MAX/MIN") { c.push(vars(mm)) },
            item("REG. VAR") { if (two) c.push(vars(reg)) },
        )
        c.push(Menu(c, items))
    }

    fun equation(c: Calculator) {
        fun form(labels: List<String>, solve: (DoubleArray) -> List<Pair<String, String>>) {
            c.push(Form(c, null, labels.map { Field("$it=", 0.0) }, { f ->
                val rows = solve(DoubleArray(f.fields.size) { f.fields[it].value!! })
                c.push(ListScreen(c, rows))
            }, { c.pop() }))
        }
        fun root(name: String, z: Cx): List<Pair<String, String>> =
            if (z.im == 0.0) listOf(Pair("$name=", c.numText(z.re)))
            else listOf(Pair("$name=", c.numText(z.re)), Pair("", (if (z.im < 0) "-" else "+") + c.numText(Math.abs(z.im)) + "i"))
        c.push(Menu(c, listOf(
            item("2-VLE") { form(listOf("a1", "b1", "c1", "a2", "b2", "c2")) { v ->
                val r = Lin.linear(arrayOf(v.copyOfRange(0, 3), v.copyOfRange(3, 6)))
                listOf(Pair("X=", c.numText(r[0])), Pair("Y=", c.numText(r[1])), Pair("D=", c.numText(r[2])))
            } },
            item("3-VLE") { form(listOf("a1", "b1", "c1", "d1", "a2", "b2", "c2", "d2", "a3", "b3", "c3", "d3")) { v ->
                val r = Lin.linear(arrayOf(v.copyOfRange(0, 4), v.copyOfRange(4, 8), v.copyOfRange(8, 12)))
                listOf(Pair("X=", c.numText(r[0])), Pair("Y=", c.numText(r[1])), Pair("Z=", c.numText(r[2])), Pair("D=", c.numText(r[3])))
            } },
            item("QUAD") { form(listOf("a", "b", "c")) { v ->
                val r = Lin.quadratic(v[0], v[1], v[2])
                val out = ArrayList<Pair<String, String>>()
                if (r[0].re == r[1].re && r[0].im == 0.0) out.addAll(root("X", r[0]))
                else { out.addAll(root("X1", r[0])); out.addAll(root("X2", r[1])) }
                val xm = fin(-v[1] / (2 * v[0]))
                val ym = fin(v[2] - v[1] * v[1] / (4 * v[0]))
                out.add(Pair(if (v[0] > 0) "MIN" else "MAX", ""))
                out.add(Pair("X=", c.numText(xm)))
                out.add(Pair("Y=", c.numText(ym)))
                out
            } },
            item("CUBIC") { form(listOf("a", "b", "c", "d")) { v ->
                val r = Lin.cubic(v[0], v[1], v[2], v[3])
                r.withIndex().flatMap { root("X${it.index + 1}", it.value) }
            } },
        ), root = true))
    }

    fun distribution(c: Calculator) {
        fun form(title: String, labels: List<Pair<String, Double?>>, f: (DoubleArray) -> Double) {
            c.push(Form(c, title, labels.map { Field(it.first, it.second) }, { fm ->
                val v = fin(Dist.trunc9(f(DoubleArray(fm.fields.size) { fm.fields[it].value!! })))
                c.ctx.ans = Num(v)
                c.push(ListScreen(c, listOf(Pair(title, ""), Pair("ANS=", ""), Pair("", c.numText(v)))))
            }, { c.pop() }))
        }
        val mu = Pair("μ:", null as Double?); val sd = Pair("σ:", null as Double?)
        fun q(s: String) = Pair(s, null as Double?)
        c.push(Menu(c, listOf(
            item("NORMAL") { c.push(Menu(c, listOf(
                item("Normal pdf") { form("Normal pdf", listOf(q("x:"), mu, sd)) { Dist.normalPdf(it[0], it[1], it[2]) } },
                item("Normal cdf") { form("Normal cdf", listOf(q("x1:"), q("x2:"), mu, sd)) { Dist.normalCdf(it[0], it[1], it[2], it[3]) } },
                item("Inverse Normal") { form("Inverse Normal", listOf(q("a:"), mu, sd)) { Dist.invNormal(it[0], it[1], it[2]) } },
            ))) },
            item("BINOMINAL") { c.push(Menu(c, listOf(
                item("Binomial pdf") { form("Binomial pdf", listOf(q("x:"), q("n:"), q("p:"))) { Dist.binomPdf(it[0], it[1], it[2]) } },
                item("Binomial cdf") { form("Binomial cdf", listOf(q("x:"), q("n:"), q("p:"))) { Dist.binomCdf(it[0], it[1], it[2]) } },
            ))) },
            item("POISSON") { c.push(Menu(c, listOf(
                item("Poisson pdf") { form("Poisson pdf", listOf(q("x:"), mu)) { Dist.poissonPdf(it[0], it[1]) } },
                item("Poisson cdf") { form("Poisson cdf", listOf(q("x:"), mu)) { Dist.poissonCdf(it[0], it[1]) } },
            ))) },
        ), root = true))
    }

    fun drill(c: Calculator) = c.push(Menu(c, listOf(
        item("MATH DRILL") { c.push(DrillSetup(c, false)) },
        item("× TABLE") { c.push(DrillSetup(c, true)) },
    ), root = true))
}

// ---------------------------------------------------------------------------- small screens

class Contrast(val c: Calculator) : Screen() {
    override fun key(a: String) {
        when (a) {
            "+" -> c.set.contrast = Math.min(8, c.set.contrast + 1)
            "-" -> c.set.contrast = Math.max(0, c.set.contrast - 1)
            "onc", "bs", "=" -> c.closeMenus()
        }
    }
    override fun draw(l: Lcd): Cur? {
        l.line(0, "LCD CONTRAST")
        l.line(1, "[+] [-]")
        l.line(2, "■".repeat(c.set.contrast + 1))
        return null
    }
}

/** SET UP 7: the name shown for a moment at power-off (up to 32 characters). */
class NameEdit(val c: Calculator) : Screen() {
    private val chars = " ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.-"
    private val buf = c.set.name.padEnd(32).take(32).toCharArray()
    private var pos = 0
    override fun key(a: String) {
        when (a) {
            "up", "down" -> {
                val k = Math.max(0, chars.indexOf(buf[pos]))
                buf[pos] = chars[(k + (if (a == "up") 1 else chars.length - 1)) % chars.length]
            }
            "left" -> pos = Math.max(0, pos - 1)
            "right" -> pos = Math.min(31, pos + 1)
            "=" -> { c.set.name = String(buf).trimEnd(); c.closeMenus() }
            "onc", "bs" -> c.closeMenus()
        }
    }
    override fun draw(l: Lcd): Cur? {
        l.line(0, "NAME:")
        l.line(1, String(buf, 0, 16)); l.line(2, String(buf, 16, 16))
        val line = 1 + pos / 16
        return Cur((pos % 16) * 6, line * 8 + 1, line * 8 + 7)
    }
}

// ---------------------------------------------------------------------------- STAT data table (M-2)

class StatTable(val c: Calculator) : Screen() {
    var r = c.stat.rows.size
    var col = 0
    val ed = Editor().apply { wv = false }
    private val rows get() = c.stat.rows
    private val ncol get() = if (c.stat.twoVar) 3 else 2
    private fun at(k: Int) = if (c.stat.twoVar) k else if (k == 0) 0 else 2

    override fun key(a: String) {
        ed.insert = c.set.insert
        when (a) {
            "=" -> enter()
            "onc" -> if (!ed.isEmpty) ed.clear() else c.pop()
            "data" -> c.pop()
            "up" -> if (ed.isEmpty && r > 0) r--
            "down" -> if (ed.isEmpty && r < rows.size) r++
            "left" -> if (!ed.isEmpty) ed.left() else if (col > 0) col--
            "right" -> if (!ed.isEmpty) ed.right() else if (r < rows.size && col < ncol - 1) col++
            "top" -> if (ed.isEmpty) r = 0
            "bottom" -> if (ed.isEmpty) r = rows.size
            "del" -> if (!ed.isEmpty) ed.del() else if (r < rows.size) rows.removeAt(r)
            "bs" -> ed.bs()
            "insd" -> if (ed.isEmpty) { c.stat.checkRoom(if (c.stat.twoVar) 2 else 1); rows.add(r, doubleArrayOf(0.0, 0.0, 1.0)) }
            "stat" -> { c.pop(); Menus.stat(c) }
            else -> c.insertAct(ed, a)
        }
        if (r >= rows.size) col = 0
    }

    private fun enter() {
        if (ed.isEmpty) { if (r < rows.size) r++; return }
        val vals = Parser.evalList(ed.root, c.ctx).map { Ops.real(it).d }
        if (r == rows.size) {
            if (vals.size > ncol) throw CalcError(1)
            val row = doubleArrayOf(0.0, 0.0, 1.0)
            for ((k, v) in vals.withIndex()) row[at(k)] = v
            c.stat.checkRoom((if (c.stat.twoVar) 2 else 1) + (if (row[2] != 1.0) 1 else 0))
            rows.add(row)
        } else {
            if (col + vals.size > ncol) throw CalcError(1)
            for ((k, v) in vals.withIndex()) rows[r][at(col + k)] = v
        }
        r++
        ed.clear()
    }

    override fun draw(l: Lcd): Cur? {
        val first = Math.max(0, Math.min(r - 1, rows.size - 2))
        val two = c.stat.twoVar
        for (k in 0 until 3) {
            val i = first + k
            if (i > rows.size) break
            val d = rows.getOrNull(i)
            if (two) {
                if (d != null) l.line(k, Fmt.cell(d[0], 5).padStart(5) + " " + Fmt.cell(d[1], 5).padStart(5) + " " + Fmt.cell(d[2], 4).padStart(4))
                if (i == r) Draw.cellInvert(l, k, intArrayOf(0, 6, 12)[col], intArrayOf(5, 5, 4)[col])
            } else {
                l.line(k, (i + 1).toString().padStart(2) + (if (d != null) " " + Fmt.cell(d[0], 6).padStart(6) + " " + Fmt.cell(d[2], 6).padStart(6) else ""))
                if (i == r) Draw.cellInvert(l, k, intArrayOf(3, 10)[col], 6)
            }
        }
        if (first > 0) c.arrows.add("↑")
        if (first + 2 < rows.size) c.arrows.add("↓")
        val name = if (two) listOf("X", "Y", "FRQ")[col] else listOf("X", "FRQ")[col]
        l.line(3, "$name=")
        if (!ed.isEmpty) return Draw.entry(l, ed, 3, name.length + 1)
        rows.getOrNull(r)?.let { l.right(3, c.numText(it[at(col)]).takeLast(15 - name.length)) }
        return Cur((name.length + 1) * 6, 25, 31)
    }
}

// ---------------------------------------------------------------------------- TABLE mode (M-4)

class TableEntry(val c: Calculator, val which: Int) : Screen(), RootScreen {
    val ed = Editor()
    override fun key(a: String) {
        ed.wv = c.set.wv; ed.insert = c.set.insert
        when (a) {
            "=" -> {
                if (which == 1 && ed.isEmpty) throw CalcError(1, Pos(ed.root, 0))
                if (!ed.isEmpty) Parser.one(ed.root, c.ctx)
                if (which == 1) { c.tblF1 = ed.root.copy(); c.push(TableEntry(c, 2).also { it.ed.set(c.tblF2.copy()) }) }
                else {
                    c.tblF2 = ed.root.copy()
                    c.push(Form(c, null, listOf(Field("X_Start:", 0.0), Field("X_Step:", 1.0)), { f ->
                        val step = f.fields[1].value!!
                        if (step <= 0 || Math.abs(step) >= 1e100 || Math.abs(f.fields[0].value!!) >= 1e100) throw CalcError(2)
                        c.push(TableView(c, f.fields[0].value!!, step))
                    }, { c.pop() }))
                }
            }
            "onc" -> if (!ed.isEmpty) { ed.clear(); c.scrollX = 0 } else if (which == 2) c.pop()
            "left" -> if (!ed.left()) ed.end()
            "right" -> if (!ed.right()) ed.home()
            "up" -> ed.vert(true)
            "down" -> ed.vert(false)
            "start" -> ed.home()
            "end" -> ed.end()
            "bs" -> if (ed.bs() == Editor.Del.CANNOT) c.msg = "Cannot delete!"
            "del" -> if (ed.del() == Editor.Del.CANNOT) c.msg = "Cannot delete!"
            "torθ", "toxy", "todeg", "drg" -> {}
            else -> c.insertAct(ed, a)
        }
    }
    override fun draw(l: Lcd): Cur? {
        c.msg?.let { l.line(0, it); c.msg = null; return null }
        l.line(0, "Function$which:")
        return c.drawEditor(l, ed, 8, true)
    }
}

class TableView(val c: Calculator, val start: Double, val step: Double) : Screen() {
    private val f1 = Parser.one(c.tblF1, c.ctx)
    private val f2 = if (c.tblF2.isEmpty) null else Parser.one(c.tblF2, c.ctx)
    private val ncol = if (f2 == null) 2 else 3
    var k0 = 0
    var row = 0
    var col = 0

    fun value(k: Int, col: Int): Double? = try {
        val x = fin(start + k * step)
        if (col == 0) x else {
            c.ctx.vars["X"] = if (isInt(x) && Math.abs(x) < 1e12) numInt(x.toLong()) else Num(x)
            Ops.real((if (col == 1) f1 else f2!!).ev(c.ctx)).d
        }
    } catch (e: CalcError) { null }

    override fun key(a: String) {
        when (a) {
            "up" -> if (row > 0) row-- else k0--
            "down" -> if (row < 2) row++ else k0++
            "left" -> col = Math.max(0, col - 1)
            "right" -> col = Math.min(ncol - 1, col + 1)
            "onc", "bs" -> while (c.screens.size > 1) c.pop()
        }
    }

    override fun draw(l: Lcd): Cur? {
        val widths = if (ncol == 2) intArrayOf(7, 7) else intArrayOf(4, 5, 5)
        val cols = if (ncol == 2) intArrayOf(0, 9) else intArrayOf(0, 5, 11)
        for (i in 0 until 3) {
            for (j in 0 until ncol) {
                val v = value(k0 + i, j)
                val s = if (v == null) "-".repeat(widths[j]) else Fmt.cell(v, widths[j])
                l.line(i, s.padStart(widths[j]), cols[j])
            }
            if (i == row) Draw.cellInvert(l, i, cols[col], widths[col])
        }
        val name = if (col == 0) "X" else if (ncol == 2) "ANS" else "ANS$col"
        l.line(3, "$name=")
        val v = value(k0 + row, col)
        l.right(3, if (v == null) "-------" else c.numText(v).takeLast(15 - name.length))
        c.arrows.add("↑"); c.arrows.add("↓")
        return null
    }
}

// ---------------------------------------------------------------------------- matrices and vectors (M-7)

class MatGrid(val c: Calculator, val vec: Boolean) : Screen() {
    private val m: Mat get() = (if (vec) c.vecBuf else c.matBuf)!!
    private val nr get() = if (vec) m.cols else m.rows
    private val nc get() = if (vec) 1 else m.cols
    private fun get(r: Int, k: Int) = if (vec) m.a[0][r] else m.a[r][k]
    private fun put(r: Int, k: Int, v: Double) { if (vec) m.a[0][r] = v else m.a[r][k] = v }
    var r = 0
    var k = 0
    val ed = Editor().apply { wv = false }

    override fun key(a: String) {
        ed.insert = c.set.insert
        when (a) {
            "=" -> {
                if (!ed.isEmpty) { put(r, k, fin(Ops.real(Parser.eval(ed.root, c.ctx)).d)); ed.clear() }
                if (k < nc - 1) k++ else if (r < nr - 1) { r++; k = 0 }
            }
            "onc" -> if (!ed.isEmpty) ed.clear() else c.pop()
            "up" -> if (ed.isEmpty && r > 0) r--
            "down" -> if (ed.isEmpty && r < nr - 1) r++
            "left" -> if (!ed.isEmpty) ed.left() else if (k > 0) k--
            "right" -> if (!ed.isEmpty) ed.right() else if (k < nc - 1) k++
            "bs" -> ed.bs()
            "del" -> ed.del()
            "math" -> if (ed.isEmpty) { c.pop(); Menus.math(c) }
            else -> c.insertAct(ed, a)
        }
    }

    override fun draw(l: Lcd): Cur? {
        val r0 = Math.max(0, Math.min(r - 1, nr - 3))
        val c0 = Math.max(0, Math.min(k, nc - 2))
        for (i in 0 until 3) {
            if (r0 + i >= nr) break
            for (j in 0 until 2) {
                if (c0 + j >= nc) break
                l.line(i, Fmt.cell(get(r0 + i, c0 + j), 7).padStart(7), j * 8)
                if (r0 + i == r && c0 + j == k) Draw.cellInvert(l, i, j * 8, 7)
            }
        }
        if (r0 > 0) c.arrows.add("↑")
        if (r0 + 3 < nr) c.arrows.add("↓")
        if (c0 > 0) c.arrows.add("←")
        if (c0 + 2 < nc) c.arrows.add("→")
        val name = if (vec) "${r + 1}" else "${r + 1},${k + 1}"
        l.line(3, "$name=")
        if (!ed.isEmpty) return Draw.entry(l, ed, 3, name.length + 1)
        l.right(3, c.numText(get(r, k)).takeLast(15 - name.length))
        return Cur((name.length + 1) * 6, 25, 31)
    }
}

object Tools {
    fun matEdit(c: Calculator, vec: Boolean) {
        c.closeMenus()
        val cur = if (vec) c.vecBuf else c.matBuf
        val fields = if (vec) listOf(Field("DIM(2-3):", (cur?.cols ?: 2).toDouble()))
        else listOf(Field("ROW(1-4):", (cur?.rows ?: 2).toDouble()), Field("COL(1-4):", (cur?.cols ?: 2).toDouble()))
        c.push(Form(c, if (vec) "VECTOR" else "MATRIX", fields, { f ->
            if (vec) {
                val n = f.fields[0].value!!
                if (n != 2.0 && n != 3.0) throw CalcError(7)
                val old = cur?.a?.get(0) ?: DoubleArray(0)
                c.vecBuf = Mat(arrayOf(DoubleArray(n.toInt()) { old.getOrElse(it) { 0.0 } }), true)
            } else {
                c.matBuf = Lin.dim(cur ?: Mat(arrayOf(DoubleArray(1))), f.fields[0].value!!, f.fields[1].value!!)
            }
            c.pop()
            c.push(MatGrid(c, vec))
        }, { c.pop() }))
    }

    private fun collect(r: Row, out: LinkedHashSet<String>) {
        for (n in r.items) when (n) {
            is Tok -> if (n.id in Parser.VARS) out.add(n.id)
            is Tmpl -> n.slots.forEach { collect(it, out) }
        }
    }

    fun numOf(d: Double): Num = if (isInt(d) && Math.abs(d) < 1e12) numInt(d.toLong()) else Num(fin(d), exactOf(java.math.BigDecimal(d.toString()).toPlainString()))

    /** Simulation calculation (ALGB, M-10): asks for each variable, then evaluates. */
    fun simulation(c: Calculator) {
        if (c.ed.isEmpty) return
        val names = LinkedHashSet<String>()
        collect(c.ed.root, names)
        if (names.isEmpty()) throw CalcError(1, Pos(c.ed.root, c.ed.root.items.size))
        Parser.one(c.ed.root, c.ctx)
        c.res = null
        val list = names.toList()
        c.push(Form(c, null, list.map { Field("$it=", (c.ctx.getVar(it) as? Num)?.d ?: 0.0) }, { f ->
            for ((k, n) in list.withIndex()) c.ctx.vars[n] = numOf(f.fields[k].value!!)
            c.pop()
            c.execute()
        }, { c.pop() }))
    }

    /** Solver (M-11): Newton's method from a start value; the root goes to X. */
    fun solver(c: Calculator) {
        if (c.ed.isEmpty) return
        val names = LinkedHashSet<String>()
        collect(c.ed.root, names)
        if (!names.contains("X")) throw CalcError(1, Pos(c.ed.root, c.ed.root.items.size))
        val ex = Parser.one(c.ed.root, c.ctx)
        c.res = null
        c.push(Form(c, null, listOf(Field("Start:", 0.0), Field("dx:", 0.00001)), { f ->
            val x = Calc.solve(c.ctx, ex, f.fields[0].value!!, f.fields[1].value!!)
            c.pop()
            c.ctx.ans = Num(x)
            c.res = ResultView(Num(x), listOf(listOf(Calculator.textRow("X=" + c.numText(x).padStart(14)))))
        }, { c.pop() }))
    }
}

// ---------------------------------------------------------------------------- DRILL mode (M-9)

class DrillSetup(val c: Calculator, val table: Boolean) : Screen() {
    private val counts = listOf(25, 50, 100)
    private val ops = listOf("+", "−", "×", "÷", "+−×÷")
    var a = 0   // question count index, or table row - 1
    var b = 0   // operator index, or 0 serial / 1 random
    override fun key(k: String) {
        when (k) {
            "up" -> a = if (table) (a + 1) % 12 else (a + 1) % 3
            "down" -> a = if (table) (a + 11) % 12 else (a + 2) % 3
            "right" -> b = if (table) (b + 1) % 2 else (b + 1) % 5
            "left" -> b = if (table) (b + 1) % 2 else (b + 4) % 5
            "=" -> c.push(DrillRun(c, table, if (table) a + 1 else counts[a], b))
            "onc", "bs" -> c.pop()
        }
    }
    override fun draw(l: Lcd): Cur? {
        if (table) { l.line(0, "× TABLE"); l.line(1, "▲▼ ROW: ${a + 1}"); l.line(2, "◄► " + if (b == 0) "Serial" else "Random") }
        else { l.line(0, "MATH DRILL"); l.line(1, "▲▼ QUESTIONS:${counts[a]}"); l.line(2, "◄► " + ops[b]) }
        l.line(3, "ENTER:START")
        return null
    }
}

class DrillRun(val c: Calculator, table: Boolean, n: Int, opt: Int) : Screen() {
    class Q(val text: String, val answer: Int)
    val qs = ArrayList<Q>()
    var i = 0
    var typed = ""
    var correct = 0
    private var failed = false
    var mark = ""

    init {
        val rnd = c.ctx.rng
        if (table) {
            val order = (1..12).toMutableList()
            if (opt == 1) order.shuffle(rnd)
            for (k in order) qs.add(Q("$n×$k=", n * k))
        } else repeat(n) {
            when (if (opt == 4) rnd.nextInt(4) else opt) {
                0 -> { val x = rnd.nextInt(21); val y = rnd.nextInt(21); qs.add(Q("$x+$y=", x + y)) }
                1 -> { val x = rnd.nextInt(21); val y = rnd.nextInt(x + 1); qs.add(Q("$x−$y=", x - y)) }
                2 -> { val x = rnd.nextInt(13); val y = rnd.nextInt(13); qs.add(Q("$x×$y=", x * y)) }
                else -> { val d = 1 + rnd.nextInt(12); val q = rnd.nextInt(13); qs.add(Q("${d * q}÷$d=", q)) }
            }
        }
    }

    private fun next() { i++; typed = ""; failed = false }

    override fun key(a: String) {
        if (i >= qs.size) { if (a == "=" || a == "onc") c.pop(); return }
        if (a.length == 1 && a[0].isDigit()) { if (typed.length < 3) typed += a; return }
        when (a) {
            "onc", "bs" -> typed = ""
            "=" -> {
                val q = qs[i]
                if (typed.isEmpty()) { mark = q.text + q.answer; next() }
                else if (typed.toInt() == q.answer) { if (!failed) correct++; mark = "○"; next() }
                else { mark = "×"; failed = true; typed = "" }
            }
        }
    }

    override fun draw(l: Lcd): Cur? {
        if (i >= qs.size) {
            l.line(0, "CORRECT")
            l.line(1, "$correct/${qs.size}")
            l.line(2, "${Math.round(100.0 * correct / qs.size)}%")
            return null
        }
        l.line(0, "Q${i + 1}/${qs.size}")
        l.right(0, mark.takeLast(9))
        val s = qs[i].text + typed
        l.line(2, s)
        return Cur(s.length * 6, 17, 23)
    }
}
