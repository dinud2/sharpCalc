package sharpcalc.engine

class Const(val sym: String, val name: String, val v: Double)
class Conv(val from: String, val to: String, val f: (Double) -> Double)

/**
 * Physical constants 01-52 and metric conversions 01-44 (D-4, D-5).
 * Names and order are the manual's. The manual says the values follow CODATA 2014 and the
 * NIST SI guide (2008); it does not print them, so the figures below are the CODATA 2014 values.
 */
object Consts {
    val list = listOf(
        Const("c", "Speed of light in vacuum", 299792458.0),
        Const("G", "Newtonian constant of gravitation", 6.67408e-11),
        Const("g", "Standard acceleration of gravity", 9.80665),
        Const("me", "Electron mass", 9.10938356e-31),
        Const("mp", "Proton mass", 1.672621898e-27),
        Const("mn", "Neutron mass", 1.674927471e-27),
        Const("mμ", "Muon mass", 1.883531594e-28),
        Const("1u", "Atomic mass unit-kilogram relationship", 1.660539040e-27),
        Const("e", "Elementary charge", 1.6021766208e-19),
        Const("h", "Planck constant", 6.626070040e-34),
        Const("k", "Boltzmann constant", 1.38064852e-23),
        Const("μ0", "Magnetic constant", 1.2566370614e-6),
        Const("ε0", "Electric constant", 8.854187817e-12),
        Const("re", "Classical electron radius", 2.8179403227e-15),
        Const("α", "Fine-structure constant", 7.2973525664e-3),
        Const("a0", "Bohr radius", 5.2917721067e-11),
        Const("R∞", "Rydberg constant", 10973731.568508),
        Const("Φ0", "Magnetic flux quantum", 2.067833831e-15),
        Const("μB", "Bohr magneton", 9.274009994e-24),
        Const("μe", "Electron magnetic moment", -9.284764620e-24),
        Const("μN", "Nuclear magneton", 5.050783699e-27),
        Const("μp", "Proton magnetic moment", 1.4106067873e-26),
        Const("μn", "Neutron magnetic moment", -9.662365e-27),
        Const("μμ", "Muon magnetic moment", -4.49044826e-26),
        Const("λc", "Compton wavelength", 2.4263102367e-12),
        Const("λcp", "Proton Compton wavelength", 1.32140985396e-15),
        Const("σ", "Stefan-Boltzmann constant", 5.670367e-8),
        Const("NA", "Avogadro constant", 6.022140857e23),
        Const("Vm", "Molar volume of ideal gas (273.15 K, 101.325 kPa)", 22.413962e-3),
        Const("R", "Molar gas constant", 8.3144598),
        Const("F", "Faraday constant", 96485.33289),
        Const("RK", "Von Klitzing constant", 25812.8074555),
        Const("-e/me", "Electron charge to mass quotient", -1.758820024e11),
        Const("h/2me", "Quantum of circulation", 3.6369475486e-4),
        Const("γp", "Proton gyromagnetic ratio", 2.675221900e8),
        Const("KJ", "Josephson constant", 4.835978525e14),
        Const("eV", "Electron volt", 1.6021766208e-19),
        Const("t", "Celsius Temperature", 273.15),
        Const("AU", "Astronomical unit", 1.495978707e11),
        Const("pc", "Parsec", 3.0856775815e16),
        Const("M(12C)", "Molar mass of carbon-12", 12e-3),
        Const("ħ", "Planck constant over 2 pi", 1.054571800e-34),
        Const("Eh", "Hartree energy", 4.359744650e-18),
        Const("G0", "Conductance quantum", 7.7480917310e-5),
        Const("α⁻¹", "Inverse fine-structure constant", 137.035999139),
        Const("mp/me", "Proton-electron mass ratio", 1836.15267389),
        Const("Mu", "Molar mass constant", 1e-3),
        Const("λcn", "Neutron Compton wavelength", 1.31959090481e-15),
        Const("c1", "First radiation constant", 3.741771790e-16),
        Const("c2", "Second radiation constant", 1.43877736e-2),
        Const("Z0", "Characteristic impedance of vacuum", 376.730313462),
        Const("atm", "Standard atmosphere", 101325.0),
    )

    private fun pair(a: String, b: String, k: Double) = listOf(Conv(a, b) { it * k }, Conv(b, a) { it / k })

    val conv: List<Conv> =
        pair("in", "cm", 2.54) + pair("ft", "m", 0.3048) + pair("yd", "m", 0.9144) + pair("mi", "km", 1.609344) +
        pair("n mi", "m", 1852.0) + pair("acre", "m²", 4046.8726098743) + pair("oz", "g", 28.349523125) +
        pair("lb", "kg", 0.45359237) +
        listOf(Conv("°F", "°C") { (it - 32) / 1.8 }, Conv("°C", "°F") { it * 1.8 + 32 }) +
        pair("gal(US)", "L", 3.785411784) + pair("gal(UK)", "L", 4.54609) +
        pair("fl oz(US)", "mL", 29.5735295625) + pair("fl oz(UK)", "mL", 28.4130625) +
        pair("calth", "J", 4.184) + pair("cal15", "J", 4.1858) + pair("calIT", "J", 4.1868) +
        pair("hp", "W", 745.69987158227) + pair("ps", "W", 735.49875) +
        pair("kgf/cm²", "Pa", 98066.5) + pair("atm", "Pa", 101325.0) +
        pair("mmHg", "Pa", 133.322368421) + pair("kgf·m", "N·m", 9.80665)

    fun convert(k: Int, x: Double): Double = conv[k - 1].f(x)
}
