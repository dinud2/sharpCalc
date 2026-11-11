package sharpcalc.engine

/** Template kinds of the WriteView editor, with their slot counts (W-1). */
enum class K(val n: Int) {
    FRAC(2),   // numerator, denominator
    MIXED(3),  // whole, numerator, denominator
    SQRT(1),
    ROOT(2),   // index, radicand
    POW(1),    // exponent (applies to the operand on its left)
    LOGN(2),   // base, argument
    ABS(1),
    SUM(3),    // start, end, body[, increment]
    PROD(3),
    INT(3),    // a, b, body[, subintervals]
    DIFF(2),   // body, x[, dx]
    OVER(1),   // recurring-decimal overline (results only)
}

sealed class Node
class Tok(val id: String) : Node()
class Tmpl(val kind: K, val slots: List<Row>) : Node() {
    constructor(kind: K) : this(kind, List(kind.n) { Row() })
    val isEmpty get() = slots.all { it.items.isEmpty() }
}

class Row(val items: MutableList<Node> = mutableListOf()) {
    fun copy(): Row = Row(items.map {
        when (it) {
            is Tok -> it
            is Tmpl -> Tmpl(it.kind, it.slots.map { s -> s.copy() })
        }
    }.toMutableList())

    val isEmpty get() = items.isEmpty()

    fun ser(): String = items.joinToString(";") {
        when (it) {
            is Tok -> it.id
            is Tmpl -> "{" + it.kind.name + "|" + it.slots.joinToString("|") { s -> s.ser() } + "}"
        }
    }

    companion object {
        fun of(vararg ids: String) = Row(ids.map { Tok(it) as Node }.toMutableList())
        fun chars(s: String) = Row(s.map { Tok(it.toString()) as Node }.toMutableList())

        fun parse(s: String): Row {
            var i = 0
            fun row(): Row {
                val r = Row()
                while (i < s.length && s[i] != '|' && s[i] != '}') {
                    if (s[i] == ';') { i++; continue }
                    if (s[i] == '{') {
                        i++
                        val j = s.indexOf('|', i)
                        val kind = K.valueOf(s.substring(i, j))
                        i = j
                        val slots = ArrayList<Row>()
                        while (s[i] == '|') { i++; slots.add(row()) }
                        i++ // '}'
                        r.items.add(Tmpl(kind, slots))
                    } else {
                        var j = i
                        while (j < s.length && s[j] != ';' && s[j] != '|' && s[j] != '}' && s[j] != '{') j++
                        r.items.add(Tok(s.substring(i, j)))
                        i = j
                    }
                }
                return r
            }
            return row()
        }
    }
}

/** A place in an expression: before item [idx] of [row]. */
class Pos(val row: Row, val idx: Int)

/** ERROR 01 syntax, 02 calculation, 03 nesting, 04 data over, 07 definition, 08 DIM unmatched, 10 undefined. */
class CalcError(val code: Int, var pos: Pos? = null) : Exception("ERROR " + code.toString().padStart(2, '0'))

class Cancelled : Exception()
