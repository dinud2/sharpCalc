package sharpcalc

import org.junit.Test
import sharpcalc.engine.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Line-style helper: builds a token row from a compact string. */
fun toks(s: String): Row {
    val r = Row()
    val multi = listOf(
        "hA", "hB", "hC", "hD", "hE", "hF", "logn(", "sin⁻¹", "cos⁻¹", "tan⁻¹", "sinh⁻¹", "cosh⁻¹", "tanh⁻¹", "sinh", "cosh", "tanh", "sin", "cos", "tan", "log", "ln",
        "neg", "Ans", "nCr", "nPr", "GCD", "LCM", "int÷", "AND", "XNOR", "XOR", "OR", "NOT", "NEG", "³√", "x√", "⁻¹", "(%)",
        "abs", "ipart", "fpart", "int", "logn(", "Σ(", "Π(", "∫(", "d/dx(", "R.Int(",
    )
    var i = 0
    outer@ while (i < s.length) {
        if (s[i] == ' ') { i++; continue }
        for (m in multi) if (s.startsWith(m, i)) { r.items.add(Tok(m)); i += m.length; continue@outer }
        r.items.add(Tok(s[i].toString())); i++
    }
    return r
}

class EngineTest {
    private val c = Ctx()
    private val st = Settings()
    private fun ev(s: String): V = Parser.eval(toks(s), c)
    private fun d(s: String): String = Fmt.num((ev(s) as Num).d, st)

    @Test fun `E-1 arithmetic and priority`() {
        assertEquals("14.", d("2+3*4"))
        assertEquals("0.3", d("0.1+0.2"))
        assertEquals("-4.", d("neg2²"))
        assertEquals("1.", d("1/3*3"))
        assertEquals("512.", d("2^3^2"))
        assertEquals("0.125", d("2^neg3"))
        assertEquals("3.", d("(1+2"))
        assertEquals("6.", d("2(3)"))
        assertEquals("120.", d("5!"))
        assertEquals("10.", d("5nCr2"))
        assertEquals("20.", d("5nPr2"))
    }

    @Test fun `E-1 implied multiplication priority`() {
        c.vars["A"] = numInt(4)
        assertEquals("0.125", d("1/2A"))
        assertEquals("1.", d("2sin30"))
        assertEquals("0.5", d("1/2sin90"))
        assertEquals("0.159154943", d("1/2π"))
    }

    @Test fun `E-1 functions`() {
        assertEquals("3.", d("log1000"))
        assertEquals("2.302585093", d("ln10"))
        assertEquals("3.", d("³√27"))
        assertEquals("-2.", d("³√neg8"))
        assertEquals("2.", d("4x√16"))
        assertEquals("12.", d("24GCD36"))
        assertEquals("45.", d("15LCM9"))
        assertEquals("5.", d("abs neg5"))
        assertEquals("3.", d("logn(2,8)"))
        assertEquals("1.175201194", d("sinh1"))
    }

    @Test fun `E-2 exact trig at quadrant boundaries`() {
        assertEquals("0.", d("sin180"))
        assertEquals("0.", d("cos90"))
        assertEquals("0.5", d("sin30"))
        assertEquals("1.", d("tan45"))
        assertFailsWith<CalcError> { ev("tan90") }
        c.angle = Angle.RAD
        assertEquals("0.", d("sinπ"))
        assertEquals("-1.", d("cosπ"))
        c.angle = Angle.GRAD
        assertEquals("1.", d("sin100"))
        c.angle = Angle.DEG
        assertEquals("30.", d("sin⁻¹0.5"))
    }

    @Test fun `E-3 sexagesimal`() {
        val v = ev("12°39°18.05°") as Num
        assertEquals("12°39′18.05″", Fmt.dms(v.d))
        assertEquals("12.65501389", Fmt.num(v.d, st))
        assertEquals("10°16′21″", Fmt.dms((ev("3°30°45°+6°45°36°") as Num).d))
    }

    @Test fun `E-6 int division`() {
        val q = ev("17int÷5") as QR
        assertEquals(3.0, q.q); assertEquals(2.0, q.r)
    }

    @Test fun `E-7 display formats`() {
        assertEquals("1.234567891${Fmt.EXP}10", Fmt.num(12345678912.0, FSE.NORM1, 0))
        assertEquals("0.000000001", Fmt.num(1e-9, FSE.NORM1, 0))
        assertEquals("1.${Fmt.EXP}-10", Fmt.num(1e-10, FSE.NORM1, 0))
        assertEquals("1.${Fmt.EXP}-03", Fmt.num(0.001, FSE.NORM2, 0))
        assertEquals("1234.568", Fmt.num(1234.5678, FSE.FIX, 3))
        assertEquals("1.235${Fmt.EXP}03", Fmt.num(1234.5678, FSE.SCI, 4))
        assertEquals("1.235${Fmt.EXP}03", Fmt.num(1234.5678, FSE.ENG, 3))
        assertEquals("12.346${Fmt.EXP}03", Fmt.num(12345.678, FSE.ENG, 3))
        assertEquals("0.333333333", Fmt.num(1.0 / 3, FSE.NORM1, 0))
        assertEquals("-0.5", Fmt.num(-0.5, FSE.NORM1, 0))
    }

    @Test fun `E-8 errors`() {
        assertEquals(1, assertFailsWith<CalcError> { ev("2+-5") }.code)
        assertEquals(2, assertFailsWith<CalcError> { ev("1/0") }.code)
        assertEquals(2, assertFailsWith<CalcError> { ev("10^100") }.code)
        assertEquals(2, assertFailsWith<CalcError> { ev("70!") }.code)
        assertEquals(2, assertFailsWith<CalcError> { ev("√neg1") }.code)
        assertEquals(2, assertFailsWith<CalcError> { ev("ln0") }.code)
    }

    @Test fun `E-10 sum product integral derivative`() {
        assertEquals("55.", d("Σ(X,1,10)"))
        assertEquals("25.", d("Σ(X,1,10,2)"))
        assertEquals("120.", d("Π(X,1,5)"))
        assertEquals("9.", d("∫(X²,0,3)"))
        assertEquals("6.", d("d/dx(X²,3)"))
        assertEquals("0.017453293", d("d/dx(sinX,0)")) // DEG: d/dx sin x° = π/180
    }

    @Test fun `W-7 exact results`() {
        fun f(s: String) = Result.forms(ev(s) as Num, st, true, c.fracUsed).first().ser()
        assertEquals("{FRAC|5|6}", f("1/2+1/3"))
        assertEquals("{MIXED|1|1|2}", f("3/2"))
        assertEquals("2;{SQRT|2}", f("√8"))
        assertEquals("{FRAC|{SQRT|3}|2}", f("sin60"))
        assertEquals("{FRAC|{SQRT|2}|2}", f("1/√2"))
        assertEquals("{FRAC|-;{SQRT|2};+;{SQRT|6}|4}", f("sin15"))
        assertEquals("2;.", f("√2*√2"))
        c.angle = Angle.RAD
        assertEquals("{FRAC|1|6};π", f("sin⁻¹0.5"))
        assertEquals("2;π", f("2π"))
        c.angle = Angle.DEG
    }

    @Test fun `M-1 n-base`() {
        c.base = Base.HEX
        assertEquals("FF", R.toBaseString((ev("hFhF") as Num).d, Base.HEX))
        assertEquals("FFFFFFFFFF", R.toBaseString((ev("NEG1") as Num).d, Base.HEX))
        assertEquals("FFFFFFFF00", R.toBaseString((ev("NOThFhF") as Num).d, Base.HEX))
        assertEquals("AA", R.toBaseString((ev("hAhF AND hFhA") as Num).d, Base.HEX))
        c.base = Base.BIN
        assertEquals("1111111111", R.toBaseString((ev("NEG1") as Num).d, Base.BIN))
        assertEquals("110", R.toBaseString((ev("11+11") as Num).d, Base.BIN))
        assertFailsWith<CalcError> { ev("111111111+1") }
        c.base = Base.DEC
    }

    @Test fun `E-5 prime factorisation`() {
        assertEquals("2;{POW|2};*;3;*;5", Result.primeRow(60, true).ser())
        assertEquals("2;*;(;1;0;0;9;)", Result.primeRow(2018, true).ser())
    }

    @Test fun `E-11 normal probability and distributions (manual examples)`() {
        // V-3: the seven worked examples of the manual's DISTRIBUTION section
        fun f(x: Double) = Fmt.num(Dist.trunc9(x), st)
        assertEquals("0.046985312", f(Dist.normalPdf(65.0, 60.0, 6.0)))
        assertEquals("0.682689492", f(Dist.normalCdf(54.0, 66.0, 60.0, 6.0)))
        assertEquals("65.0497274", f(Dist.invNormal(0.8, 60.0, 6.0)))
        assertEquals("0.081130033", f(Dist.binomPdf(7.0, 15.0, 0.3)))
        assertEquals("0.949987459", f(Dist.binomCdf(7.0, 15.0, 0.3)))
        assertEquals("0.191222339", f(Dist.poissonPdf(4.0, 3.6)))
        assertEquals("0.706438449", f(Dist.poissonCdf(4.0, 3.6)))
    }

    @Test fun `M-5 complex`() {
        c.domain = Domain.COMPLEX
        val z = ev("(1+i)(1-i)")
        assertEquals(2.0, (z as Num).d)
        val w = ev("√neg4") as Cx
        assertEquals(2.0, w.im)
        val p = ev("2∠90") as Cx
        assertEquals(0.0, p.re); assertEquals(2.0, p.im)
        c.domain = Domain.REAL
    }

    @Test fun `M-6 equations`() {
        val r = Lin.linear(arrayOf(doubleArrayOf(2.0, 3.0, 4.0), doubleArrayOf(5.0, 6.0, 7.0)))
        assertEquals(-1.0, r[0]); assertEquals(2.0, r[1]); assertEquals(-3.0, r[2])
        val q = Lin.quadratic(3.0, 4.0, -95.0)
        assertEquals(5.0, q[0].re); assertEquals("-6.333333333", Fmt.num(q[1].re, st))
        val cq = Lin.quadratic(1.0, 2.0, 5.0)
        assertEquals(-1.0, cq[0].re); assertEquals(2.0, cq[0].im)
        val cu = Lin.cubic(1.0, -6.0, 11.0, -6.0)
        assertEquals(listOf(3.0, 2.0, 1.0), cu.map { it.re })
    }

    @Test fun `M-7 matrix`() {
        val m = Mat(arrayOf(doubleArrayOf(1.0, 2.0), doubleArrayOf(3.0, 4.0)))
        assertEquals(-2.0, Lin.det(m))
        val inv = Lin.inverse(m)
        assertEquals(-2.0, inv.a[0][0]); assertEquals(1.5, inv.a[1][0])
        val rr = Lin.ref(m, true)
        assertEquals(1.0, rr.a[0][0]); assertEquals(0.0, rr.a[0][1])
    }

    @Test fun `M-3 statistics`() {
        val s = StatData()
        for (x in listOf(30.0, 40.0, 40.0, 50.0)) s.rows.add(doubleArrayOf(x, 0.0, 1.0))
        assertEquals(4.0, s.value("n")); assertEquals(40.0, s.value(StatData.XB))
        assertEquals("8.164965809", Fmt.num(s.value("sx"), st))
        s.sub = 1; s.rows.clear()
        for ((x, y) in listOf(2.0 to 5.0, 12.0 to 24.0, 21.0 to 40.0, 21.0 to 40.0, 21.0 to 40.0, 15.0 to 25.0))
            s.rows.add(doubleArrayOf(x, y, 1.0))
        assertEquals("0.787383178", Fmt.num(s.value("a"), st))
        assertEquals("1.839953271", Fmt.num(s.value("b"), st))
        assertEquals("0.992818842", Fmt.num(s.value("r"), st))
        assertEquals("6.307242991", Fmt.num(s.estY(3.0), st))
        assertEquals("24.57269841", Fmt.num(s.estX(46.0)[0], st))
    }

    @Test fun `W-9 serialisation round trip`() {
        val s = "1;{FRAC|2;{POW|3}|{SQRT|5}};+;sin;{ABS|}"
        assertEquals(s, Row.parse(s).ser())
    }
}
