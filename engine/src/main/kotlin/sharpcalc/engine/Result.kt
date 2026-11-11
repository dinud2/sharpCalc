package sharpcalc.engine

import java.math.BigInteger

/** Builds the display forms of a result; the CHANGE key cycles through them (E-7, W-7). */
object Result {
    private fun toks(s: String, comma: Boolean = false): MutableList<Node> =
        s.map { Tok(if (it == '.' && comma) "," else it.toString()) as Node }.toMutableList()

    fun decimal(v: Num, s: Settings): Row =
        Row(toks(if (v.dms) Fmt.dms(v.d) else Fmt.num(v.d, s), s.comma))

    private fun len(b: BigInteger) = b.abs().toString().length

    private fun frac(q: Q, wv: Boolean): Row? {
        if (len(q.n) + len(q.d) > 9) return null
        val r = Row()
        if (q.sign < 0) r.items.add(Tok("-"))
        if (wv) r.items.add(Tmpl(K.FRAC, listOf(Row(toks(q.n.abs().toString())), Row(toks(q.d.toString())))))
        else { r.items.addAll(toks(q.n.abs().toString())); r.items.add(Tok("⌟")); r.items.addAll(toks(q.d.toString())) }
        return r
    }

    private fun mixed(q: Q, wv: Boolean): Row? {
        val n = q.n.abs()
        if (n < q.d) return null
        val w = n / q.d
        val rem = n.mod(q.d)
        if (len(w) + len(rem) + len(q.d) > 8) return null
        val r = Row()
        if (q.sign < 0) r.items.add(Tok("-"))
        if (wv) r.items.add(Tmpl(K.MIXED, listOf(Row(toks(w.toString())), Row(toks(rem.toString())), Row(toks(q.d.toString())))))
        else {
            r.items.addAll(toks(w.toString())); r.items.add(Tok("⌟"))
            r.items.addAll(toks(rem.toString())); r.items.add(Tok("⌟")); r.items.addAll(toks(q.d.toString()))
        }
        return r
    }

    private fun recurring(q: Q, wv: Boolean, comma: Boolean): Row? {
        val n = q.n.abs()
        val ip = n / q.d
        var rem = n.mod(q.d)
        val seen = HashMap<BigInteger, Int>()
        val digits = StringBuilder()
        while (rem.signum() != 0 && !seen.containsKey(rem)) {
            if (digits.length > 12) return null
            seen[rem] = digits.length
            rem = rem * BigInteger.TEN
            digits.append((rem / q.d).toString())
            rem = rem.mod(q.d)
        }
        if (rem.signum() == 0) return null
        val start = seen[rem]!!
        if (ip.toString().length + digits.length > 10) return null
        val r = Row()
        if (q.sign < 0) r.items.add(Tok("-"))
        r.items.addAll(toks(ip.toString() + "." + digits.substring(0, start), comma))
        if (wv) r.items.add(Tmpl(K.OVER, listOf(Row(toks(digits.substring(start))))))
        else { r.items.add(Tok("(")); r.items.addAll(toks(digits.substring(start))); r.items.add(Tok(")")) }
        return r
    }

    private fun lcm(a: BigInteger, b: BigInteger) = a / a.gcd(b) * b

    /** Irrational form: a√b/e ± c√d/f, or a rational multiple of π. */
    private fun irrational(ex: Exact): Row? {
        val r = Row()
        if (ex.pi == 1) {
            val q = ex.t[1L] ?: return null
            if (ex.t.size != 1 || len(q.d) > 3 || len(q.n) > 6) return null
            if (q.sign < 0) r.items.add(Tok("-"))
            if (q.isInt) { if (q.n.abs() != BigInteger.ONE) r.items.addAll(toks(q.n.abs().toString())) }
            else r.items.add(Tmpl(K.FRAC, listOf(Row(toks(q.n.abs().toString())), Row(toks(q.d.toString())))))
            r.items.add(Tok("π"))
            return r
        }
        val terms = ex.t.entries.sortedBy { if (it.key == 1L) 0L else it.key }
        var den = BigInteger.ONE
        for (t in terms) den = lcm(den, t.value.d)
        if (den >= BigInteger.valueOf(1000)) return null
        val numer = Row()
        val single = terms.size == 1
        var first = true
        for (t in terms) {
            val a = t.value.n * (den / t.value.d)
            if (a.abs() >= BigInteger.valueOf(100) && t.key != 1L || a.abs() >= BigInteger.valueOf(10000) || t.key >= 1000) return null
            val negative = a.signum() < 0
            if (first) { if (negative) (if (single) r else numer).items.add(Tok("-")) }
            else numer.items.add(Tok(if (negative) "-" else "+"))
            if (t.key == 1L) numer.items.addAll(toks(a.abs().toString()))
            else {
                if (a.abs() != BigInteger.ONE) numer.items.addAll(toks(a.abs().toString()))
                numer.items.add(Tmpl(K.SQRT, listOf(Row(toks(t.key.toString())))))
            }
            first = false
        }
        if (den == BigInteger.ONE) r.items.addAll(numer.items)
        else r.items.add(Tmpl(K.FRAC, listOf(numer, Row(toks(den.toString())))))
        return r
    }

    fun forms(v: Num, s: Settings, wv: Boolean, fracCalc: Boolean): List<Row> {
        val dec = decimal(v, s)
        if (v.dms) return listOf(dec)
        val ex = v.ex ?: return listOf(dec)
        val q = ex.rational()
        if (q != null) {
            if (q.isInt) return listOf(dec)
            val improper = frac(q, wv) ?: return listOf(dec)
            val mixed = mixed(q, wv)
            val rec = if (s.recurring) recurring(q, wv, s.comma) else null
            return when {
                (wv && s.exact) || fracCalc -> listOfNotNull(mixed, improper, rec, dec)
                wv -> listOfNotNull(dec, mixed, improper, rec)
                else -> listOfNotNull(rec, dec, mixed, improper)
            }
        }
        if (wv && s.exact) irrational(ex)?.let { return listOf(it, dec) }
        return listOf(dec)
    }

    /** Prime factorisation shown as a product of powers (E-5). */
    fun primeRow(n: Long, wv: Boolean): Row {
        val r = Row()
        for ((k, pf) in R.primeFactors(n).withIndex()) {
            if (k > 0) r.items.add(Tok("*"))
            val (p, e) = pf
            if (e == 0) { r.items.add(Tok("(")); r.items.addAll(toks(p.toString())); r.items.add(Tok(")")) }
            else {
                r.items.addAll(toks(p.toString()))
                if (e > 1) {
                    if (wv) r.items.add(Tmpl(K.POW, listOf(Row(toks(e.toString())))))
                    else { r.items.add(Tok("^")); r.items.addAll(toks(e.toString())) }
                }
            }
        }
        return r
    }
}
