package sharpcalc.engine

/** A parsed sub-expression that can be evaluated again and again (Σ, ∫, Solver, Table). */
class Ex(val pos: Pos, private val f: (Ctx) -> V) {
    /** For "x%": the x, so that a ± x% can mean a ± a·x/100. */
    var pct: Ex? = null
    fun ev(c: Ctx): V = try { f(c) } catch (e: CalcError) { if (e.pos == null) e.pos = pos; throw e }
}

/**
 * Recursive-descent parser over the editor's expression tree.
 * Priority, lowest to highest (section 6 of the requirements, from the manual):
 * OR/XOR/XNOR < AND < + − < × ÷ int÷ < nCr nPr GCD LCM conversions < implied × of functions
 * < prefix functions < implied × of memories < y^x, x√ < postfix (x², x⁻¹, n!, %) < ∠, prefixes < fractions.
 */
class Parser(private val row: Row, private val c: Ctx) {
    private var i = 0
    private val items = row.items

    companion object {
        val VARS = setOf("A", "B", "C", "D", "E", "F", "X", "Y", "M")
        val PREFIX = setOf(
            "sin", "cos", "tan", "sin⁻¹", "cos⁻¹", "tan⁻¹", "sinh", "cosh", "tanh", "sinh⁻¹", "cosh⁻¹", "tanh⁻¹",
            "log", "ln", "√", "³√", "abs", "ipart", "fpart", "int", "NOT", "NEG", "det", "trans", "identity",
        )
        val ENG = mapOf("pk" to 3, "pM" to 6, "pG" to 9, "pT" to 12, "pm" to -3, "pμ" to -6, "pn" to -9, "pp" to -12, "pf" to -15)

        fun one(row: Row, c: Ctx): Ex = Parser(row, c).parseOne()
        fun list(row: Row, c: Ctx): List<Ex> = Parser(row, c).parseList()

        fun eval(row: Row, c: Ctx): V {
            c.fracUsed = false
            try { return one(row, c).ev(c) } catch (e: StackOverflowError) { throw CalcError(3) }
        }
        fun evalList(row: Row, c: Ctx): List<V> {
            c.fracUsed = false
            try { return list(row, c).map { it.ev(c) } } catch (e: StackOverflowError) { throw CalcError(3) }
        }

        private fun post(c: Ctx, v: V): V = if (c.base != Base.DEC && v is Num) Num(R.baseFix(v.d, c.base)) else v
        private fun isDigit(s: String?) = s != null && s.length == 1 && s[0] in '0'..'9'
        private fun isHex(s: String?) = s != null && s.length == 2 && s[0] == 'h'
        private fun isConst(t: String) = t.length == 3 && t[0] == 'k' && t[1].isDigit()
        private fun isConv(t: String?) = t != null && t.length == 4 && t.startsWith("cv")
        private fun isName(t: String) = t.startsWith("mat") && t.length == 4 || t.startsWith("vect") && t.length == 5

        private fun isTight(n: Node?): Boolean = when (n) {
            null -> false
            is Tmpl -> n.kind != K.POW && n.kind != K.OVER
            is Tok -> n.id in VARS || n.id == "π" || n.id == "e" || n.id == "Ans" || n.id == "i" || n.id == "(" ||
                n.id.startsWith("st:") || isConst(n.id) || isName(n.id)
        }
        private fun startsOperand(n: Node?): Boolean = isTight(n) || (n is Tok && (isDigit(n.id) || n.id == "." || isHex(n.id) ||
            n.id in PREFIX || n.id.endsWith("(") || n.id == "RANDOM" || n.id == "R.Dice" || n.id == "R.Coin"))
    }

    private fun id(k: Int = i): String? = (items.getOrNull(k) as? Tok)?.id
    private fun err(at: Int = i, code: Int = 1): Nothing = throw CalcError(code, Pos(row, Math.min(at, items.size)))
    private fun ex(at: Int, f: (Ctx) -> V) = Ex(Pos(row, at), f)

    fun parseList(): List<Ex> {
        if (items.isEmpty()) err(0)
        val l = arrayListOf(expr())
        while (id() == ",") { i++; l.add(expr()) }
        if (i < items.size) err()
        return l
    }

    fun parseOne(): Ex {
        val l = parseList()
        if (l.size != 1) err(items.size)
        return l[0]
    }

    private fun expr(): Ex = orE()

    private fun logic(c: Ctx, op: String, a: V, b: V): V =
        Num(R.logic(op, R.baseFix(Ops.real(a).d, c.base), R.baseFix(Ops.real(b).d, c.base), c.base))

    private fun orE(): Ex {
        var a = andE()
        while (true) {
            val op = id()
            if (op != "OR" && op != "XOR" && op != "XNOR") return a
            val p = i; i++
            val l = a; val b = andE()
            a = ex(p) { logic(it, op, l.ev(it), b.ev(it)) }
        }
    }

    private fun andE(): Ex {
        var a = addE()
        while (id() == "AND") {
            val p = i; i++
            val l = a; val b = addE()
            a = ex(p) { logic(it, "AND", l.ev(it), b.ev(it)) }
        }
        return a
    }

    private fun addE(): Ex {
        var a = mulE()
        while (true) {
            val op = id()
            if (op != "+" && op != "-") return a
            val p = i; i++
            val l = a; val b = mulE(); val pc = b.pct
            a = if (pc != null) ex(p) {
                val x = l.ev(it)
                val y = Ops.div(Ops.mul(x, pc.ev(it)), numInt(100))
                post(it, if (op == "+") Ops.add(x, y) else Ops.sub(x, y))
            } else ex(p) {
                val x = l.ev(it); val y = b.ev(it)
                post(it, if (op == "+") Ops.add(x, y) else Ops.sub(x, y))
            }
        }
    }

    private fun mulE(): Ex {
        var a = combE()
        while (true) {
            val op = id()
            if (op != "*" && op != "/" && op != "int÷") return a
            val p = i; i++
            val l = a; val b = combE()
            a = ex(p) {
                val x = l.ev(it); val y = b.ev(it)
                when (op) {
                    "*" -> post(it, Ops.mul(x, y))
                    "/" -> post(it, Ops.div(x, y))
                    else -> R.intDiv(Ops.real(x), Ops.real(y))
                }
            }
        }
    }

    private fun combE(): Ex {
        var a = implF()
        while (true) {
            val op = id()
            val p = i
            val l = a
            if (isConv(op)) {
                i++
                val k = op!!.substring(2).toInt()
                a = ex(p) { num(Consts.convert(k, Ops.real(l.ev(it)).d)) }
                continue
            }
            if (op != "nCr" && op != "nPr" && op != "GCD" && op != "LCM") return a
            i++
            val b = implF()
            a = ex(p) {
                val x = Ops.real(l.ev(it)); val y = Ops.real(b.ev(it))
                when (op) { "nCr" -> R.nCr(x, y); "nPr" -> R.nPr(x, y); "GCD" -> R.gcd(x, y); else -> R.lcm(x, y) }
            }
        }
    }

    private fun implF(): Ex {
        var a = unary()
        while (startsOperand(items.getOrNull(i))) {
            val p = i
            val l = a; val b = unary()
            a = ex(p) { post(it, Ops.mul(l.ev(it), b.ev(it))) }
        }
        return a
    }

    private fun unary(): Ex {
        val t = id()
        val p = i
        if (t == "neg") { i++; val a = unary(); return ex(p) { post(it, Ops.neg(a.ev(it))) } }
        if (t != null && t in PREFIX) { i++; val a = unary(); return ex(p) { post(it, Ops.fn1(it, t, a.ev(it))) } }
        return implM()
    }

    private fun implM(): Ex {
        var a = power()
        while (isTight(items.getOrNull(i))) {
            val p = i
            val l = a; val b = power()
            a = ex(p) { post(it, Ops.mul(l.ev(it), b.ev(it))) }
        }
        return a
    }

    private fun power(): Ex {
        val a = postfix()
        val t = id()
        val p = i
        if (t == "^") { i++; val b = powRhs(); return ex(p) { post(it, Ops.pow(it, a.ev(it), b.ev(it))) } }
        if (t == "x√") { i++; val b = powRhs(); return ex(p) { post(it, Ops.root(it, a.ev(it), b.ev(it))) } }
        return a
    }

    private fun powRhs(): Ex {
        val t = id()
        val p = i
        if (t == "neg") { i++; val b = powRhs(); return ex(p) { Ops.neg(b.ev(it)) } }
        if (t != null && t in PREFIX) return unary()
        return power()
    }

    private fun postfix(): Ex {
        var a = primary()
        while (true) {
            val p = i
            val n = items.getOrNull(i) ?: return a
            val b = a
            if (n is Tmpl) {
                if (n.kind != K.POW) return a
                i++
                val e = Parser(n.slots[0], c).parseOne()
                a = ex(p) { post(it, Ops.pow(it, b.ev(it), e.ev(it))) }
                continue
            }
            val t = (n as Tok).id
            a = when (t) {
                "²" -> ex(p) { post(it, Ops.pow(it, b.ev(it), numInt(2))) }
                "³" -> ex(p) { post(it, Ops.pow(it, b.ev(it), numInt(3))) }
                "⁻¹" -> ex(p) { post(it, Ops.pow(it, b.ev(it), numInt(-1))) }
                "!" -> ex(p) { R.fact(Ops.real(b.ev(it))) }
                "%" -> ex(p) { Ops.div(b.ev(it), numInt(100)) }.also { it.pct = b }
                "(%)" -> ex(p) { Ops.div(b.ev(it), numInt(100)) }
                "→t" -> ex(p) { val s = it.stat ?: throw CalcError(1); R.div(R.sub(Ops.real(b.ev(it)), Num(s.value("x̄"))), Num(s.value("sx"))) }
                "y′" -> ex(p) { val s = it.stat ?: throw CalcError(1); num(s.estY(Ops.real(b.ev(it)).d)) }
                "x′" -> ex(p) {
                    val s = it.stat ?: throw CalcError(1)
                    val r = s.estX(Ops.real(b.ev(it)).d)
                    if (r.size > 1) { it.vars["X"] = num(r[0]); it.vars["Y"] = num(r[1]) }
                    num(r[0])
                }
                "∠" -> {
                    i++
                    val neg = id() == "neg"
                    if (neg) i++
                    val th = primary()
                    i--
                    ex(p) { val a0 = th.ev(it); Ops.polar(it, b.ev(it), if (neg) Ops.neg(a0) else a0) }
                }
                else -> {
                    val e = ENG[t] ?: return a
                    ex(p) { R.mul(Ops.real(b.ev(it)), R.pow(numInt(10), numInt(e.toLong()))) }
                }
            }
            i++
        }
    }

    private fun plainNumber(): String {
        val sb = StringBuilder()
        while (isDigit(id())) { sb.append(id()); i++ }
        if (id() == ".") { sb.append('.'); i++; while (isDigit(id())) { sb.append(id()); i++ } }
        if (sb.isEmpty()) err()
        return if (sb.toString() == ".") "0" else sb.toString()
    }

    private fun lit(text: String): Num = Num(fin(text.toDouble()), exactOf(text))

    private fun number(): Ex {
        val p = i
        if (c.base != Base.DEC) {
            val sb = StringBuilder()
            while (true) {
                val t = id() ?: break
                val ch = when { isDigit(t) -> t[0]; isHex(t) -> t[1]; else -> break }
                if (Character.digit(ch, c.base.radix) < 0) err()
                sb.append(ch); i++
            }
            if (id() == "." || sb.isEmpty()) err()
            val v = try { R.parseBase(sb.toString(), c.base) } catch (e: CalcError) { e.pos = Pos(row, p); throw e }
            return ex(p) { Num(v) }
        }
        var text = if (id() == "E") "1" else plainNumber()
        if (id() == "E") {
            i++
            var es = ""
            if (id() == "neg" || id() == "-") { es = "-"; i++ } else if (id() == "+") i++
            val st = i
            while (isDigit(id())) { es += id(); i++ }
            if (i == st) err()
            if (i - st > 2) err(st, 2)
            text += "E$es"
        }
        val v = lit(text)
        if (id() == "°") {
            i++
            var value: Num = v
            if (isDigit(id()) || id() == ".") {
                value = R.add(value, R.div(lit(plainNumber()), numInt(60)))
                if (id() == "°") {
                    i++
                    if (isDigit(id()) || id() == ".") {
                        value = R.add(value, R.div(lit(plainNumber()), numInt(3600)))
                        if (id() == "°") i++
                    }
                }
            }
            val r = Num(value.d, value.ex, true)
            return ex(p) { r }
        }
        if (id() == "⌟") {
            i++
            val b = lit(plainNumber())
            if (id() == "⌟") {
                i++
                val d = lit(plainNumber())
                return ex(p) { it.fracUsed = true; R.add(v, R.div(b, d)) }
            }
            return ex(p) { it.fracUsed = true; R.div(v, b) }
        }
        return ex(p) { v }
    }

    private fun primary(): Ex {
        val p = i
        val n = items.getOrNull(i) ?: err()
        if (n is Tmpl) { i++; return tmpl(n, p) }
        val t = (n as Tok).id
        if (isDigit(t) || t == "." || t == "E" || isHex(t)) return number()
        i++
        return when {
            t == "(" -> {
                if (i >= items.size) err()
                val e = expr()
                if (id() == ")") i++ else if (i < items.size) err()
                e
            }
            t in VARS -> ex(p) { it.getVar(t) }
            t == "π" -> ex(p) { Num(PI14, Exact.PI) }
            t == "e" -> ex(p) { Num(E14) }
            t == "Ans" -> ex(p) { it.ans.let { a -> if (a is QR) Num(a.q) else a } }
            t == "i" -> { if (c.domain != Domain.COMPLEX) { i--; err() }; ex(p) { Cx(0.0, 1.0) } }
            t == "RANDOM" -> ex(p) { numQ(Q.of(it.rng.nextInt(1000).toLong(), 1000)) }
            t == "R.Dice" -> ex(p) { numInt(1L + it.rng.nextInt(6)) }
            t == "R.Coin" -> ex(p) { numInt(it.rng.nextInt(2).toLong()) }
            isConst(t) -> { val k = t.substring(1).toInt(); ex(p) { Num(Consts.list[k - 1].v) } }
            t.startsWith("st:") -> ex(p) { num((it.stat ?: throw CalcError(1)).value(t.substring(3))) }
            isName(t) -> {
                val vec = t.startsWith("vect")
                val k = t.last() - 'A'
                ex(p) { (if (vec) it.vecs[k] else it.mats[k]) ?: throw CalcError(10) }
            }
            t.endsWith("(") -> call(t, p)
            else -> { i--; err() }
        }
    }

    private fun call(t: String, p: Int): Ex {
        val a = ArrayList<Ex>()
        if (id() != ")" && i < items.size) {
            a.add(expr())
            while (id() == ",") { i++; a.add(expr()) }
        }
        if (id() == ")") i++ else if (i < items.size) err()
        fun need(r: IntRange) { if (a.size !in r) err(p) }
        fun re(c: Ctx, k: Int) = Ops.real(a[k].ev(c))
        fun mt(c: Ctx, k: Int) = a[k].ev(c) as? Mat ?: throw CalcError(1)
        return when (t) {
            "R.Int(" -> { need(2..2); ex(p) {
                val lo = re(it, 0).d; val hi = re(it, 1).d
                if (!isInt(lo) || !isInt(hi) || lo > hi || hi - lo >= 1e10) throw CalcError(2)
                numInt(lo.toLong() + (it.rng.nextDouble() * (hi - lo + 1)).toLong())
            } }
            "logn(" -> { need(2..2); ex(p) { R.logn(re(it, 0), re(it, 1)) } }
            "Σ(", "Π(" -> { need(3..4); ex(p) { Calc.series(it, a[0], a[1].ev(it), a[2].ev(it), a.getOrNull(3)?.ev(it), t == "Σ(") } }
            "∫(" -> { need(3..4); ex(p) { Calc.integral(it, a[0], a[1].ev(it), a[2].ev(it), a.getOrNull(3)?.ev(it)) } }
            "d/dx(" -> { need(2..3); ex(p) { Calc.derivative(it, a[0], a[1].ev(it), a.getOrNull(2)?.ev(it)) } }
            "conj(" -> { need(1..1); ex(p) { val z = Ops.cx(a[0].ev(it)); Ops.simp(z.re, -z.im) } }
            "arg(" -> { need(1..1); ex(p) { Ops.arg(it, a[0].ev(it)) } }
            "real(" -> { need(1..1); ex(p) { Num(Ops.cx(a[0].ev(it)).re) } }
            "img(" -> { need(1..1); ex(p) { Num(Ops.cx(a[0].ev(it)).im) } }
            "P(" -> { need(1..1); ex(p) { num(Dist.round6(Dist.phi(re(it, 0).d))) } }
            "Q(" -> { need(1..1); ex(p) { num(Dist.round6(Math.abs(Dist.phi(re(it, 0).d) - 0.5))) } }
            "R(" -> { need(1..1); ex(p) { num(Dist.round6(1 - Dist.phi(re(it, 0).d))) } }
            "dim(" -> { need(3..3); ex(p) { Lin.dim(mt(it, 0), re(it, 1).d, re(it, 2).d) } }
            "fill(" -> { need(3..3); ex(p) { Lin.fill(re(it, 0).d, re(it, 1).d, re(it, 2).d) } }
            "rand_mat(" -> { need(2..2); ex(p) { Lin.randMat(it, re(it, 0).d, re(it, 1).d) } }
            "ref(" -> { need(1..1); ex(p) { Lin.ref(mt(it, 0), false) } }
            "rref(" -> { need(1..1); ex(p) { Lin.ref(mt(it, 0), true) } }
            "DotPro(" -> { need(2..2); ex(p) { num(Lin.dot(mt(it, 0), mt(it, 1))) } }
            "CrossPro(" -> { need(2..2); ex(p) { Lin.cross(mt(it, 0), mt(it, 1)) } }
            "Angl(" -> { need(2..2); ex(p) { num(Lin.angle(it, mt(it, 0), mt(it, 1))) } }
            "Unit(" -> { need(1..1); ex(p) { Lin.unit(mt(it, 0)) } }
            else -> err(p)
        }
    }

    private fun tmpl(t: Tmpl, p: Int): Ex {
        fun s(k: Int) = Parser(t.slots[k], c).parseOne()
        fun sl(k: Int): List<Ex> { val l = Parser(t.slots[k], c).parseList(); if (l.size > 2) throw CalcError(1, Pos(t.slots[k], t.slots[k].items.size)); return l }
        return when (t.kind) {
            K.FRAC -> { val a = s(0); val b = s(1); ex(p) { it.fracUsed = true; post(it, Ops.div(a.ev(it), b.ev(it))) } }
            K.MIXED -> { val w = s(0); val a = s(1); val b = s(2); ex(p) { it.fracUsed = true; Ops.add(w.ev(it), Ops.div(a.ev(it), b.ev(it))) } }
            K.SQRT -> { val a = s(0); ex(p) { Ops.sqrt(it, a.ev(it)) } }
            K.ROOT -> { val x = s(0); val a = s(1); ex(p) { Ops.root(it, x.ev(it), a.ev(it)) } }
            K.LOGN -> { val b = s(0); val a = s(1); ex(p) { R.logn(Ops.real(b.ev(it)), Ops.real(a.ev(it))) } }
            K.ABS -> { val a = s(0); ex(p) { Ops.abs(a.ev(it)) } }
            K.SUM, K.PROD -> { val a = s(0); val b = s(1); val l = sl(2); val sum = t.kind == K.SUM
                ex(p) { Calc.series(it, l[0], a.ev(it), b.ev(it), l.getOrNull(1)?.ev(it), sum) } }
            K.INT -> { val a = s(0); val b = s(1); val l = sl(2); ex(p) { Calc.integral(it, l[0], a.ev(it), b.ev(it), l.getOrNull(1)?.ev(it)) } }
            K.DIFF -> { val f = s(0); val l = sl(1); ex(p) { Calc.derivative(it, f, l[0].ev(it), l.getOrNull(1)?.ev(it)) } }
            K.POW, K.OVER -> err(p)
        }
    }
}

/** Numeric Σ, Π, ∫, d/dx and the Newton solver (E-10, M-11). */
object Calc {
    private inline fun <T> withX(c: Ctx, body: () -> T): T {
        val old = c.vars["X"]
        try { return body() } finally { if (old == null) c.vars.remove("X") else c.vars["X"] = old }
    }

    fun series(c: Ctx, f: Ex, a: V, b: V, n: V?, sum: Boolean): V {
        val start = Ops.real(a).d; val end = Ops.real(b).d
        val step = if (n == null) 1.0 else Ops.real(n).d
        if (!isInt(start) || !isInt(end) || !isInt(step) || step <= 0 || start > end) throw CalcError(2)
        return withX(c) {
            var acc: V = if (sum) numInt(0) else numInt(1)
            var x = start
            while (x <= end) {
                c.tick()
                c.vars["X"] = numInt(x.toLong())
                val v = f.ev(c)
                acc = if (sum) Ops.add(acc, v) else Ops.mul(acc, v)
                x += step
            }
            acc
        }
    }

    fun integral(c: Ctx, f: Ex, a: V, b: V, n: V?): V {
        val lo = Ops.real(a).d; val hi = Ops.real(b).d
        val sub = if (n == null) 100.0 else Ops.real(n).d
        if (!isInt(sub) || sub < 1 || sub > 1e6) throw CalcError(2)
        val cnt = 2 * sub.toInt()
        val h = (hi - lo) / cnt
        return withX(c) {
            fun at(x: Double): Double { c.tick(); c.vars["X"] = Num(x); return Ops.real(f.ev(c)).d }
            var s = at(lo) + at(hi)
            for (k in 1 until cnt) s += (if (k % 2 == 1) 4 else 2) * at(lo + k * h)
            num(s * h / 3)
        }
    }

    fun derivative(c: Ctx, f: Ex, xv: V, dxv: V?): V {
        val x = Ops.real(xv).d
        val dx = if (dxv != null) Ops.real(dxv).d else if (x == 0.0) 1e-5 else Math.abs(x) * 1e-5
        if (dx == 0.0) throw CalcError(2)
        return withX(c) {
            fun at(t: Double): Double { c.vars["X"] = Num(t); return Ops.real(f.ev(c)).d }
            val d = (at(x + dx / 2) - at(x - dx / 2)) / dx
            // the calculator shows 10 digits; drop the noise of the difference quotient below that
            num(java.math.BigDecimal(d).round(java.math.MathContext(11)).toDouble())
        }
    }

    /** Newton's method on f(X) = 0; ERROR 02 when it does not converge. The root is left in X. */
    fun solve(c: Ctx, f: Ex, start: Double, dx: Double): Double {
        if (dx == 0.0) throw CalcError(2)
        var x = start
        fun at(t: Double): Double { c.tick(); c.vars["X"] = Num(t); return Ops.real(f.ev(c)).d }
        val old = c.vars["X"]
        try {
            for (iter in 0 until 100) {
                val fx = at(x)
                if (fx == 0.0) break
                val d = (at(x + dx / 2) - at(x - dx / 2)) / dx
                if (d == 0.0 || d.isNaN()) throw CalcError(2)
                val nx = x - fx / d
                if (nx.isNaN() || nx.isInfinite()) throw CalcError(2)
                if (Math.abs(nx - x) <= 1e-13 * Math.max(1.0, Math.abs(nx))) { x = nx; break }
                x = nx
                if (iter == 99) throw CalcError(2)
            }
            if (Math.abs(at(x)) > 1e-6 * Math.max(1.0, Math.abs(x))) throw CalcError(2)
        } catch (e: Throwable) {
            if (old == null) c.vars.remove("X") else c.vars["X"] = old
            throw e
        }
        val r = fin(java.math.BigDecimal(x).round(java.math.MathContext(12)).toDouble())
        c.vars["X"] = Num(r)
        return r
    }
}
