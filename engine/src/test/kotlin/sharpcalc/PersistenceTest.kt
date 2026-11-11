package sharpcalc

import org.junit.Test
import sharpcalc.calc.*
import sharpcalc.calc.Key.*
import sharpcalc.engine.*
import kotlin.test.*

class PersistenceTest : Rig() {
    @Test fun `N-2 reopening does not evaluate ANS again`() {
        t("2+3=").p(MUL).t("2=")
        assertEquals(10.0, (c.res!!.value as Num).d)
        val restored = Calculator().also { it.load(c.save()) }
        assertEquals(10.0, (restored.res!!.value as Num).d)
        assertEquals(c.display().lines, restored.display().lines)
    }

    @Test fun `D-6 exact memories survive restart`() {
        t("1/3=").p(STO, POW)
        val restored = Calculator().also { it.load(c.save()) }
        assertEquals(Q.of(1, 3), (restored.ctx.vars["A"] as Num).ex?.rational())
    }

    @Test fun `N-2 selected result format survives restart`() {
        t("1/3=").p(CHANGE)
        val restored = Calculator().also { it.load(c.save()) }
        assertEquals(c.res!!.k, restored.res!!.k)
        assertEquals(c.display().lines, restored.display().lines)
    }
    @Test fun `N-2 history retains its answers after restart`() {
        t("2+3=").p(MUL).t("2=")
        val restored = Calculator().also { it.load(c.save()) }
        assertEquals(listOf(5.0, 10.0), restored.history.map { (it.second!!.value as Num).d })
    }

    @Test fun `N-2 loading old state uses saved ANS without reevaluation`() {
        val restored = Calculator()
        restored.load("v=1\nmode=NORMAL\nentry=Ans;*;2\nans=n:10.0\nshown=true")
        assertEquals(10.0, (restored.res!!.value as Num).d)
    }

    @Test fun `D-6 roots pi and complex memories retain their values`() {
        c.ctx.vars["A"] = Num(kotlin.math.sqrt(2.0), Exact.surd(Q.ONE, 2))
        c.ctx.vars["B"] = Num(Math.PI, Exact.PI)
        c.ctx.vars["C"] = Cx(3.0, -4.0)
        val restored = Calculator().also { it.load(c.save()) }
        assertEquals(Q.ONE, (restored.ctx.vars["A"] as Num).ex!!.t[2L])
        assertEquals(1, (restored.ctx.vars["B"] as Num).ex!!.pi)
        assertEquals(-4.0, (restored.ctx.vars["C"] as Cx).im)
    }
}
