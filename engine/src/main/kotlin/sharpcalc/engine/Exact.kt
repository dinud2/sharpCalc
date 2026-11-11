package sharpcalc.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext

/** Normalised rational number. */
class Q private constructor(val n: BigInteger, val d: BigInteger) {
    companion object {
        val ZERO = Q(BigInteger.ZERO, BigInteger.ONE)
        val ONE = Q(BigInteger.ONE, BigInteger.ONE)
        fun of(n: BigInteger, d: BigInteger): Q {
            if (d.signum() == 0) throw CalcError(2)
            val g = n.gcd(d).let { if (it.signum() == 0) BigInteger.ONE else it }
            var nn = n / g
            var dd = d / g
            if (dd.signum() < 0) { nn = -nn; dd = -dd }
            return Q(nn, dd)
        }
        fun of(n: Long, d: Long = 1) = of(BigInteger.valueOf(n), BigInteger.valueOf(d))
    }

    operator fun plus(o: Q) = of(n * o.d + o.n * d, d * o.d)
    operator fun minus(o: Q) = of(n * o.d - o.n * d, d * o.d)
    operator fun times(o: Q) = of(n * o.n, d * o.d)
    operator fun div(o: Q) = of(n * o.d, d * o.n)
    operator fun unaryMinus() = Q(-n, d)
    val sign get() = n.signum()
    val isInt get() = d == BigInteger.ONE
    fun abs() = Q(n.abs(), d)
    fun toDouble() = BigDecimal(n).divide(BigDecimal(d), MathContext.DECIMAL64).toDouble()
    override fun equals(other: Any?) = other is Q && other.n == n && other.d == d
    override fun hashCode() = n.hashCode() * 31 + d.hashCode()
    override fun toString() = if (isInt) "$n" else "$n/$d"
}

/**
 * Exact value: (sum of q_i * sqrt(r_i)) * pi^pi, r_i square-free, at most two terms.
 * This is the family of results the WriteView EXACT setting can display.
 */
class Exact private constructor(val t: Map<Long, Q>, val pi: Int) {
    companion object {
        private const val MAXBITS = 46
        val ZERO = Exact(emptyMap(), 0)
        val PI = Exact(mapOf(1L to Q.ONE), 1)
        fun make(t: Map<Long, Q>, pi: Int): Exact? {
            val m = t.filterValues { it.sign != 0 }
            if (m.isEmpty()) return ZERO
            if (m.size > 2 || pi !in 0..1) return null
            for ((r, q) in m) if (r > 1_000_000_000L || q.n.bitLength() > MAXBITS || q.d.bitLength() > MAXBITS) return null
            return Exact(m.toSortedMap(), pi)
        }
        fun rat(q: Q) = make(mapOf(1L to q), 0)
        fun int(n: Long) = rat(Q.of(n))
        fun surd(q: Q, r: Long) = make(mapOf(r to q), 0)
        private fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
    }

    val isZero get() = t.isEmpty()
    fun rational(): Q? = if (t.isEmpty()) Q.ZERO else if (pi == 0 && t.size == 1) t[1L] else null

    fun toDouble(): Double {
        var s = 0.0
        for ((r, q) in t) s += q.toDouble() * Math.sqrt(r.toDouble())
        return if (pi == 1) s * Math.PI else s
    }

    operator fun unaryMinus() = Exact(t.mapValues { -it.value }, pi)

    fun plus(o: Exact): Exact? {
        if (isZero) return o
        if (o.isZero) return this
        if (pi != o.pi) return null
        val m = HashMap(t)
        for ((r, q) in o.t) m[r] = (m[r] ?: Q.ZERO) + q
        return make(m, pi)
    }

    fun minus(o: Exact) = plus(-o)

    fun times(o: Exact): Exact? {
        if (isZero || o.isZero) return ZERO
        val m = HashMap<Long, Q>()
        for ((ra, qa) in t) for ((rb, qb) in o.t) {
            val g = gcd(ra, rb)
            val r = (ra / g) * (rb / g)
            m[r] = (m[r] ?: Q.ZERO) + qa * qb * Q.of(g)
        }
        return make(m, pi + o.pi)
    }

    fun inv(): Exact? {
        if (isZero || pi != 0) return null
        val e = t.entries.toList()
        if (e.size == 1) {
            val (r, q) = e[0]
            return make(mapOf(r to Q.ONE / (q * Q.of(r))), 0)
        }
        val (r1, q1) = e[0]
        val (r2, q2) = e[1]
        val den = q1 * q1 * Q.of(r1) - q2 * q2 * Q.of(r2)
        if (den.sign == 0) return null
        return make(mapOf(r1 to q1 / den, r2 to -q2 / den), 0)
    }

    fun div(o: Exact): Exact? = o.inv()?.let { times(it) }

    fun sqrt(): Exact? {
        val q = rational() ?: return null
        if (q.sign < 0) return null
        if (q.sign == 0) return ZERO
        val m = q.n * q.d
        if (m.bitLength() > 34) return null
        var r = m.toLong()
        var s = 1L
        var f = 2L
        while (f * f <= r) {
            while (r % (f * f) == 0L) { r /= f * f; s *= f }
            f++
        }
        return make(mapOf(r to Q.of(BigInteger.valueOf(s), q.d)), 0)
    }

    fun powInt(n: Int): Exact? {
        if (n < 0) return inv()?.powInt(-n)
        var result: Exact? = int(1)
        var base: Exact? = this
        var k = n
        while (k > 0) {
            if (k and 1 == 1) result = result?.let { r -> base?.let { r.times(it) } }
            k = k shr 1
            if (k > 0) base = base?.let { it.times(it) }
            if (result == null || base == null) return null
        }
        return result
    }

    override fun equals(other: Any?) = other is Exact && other.pi == pi && other.t == t
    override fun hashCode() = t.hashCode() + pi
}
