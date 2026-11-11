package sharpcalc

import org.junit.Test
import sharpcalc.calc.*
import sharpcalc.calc.Key.*
import sharpcalc.engine.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Drives the calculator through its keys, the way a person would (V-2). */
open class Rig {
    val c = Calculator().also { it.ctx.rng = java.util.Random(1) }
    fun p(vararg k: Key): Rig { for (x in k) c.press(x); return this }
    /** Types digits and basic operators from a string, e.g. "12+3.5". */
    fun t(s: String): Rig {
        for (ch in s) c.press(when (ch) {
            '0' -> N0; '1' -> N1; '2' -> N2; '3' -> N3; '4' -> N4; '5' -> N5; '6' -> N6; '7' -> N7; '8' -> N8; '9' -> N9
            '.' -> DOT; '+' -> ADD; '-' -> SUB; '*' -> MUL; '/' -> DIV; '(' -> LP; ')' -> RP; '=' -> EQ; ',' -> XY; '~' -> NEG
            else -> error("no key for $ch")
        })
        return this
    }
    fun s(k: Key) = p(SECOND, k)
    fun a(k: Key) = p(ALPHA, k)
    /** Current result as text; templates in serialised form. */
    val r: String get() = c.res?.let { v -> v.forms[v.k].joinToString("|") { it.ser().replace(";", "") } } ?: "(none)"
    /** The decimal form of the result (the last one CHANGE reaches in EXACT). */
    val dec: String get() = c.res?.let { v -> v.forms.last().joinToString("|") { it.ser().replace(";", "") } } ?: "(none)"
    val entry: String get() = c.ed.root.ser()
    val lines: List<String> get() = c.display().lines
    val ann: Set<String> get() = c.display().ann
    val err: Int get() = c.err?.code ?: 0
}

class KeyTest : Rig() {
    @Test fun `K-10 equals and chained ANS`() {
        t("2+3=")
        assertEquals("5.", r)
        t("*4=")
        assertEquals("Ans;*;4", entry)
        assertEquals("20.", r)
        p(ALPHA, EQ, ADD, N1, EQ)
        assertEquals("21.", r)
    }

    @Test fun `K-1 2ndF is one shot and cancels`() {
        p(SECOND)
        assertTrue("2ndF" in ann)
        p(SECOND)
        assertTrue("2ndF" !in ann)
        s(SIN).t("0.5=")
        assertEquals("30.", r)
        assertTrue("2ndF" !in ann)
    }

    @Test fun `K-2 ALPHA is one shot`() {
        p(ALPHA)
        assertTrue("ALPHA" in ann)
        p(POW)
        assertEquals("A", entry)
        assertTrue("ALPHA" !in ann)
    }

    @Test fun `K-3 hyp prefix`() {
        p(HYP, SIN).t("1=")
        assertEquals("sinh;1", entry)
        assertEquals("1.175201194", r)
        p(ONC).s(HYP).p(SIN).t("1=")
        assertEquals("sinh⁻¹;1", entry)
        assertEquals("0.881373587", r)
    }

    @Test fun `K-4 K-5 STO and RCL variables`() {
        t("12").p(STO, POW)
        assertEquals(12.0, (c.ctx.vars["A"] as Num).d)
        p(ONC).p(RCL, POW).t("*2=")
        assertEquals("24.", r)
        p(ONC).t("3").a(POW).p(EQ)
        assertEquals("36.", r)
    }

    @Test fun `K-4 K-5 definable memory stores a formula and a function`() {
        t("2").a(RCL).p(SQ).p(STO, D1)       // 2X² into D1
        p(ONC)
        c.ctx.vars["X"] = numInt(3)
        p(D1, EQ)
        assertEquals("18.", r)
        p(ONC, STO, D2, HYP, COS)            // cosh into D2
        p(ONC, D2).t("0=")
        assertEquals("1.", r)
    }

    @Test fun `K-6 M+ and M- and the M annunciator`() {
        t("5").p(MPLUS)
        assertTrue("M" in ann)
        t("2").s(MPLUS)
        assertEquals(3.0, (c.ctx.vars["M"] as Num).d)
        p(ONC).a(MPLUS).p(EQ)
        assertEquals("3.", r)
        p(ONC).t("0").p(STO, MPLUS)
        assertTrue("M" !in ann)
    }

    @Test fun `K-7 BS and DEL`() {
        t("123").p(BS)
        assertEquals("1;2", entry)
        p(LEFT, LEFT).s(BS)
        assertEquals("2", entry)
    }

    @Test fun `K-8 ON C clears entry, OFF and CA`() {
        t("12").p(STO, POW, ONC)
        assertEquals("", entry)
        assertEquals(12.0, (c.ctx.vars["A"] as Num).d)
        s(MODE)
        assertEquals(12.0, (c.ctx.vars["A"] as Num).d) // CA keeps A-F (manual's clearing table)
        assertEquals(0.0, (c.ctx.ans as Num).d)
        s(ONC)
        assertTrue(c.display().off)
        p(ONC)
        assertTrue(!c.display().off)
    }

    @Test fun `K-2 M-CLR clears memories`() {
        t("7").p(STO, POW).p(SECOND, ALPHA)
        assertEquals("0:DISP 1:MEMORY", lines[0])
        t("10")
        assertNull(c.ctx.vars["A"])
    }

    @Test fun `K-9 K-14 arrows and answer history`() {
        t("1+1=").t("2+2=").t("3+3=")
        p(UP)
        assertEquals("2;+;2", entry); assertEquals("4.", r)
        p(UP)
        assertEquals("1;+;1", entry)
        p(DOWN, DOWN)
        assertEquals("3;+;3", entry)
        p(LEFT).t("0=")
        assertEquals("33.", r)
        repeat(25) { t("1=") }
        assertEquals(20, c.history.size)
    }

    @Test fun `K-11 menus open, page, go back and close`() {
        p(MODE)
        assertEquals("0:NORMAL 1:STAT", lines[0])
        assertEquals("4:EQUATION", lines[3])
        p(DOWN)
        assertEquals("5:MATRIX", lines[0].take(8))
        p(ONC)
        assertEquals(0, c.screens.size)
        s(MATH)
        assertEquals("0:DRG 1:FSE", lines[0])
        t("0")
        assertEquals("0:DEG 1:RAD", lines[0])
        p(BS)
        assertEquals("0:DRG 1:FSE", lines[0])
        t("01")
        assertEquals(Angle.RAD, c.ctx.angle)
        assertTrue("RAD" in ann)
    }

    @Test fun `K-12 HOME returns to NORMAL`() {
        p(MODE).t("3")
        assertEquals(Mode.COMPLEX, c.mode)
        p(HOME)
        assertEquals(Mode.NORMAL, c.mode)
    }

    @Test fun `K-13 base conversion keys`() {
        t("255=").s(MUL)
        assertEquals("FF", lines[3].trim()); assertTrue("HEX" in ann)
        s(DIV)
        assertEquals("11111111", lines[3].trim())
        s(SUB)
        assertEquals("377", lines[3].trim())
        s(ADD)
        assertEquals("2010", lines[3].trim())
        s(EQ)
        assertEquals("255.", r)
        // hex digits come from the yx..(x,y) keys while in HEX
        p(ONC).s(MUL).p(POW, XY, ADD, N1, EQ)
        assertEquals("B0", lines[3].trim())
        s(EQ)
    }

    @Test fun `E-8 error shows code and cursor returns to the place`() {
        t("2+").p(SUB).t("5=")
        assertEquals(1, err)
        assertEquals("ERROR 01", lines[0]); assertEquals("SYNTAX", lines[1])
        p(LEFT)
        assertEquals(0, err)
        assertEquals(2, c.ed.idx)
        p(ONC).t("1/0=")
        assertEquals(2, err)
        p(ONC)
        assertEquals("", entry)
    }

    @Test fun `E-4 polar and rectangular conversion store X and Y`() {
        t("3,4").s(N8)
        assertEquals("r=            5.|θ=   53.13010235", r)
        assertEquals(5.0, (c.ctx.vars["X"] as Num).d)
        p(ONC).t("2,60").s(N9)
        assertEquals("x=            1.|y=   1.732050808", r)
    }

    @Test fun `E-3 sexagesimal entry and conversion`() {
        t("12").p(DMS).t("39").p(DMS).t("18.05").p(DMS, EQ)
        assertEquals("12°39′18.05″", r)
        s(DMS)
        assertEquals("12.65501389", r)
        s(DMS)
        assertEquals("12°39′18.05″", r)
    }

    @Test fun `E-2 DRG conversion`() {
        t("90=").s(DOT)
        assertEquals("1.570796327", r); assertTrue("RAD" in ann)
        s(DOT)
        assertEquals("100.", r); assertTrue("GRAD" in ann)
        s(DOT)
        assertEquals("90.", r)
    }

    @Test fun `E-5 prime factorisation key`() {
        t("360=").s(EXP)
        assertEquals("2{POW|3}*3{POW|2}*5", r)
    }

    @Test fun `E-6 int division shows quotient and remainder`() {
        t("17").s(STO).t("5=")
        assertEquals("Q=            3.|R=            2.", r)
    }

    @Test fun `E-7 CHANGE cycles fraction forms and ENG shifts`() {
        t("3/2=")
        assertEquals("{MIXED|1|1|2}", r)
        p(CHANGE); assertEquals("{FRAC|3|2}", r)
        p(CHANGE); assertEquals("1.5", r)
        p(CHANGE); assertEquals("{MIXED|1|1|2}", r)
        p(ONC).t("12345=").a(N2)
        assertEquals("12.345ᴇ03", r)
        a(N2); assertEquals("0.012345ᴇ06", r)
        a(N1); a(N1); assertEquals("12345.ᴇ00", r)
    }

    @Test fun `E-9 MDF rounds the stored value to the display`() {
        s(MATH).t("10").t("1")       // FIX, TAB 1
        t("5/9=").p(CHANGE)
        assertEquals("0.6", r)
        s(N0)
        assertEquals(0.6, (c.ctx.ans as Num).d)
        t("*9=")
        assertEquals("5.4", r)
    }

    @Test fun `E-1 percent`() {
        t("125+10").s(N1).p(EQ); assertEquals("137.5", dec)
        p(ONC).t("125-20").s(N1).p(EQ); assertEquals("100.", r)
        p(ONC).t("125*15").s(N1).p(EQ); assertEquals("18.75", dec)
        p(ONC).t("125/5").s(N1).p(EQ); assertEquals("2500.", r)
    }

    @Test fun `E-1 random functions`() {
        s(N7).t("0").p(EQ)
        assertTrue((c.ctx.ans as Num).d in 0.0..0.999)
        p(ONC).s(N7).t("1").p(EQ)
        assertTrue((c.ctx.ans as Num).d in 1.0..6.0)
        p(ONC).s(N7).t("2").p(EQ)
        assertTrue((c.ctx.ans as Num).d in 0.0..1.0)
        p(ONC).s(N7).t("3").t("1,99)=")
        assertTrue((c.ctx.ans as Num).d in 1.0..99.0)
    }

    @Test fun `D-3 ANS recall`() {
        t("6*7=").p(ONC).a(EQ).t("+1=")
        assertEquals("43.", r)
    }

    @Test fun `D-2 memory list`() {
        t("5").p(STO, SQRT, ONC).a(N9)
        assertEquals("A=            0.", lines[0])
        assertEquals("B=            5.", lines[1])
        p(DOWN, DOWN, DOWN, DOWN, DOWN)
        assertEquals("M=            0.", lines[3])
    }

    @Test fun `D-4 physical constants list`() {
        a(N4)
        assertEquals("01:c 02:G 03:g", lines[0])
        t("03").t("*2=")
        assertEquals("k03;*;2", entry)
        assertEquals("19.6133", r)
        p(ONC).a(N4).t("5")
        assertTrue(lines.any { it.contains("52:atm") })
        t("2").p(EQ)
        assertEquals("101325.", r)
        assertEquals(52, Consts.list.size)
    }

    @Test fun `D-5 metric conversions`() {
        t("125").a(N5).t("05").p(EQ)      // yd -> m
        assertEquals("114.3", r)
        p(ONC).t("100").a(N5).t("18").p(EQ) // °C -> °F
        assertEquals("212.", r)
        assertEquals(44, Consts.conv.size)
    }

    @Test fun `D-6 N-2 state survives save and load`() {
        t("9").p(STO, POW, ONC).t("1+2")
        p(STO, D1)
        s(MATH).t("01")
        t("=")
        val saved = c.save()
        val n = Calculator()
        n.load(saved)
        assertEquals(9.0, (n.ctx.vars["A"] as Num).d)
        assertEquals("1;+;2", n.ed.root.ser())
        assertEquals(Angle.RAD, n.ctx.angle)
        assertEquals("row:1;+;2", n.dmem[0])
        assertNotNull(n.res)
        assertEquals(2, n.history.size)
    }

    @Test fun `M-10 simulation asks for each variable`() {
        t("2").a(POW).t("+").a(SQRT).s(D1)
        assertEquals("A=", lines[0].take(2))
        t("3=").t("4=")
        assertEquals("10.", r)
        s(D1).t("5==")
        assertEquals("14.", r)
    }

    @Test fun `M-11 solver finds a root and stores it in X`() {
        a(RCL).p(SQ).t("-2").s(D2)
        assertEquals("Start:", lines[0].take(6))
        t("1==")
        assertEquals("X=   1.414213562", r)
        assertEquals("1.414213562", Fmt.num((c.ctx.vars["X"] as Num).d, c.set))
        // no real root: ERROR 02
        p(ONC).a(RCL).p(SQ).t("+1").s(D2).t("==")
        assertEquals(2, err)
    }

    @Test fun `M-12 mode change clears the entry and keeps memories`() {
        t("4").p(STO, POW, ONC).t("12")
        p(MODE).t("3")
        assertEquals("", entry)
        assertEquals(4.0, (c.ctx.vars["A"] as Num).d)
    }

    @Test fun `M-2 M-3 statistics data entry, edit, insert, delete and results`() {
        p(MODE).t("10")
        assertEquals(Mode.STAT, c.mode)
        t("30=40,2=50=")
        assertEquals(3, c.stat.rows.size)
        assertEquals(2.0, c.stat.rows[1][2])
        p(UP).t("55=")                     // correct the last X
        assertEquals(55.0, c.stat.rows[2][0])
        p(UP).a(CHANGE)                    // INS-D
        assertEquals(4, c.stat.rows.size)
        s(BS)                              // delete the inserted line
        assertEquals(3, c.stat.rows.size)
        t("50=")
        p(ONC)                             // close table
        a(N8).t("0")
        assertEquals("n=            4.", lines[0])
        p(ONC)
        a(N8).t("2").t("0").p(EQ)          // x̄
        assertEquals("40.", r)
        // regression
        p(MODE).t("11")
        t("2,5=12,24=21,40,3=15,25=").p(ONC)
        a(N8).t("5").t("1").p(EQ)
        assertEquals("1.839953271", r)
        p(ONC).t("3").s(RP).p(EQ)          // y′
        assertEquals("6.307242991", r)
        p(ONC).t("46").s(LP).p(EQ)         // x′
        assertEquals("24.57269841", r)
    }

    @Test fun `E-11 normal probability from the STAT MATH menu`() {
        p(MODE).t("10").p(ONC)
        p(MATH).t("6").t("1)=")
        assertEquals("0.841345", r)
    }

    @Test fun `M-4 table mode`() {
        p(MODE).t("2")
        assertEquals("Function1:", lines[0])
        a(RCL).p(SQ, EQ)                   // X²
        assertEquals("Function2:", lines[0])
        p(EQ)
        assertEquals("X_Start:", lines[0].take(8))
        t("1=").t("2=")
        assertEquals("      1        1", lines[0])
        assertEquals("      3        9", lines[1])
        p(DOWN, DOWN, DOWN, RIGHT)
        assertEquals("ANS=         49.", lines[3])
        // step 0 is an error
        p(ONC, EQ, EQ).t("=0=")
        assertEquals(2, err)
    }

    @Test fun `M-5 complex mode`() {
        p(MODE).t("3")
        t("(1+2").p(XY).t(")*(3-").p(XY).t(")=")
        assertEquals("5.+5.i", r)
        s(N8)
        assertEquals("7.071067812∠45.", r)
        s(N9)
        p(ONC).t("2").s(XY).t("90=")
        assertEquals("0.+2.i", r)
        p(ONC, MATH).t("1").t("3+4").p(XY).t(")=")
        assertEquals("3.-4.i", r)
    }

    @Test fun `M-6 equation mode`() {
        p(MODE).t("4").t("2")
        assertEquals("a=", lines[0].take(2))
        t("1=").t("2=").t("5=")
        assertEquals("X1=          -1.", lines[0])
        assertEquals("            +2.i", lines[1])
        p(ONC, ONC)
        t("0")                              // 2-VLE
        t("2=3=4=5=6=7=")
        assertEquals("X=           -1.", lines[0])
        assertEquals("Y=            2.", lines[1])
        assertEquals("D=           -3.", lines[2])
    }

    @Test fun `M-7 matrix mode`() {
        p(MODE).t("5")
        p(MATH).t("1")                      // EDIT
        t("2=2=")
        t("1=2=3=4=").p(ONC)
        p(MATH).t("3").t("0")               // STORE matA
        p(MATH).t("4").p(MATH).t("0").t("0").p(EQ)   // det matA
        assertEquals("-2.", r)
        p(ONC, MATH).t("00").p(SQ, EQ)      // matA²
        assertEquals("      7      10", lines[0])
        assertEquals("     15      22", lines[1])
        p(ONC, ONC, MATH).t("00").s(SQ).p(EQ) // inverse
        assertEquals("     -2       1", lines[0])
    }

    @Test fun `M-7 vector mode`() {
        p(MODE).t("6")
        p(MATH).t("1").t("3=").t("1=2=3=").p(ONC, MATH).t("30")
        p(MATH).t("1").t("3=").t("4=5=6=").p(ONC, MATH).t("31")
        p(MATH).t("4").p(MATH).t("00").p(XY).p(MATH).t("01").t(")=")
        assertEquals("32.", r)
        p(ONC, MATH).t("00").p(MUL, MATH).t("01").p(EQ)  // cross product
        assertEquals("     -3", lines[0]); assertEquals("      6", lines[1]); assertEquals("     -3", lines[2])
    }

    @Test fun `M-8 distribution mode (manual example)`() {
        p(MODE).t("7").t("0").t("0")
        assertEquals("Normal pdf", lines[0])
        t("65=60=6=")
        assertEquals("ANS=", lines[1]); assertEquals("0.046985312", lines[2].trim())
    }

    @Test fun `M-9 drill mode`() {
        p(MODE).t("8").t("1")
        p(EQ)                               // 1 x table, serial
        assertEquals("1×1=", lines[2])
        t("1=")
        assertEquals("1×2=", lines[2])
        t("5=")                             // wrong
        assertEquals("1×2=", lines[2])
        t("2=")
        repeat(10) { p(EQ) }                // skip the rest
        assertEquals("CORRECT", lines[0]); assertEquals("1/12", lines[1])
    }

    @Test fun `L-2 annunciators`() {
        assertTrue(ann.containsAll(listOf("DEG", "N1", "W-VIEW")))
        p(HYP); assertTrue("HYP" in ann); p(HYP)
        p(STO); assertTrue("STO" in ann); p(ONC)
        p(RCL); assertTrue("RCL" in ann); p(ONC)
    }

    @Test fun `V-2 every key has a primary action and every legend an action`() {
        for (k in Key.values()) {
            if (k != SECOND && k != ALPHA) assertTrue(k.a1.isNotEmpty(), "$k")
            if (k !in Key.ARROWS) assertEquals(k.second.isNotEmpty(), k.a2 != null, "$k 2ndF")
            if (k != NEG && k != EQ) assertEquals(k.alpha.isNotEmpty(), k.a3 != null, "$k ALPHA")
        }
        assertEquals(47, Key.ROWS.sumOf { it.size })
    }
}
