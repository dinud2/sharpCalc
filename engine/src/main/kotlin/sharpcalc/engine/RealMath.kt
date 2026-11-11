package sharpcalc.engine

import kotlin.math.abs

/** Real-number functions (E-1 to E-6). */
object R {
    private fun pl(a: Exact?, b: Exact?) = if (a != null && b != null) a.plus(b) else null
    private fun mi(a: Exact?, b: Exact?) = if (a != null && b != null) a.minus(b) else null
    private fun ti(a: Exact?, b: Exact?) = if (a != null && b != null) a.times(b) else null
    private fun dv(a: Exact?, b: Exact?) = if (a != null && b != null) a.div(b) else null

    fun add(a: Num, b: Num) = num(a.d + b.d, pl(a.ex, b.ex), a.dms || b.dms)
    fun sub(a: Num, b: Num) = num(a.d - b.d, mi(a.ex, b.ex), a.dms || b.dms)
    fun mul(a: Num, b: Num) = num(a.d * b.d, ti(a.ex, b.ex), a.dms || b.dms)
    fun div(a: Num, b: Num): Num {
        if (b.d == 0.0) throw CalcError(2)
        return num(a.d / b.d, dv(a.ex, b.ex), a.dms || b.dms)
    }
    fun neg(a: Num) = Num(-a.d, a.ex?.let { -it }, a.dms)

    fun sqrt(a: Num): Num {
        if (a.d < 0) throw CalcError(2)
        return num(Math.sqrt(a.d), a.ex?.sqrt())
    }

    private fun snapRoot(r: Double, x: Double, target: Double): Num {
        val k = Math.rint(r)
        if (abs(r - k) < 1e-9 && Math.pow(k, x) == target) return numInt(k.toLong())
        return num(r)
    }

    /** x-th root of a. */
    fun root(x: Num, a: Num): Num {
        if (x.d == 0.0) throw CalcError(2)
        if (x.d == 2.0) return sqrt(a)
        if (a.d < 0) {
            if (!isInt(x.d) || x.d % 2.0 == 0.0) throw CalcError(2)
            return neg(snapRoot(Math.pow(-a.d, 1 / x.d), x.d, -a.d))
        }
        if (a.d == 0.0) { if (x.d < 0) throw CalcError(2); return numInt(0) }
        return if (isInt(x.d) && x.d > 0 && isInt(a.d)) snapRoot(Math.pow(a.d, 1 / x.d), x.d, a.d) else num(Math.pow(a.d, 1 / x.d))
    }

    fun pow(a: Num, b: Num): Num {
        val e = b.d
        if (a.d == 0.0) {
            if (e <= 0) throw CalcError(2)
            return numInt(0)
        }
        var ex: Exact? = null
        if (a.ex != null && isInt(e) && abs(e) <= 200) ex = a.ex.powInt(e.toInt())
        else if (a.ex != null && e == 0.5) ex = a.ex.sqrt()
        if (a.d < 0) {
            if (isInt(e)) {
                val r = Math.pow(-a.d, e)
                return num(if (e % 2.0 == 0.0) r else -r, ex)
            }
            var q = 3
            while (q < 200) {
                val p = e * q
                if (abs(p - Math.rint(p)) < 1e-10) {
                    val r = Math.pow(-a.d, e)
                    return num(if (Math.rint(p) % 2.0 == 0.0) r else -r)
                }
                q += 2
            }
            throw CalcError(2)
        }
        return num(Math.pow(a.d, e), ex)
    }

    fun ln(a: Num): Num { if (a.d <= 0) throw CalcError(2); return num(Math.log(a.d)) }
    fun log(a: Num): Num { if (a.d <= 0) throw CalcError(2); return num(Math.log10(a.d)) }
    fun logn(base: Num, a: Num): Num {
        if (a.d <= 0 || base.d <= 0 || base.d == 1.0) throw CalcError(2)
        return num(Math.log(a.d) / Math.log(base.d))
    }
    fun exp(a: Num) = num(Math.exp(a.d))
    fun exp10(a: Num) = pow(numInt(10), a)

    // ---- trigonometry ----
    private val S15: Array<Exact?> by lazy {
        val q4 = Q.of(1, 4)
        arrayOf(
            Exact.ZERO,
            Exact.make(mapOf(6L to q4, 2L to -q4), 0),
            Exact.rat(Q.of(1, 2)),
            Exact.surd(Q.of(1, 2), 2),
            Exact.surd(Q.of(1, 2), 3),
            Exact.make(mapOf(6L to q4, 2L to q4), 0),
            Exact.int(1),
        )
    }

    private fun exSin15(k0: Long): Exact? {
        var k = ((k0 % 24) + 24) % 24
        var neg = false
        if (k >= 12) { k -= 12; neg = true }
        if (k > 6) k = 12 - k
        val v = S15[k.toInt()]
        return if (neg) v?.let { -it } else v
    }

    /** Angle in degrees as an exact rational, when the argument is exact. */
    private fun degExact(a: Num, u: Angle): Q? {
        val ex = a.ex ?: return null
        return when (u) {
            Angle.DEG -> ex.rational()
            Angle.GRAD -> ex.rational()?.let { it * Q.of(9, 10) }
            Angle.RAD -> if (ex.isZero) Q.ZERO else if (ex.pi == 1 && ex.t.size == 1 && ex.t[1L] != null) ex.t[1L]!! * Q.of(180) else null
        }
    }

    private fun k15(q: Q?): Long? {
        if (q == null) return null
        val k = q / Q.of(15)
        return if (k.isInt && k.n.bitLength() < 40) k.n.toLong() else null
    }

    private fun sinDeg(deg: Double): Double {
        var a = deg % 360.0
        if (a < 0) a += 360.0
        return when (a) {
            0.0, 180.0 -> 0.0
            90.0 -> 1.0
            270.0 -> -1.0
            30.0, 150.0 -> 0.5
            210.0, 330.0 -> -0.5
            else -> Math.sin(Math.toRadians(a))
        }
    }

    /** Sine and cosine with clean values at quadrant boundaries (E-2). */
    private fun sc(x: Double, u: Angle): Pair<Double, Double> {
        when (u) {
            Angle.DEG -> return Pair(sinDeg(x), sinDeg(x + 90.0))
            Angle.GRAD -> { val d = x * 9.0 / 10.0; return Pair(sinDeg(d), sinDeg(d + 90.0)) }
            Angle.RAD -> {
                val q = x / (Math.PI / 2)
                val k = Math.rint(q)
                if (abs(q - k) < 1e-12 * Math.max(1.0, abs(q)) && abs(k) < 1e9) {
                    val m = ((k.toLong() % 4) + 4) % 4
                    return when (m) { 0L -> Pair(0.0, 1.0); 1L -> Pair(1.0, 0.0); 2L -> Pair(0.0, -1.0); else -> Pair(-1.0, 0.0) }
                }
                return Pair(Math.sin(x), Math.cos(x))
            }
        }
    }

    fun sin(a: Num, u: Angle): Num = num(sc(a.d, u).first, k15(degExact(a, u))?.let { exSin15(it) })
    fun cos(a: Num, u: Angle): Num = num(sc(a.d, u).second, k15(degExact(a, u))?.let { exSin15(it + 6) })
    fun tan(a: Num, u: Angle): Num {
        val (s, c) = sc(a.d, u)
        if (c == 0.0) throw CalcError(2)
        val k = k15(degExact(a, u))
        val ex = if (k != null) { val es = exSin15(k); val ec = exSin15(k + 6); if (es != null && ec != null) es.div(ec) else null } else null
        return num(s / c, ex)
    }

    private fun fromRad(r: Double, u: Angle) = when (u) {
        Angle.DEG -> Math.toDegrees(r)
        Angle.RAD -> r
        Angle.GRAD -> Math.toDegrees(r) * 10.0 / 9.0
    }

    private fun exAngle(k: Long, u: Angle): Exact? = when (u) {
        Angle.DEG -> Exact.int(k * 15)
        Angle.GRAD -> Exact.rat(Q.of(k * 50, 3))
        Angle.RAD -> Exact.rat(Q.of(k, 12))?.times(Exact.PI)
    }

    private fun invExact(ex: Exact?, range: LongRange, f: (Long) -> Exact?, u: Angle): Exact? {
        if (ex == null) return null
        for (k in range) if (f(k) == ex) return exAngle(k, u)
        return null
    }

    fun asin(a: Num, u: Angle): Num {
        if (abs(a.d) > 1) throw CalcError(2)
        return num(fromRad(Math.asin(a.d), u), invExact(a.ex, -6L..6L, { exSin15(it) }, u))
    }
    fun acos(a: Num, u: Angle): Num {
        if (abs(a.d) > 1) throw CalcError(2)
        return num(fromRad(Math.acos(a.d), u), invExact(a.ex, 0L..12L, { exSin15(it + 6) }, u))
    }
    fun atan(a: Num, u: Angle): Num =
        num(fromRad(Math.atan(a.d), u), invExact(a.ex, -5L..5L, { k -> exSin15(k + 6)?.let { c -> exSin15(k)?.div(c) } }, u))

    fun sinh(a: Num) = num(Math.sinh(a.d))
    fun cosh(a: Num) = num(Math.cosh(a.d))
    fun tanh(a: Num) = num(Math.tanh(a.d))
    fun asinh(a: Num) = num(Math.log(a.d + Math.sqrt(a.d * a.d + 1)).let { if (a.d < 0) -Math.log(-a.d + Math.sqrt(a.d * a.d + 1)) else it })
    fun acosh(a: Num): Num { if (a.d < 1) throw CalcError(2); return num(Math.log(a.d + Math.sqrt(a.d * a.d - 1))) }
    fun atanh(a: Num): Num { if (abs(a.d) >= 1) throw CalcError(2); return num(0.5 * Math.log((1 + a.d) / (1 - a.d))) }

    // ---- integers ----
    fun fact(a: Num): Num {
        if (!isInt(a.d) || a.d < 0 || a.d > 69) throw CalcError(2)
        var r = 1.0
        var e: Exact? = Exact.int(1)
        for (i in 2..a.d.toInt()) { r *= i; e = e?.times(Exact.int(i.toLong())!!) }
        return num(r, e)
    }
    private fun chk(n: Num, r: Num) { if (!isInt(n.d) || !isInt(r.d) || r.d < 0 || n.d < r.d || n.d >= 1e10) throw CalcError(2) }
    fun nPr(n: Num, r: Num): Num {
        chk(n, r)
        var p = 1.0
        var i = 0.0
        while (i < r.d) { p *= (n.d - i); i++; if (p >= 1e100) throw CalcError(2) }
        return num(p, if (p < 1e12) Exact.int(p.toLong()) else null)
    }
    fun nCr(n: Num, r: Num): Num {
        chk(n, r)
        val k = Math.min(r.d, n.d - r.d)
        var p = 1.0
        var i = 1.0
        while (i <= k) { p = p * (n.d - k + i) / i; i++; if (p >= 1e100) throw CalcError(2) }
        p = Math.rint(p)
        return num(p, if (p < 1e12) Exact.int(p.toLong()) else null)
    }
    private fun gcdL(a: Long, b: Long): Long = if (b == 0L) a else gcdL(b, a % b)
    private fun intArg(a: Num): Long { if (!isInt(a.d) || abs(a.d) >= 1e10) throw CalcError(2); return abs(a.d.toLong()) }
    fun gcd(a: Num, b: Num): Num { val g = gcdL(intArg(a), intArg(b)); return numInt(g) }
    fun lcm(a: Num, b: Num): Num {
        val x = intArg(a); val y = intArg(b)
        if (x == 0L || y == 0L) return numInt(0)
        return num((x / gcdL(x, y)).toDouble() * y.toDouble()).let { if (it.d < 1e12) numInt(it.d.toLong()) else it }
    }
    fun intDiv(a: Num, b: Num): V {
        if (b.d == 0.0) throw CalcError(2)
        val q = if (a.d / b.d >= 0) Math.floor(a.d / b.d) else Math.ceil(a.d / b.d)
        if (abs(q) >= 1e10) return div(a, b)
        return QR(q, fin(a.d - q * b.d))
    }

    fun abs(a: Num) = if (a.d < 0) neg(a) else a
    fun ipart(a: Num) = num(if (a.d < 0) Math.ceil(a.d) else Math.floor(a.d))
    fun fpart(a: Num) = sub(Num(a.d), ipart(a))
    fun int(a: Num) = num(Math.floor(a.d))

    /** Prime factors (E-5): list of (prime, power); a last entry with power 0 is an unfactored remainder. */
    fun primeFactors(n0: Long): List<Pair<Long, Int>> {
        val out = ArrayList<Pair<Long, Int>>()
        var n = n0
        var p = 2L
        while (p <= 997 && p * p <= n) {
            var k = 0
            while (n % p == 0L) { n /= p; k++ }
            if (k > 0) out.add(Pair(p, k))
            p += if (p == 2L) 1 else 2
        }
        if (n > 1) out.add(Pair(n, if (n <= 997) 1 else 0))
        return out
    }

    fun convertAngle(x: Double, from: Angle, to: Angle): Double {
        val deg = when (from) { Angle.DEG -> x; Angle.RAD -> Math.toDegrees(x); Angle.GRAD -> x * 9.0 / 10.0 }
        return when (to) { Angle.DEG -> deg; Angle.RAD -> Math.toRadians(deg); Angle.GRAD -> deg * 10.0 / 9.0 }
    }

    // ---- N-base (M-1) ----
    fun baseFix(d: Double, b: Base): Double {
        val t = if (d < 0) Math.ceil(d) else Math.floor(d)
        if (t > b.posMax || t < b.negMin) throw CalcError(2)
        return t
    }
    private const val MASK40 = (1L shl 40) - 1
    private fun bits(b: Base) = when (b) { Base.BIN -> 10; Base.OCT -> 30; else -> 40 }
    private fun toBits(d: Double, b: Base): Long = d.toLong() and ((1L shl bits(b)) - 1)
    private fun fromBits(v: Long, b: Base): Double {
        val n = bits(b)
        val m = v and ((1L shl n) - 1)
        val s = if (m and (1L shl (n - 1)) != 0L) m - (1L shl n) else m
        return baseFix(s.toDouble(), b)
    }
    fun logic(op: String, a: Double, b: Double, base: Base): Double {
        val x = toBits(a, base); val y = toBits(b, base)
        return fromBits(when (op) { "AND" -> x and y; "OR" -> x or y; "XOR" -> x xor y; else -> (x xor y).inv() }, base)
    }
    fun not(a: Double, base: Base) = fromBits(toBits(a, base).inv(), base)

    fun toBaseString(d: Double, b: Base): String {
        var v = d.toLong()
        if (v < 0) {
            var m = 1L
            repeat(10) { m *= b.radix }
            v += m
        }
        return java.lang.Long.toString(v, b.radix).uppercase()
    }
    fun parseBase(s: String, b: Base): Double {
        if (s.length > 10) throw CalcError(2)
        val v = java.lang.Long.parseLong(s, b.radix)
        if (v <= b.posMax) return v.toDouble()
        var m = 1L
        repeat(10) { m *= b.radix }
        val n = v - m
        if (n < b.negMin || n >= 0) throw CalcError(2)
        return n.toDouble()
    }
}
