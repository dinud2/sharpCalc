package sharpcalc

import org.junit.Test
import sharpcalc.calc.*
import sharpcalc.calc.Key.*
import sharpcalc.engine.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** WriteView editor checks (V-4): entry, navigation, delete and evaluation for each template. */
class WriteViewTest : Rig() {
    private fun snap(name: String) {
        val d = c.display()
        val s = 6
        val img = BufferedImage(Lcd.W * s, Lcd.H * s, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until Lcd.H * s) for (x in 0 until Lcd.W * s) {
            val on = d.px[(y / s) * Lcd.W + x / s] && x % s != s - 1 && y % s != s - 1
            val cur = d.cursor && x / s == d.curX && y / s in d.curTop..d.curBot
            img.setRGB(x, y, if (on || cur) 0x1b2a4a else 0xb8c4a8)
        }
        val dir = File("build/snapshots").also { it.mkdirs() }
        ImageIO.write(img, "png", File(dir, "$name.png"))
    }

    @Test fun `W-1 W-5 fraction takes the number before it as numerator`() {
        t("1").p(FRAC).t("2").p(RIGHT).t("+").p(FRAC).t("1").p(DOWN).t("3")
        assertEquals("{FRAC|1|2};+;{FRAC|1|3}", entry)
        snap("w1_fraction_entry")
        p(EQ)
        assertEquals("{FRAC|5|6}", r)
        snap("w7_fraction_result")
    }

    @Test fun `W-1 mixed fraction`() {
        t("3").s(FRAC).t("1").p(DOWN).t("2").p(RIGHT).t("+").p(FRAC).t("5").p(DOWN).t("7").p(EQ)
        assertEquals("{MIXED|3|1|2};+;{FRAC|5|7}", entry)
        assertEquals("{MIXED|4|3|14}", r)           // manual example: 3 1/2 + 5/7
        snap("w1_mixed")
        p(CHANGE); assertEquals("{FRAC|59|14}", r)
        p(CHANGE); assertEquals("4.214285714", r)
    }

    @Test fun `W-1 roots`() {
        p(SQRT).t("8").p(EQ)
        assertEquals("{SQRT|8}", entry); assertEquals("2{SQRT|2}", r)
        snap("w1_sqrt")
        p(ONC).s(SQRT).t("27=")
        assertEquals("{ROOT|3|2;7}", entry); assertEquals("3.", r)
        p(ONC).t("4").s(POW).t("16=")              // W-5: 4 becomes the index
        assertEquals("{ROOT|4|1;6}", entry); assertEquals("2.", r)
        snap("w1_nth_root")
    }

    @Test fun `W-1 powers`() {
        t("2").p(POW).t("10=")
        assertEquals("2;{POW|1;0}", entry); assertEquals("1024.", r)
        p(ONC).t("5").p(SQ, EQ); assertEquals("5;²", entry); assertEquals("25.", r)
        p(ONC).t("2").s(RCL).p(EQ); assertEquals("8.", r)          // x³
        p(ONC).t("4").s(SQ).p(EQ); assertEquals("{FRAC|1|4}", r)   // x⁻¹
        p(ONC).s(LOG).t("3=") ; assertEquals("1;0;{POW|3}", entry); assertEquals("1000.", r)
        p(ONC).s(LN).t("1=") ; assertEquals("e;{POW|1}", entry); assertEquals("2.718281828", r)
        snap("w1_power")
    }

    @Test fun `W-1 log base and absolute value`() {
        s(PI).t("2").p(RIGHT).t("8=")
        assertEquals("{LOGN|2|8}", entry); assertEquals("3.", r)
        snap("w1_logn")
        p(ONC).s(NEG).p(NEG).t("5=")
        assertEquals("{ABS|neg;5}", entry); assertEquals("5.", r)
        snap("w1_abs")
    }

    @Test fun `W-1 W-4 sum and product with limits`() {
        a(MUL).t("1").p(UP).t("10").p(DOWN, UP, RIGHT)
        assertEquals("{SUM|1|1;0|}", entry)
        a(RCL).p(SQ)
        snap("w1_sum_entry")
        p(EQ)
        assertEquals("385.", r)
        p(ONC).a(DIV).t("1").p(RIGHT).t("5").p(RIGHT).a(RCL).p(EQ)
        assertEquals("{PROD|1|5|X}", entry); assertEquals("120.", r)
        // optional increment after a comma
        p(ONC).a(MUL).t("1").p(RIGHT).t("10").p(RIGHT).a(RCL).t(",2=")
        assertEquals("25.", r)
    }

    @Test fun `W-1 integral and derivative`() {
        a(LP).t("0").p(RIGHT).t("3").p(RIGHT).a(RCL).p(SQ)
        assertEquals("{INT|0|3|X;²}", entry)
        snap("w1_integral_entry")
        p(EQ)
        assertEquals("9.", r)
        p(ONC).a(RP).a(RCL).p(SQ, RIGHT).t("3=")
        assertEquals("{DIFF|X;²|3}", entry); assertEquals("6.", r)
        snap("w1_diff")
    }

    @Test fun `W-2 templates nest`() {
        t("2").p(POW).p(SQRT).p(FRAC).t("1").p(DOWN).t("4")
        assertEquals("2;{POW|{SQRT|{FRAC|1|4}}}", entry)
        snap("w2_nested")
        p(EQ)
        assertEquals("{SQRT|2}", r)
    }

    @Test fun `W-3 empty slots and reading-order cursor`() {
        p(FRAC)
        assertEquals("{FRAC||}", entry)
        snap("w3_empty_slots")
        p(RIGHT); assertTrue(c.ed.row === (c.ed.root.items[0] as Tmpl).slots[1])
        p(RIGHT); assertTrue(c.ed.row === c.ed.root); assertEquals(1, c.ed.idx)
        p(LEFT); assertTrue(c.ed.row === (c.ed.root.items[0] as Tmpl).slots[1])
        p(LEFT, LEFT); assertTrue(c.ed.row === c.ed.root); assertEquals(0, c.ed.idx)
        // an empty slot is a syntax error and the cursor goes to it
        p(EQ); assertEquals(1, err)
        p(RIGHT); assertTrue(c.ed.row === (c.ed.root.items[0] as Tmpl).slots[0])
    }

    @Test fun `W-6 delete on templates and Cannot delete`() {
        p(FRAC, RIGHT, RIGHT, BS)                  // empty template: removed
        assertEquals("", entry)
        t("1").p(FRAC).t("2").p(RIGHT, BS)          // filled template: cursor steps in
        assertEquals("{FRAC|1|2}", entry)
        assertTrue(c.ed.row === (c.ed.root.items[0] as Tmpl).slots[1])
        p(BS); assertEquals("{FRAC|1|}", entry)
        p(BS); assertTrue(c.ed.row === (c.ed.root.items[0] as Tmpl).slots[0])
        p(BS); assertEquals("{FRAC||}", entry)
        p(BS); assertEquals("", entry)
        // manual example: ( 5 ) x² ◄ BS
        p(ONC).t("(5)").p(SQ, LEFT, BS)
        assertEquals("Cannot delete!", lines[0])
        p(ONC)
        assertEquals("(;5;);²", entry)
        p(RIGHT, BS, BS)
        assertEquals("(;5", entry)
    }

    @Test fun `W-8 long expressions scroll sideways with markers`() {
        t("1234567890+1234567890+1234567890")
        assertTrue("←" in ann); assertTrue("→" !in ann)
        snap("w8_scrolled")
        s(LEFT)
        assertTrue("→" in ann); assertTrue("←" !in ann)
        val d = c.display()
        assertTrue(d.cursor && d.curX in 0 until Lcd.W)
    }

    @Test fun `W-9 templates evaluate like their Line-editor forms`() {
        val ctx = Ctx()
        fun same(wv: String, line: String) {
            val a = Parser.eval(Row.parse(wv), ctx) as Num
            val b = Parser.eval(toks(line), ctx) as Num
            assertEquals(Fmt.num(b.d, FSE.NORM1, 0), Fmt.num(a.d, FSE.NORM1, 0), wv)
        }
        same("{FRAC|3|4}", "3⌟4")
        same("{MIXED|1|2|3}", "1⌟2⌟3")
        same("{SQRT|2}", "√2")
        same("{ROOT|5|3;2}", "5x√32")
        same("3;{POW|4}", "3^4")
        same("{LOGN|3|8;1}", "logn(3,81)")
        same("{ABS|neg;7}", "abs neg7")
        same("{SUM|1|5|X;²}", "Σ(X²,1,5)")
        same("{PROD|1|5|X}", "Π(X,1,5)")
        same("{INT|0|1|X;³}", "∫(X³,0,1)")
        same("{DIFF|X;³|2}", "d/dx(X³,2)")
    }

    @Test fun `W-10 Line editor forms`() {
        s(MATH).t("21")                              // EDITOR: LINE
        assertTrue("W-VIEW" !in ann)
        t("1").p(FRAC).t("2+1").p(FRAC).t("3=")
        assertEquals("1;⌟;2;+;1;⌟;3", entry)
        assertEquals("5⌟6", r)
        snap("w10_line_editor")
        p(ONC).a(MUL).a(RCL).t(",1,10)=")
        assertEquals("Σ(;X;,;1;,;1;0;)", entry); assertEquals("55.", r)
        p(ONC).a(LP).a(RCL).p(SQ).t(",0,3)="); assertEquals("9.", r)
        p(ONC).a(RP).a(RCL).p(SQ).t(",3)="); assertEquals("6.", r)
        p(ONC).t("2").p(POW).t("8="); assertEquals("256.", r)
        p(ONC).p(SQRT).t("16="); assertEquals("4.", r)
    }

    @Test fun `W-11 recalled entries can be edited`() {
        t("1").p(FRAC).t("2").p(EQ, ONC, UP)
        assertEquals("{FRAC|1|2}", entry)
        p(LEFT, BS, BS).t("4=")
        assertEquals("{FRAC|1|4}", r)
    }

    @Test fun `W-7 irrational results`() {
        p(SIN).t("60=")
        assertEquals("{FRAC|{SQRT|3}|2}", r)
        snap("w7_surd_result")
        p(CHANGE); assertEquals("0.866025404", r)
        s(MATH).t("01")
        p(ONC).s(SIN).t("0.5=")
        assertEquals("{FRAC|1|6}π", r)
        snap("w7_pi_result")
        s(MATH).t("201")                             // APPROX.
        p(ONC).p(SQRT).t("8=")
        assertEquals("2.828427125", r)
    }

    @Test fun `V-4 snapshots of menus and modes`() {
        p(MODE); snap("menu_mode")
        p(ONC).s(MATH); snap("menu_setup")
        p(ONC).a(N4); snap("menu_const")
        p(ONC).t("1/0="); snap("error")
        p(ONC, MODE).t("11").t("2,5=12,24="); snap("stat_table")
        p(MODE).t("2").a(RCL).p(SQ, EQ, EQ, EQ, EQ); snap("table_view")
        p(MODE).t("5").p(MATH).t("1").t("2=2=1=2=3="); snap("matrix_grid")
        p(MODE).t("3").t("1+2").p(XY, EQ); snap("complex")
        p(MODE).t("8").t("0"); snap("drill_setup")
        assertTrue(File("build/snapshots/menu_mode.png").exists())
    }
}
