package sharpcalc

import org.junit.Test
import sharpcalc.engine.Consts
import kotlin.math.abs
import kotlin.test.*

class ConstantsReferenceTest {
    @Test fun `D-4 scientific constants agree with the NIST 2014 archive`() {
        val rows = javaClass.getResource("/codata2014.tsv")!!.readText().lineSequence()
            .filter { it.isNotBlank() && !it.startsWith('#') }.toList()
        assertEquals(49, rows.size)
        for (row in rows) {
            val (symbol, value) = row.split('\t')
            val expected = value.toDouble()
            val actual = Consts.list.single { it.sym == symbol }.v
            // Published exact values sometimes end in an ellipsis. Compare to 10 significant digits.
            assertTrue(abs(actual - expected) <= abs(expected) * 1e-10, "$symbol: $actual versus $expected")
        }
    }
    @Test fun `D-5 conversions agree with NIST reference values in both directions`() {
        // NIST SP 811 Appendix B.8 (rounded factors as published):
        // https://www.nist.gov/pml/special-publication-811/nist-guide-si-appendix-b-conversion-factors/nist-guide-si-appendix-b8
        val factors = mapOf(
            "in" to 2.54, "acre" to 4046.873, "gal(US)" to 3.785412,
            "gal(UK)" to 4.54609, "fl oz(US)" to 29.57353, "fl oz(UK)" to 28.41306,
            "calth" to 4.184, "cal15" to 4.18580, "calIT" to 4.1868,
            "hp" to 745.6999, "ps" to 735.4988, "atm" to 101325.0,
            "mmHg" to 133.3224,
        )
        for ((unit, expected) in factors) {
            val forward = Consts.conv.single { it.from == unit }
            val reverse = Consts.conv.single { it.from == forward.to && it.to == unit }
            assertEquals(expected, forward.f(1.0), expected * 5e-7, unit)
            assertEquals(1.0, reverse.f(expected), 5e-7, "inverse $unit")
        }
        val toC = Consts.conv.single { it.from == "°F" }
        val toF = Consts.conv.single { it.from == "°C" }
        assertEquals(0.0, toC.f(32.0), 1e-12)
        assertEquals(100.0, toC.f(212.0), 1e-12)
        assertEquals(32.0, toF.f(0.0), 1e-12)
        assertEquals(212.0, toF.f(100.0), 1e-12)
    }
}
