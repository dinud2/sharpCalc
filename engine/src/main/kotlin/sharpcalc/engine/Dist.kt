package sharpcalc.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

/** Normal probability (E-11) and the distribution functions of DISTRIBUTION mode. */
object Dist {
    private const val LN_SQRT_2PI = 0.91893853320467274178

    fun pdf(z: Double) = exp(-0.5 * z * z - LN_SQRT_2PI)

    private fun tail(x: Double): Double { // upper tail for x >= 3, continued fraction
        var f = 0.0
        for (k in 200 downTo 1) f = k / (x + f)
        return pdf(x) / (x + f)
    }

    /** Standard normal cumulative distribution. */
    fun phi(x: Double): Double {
        if (x > 40) return 1.0
        if (x < -40) return 0.0
        if (x <= -3) return tail(-x)
        if (x >= 6) return 1 - tail(x)
        var s = x; var t = 0.0; var b = x
        val q = x * x
        var i = 1.0
        while (s != t) { t = s; i += 2; b *= q / i; s = t + b }
        return 0.5 + s * exp(-0.5 * q - LN_SQRT_2PI)
    }

    fun round6(x: Double) = Math.rint(x * 1e6) / 1e6

    /**
     * DISTRIBUTION results are cut (not rounded) after the 9th decimal place: three of the manual's
     * worked examples (0.046985312, 0.949987459, 0.706438449) only come out that way.
     */
    fun trunc9(x: Double): Double =
        java.math.BigDecimal(java.lang.Double.toString(r14(x))).setScale(9, java.math.RoundingMode.DOWN).toDouble()

    fun normalPdf(x: Double, mu: Double, sd: Double): Double { if (sd <= 0) throw CalcError(2); return pdf((x - mu) / sd) / sd }
    fun normalCdf(x1: Double, x2: Double, mu: Double, sd: Double): Double {
        if (sd <= 0) throw CalcError(2)
        return phi((x2 - mu) / sd) - phi((x1 - mu) / sd)
    }
    fun invNormal(a: Double, mu: Double, sd: Double): Double {
        if (a <= 0 || a >= 1 || sd <= 0) throw CalcError(2)
        var lo = -40.0; var hi = 40.0
        repeat(200) { val m = (lo + hi) / 2; if (phi(m) < a) lo = m else hi = m }
        return mu + sd * (lo + hi) / 2
    }

    private fun chkBin(x: Double, n: Double, p: Double) { if (!isInt(x) || !isInt(n) || x < 0 || n < 0 || x > n || p < 0 || p > 1 || n > 1e6) throw CalcError(2) }
    fun binomPdf(x: Double, n: Double, p: Double): Double {
        chkBin(x, n, p)
        return R.nCr(Num(n), Num(x)).d * Math.pow(p, x) * Math.pow(1 - p, n - x)
    }
    fun binomCdf(x: Double, n: Double, p: Double): Double {
        chkBin(x, n, p)
        var s = 0.0
        var k = 0.0
        while (k <= x) { s += binomPdf(k, n, p); k++ }
        return Math.min(1.0, s)
    }
    fun poissonPdf(x: Double, mu: Double): Double {
        if (!isInt(x) || x < 0 || mu <= 0 || x > 1e6) throw CalcError(2)
        var t = exp(-mu)
        var k = 1.0
        while (k <= x) { t *= mu / k; k++ }
        return t
    }
    fun poissonCdf(x: Double, mu: Double): Double {
        if (!isInt(x) || x < 0 || mu <= 0 || x > 1e6) throw CalcError(2)
        var t = exp(-mu); var s = t
        var k = 1.0
        while (k <= x) { t *= mu / k; s += t; k++ }
        return Math.min(1.0, s)
    }
}

/** Statistics data and results (M-2, M-3). */
class StatData : StatSource {
    /** 0 SD, 1 a+bx, 2 a+bx+cx², 3 a·e^bx, 4 a+b·lnx, 5 a·x^b, 6 a+b/x, 7 a·b^x */
    var sub = 0
    val rows = ArrayList<DoubleArray>() // x, y, frq
    val twoVar get() = sub != 0

    companion object {
        val SUBS = listOf("SD", "a+bx", "a+bx+cx²", "a·e^bx", "a+b·lnx", "a·x^b", "a+b/x", "a·b^x")
        const val XB = ""
        const val YB = ""
        val X_VARS = listOf("n", XB, "sx", "s²x", "σx", "σ²x", "Σx", "Σx²", "xmin", "xmax")
        val Y_VARS = listOf(YB, "sy", "s²y", "σy", "σ²y", "Σy", "Σy²", "Σxy", "Σx²y", "Σx³", "Σx⁴", "ymin", "ymax")
        val Q_VARS = listOf("Q1", "Med", "Q3")
    }

    /** Data items used, counted the way the manual describes (limit 100). */
    fun used(): Int = rows.sumOf { (if (twoVar) 2 else 1) + (if (it[2] != 1.0) 1 else 0) }
    fun checkRoom(extra: Int) { if (used() + extra > 100) throw CalcError(4) }

    private fun tx(x: Double): Double = when (sub) {
        4, 5 -> { if (x <= 0) throw CalcError(2); Math.log(x) }
        6 -> { if (x == 0.0) throw CalcError(2); 1 / x }
        else -> x
    }
    private fun ty(y: Double): Double = when (sub) {
        3, 5, 7 -> { if (y <= 0) throw CalcError(2); Math.log(y) }
        else -> y
    }

    private class Sums { var n = 0.0; var x = 0.0; var x2 = 0.0; var y = 0.0; var y2 = 0.0; var xy = 0.0; var x2y = 0.0; var x3 = 0.0; var x4 = 0.0
        var xmin = Double.MAX_VALUE; var xmax = -Double.MAX_VALUE; var ymin = Double.MAX_VALUE; var ymax = -Double.MAX_VALUE }

    private fun sums(): Sums {
        val s = Sums()
        for (r in rows) {
            val x = tx(r[0]); val y = if (twoVar) ty(r[1]) else 0.0; val f = r[2]
            s.n += f; s.x += f * x; s.x2 += f * x * x; s.y += f * y; s.y2 += f * y * y; s.xy += f * x * y
            s.x2y += f * x * x * y; s.x3 += f * x * x * x; s.x4 += f * x * x * x * x
            s.xmin = Math.min(s.xmin, x); s.xmax = Math.max(s.xmax, x); s.ymin = Math.min(s.ymin, y); s.ymax = Math.max(s.ymax, y)
        }
        if (s.n <= 0) throw CalcError(2)
        return s
    }

    private fun sq(v: Double): Double { if (v < 0) { if (v > -1e-9) return 0.0; throw CalcError(2) }; return sqrt(v) }
    private fun dv(a: Double, b: Double): Double { if (b == 0.0) throw CalcError(2); return a / b }

    /** Linear fit on converted data: returns (A, B). */
    private fun lin(s: Sums): Pair<Double, Double> {
        val b = dv(s.n * s.xy - s.x * s.y, s.n * s.x2 - s.x * s.x)
        return Pair((s.y - b * s.x) / s.n, b)
    }

    private fun quad(s: Sums): DoubleArray {
        val r = Lin.linear(arrayOf(
            doubleArrayOf(s.n, s.x, s.x2, s.y),
            doubleArrayOf(s.x, s.x2, s.x3, s.xy),
            doubleArrayOf(s.x2, s.x3, s.x4, s.x2y),
        ))
        return doubleArrayOf(r[0], r[1], r[2])
    }

    private fun coef(): DoubleArray {
        val s = sums()
        if (sub == 2) return quad(s)
        val (a, b) = lin(s)
        return when (sub) {
            3, 5 -> doubleArrayOf(exp(a), b)
            7 -> doubleArrayOf(exp(a), exp(b))
            else -> doubleArrayOf(a, b)
        }
    }

    private fun corr(s: Sums) = dv(s.n * s.xy - s.x * s.y, sq((s.n * s.x2 - s.x * s.x) * (s.n * s.y2 - s.y * s.y)))

    private fun quartile(which: Int): Double {
        val data = ArrayList<Double>()
        for (r in rows) {
            if (!isInt(r[2]) || r[2] < 0) throw CalcError(2)
            if (data.size + r[2] > 100000) throw CalcError(2)
            repeat(r[2].toInt()) { data.add(tx(r[0])) }
        }
        if (data.isEmpty()) throw CalcError(2)
        data.sort()
        fun med(l: List<Double>): Double { if (l.isEmpty()) throw CalcError(2); val m = l.size / 2; return if (l.size % 2 == 1) l[m] else (l[m - 1] + l[m]) / 2 }
        val half = data.size / 2
        return when (which) {
            1 -> if (data.size == 1) data[0] else med(data.subList(0, half))
            2 -> med(data)
            else -> if (data.size == 1) data[0] else med(data.subList(data.size - half, data.size))
        }
    }

    override fun value(id: String): Double {
        val needY = id in Y_VARS || id in listOf("r", "a", "b", "c", "R²", "r²")
        if (needY && !twoVar) throw CalcError(1)
        val s = sums()
        val mx = s.x / s.n; val my = s.y / s.n
        return fin(when (id) {
            "n" -> s.n
            XB -> mx
            "sx" -> sq(dv(s.x2 - s.n * mx * mx, s.n - 1))
            "s²x" -> dv(s.x2 - s.n * mx * mx, s.n - 1)
            "σx" -> sq((s.x2 - s.n * mx * mx) / s.n)
            "σ²x" -> (s.x2 - s.n * mx * mx) / s.n
            "Σx" -> s.x
            "Σx²" -> s.x2
            "xmin" -> s.xmin
            "xmax" -> s.xmax
            YB -> my
            "sy" -> sq(dv(s.y2 - s.n * my * my, s.n - 1))
            "s²y" -> dv(s.y2 - s.n * my * my, s.n - 1)
            "σy" -> sq((s.y2 - s.n * my * my) / s.n)
            "σ²y" -> (s.y2 - s.n * my * my) / s.n
            "Σy" -> s.y
            "Σy²" -> s.y2
            "Σxy" -> s.xy
            "Σx²y" -> s.x2y
            "Σx³" -> s.x3
            "Σx⁴" -> s.x4
            "ymin" -> s.ymin
            "ymax" -> s.ymax
            "Q1" -> quartile(1)
            "Med" -> quartile(2)
            "Q3" -> quartile(3)
            "r" -> { if (sub == 2) throw CalcError(1); corr(s) }
            "r²" -> { if (sub == 2) throw CalcError(1); val r = corr(s); r * r }
            "a" -> coef()[0]
            "b" -> coef()[1]
            "c" -> { if (sub != 2) throw CalcError(1); coef()[2] }
            "R²" -> {
                if (sub != 2) throw CalcError(1)
                val k = coef()
                var res = 0.0
                for (r in rows) { val e = r[1] - (k[0] + k[1] * r[0] + k[2] * r[0] * r[0]); res += r[2] * e * e }
                1 - dv(res, s.y2 - s.n * my * my)
            }
            else -> throw CalcError(1)
        })
    }

    override fun estY(x: Double): Double {
        if (!twoVar) throw CalcError(1)
        val k = coef()
        return fin(when (sub) {
            1 -> k[0] + k[1] * x
            2 -> k[0] + k[1] * x + k[2] * x * x
            3 -> k[0] * exp(k[1] * x)
            4 -> { if (x <= 0) throw CalcError(2); k[0] + k[1] * Math.log(x) }
            5 -> k[0] * Math.pow(x, k[1])
            6 -> k[0] + dv(k[1], x)
            else -> k[0] * Math.pow(k[1], x)
        })
    }

    override fun estX(y: Double): List<Double> {
        if (!twoVar) throw CalcError(1)
        val k = coef()
        fun ln(v: Double): Double { if (v <= 0) throw CalcError(2); return Math.log(v) }
        return when (sub) {
            1 -> listOf(fin(dv(y - k[0], k[1])))
            2 -> {
                val r = Lin.quadratic(k[2], k[1], k[0] - y)
                if (r[0].im != 0.0) throw CalcError(2)
                if (r[0].re == r[1].re) listOf(r[0].re) else listOf(r[0].re, r[1].re)
            }
            3 -> listOf(fin(dv(ln(dv(y, k[0])), k[1])))
            4 -> listOf(fin(exp(dv(y - k[0], k[1]))))
            5 -> listOf(fin(Math.pow(dv(y, k[0]), dv(1.0, k[1]))))
            6 -> listOf(fin(dv(k[1], y - k[0])))
            else -> listOf(fin(dv(ln(dv(y, k[0])), ln(k[1]))))
        }
    }
}
