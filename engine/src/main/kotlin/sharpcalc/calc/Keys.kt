package sharpcalc.calc

/**
 * The key table (L-3, L-4): 47 keys plus the four arrows, in the order of the calculator's face.
 * [label] is printed on the key, [second] above-left in orange, [alpha] above-right in teal.
 * [a1]/[a2]/[a3] are the actions for a plain press, after 2ndF and after ALPHA.
 */
enum class Key(
    val label: String, val second: String, val alpha: String,
    val a1: String, val a2: String?, val a3: String?,
    val say: String, val boxed: Boolean = false, val below: String = "",
) {
    HOME("HOME", "", "", "home", null, null, "home"),
    UP("▲", "", "", "up", "top", null, "up"),
    DOWN("▼", "", "", "down", "bottom", null, "down"),
    LEFT("◄", "", "", "left", "start", null, "left"),
    RIGHT("►", "", "", "right", "end", null, "right"),
    ONC("ON/C", "OFF", "", "onc", "off", null, "on, clear"),
    SECOND("2ndF", "", "", "", null, null, "second function"),
    MODE("MODE", "CA", "", "mode", "ca", null, "mode"),
    ALPHA("ALPHA", "M-CLR", "", "", "mclr", null, "alpha"),
    MATH("MATH", "SET UP", "", "math", "setup", null, "math menu"),
    D1("D1", "ALGB", "", "d1", "algb", null, "definable 1"),
    D2("D2", "SOLVER", "", "d2", "solver", null, "definable 2"),
    D3("D3", "", "", "d3", null, null, "definable 3"),
    BS("BS", "DEL", "", "bs", "del", null, "backspace"),
    HYP("hyp", "arc hyp", "NOT", "hyp", "archyp", "NOT", "hyperbolic"),
    SIN("sin", "sin⁻¹", "AND", "sin", "sin⁻¹", "AND", "sine"),
    COS("cos", "cos⁻¹", "OR", "cos", "cos⁻¹", "OR", "cosine"),
    TAN("tan", "tan⁻¹", "XOR", "tan", "tan⁻¹", "XOR", "tangent"),
    PI("π", "logₐx", "XNOR", "π", "logn", "XNOR", "pi"),
    DMS("D°M′S", "⇔DEG", "", "°", "todeg", null, "degrees minutes seconds"),
    POW("yˣ", "ˣ√", "A", "pow", "root", "A", "power"),
    SQRT("√", "³√", "B", "sqrt", "cbrt", "B", "square root"),
    SQ("x²", "x⁻¹", "C", "sq", "inv", "C", "square"),
    LOG("log", "10ˣ", "D", "log", "10x", "D", "log"),
    LN("ln", "eˣ", "E", "ln", "ex", "E", "natural log"),
    XY("(x,y)", "∠", "F", ",", "ang", "F", "comma", below = "i"),
    EXP("Exp", "P.FACT", "e", "E", "pfact", "e", "exponent"),
    FRAC("a/b", "ab/c", "", "frac", "mixed", null, "fraction"),
    RCL("RCL", "x³", "X", "rcl", "cube", "X", "recall"),
    STO("STO", "int÷", "Y", "sto", "intdiv", "Y", "store"),
    MPLUS("M+", "M−", "M", "m+", "m-", "M", "memory plus"),
    CHANGE("CHANGE", "DATA", "INS-D", "change", "data", "insd", "change"),
    N7("7", "RANDOM", "", "7", "random", null, "7"),
    N8("8", "→rθ", "STAT", "8", "torθ", "stat", "8"),
    N9("9", "→xy", "MEMORY", "9", "toxy", "memory", "9"),
    LP("(", "x′", "∫dx", "(", "x′", "integ", "open parenthesis"),
    RP(")", "y′", "d/dx", ")", "y′", "diff", "close parenthesis"),
    N4("4", "n!", "CONST", "4", "!", "const", "4"),
    N5("5", "nCr", "CONV", "5", "nCr", "conv", "5"),
    N6("6", "nPr", "", "6", "nPr", null, "6"),
    MUL("×", "→HEX", "Σ", "*", "tohex", "sum", "times", boxed = true),
    DIV("÷", "→BIN", "Π", "/", "tobin", "prod", "divide", boxed = true),
    N1("1", "%", "◄ENG", "1", "%", "eng<", "1"),
    N2("2", "GCD", "ENG►", "2", "GCD", "eng>", "2"),
    N3("3", "LCM", "", "3", "LCM", null, "3"),
    ADD("+", "→PEN", "", "+", "topen", null, "plus", boxed = true),
    SUB("−", "→OCT", "", "-", "tooct", null, "minus", boxed = true),
    N0("0", "MDF", "", "0", "mdf", null, "0"),
    DOT("•", "DRG►", "", ".", "drg", null, "decimal point"),
    NEG("(−)", "abs", "NEG", "neg", "abs", "NEG", "negative"),
    EQ("=", "→DEC", "ANS", "=", "todec", "Ans", "equals", boxed = true, below = "ENTER");

    companion object {
        /** Rows of the keypad, top to bottom (L-3). The arrow cross sits between HOME and ON/C. */
        val ROWS: List<List<Key>> = listOf(
            listOf(HOME, ONC),
            listOf(SECOND, MODE),
            listOf(ALPHA, MATH, D1, D2, D3, BS),
            listOf(HYP, SIN, COS, TAN, PI, DMS),
            listOf(POW, SQRT, SQ, LOG, LN, XY),
            listOf(EXP, FRAC, RCL, STO, MPLUS, CHANGE),
            listOf(N7, N8, N9, LP, RP),
            listOf(N4, N5, N6, MUL, DIV),
            listOf(N1, N2, N3, ADD, SUB),
            listOf(N0, DOT, NEG, EQ),
        )
        val ARROWS = listOf(UP, DOWN, LEFT, RIGHT)
        /** Memory a key names after STO, RCL or ALPHA. */
        val VAR = mapOf(POW to "A", SQRT to "B", SQ to "C", LOG to "D", LN to "E", XY to "F", RCL to "X", STO to "Y", MPLUS to "M")
    }
}
