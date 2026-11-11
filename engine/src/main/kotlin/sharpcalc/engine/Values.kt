package sharpcalc.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

sealed class V
/** Real number. [ex] is the exact form when known; [dms] marks sexagesimal display. */
class Num(val d: Double, val ex: Exact? = null, val dms: Boolean = false) : V()
class Cx(val re: Double, val im: Double) : V()
class Mat(val a: Array<DoubleArray>, val vec: Boolean = false) : V() {
    val rows get() = a.size
    val cols get() = a[0].size
}
/** Quotient and remainder of int÷ (E-6). */
class QR(val q: Double, val r: Double) : V()

enum class Angle { DEG, RAD, GRAD }
enum class Base(val radix: Int, val posMax: Long, val negMin: Long) {
    DEC(10, 9999999999L, -9999999999L),
    BIN(2, 511, -512),
    PEN(5, 4882812, -4882812),
    OCT(8, 536870911, -536870912),
    HEX(16, 9999999999L, -9999999999L),
}
enum class Domain { REAL, COMPLEX, MATRIX, VECTOR }

interface StatSource {
    fun value(id: String): Double
    fun estY(x: Double): Double
    fun estX(y: Double): List<Double>
}

/** Everything an evaluation can read or change. */
class Ctx {
    var angle = Angle.DEG
    var base = Base.DEC
    var domain = Domain.REAL
    val vars = HashMap<String, V>()
    var ans: V = Num(0.0, Exact.ZERO)
    var stat: StatSource? = null
    val mats = arrayOfNulls<Mat>(4)
    val vecs = arrayOfNulls<Mat>(4)
    var rng = java.util.Random()
    @Volatile var cancel = false
    /** Set when the expression used a fraction, which changes the result format in APPROX and Line. */
    var fracUsed = false
    fun tick() { if (cancel) throw Cancelled() }
    fun getVar(n: String): V = vars[n] ?: Num(0.0, Exact.ZERO)
}

private val MC14 = MathContext(14, RoundingMode.HALF_UP)

/** Round to the calculator's 14-digit internal mantissa. */
fun r14(x: Double): Double {
    if (x == 0.0 || x.isNaN() || x.isInfinite()) return x
    return BigDecimal(x).round(MC14).toDouble()
}

/** Round and apply the calculation range: |x| >= 1E100 is ERROR 02, |x| < 1E-99 is 0. */
fun fin(x: Double): Double {
    if (x.isNaN() || x.isInfinite()) throw CalcError(2)
    val r = r14(x)
    val a = Math.abs(r)
    if (a >= 1e100) throw CalcError(2)
    if (a < 1e-99) return 0.0
    return r
}

fun num(d: Double, ex: Exact? = null, dms: Boolean = false): Num =
    if (ex != null) Num(fin(ex.toDouble()), ex, dms) else Num(fin(d), null, dms)

fun numQ(q: Q): Num { val e = Exact.rat(q); return if (e == null) Num(fin(q.toDouble())) else num(0.0, e) }
fun numInt(n: Long): Num = Num(n.toDouble(), Exact.int(n))

val PI14 = 3.1415926535898
val E14 = 2.7182818284590

fun isInt(d: Double) = d == Math.rint(d) && Math.abs(d) < 1e15

/** Exact form of a decimal literal such as "0.125". */
fun exactOf(text: String): Exact? = try {
    val bd = BigDecimal(text)
    if (bd.precision() > 12 || Math.abs(bd.scale()) > 12) null
    else if (bd.scale() <= 0) Exact.rat(Q.of(bd.toBigIntegerExact(), java.math.BigInteger.ONE))
    else Exact.rat(Q.of(bd.unscaledValue(), java.math.BigInteger.TEN.pow(bd.scale())))
} catch (e: Exception) { null }
