package sharpcalc.calc

import sharpcalc.engine.*

/** The 96x32 dot matrix (L-1). Text lines are 8 dots high; glyphs occupy rows 1..7 of each line. */
class Lcd {
    companion object { const val W = 96; const val H = 32 }
    val px = BooleanArray(W * H)
    /** Mirror of characters drawn on the 16x4 text grid, for tests and accessibility. */
    val chars = Array(4) { CharArray(16) { ' ' } }

    fun set(x: Int, y: Int, on: Boolean = true) { if (x in 0 until W && y in 0 until H) px[y * W + x] = on }
    fun get(x: Int, y: Int) = x in 0 until W && y in 0 until H && px[y * W + x]
    fun hline(x0: Int, x1: Int, y: Int) { for (x in x0..x1) set(x, y) }
    fun vline(x: Int, y0: Int, y1: Int) { for (y in y0..y1) set(x, y) }
    fun clearRect(x0: Int, y0: Int, x1: Int, y1: Int) {
        for (y in y0..y1) for (x in x0..x1) set(x, y, false)
        for (r in 0 until 4) for (c in 0 until 16) if (c * 6 + 5 >= x0 && c * 6 <= x1 && r * 8 + 7 >= y0 && r * 8 + 1 <= y1) chars[r][c] = ' '
    }
    fun invert(x0: Int, y0: Int, x1: Int, y1: Int) { for (y in y0..y1) for (x in x0..x1) if (x in 0 until W && y in 0 until H) px[y * W + x] = !px[y * W + x] }

    fun glyph(c: Char, x: Int, y: Int) {
        val g = Font.glyph(c)
        for (r in 0 until 7) for (k in 0 until 5) if ((g[r] shr (4 - k)) and 1 == 1) set(x + k, y + r)
        if (x >= 0 && x % 6 == 0 && y >= 1 && (y - 1) % 8 == 0 && x / 6 < 16 && (y - 1) / 8 < 4) chars[(y - 1) / 8][x / 6] = c
    }
    fun small(c: Char, x: Int, y: Int) {
        val g = Font.smallGlyph(c) ?: return
        for (r in 0 until 5) for (k in 0 until 3) if ((g[r] shr (2 - k)) and 1 == 1) set(x + k, y + r)
    }
    fun text(s: String, x: Int, y: Int) { var cx = x; for (c in s) { glyph(c, cx, y); cx += 6 } }
    fun line(n: Int, s: String, col: Int = 0) = text(s, col * 6, n * 8 + 1)
    fun right(n: Int, s: String) = text(s, W - s.length * 6, n * 8 + 1)
    fun textLines(): List<String> = chars.map { String(it).trimEnd() }
    fun dump(): String = (0 until H).joinToString("\n") { y -> (0 until W).joinToString("") { x -> if (px[y * W + x]) "#" else "." } }
}

/** A laid-out piece of an expression. Rows [top]..[bot] are relative to the top row of ordinary text. */
class Box(val w: Int, val top: Int, val bot: Int, val draw: (Lcd, Int, Int) -> Unit)

class CurPos { var x = 0; var top = 0; var bot = 6; var found = false }

/** WriteView layout (W-1 to W-3): turns an expression tree into dots. */
class Layout(private val cursor: Pos?, val cur: CurPos = CurPos()) {
    private fun mark(x: Int, top: Int, bot: Int) { cur.x = x; cur.top = top; cur.bot = bot; cur.found = true }
    private fun shift(b: Box, dy: Int) = Box(b.w, b.top + dy, b.bot + dy) { l, x, y -> b.draw(l, x, y + dy) }
    private fun text(s: String) = Box(6 * s.length, 0, 6) { l, x, y -> l.text(s, x, y) }
    private fun smallText(s: String) = Box(4 * s.length, 0, 4) { l, x, y -> var cx = x; for (c in s) { l.small(c, cx, y); cx += 4 } }
    private fun hcat(vararg b: Box): Box {
        var w = 0
        val xs = IntArray(b.size)
        for ((k, e) in b.withIndex()) { xs[k] = w; w += e.w }
        return Box(w, b.minOf { it.top }, b.maxOf { it.bot }) { l, x, y -> for ((k, e) in b.withIndex()) e.draw(l, x + xs[k], y) }
    }
    private fun canSmall(r: Row) = r.items.all { it is Tok && Font.hasSmall(Disp.SUPER[it.id] ?: Disp.text(it.id)) }
    private fun isSuper(n: Node) = n is Tok && Disp.SUPER.containsKey(n.id) || n is Tmpl && n.kind == K.POW

    fun row(r: Row, small: Boolean = false, root: Boolean = false): Box {
        val sm = small && canSmall(r)
        val isCur = cursor != null && cursor.row === r
        val n = r.items.size
        if (n == 0) {
            val b = if (root) Box(0, 0, 6) { _, _, _ -> }
            else if (sm) Box(4, 0, 4) { l, x, y -> l.small('□', x, y) }
            else Box(6, 0, 6) { l, x, y -> l.glyph('□', x, y) }
            return if (isCur) Box(b.w, b.top, b.bot) { l, x, y -> b.draw(l, x, y); mark(x, y + b.top, y + b.bot) } else b
        }
        val boxes = ArrayList<Box>()
        val xs = IntArray(n + 1)
        var prevTop = 0
        var w = 0
        for ((k, it) in r.items.withIndex()) {
            val b = if (isSuper(it)) superBox(it, sm, prevTop) else when (it) {
                is Tok -> if (sm) smallText(Disp.text(it.id)) else text(Disp.text(it.id))
                is Tmpl -> tmpl(it)
            }
            xs[k] = w; w += b.w; boxes.add(b)
            if (!isSuper(it)) prevTop = b.top
        }
        xs[n] = w
        val t0 = boxes.minOf { it.top }
        val bot = boxes.maxOf { it.bot }
        val top = if (root && t0 > -1) -1 else t0
        val ci = if (isCur) cursor!!.idx.coerceIn(0, n) else 0
        return Box(w, top, bot) { l, x, y ->
            for ((k, b) in boxes.withIndex()) b.draw(l, x + xs[k], y)
            if (isCur) mark(x + xs[ci], y + t0, y + bot)
        }
    }

    private fun superBox(n: Node, sm: Boolean, prevTop: Int): Box {
        val content: Box
        val isSmall: Boolean
        if (n is Tok) { content = smallText(Disp.SUPER[n.id]!!); isSmall = true }
        else { val slot = (n as Tmpl).slots[0]; isSmall = canSmall(slot); content = row(slot, true) }
        val bottom = if (!isSmall) prevTop + 2 else if (sm) prevTop + 1 else prevTop + 3
        return shift(content, bottom - content.bot)
    }

    private fun paren(b: Box) = hcat(text("("), b, text(")"))

    private fun frac(a: Row, b: Row): Box {
        val n = row(a); val d = row(b)
        val w = Math.max(n.w, d.w) + 2
        val ndy = 2 - n.bot; val ddy = 5 - d.top
        return Box(w, n.top + ndy, d.bot + ddy) { l, x, y ->
            n.draw(l, x + (w - n.w + 1) / 2, y + ndy)
            l.hline(x, x + w - 2, y + 3)
            d.draw(l, x + (w - d.w + 1) / 2, y + ddy)
        }
    }

    private fun sqrt(c: Box) = Box(c.w + 6, c.top - 2, c.bot) { l, x, y ->
        l.hline(x + 4, x + c.w + 4, y + c.top - 2)
        l.vline(x + 4, y + c.top - 2, y + c.bot)
        l.set(x + 3, y + c.bot); l.set(x + 2, y + c.bot - 1); l.set(x + 1, y + c.bot - 2)
        c.draw(l, x + 6, y)
    }

    private fun tmpl(t: Tmpl): Box {
        val s = t.slots
        return when (t.kind) {
            K.FRAC -> frac(s[0], s[1])
            K.MIXED -> hcat(row(s[0]), frac(s[1], s[2]))
            K.SQRT -> sqrt(row(s[0]))
            K.ROOT -> {
                val idx = row(s[0], true)
                val sq = sqrt(row(s[1]))
                val dy = (sq.bot - 4) - idx.bot
                val sx = Math.max(0, idx.w - 2)
                Box(sx + sq.w, Math.min(sq.top, idx.top + dy), sq.bot) { l, x, y -> idx.draw(l, x, y + dy); sq.draw(l, x + sx, y) }
            }
            K.LOGN -> {
                val base = row(s[0], true)
                hcat(text("log"), shift(base, (if (canSmall(s[0])) 3 else 4) - base.top), paren(row(s[1])))
            }
            K.ABS -> {
                val c = row(s[0])
                Box(c.w + 5, c.top, c.bot) { l, x, y ->
                    l.vline(x + 1, y + c.top, y + c.bot); c.draw(l, x + 3, y); l.vline(x + c.w + 3, y + c.top, y + c.bot)
                }
            }
            K.SUM, K.PROD -> {
                val hi = row(s[1], true)
                val lo = hcat(if (canSmall(s[0])) smallText("x=") else text("X="), row(s[0], true))
                val hdy = -2 - hi.bot; val ldy = 8 - lo.top
                val cw = Math.max(6, Math.max(hi.w, lo.w))
                val sym = if (t.kind == K.SUM) 'Σ' else 'Π'
                val col = Box(cw + 1, hi.top + hdy, lo.bot + ldy) { l, x, y ->
                    l.glyph(sym, x + (cw - 5) / 2, y)
                    hi.draw(l, x + (cw - hi.w + 1) / 2, y + hdy)
                    lo.draw(l, x + (cw - lo.w + 1) / 2, y + ldy)
                }
                hcat(col, paren(row(s[2])))
            }
            K.INT -> {
                val body = row(s[2]); val hi = row(s[1], true); val lo = row(s[0], true)
                val top = Math.min(body.top, 0) - 4
                val bot = Math.max(body.bot, 6) + 4
                val hdy = top - hi.top; val ldy = bot - lo.bot
                val lw = Math.max(hi.w + 1, lo.w) + 1
                val sign = Box(5 + lw, top, bot) { l, x, y ->
                    l.vline(x + 2, y + top + 1, y + bot - 1)
                    l.set(x + 3, y + top); l.set(x + 4, y + top + 1); l.set(x + 1, y + bot); l.set(x, y + bot - 1)
                    hi.draw(l, x + 6, y + hdy); lo.draw(l, x + 5, y + ldy)
                }
                hcat(sign, body, text("dx"))
            }
            K.DIFF -> hcat(text("d/dx("), row(s[0]), text(")"), text("|x="), row(s[1]))
            K.OVER -> {
                val c = row(s[0])
                Box(c.w, c.top - 2, c.bot) { l, x, y -> c.draw(l, x, y); l.hline(x, x + c.w - 2, y + c.top - 2) }
            }
            K.POW -> text("^")
        }
    }
}
