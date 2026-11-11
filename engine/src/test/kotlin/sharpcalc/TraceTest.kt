package sharpcalc

import org.junit.Test
import sharpcalc.calc.*
import sharpcalc.calc.Key.*
import sharpcalc.engine.*
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/** Requirements that are about structure, layout data and limits rather than arithmetic. */
class TraceTest : Rig() {
    private fun sources(dir: String) = File(dir).walkTopDown().filter { it.extension == "kt" }.toList()

    @Test fun `P-4 P-5 engine and key logic have no Android or Compose imports`() {
        val files = sources("src/main")
        assertTrue(files.size >= 10)
        for (f in files) for (line in f.readLines())
            assertFalse(line.startsWith("import android") || line.startsWith("import androidx"), "${f.name}: $line")
    }

    @Test fun `P-1 P-2 P-3 N-6 app is Compose, API 24 to 34, portrait, with no permissions`() {
        val manifest = File("../app/src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("uses-permission"))
        assertTrue(manifest.contains("android:screenOrientation=\"portrait\""))
        val gradle = File("../app/build.gradle.kts").readText()
        assertTrue(gradle.contains("minSdk = 24") && gradle.contains("targetSdk = 34") && gradle.contains("compose = true"))
    }

    @Test fun `N-5 no third-party runtime libraries`() {
        val deps = File("../app/build.gradle.kts").readLines().filter { it.trim().startsWith("implementation(") }
        for (d in deps) assertTrue(d.contains("androidx.") || d.contains("project(\":engine\")"), d)
        assertFalse(File("build.gradle.kts").readLines().any { it.trim().startsWith("implementation(") })
    }

    @Test fun `P-6 P-7 project has wrapper files and both modules`() {
        for (f in listOf("../gradlew", "../gradlew.bat", "../gradle/wrapper/gradle-wrapper.jar", "../gradle/wrapper/gradle-wrapper.properties", "../settings.gradle.kts"))
            assertTrue(File(f).exists(), f)
        assertTrue(File("../settings.gradle.kts").readText().contains("\":engine\", \":app\""))
    }

    @Test fun `L-1 LCD is 96 by 32 dots, four lines of sixteen 5x7 characters`() {
        val d = c.display()
        assertEquals(96 * 32, d.px.size)
        assertEquals(4, d.lines.size)
        val l = Lcd()
        l.line(3, "0123456789ABCDEF")
        assertEquals("0123456789ABCDEF", l.textLines()[3])
        assertTrue((0 until Lcd.W).any { l.get(it, 31) })      // last line reaches the bottom row
        assertTrue((0 until Lcd.H).none { l.get(95, it) })     // sixth column of each cell is spacing
    }

    @Test fun `L-3 key rows in the order of the calculator face`() {
        assertEquals(listOf(2, 2, 6, 6, 6, 6, 5, 5, 5, 4), Key.ROWS.map { it.size })
        assertEquals(listOf(HOME, ONC), Key.ROWS[0]); assertEquals(listOf(SECOND, MODE), Key.ROWS[1])
        assertEquals(ALPHA, Key.ROWS[2].first()); assertEquals(BS, Key.ROWS[2].last())
        assertEquals(HYP, Key.ROWS[3].first()); assertEquals(DMS, Key.ROWS[3].last())
        assertEquals(POW, Key.ROWS[4].first()); assertEquals(XY, Key.ROWS[4].last())
        assertEquals(EXP, Key.ROWS[5].first()); assertEquals(CHANGE, Key.ROWS[5].last())
        assertEquals(listOf(N0, DOT, NEG, EQ), Key.ROWS[9])
        assertEquals(47, Key.ROWS.flatten().toSet().size)
        assertEquals(4, Key.ARROWS.size)
    }

    @Test fun `L-4 legends of section 3`() {
        val t = mapOf(
            ONC to ("OFF" to ""), MODE to ("CA" to ""), ALPHA to ("M-CLR" to ""), MATH to ("SET UP" to ""), D1 to ("ALGB" to ""), D2 to ("SOLVER" to ""),
            BS to ("DEL" to ""), HYP to ("arc hyp" to "NOT"), SIN to ("sin⁻¹" to "AND"), COS to ("cos⁻¹" to "OR"), TAN to ("tan⁻¹" to "XOR"),
            PI to ("logₐx" to "XNOR"), POW to ("ˣ√" to "A"), SQRT to ("³√" to "B"), SQ to ("x⁻¹" to "C"), LOG to ("10ˣ" to "D"), LN to ("eˣ" to "E"),
            XY to ("∠" to "F"), EXP to ("P.FACT" to "e"), FRAC to ("ab/c" to ""), RCL to ("x³" to "X"), STO to ("int÷" to "Y"), MPLUS to ("M−" to "M"),
            CHANGE to ("DATA" to "INS-D"), N7 to ("RANDOM" to ""), N8 to ("→rθ" to "STAT"), N9 to ("→xy" to "MEMORY"), LP to ("x′" to "∫dx"), RP to ("y′" to "d/dx"),
            N4 to ("n!" to "CONST"), N5 to ("nCr" to "CONV"), N6 to ("nPr" to ""), MUL to ("→HEX" to "Σ"), DIV to ("→BIN" to "Π"), N1 to ("%" to "◄ENG"),
            N2 to ("GCD" to "ENG►"), N3 to ("LCM" to ""), ADD to ("→PEN" to ""), SUB to ("→OCT" to ""), N0 to ("MDF" to ""), DOT to ("DRG►" to ""),
            NEG to ("abs" to "NEG"), EQ to ("→DEC" to "ANS"),
        )
        for ((k, v) in t) { assertEquals(v.first, k.second, "$k 2ndF"); assertEquals(v.second, k.alpha, "$k ALPHA") }
        assertTrue(listOf(MUL, DIV, ADD, SUB, EQ).all { it.boxed })       // boxed orange legends: base conversion keys
    }

    @Test fun `L-5 2ndF and ALPHA show their annunciators while active`() {
        p(SECOND); assertTrue("2ndF" in ann); p(N1)
        p(ALPHA); assertTrue("ALPHA" in ann)
    }

    @Test fun `V-2 every legend does something`() {
        // each shifted function either changes the entry, the result, a setting, or opens a screen
        for (k in Key.values()) for (sh in listOf(Shift.SECOND, Shift.ALPHA)) {
            val a = (if (sh == Shift.SECOND) k.a2 else k.a3) ?: continue
            if (k in Key.ARROWS) continue
            val r = Rig()
            r.c.ctx.vars["X"] = numInt(2)
            if (a in listOf("x′", "y′", "stat", "data", "insd")) r.p(MODE).t("11").t("1,2=3,4=").p(ONC)
            if (a == "insd") r.s(CHANGE)
            if (a == "ang") r.p(MODE).t("3")
            if (a in listOf("algb", "solver")) r.a(RCL)
            if (a in listOf("torθ", "toxy")) r.t("3,4")
            if (a == "mdf") r.t("2/3=")
            if (a in listOf("todeg", "drg", "pfact", "eng<", "eng>", "m-", "tohex", "tobin", "topen", "tooct")) r.t("12=")
            if (a == "todec") r.t("12=").s(MUL)
            if (a == "del") r.t("12").p(LEFT)
            if (a == "off") { r.c.press(SECOND); r.c.press(k); assertTrue(r.c.display().off); continue }
            if (a == "ca") r.t("5=")
            val before = snapshot(r)
            r.c.shift = sh
            r.c.press(k)
            assertTrue(snapshot(r) != before, "$k with $sh ($a) did nothing")
        }
    }

    private fun snapshot(r: Rig) = listOf(r.entry, r.r, r.c.screens.size, r.c.ctx.angle, r.c.ctx.base, r.c.hyp, r.c.ctx.vars["M"].let { (it as? Num)?.d },
        r.c.stat.rows.size, (r.c.ctx.ans as? Num)?.d, r.c.cplxPolar, r.c.display().lines).toString()

    @Test fun `D-1 memories A to F, X, Y, M and D1 to D3`() {
        assertEquals(setOf("A", "B", "C", "D", "E", "F", "X", "Y", "M"), Key.VAR.values.toSet())
        assertEquals(3, c.dmem.size)
        for ((k, name) in Key.VAR) { p(ONC).t("7").p(STO, k); assertEquals(7.0, (c.ctx.vars[name] as Num).d, name) }
    }

    @Test fun `N-1 keys respond quickly and long calculations can be cancelled`() {
        t("1+1=")                                    // warm up
        val t0 = System.nanoTime()
        p(ONC).t("123.456*789").p(SIN).t("30=")
        val perKey = (System.nanoTime() - t0) / 1e6 / 14
        assertTrue(perKey < 50, "per key $perKey ms")
        // ∫ with a million subintervals, cancelled from another thread as ON/C would
        p(ONC).a(LP).t("0").p(RIGHT).t("1").p(RIGHT).p(SIN).a(RCL).t(",999999")
        val th = Thread { c.press(EQ) }
        th.start()
        Thread.sleep(100)
        c.cancel()
        th.join(5000)
        assertFalse(th.isAlive)
        assertEquals(0, err)
        assertEquals("(none)", r)
    }

    @Test fun `N-2 state round trip in every mode`() {
        for (m in listOf("0", "10", "3", "5", "6")) {
            val a = Rig()
            a.p(MODE).t(m).p(ONC).t("12+3")
            val b = Calculator()
            b.load(a.c.save())
            assertEquals(a.c.mode, b.mode)
            assertEquals("1;2;+;3", b.ed.root.ser())
            assertEquals(a.c.save(), b.save())
        }
        // a damaged state falls back to a clean calculator instead of crashing
        val z = Calculator(); z.load("v=1\nmode=NOPE"); assertEquals(Mode.NORMAL, z.mode)
    }

    @Test fun `N-3 every key has a spoken description`() {
        for (k in Key.values()) assertTrue(k.say.isNotBlank(), "$k")
    }

    @Test fun `N-8 a key's function is one table entry plus one engine entry`() {
        // every prefix function the parser knows is reachable as a plain token
        for (f in Parser.PREFIX) assertTrue(c.isInsertable(f), f)
    }

    @Test fun `E-7 recurring decimals and decimal comma`() {
        s(MATH).t("51")                              // RECUR: ON
        t("1/3=")
        assertEquals("{FRAC|1|3}", r)
        p(CHANGE); assertEquals("0.{OVER|3}", r)
        p(CHANGE); assertEquals("0.333333333", r)
        s(MATH).t("61")                              // DECIMAL: COMMA
        assertEquals("0,333333333", dec)
    }

    @Test fun `E-1 engineering prefixes and (%) from the MATH menu`() {
        t("5").p(MATH).t("5").t("0").p(EQ)           // 5 k
        assertEquals("5000.", r)
        p(ONC).t("25").p(MATH).t("4").p(EQ)          // 25 (%)
        assertEquals("0.25", dec)
        p(ONC, MATH).t("1").t("3.7=")                // ipart
        assertEquals("3.", r)
    }

    @Test fun `E-1 constant calculation reuses the operator and constant`() {
        t("34+57=").t("45=")
        assertEquals("102.", r)                      // 45 + 57
        p(ONC).t("68*25=").t("40=")
        assertEquals("2720.", r)                     // 68 x 40
    }

    @Test fun `V-3 worked examples printed in the operation manual`() {
        t("24").s(N2).t("36="); assertEquals("12.", r)            // GCD
        p(ONC).t("15").s(N3).t("9="); assertEquals("45.", r)      // LCM
        p(ONC).t("2+").p(SUB).t("5="); assertEquals(1, err)        // ERROR 01 example
        p(ONC).t("10000/8.1=")                                     // operation guide, display formats
        assertEquals("1234.567901", dec)
        s(MATH).t("10").t("3"); assertEquals("1234.568", dec)
        s(MATH).t("11").t("0"); assertEquals("1.234567901ᴇ03", dec)
        s(MATH).t("12").t("0"); assertEquals("1.ᴇ03", dec)
        s(MATH).t("13"); assertEquals("1234.567901", dec)
        p(ONC).t("0.32/1.6").p(EXP).t("~19=")                      // operation guide, exponent display
        assertEquals("2.ᴇ18", dec)
    }
}

class MemoryKeyTest : Rig() {
    @Test fun `K-4 STO accepts the memory key with or without ALPHA`() {
        t("8").p(STO).a(SQRT)
        assertEquals(8.0, (c.ctx.vars["B"] as Num).d)
        p(ONC).p(RCL).a(SQRT).p(EQ)
        assertEquals("8.", r)
    }
}
