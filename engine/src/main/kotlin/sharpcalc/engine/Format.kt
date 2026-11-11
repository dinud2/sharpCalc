package sharpcalc.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

enum class FSE { FIX, SCI, ENG, NORM1, NORM2 }

class Settings {
    var fse = FSE.NORM1
    var tab = 0
    var wv = true        // WriteView editor (false: Line editor)
    var exact = true     // EXACT(a/b,r,p) (false: APPROX.)
    var insert = true
    var recurring = false
    var comma = false
    var contrast = 4
    var name = ""
}

/** Number display (E-7): 10-digit mantissa, 2-digit exponent. */
object Fmt {
    const val EXP = 'ᴇ'
    private val HU = RoundingMode.HALF_UP
    private fun bd(x: Double) = BigDecimal(java.lang.Double.toString(r14(x)))
    private fun exp2(e: Int) = (if (e < 0) "-" else "") + Math.abs(e).toString().padStart(2, '0')
    private fun dot(s: String) = if (s.contains('.')) s else "$s."
    private fun strip(s: String) = if (s.contains('.')) s.trimEnd('0') else "$s."
    private fun plain(b: BigDecimal, dec: Int, strip: Boolean): String {
        val r = b.setScale(Math.max(0, dec), HU)
        if (r.signum() == 0) return if (strip) "0." else dot(r.abs().toPlainString())
        val s = r.toPlainString()
        return if (strip) strip(s) else dot(s)
    }
    private fun intDigits(b: BigDecimal) = Math.max(1, b.precision() - b.scale())

    private fun sci(b: BigDecimal, sig: Int, strip: Boolean): String {
        val r = b.round(MathContext(sig, HU))
        val e = r.precision() - r.scale() - 1
        val m = r.movePointLeft(e).setScale(sig - 1, HU).toPlainString()
        return (if (strip) strip(m) else dot(m)) + EXP + exp2(e)
    }

    private fun eng(b: BigDecimal, tab: Int): String {
        val r = b.round(MathContext(14, HU))
        var e = Math.floorDiv(r.precision() - r.scale() - 1, 3) * 3
        var m = b.movePointLeft(e).setScale(tab, HU)
        if (m.abs() >= BigDecimal(1000)) { e += 3; m = b.movePointLeft(e).setScale(tab, HU) }
        return dot(m.toPlainString()) + EXP + exp2(e)
    }

    fun num(x: Double, s: Settings): String = num(x, s.fse, s.tab)

    fun num(x: Double, fse: FSE, tab: Int): String {
        if (x == 0.0) return when (fse) {
            FSE.FIX -> plain(BigDecimal.ZERO, tab, false)
            FSE.SCI -> dot(BigDecimal.ZERO.setScale((if (tab == 0) 10 else tab) - 1).toPlainString()) + EXP + "00"
            FSE.ENG -> dot(BigDecimal.ZERO.setScale(tab).toPlainString()) + EXP + "00"
            else -> "0."
        }
        val b = bd(x)
        val ax = b.abs()
        val big = ax >= BigDecimal("9999999999.5")
        return when (fse) {
            FSE.NORM1, FSE.NORM2 -> {
                val lo = if (fse == FSE.NORM1) BigDecimal("0.000000001") else BigDecimal("0.01")
                if (ax < lo || big) sci(b, 10, true) else plain(b, 10 - intDigits(b), true)
            }
            FSE.FIX -> if (big) sci(b, 10, true) else plain(b, Math.min(tab, 10 - intDigits(b)), false)
            FSE.SCI -> sci(b, if (tab == 0) 10 else tab, false)
            FSE.ENG -> eng(b, tab)
        }
    }

    /** Engineering notation with a chosen exponent (the ENG shift keys). */
    fun engAt(x: Double, e: Int): String {
        if (x == 0.0) return "0.$EXP" + exp2(e)
        val m = bd(x).movePointLeft(e).round(MathContext(10, HU))
        return strip(m.toPlainString().let { if (it.contains('.')) it else "$it." }) + EXP + exp2(e)
    }

    fun engExp(x: Double): Int {
        if (x == 0.0) return 0
        val r = bd(x)
        return Math.floorDiv(r.precision() - r.scale() - 1, 3) * 3
    }

    /** Sexagesimal display d°m′s″ (E-3). */
    fun dms(x: Double): String {
        val b = bd(x)
        val dl = intDigits(b)
        val dec = Math.max(0, Math.min(6, 10 - dl - 4))
        val total = b.abs().multiply(BigDecimal(3600)).setScale(dec, HU)
        val whole = total.toBigInteger()
        val d = whole.divide(BigInteger.valueOf(3600))
        val m = whole.mod(BigInteger.valueOf(3600)).divide(BigInteger.valueOf(60))
        val s = total.subtract(BigDecimal(d.multiply(BigInteger.valueOf(3600)).add(m.multiply(BigInteger.valueOf(60)))))
        var ss = s.toPlainString()
        if (ss.contains('.')) ss = ss.trimEnd('0').trimEnd('.')
        return (if (b.signum() < 0) "-" else "") + "$d°$m′$ss″"
    }

    /** The number a display string stands for (used by MDF and the table). */
    fun parse(s: String): Double = s.replace(EXP, 'E').replace(',', '.').let { if (it.endsWith(".")) it + "0" else it }.replace(".E", ".0E").toDouble()

    /** Compact form for narrow cells (tables, matrices): at most [width] characters. */
    fun cell(x: Double, width: Int): String {
        var s = num(x, FSE.NORM1, 0).trimEnd('.')
        if (s.length <= width) return s
        if (!s.contains(EXP)) {
            val b = bd(x)
            val neg = if (b.signum() < 0) 1 else 0
            val id = intDigits(b)
            if (id + neg <= width && b.abs() >= BigDecimal("0.001")) {
                val dec = width - neg - id - 1
                if (dec >= 1 || id + neg <= width) {
                    s = plain(b, Math.max(0, dec), true).trimEnd('.')
                    if (s.length <= width) return s
                }
            }
        }
        for (sig in 10 downTo 1) {
            val t = sci(bd(x), sig, true).replace(".$EXP", "$EXP")
            if (t.length <= width) return t
        }
        return s.take(width)
    }
}
