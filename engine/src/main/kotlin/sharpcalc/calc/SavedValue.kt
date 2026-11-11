package sharpcalc.calc

import sharpcalc.engine.*

/** State values are data, never expressions: restoring must not evaluate ANS or random functions. */
internal object SavedValue {
    fun encode(v: V?): String = when (v) {
        is Num -> "n:${v.d}|${v.dms}|" + (v.ex?.let { ex ->
            "${ex.pi}|" + ex.t.entries.joinToString(";") { (r, q) -> "$r,${q.n},${q.d}" }
        } ?: "")
        is Cx -> "c:${v.re},${v.im}"
        is QR -> "q:${v.q},${v.r}"
        is Mat -> "m:${v.vec}|" + v.a.joinToString(";") { it.joinToString(",") }
        null -> "n:0.0"
    }

    fun decode(s: String): V {
        val body = s.substring(2)
        return when (s.take(2)) {
            "c:" -> body.split(',').let { Cx(it[0].toDouble(), it[1].toDouble()) }
            "q:" -> body.split(',').let { QR(it[0].toDouble(), it[1].toDouble()) }
            "m:" -> body.split('|', limit = 2).let { parts ->
                Mat(parts[1].split(';').map { row -> row.split(',').map { it.toDouble() }.toDoubleArray() }.toTypedArray(), parts[0].toBoolean())
            }
            "n:" -> {
                val parts = body.split('|')
                val d = parts[0].toDouble()
                val ex = if (parts.size >= 4 && parts[2].isNotEmpty()) {
                    val terms = if (parts[3].isEmpty()) emptyMap() else parts[3].split(';').associate { term ->
                        val t = term.split(',')
                        t[0].toLong() to Q.of(t[1].toBigInteger(), t[2].toBigInteger())
                    }
                    Exact.make(terms, parts[2].toInt())
                } else if (parts.size == 1 && isInt(d) && kotlin.math.abs(d) < 1e12) Exact.int(d.toLong()) else null
                Num(d, ex, parts.getOrNull(1)?.toBoolean() ?: false)
            }
            else -> error("Unknown saved value")
        }
    }

    fun putResult(m: MutableMap<String, String>, key: String, result: ResultView?) {
        if (result == null) return
        m[key] = encode(result.value)
        m["$key.k"] = result.k.toString()
        m["$key.prime"] = result.prime.toString()
        result.eng?.let { m["$key.eng"] = it.toString() }
        m["$key.forms"] = result.forms.size.toString()
        result.forms.forEachIndexed { i, rows ->
            m["$key.$i.rows"] = rows.size.toString()
            rows.forEachIndexed { j, row -> m["$key.$i.$j"] = row.ser() }
        }
    }

    fun getResult(m: Map<String, String>, key: String): ResultView? {
        val value = m[key] ?: return null
        val forms = List(m.getValue("$key.forms").toInt()) { i ->
            List(m.getValue("$key.$i.rows").toInt()) { j -> Row.parse(m.getValue("$key.$i.$j")) }
        }
        return ResultView(decode(value), forms).also {
            it.k = m.getValue("$key.k").toInt().coerceIn(forms.indices)
            it.prime = m["$key.prime"].toBoolean()
            it.eng = m["$key.eng"]?.toInt()
        }
    }
}
