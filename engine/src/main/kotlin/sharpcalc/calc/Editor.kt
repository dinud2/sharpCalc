package sharpcalc.calc

import sharpcalc.engine.*

/** Expression editor: cursor movement, insertion with templates, template-aware delete (W-3 to W-6, K-7). */
class Editor {
    var root = Row()
    var row = root
    var idx = 0
    var wv = true
    var insert = true

    enum class Del { OK, NOTHING, CANNOT }
    class Parent(val row: Row, val i: Int, val slot: Int)

    val isEmpty get() = root.items.isEmpty()
    val pos get() = Pos(row, idx)
    val limit get() = if (wv) 159 else 161

    fun clear() { root = Row(); row = root; idx = 0 }
    fun set(r: Row, atEnd: Boolean = true) { root = r; row = r; idx = if (atEnd) r.items.size else 0 }

    fun parentOf(target: Row, r: Row = root): Parent? {
        for ((i, n) in r.items.withIndex()) if (n is Tmpl) for ((s, sr) in n.slots.withIndex()) {
            if (sr === target) return Parent(r, i, s)
            parentOf(target, sr)?.let { return it }
        }
        return null
    }

    fun goTo(p: Pos) {
        if (p.row === root || parentOf(p.row) != null) { row = p.row; idx = p.idx.coerceIn(0, row.items.size) }
    }

    fun size(r: Row = root): Int {
        var n = 0
        for (it in r.items) n += if (it is Tmpl) 1 + it.slots.sumOf { s -> size(s) } else 1
        return n
    }

    /** Returns false when the input buffer is full. */
    fun tok(id: String): Boolean {
        if (size() >= limit) return false
        if (!wv && !insert && idx < row.items.size) row.items[idx] = Tok(id) else row.items.add(idx, Tok(id))
        idx++
        return true
    }

    fun insertRow(r: Row) { for (n in r.copy().items) { row.items.add(idx, n); idx++ } }

    private fun takeNumber(): List<Node> {
        var s = idx
        while (s > 0) {
            val n = row.items[s - 1]
            if (n is Tok && n.id.length == 1 && (n.id[0].isDigit() || n.id == ".")) s-- else break
        }
        val taken = ArrayList(row.items.subList(s, idx))
        repeat(idx - s) { row.items.removeAt(s) }
        idx = s
        return taken
    }

    /** Inserts a template. A number just before the cursor becomes the numerator, whole part or root index (W-5). */
    fun tmpl(kind: K, prefill: String? = null): Boolean {
        if (size() >= limit) return false
        val t = Tmpl(kind)
        if (prefill != null) {
            t.slots[0].items.addAll(Row.chars(prefill).items)
            row.items.add(idx, t); row = t.slots[1]; idx = 0
        } else if (kind == K.FRAC || kind == K.MIXED || kind == K.ROOT) {
            val num = takeNumber()
            row.items.add(idx, t)
            if (num.isNotEmpty()) { t.slots[0].items.addAll(num); row = t.slots[1] } else row = t.slots[0]
            idx = 0
        } else {
            row.items.add(idx, t); row = t.slots[0]; idx = 0
        }
        return true
    }

    fun right(): Boolean {
        if (idx < row.items.size) {
            val n = row.items[idx]
            if (n is Tmpl && n.kind != K.OVER) { row = n.slots[0]; idx = 0 } else idx++
            return true
        }
        val p = parentOf(row) ?: return false
        val t = p.row.items[p.i] as Tmpl
        if (p.slot + 1 < t.slots.size) { row = t.slots[p.slot + 1]; idx = 0 } else { row = p.row; idx = p.i + 1 }
        return true
    }

    fun left(): Boolean {
        if (idx > 0) {
            val n = row.items[idx - 1]
            if (n is Tmpl && n.kind != K.OVER) { row = n.slots.last(); idx = row.items.size } else idx--
            return true
        }
        val p = parentOf(row) ?: return false
        val t = p.row.items[p.i] as Tmpl
        if (p.slot > 0) { row = t.slots[p.slot - 1]; idx = row.items.size } else { row = p.row; idx = p.i }
        return true
    }

    private fun vmap(kind: K, slot: Int, up: Boolean): Int? = when (kind) {
        K.FRAC -> if (up && slot == 1) 0 else if (!up && slot == 0) 1 else null
        K.MIXED -> if (up && slot == 2) 1 else if (!up && slot == 1) 2 else null
        K.SUM, K.PROD, K.INT -> if (up && slot != 1) 1 else if (!up && slot != 0) 0 else null
        else -> null
    }

    /** Moves between the slots of a template (W-4). False when there is nowhere to go. */
    fun vert(up: Boolean): Boolean {
        var r = row
        while (true) {
            val p = parentOf(r) ?: return false
            val t = p.row.items[p.i] as Tmpl
            val target = vmap(t.kind, p.slot, up)
            if (target != null) { row = t.slots[target]; idx = row.items.size; return true }
            r = p.row
        }
    }

    fun home() { row = root; idx = 0 }
    fun end() { row = root; idx = root.items.size }

    private fun isPower(n: Node?) = n is Tmpl && n.kind == K.POW || n is Tok && (n.id == "²" || n.id == "³" || n.id == "⁻¹" || n.id == "^")

    /** A parenthesis that carries an exponent cannot be deleted before the exponent is. */
    private fun guarded(k: Int): Boolean {
        val n = row.items[k] as? Tok ?: return false
        if (n.id == ")") return isPower(row.items.getOrNull(k + 1))
        if (n.id == "(") {
            var depth = 0
            for (j in k until row.items.size) {
                val t = (row.items[j] as? Tok)?.id
                if (t != null && t.endsWith("(")) depth++
                if (t == ")") { depth--; if (depth == 0) return isPower(row.items.getOrNull(j + 1)) }
            }
        }
        return false
    }

    fun bs(): Del {
        if (idx > 0) {
            val n = row.items[idx - 1]
            if (n is Tmpl) {
                if (n.isEmpty || n.kind == K.OVER) { row.items.removeAt(idx - 1); idx-- }
                else { row = n.slots.last(); idx = row.items.size }
                return Del.OK
            }
            if (guarded(idx - 1)) return Del.CANNOT
            row.items.removeAt(idx - 1); idx--
            return Del.OK
        }
        val p = parentOf(row) ?: return Del.NOTHING
        val t = p.row.items[p.i] as Tmpl
        if (t.isEmpty) { p.row.items.removeAt(p.i); row = p.row; idx = p.i; return Del.OK }
        if (p.slot > 0) { row = t.slots[p.slot - 1]; idx = row.items.size; return Del.OK }
        return Del.CANNOT
    }

    fun del(): Del {
        if (idx >= row.items.size) return if (isEmpty) Del.NOTHING else bs()
        val n = row.items[idx]
        if (n is Tmpl) {
            if (!n.isEmpty && n.kind != K.OVER) return Del.CANNOT
            row.items.removeAt(idx)
            return Del.OK
        }
        if (guarded(idx)) return Del.CANNOT
        row.items.removeAt(idx)
        return Del.OK
    }

    /** Cursor as a path, for saving state (N-2). */
    fun cursorPath(): String {
        val parts = ArrayList<String>()
        var r = row
        while (true) {
            val p = parentOf(r) ?: break
            parts.add(0, "${p.i}.${p.slot}")
            r = p.row
        }
        parts.add(idx.toString())
        return parts.joinToString("/")
    }

    fun restoreCursor(path: String) {
        try {
            val parts = path.split("/")
            var r = root
            for (k in 0 until parts.size - 1) {
                val (i, s) = parts[k].split(".").map { it.toInt() }
                r = (r.items[i] as Tmpl).slots[s]
            }
            row = r; idx = parts.last().toInt().coerceIn(0, r.items.size)
        } catch (e: Exception) { end() }
    }
}
