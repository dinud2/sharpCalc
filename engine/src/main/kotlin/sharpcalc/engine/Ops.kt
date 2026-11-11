package sharpcalc.engine

import kotlin.math.abs

/** Operators and functions over all value types (real, complex, matrix, vector). */
object Ops {
    fun cx(v: V): Cx = when (v) {
        is Num -> Cx(v.d, 0.0)
        is Cx -> v
        else -> throw CalcError(1)
    }
    fun simp(re: Double, im: Double): V { val i = fin(im); val r = fin(re); return if (i == 0.0) Num(r) else Cx(r, i) }
    fun real(v: V): Num = when (v) {
        is Num -> v
        is Cx -> if (v.im == 0.0) Num(v.re) else throw CalcError(2)
        else -> throw CalcError(1)
    }
    private fun isM(v: V) = v is Mat

    fun add(a: V, b: V): V = when {
        a is Num && b is Num -> R.add(a, b)
        a is Mat && b is Mat -> Lin.zip(a, b) { x, y -> x + y }
        isM(a) || isM(b) || a is QR || b is QR -> throw CalcError(1)
        else -> { val x = cx(a); val y = cx(b); simp(x.re + y.re, x.im + y.im) }
    }
    fun sub(a: V, b: V): V = when {
        a is Num && b is Num -> R.sub(a, b)
        a is Mat && b is Mat -> Lin.zip(a, b) { x, y -> x - y }
        isM(a) || isM(b) || a is QR || b is QR -> throw CalcError(1)
        else -> { val x = cx(a); val y = cx(b); simp(x.re - y.re, x.im - y.im) }
    }
    fun mul(a: V, b: V): V = when {
        a is Num && b is Num -> R.mul(a, b)
        a is Mat && b is Mat -> if (a.vec && b.vec) Lin.cross(a, b) else Lin.mul(a, b)
        a is Mat && b is Num -> Lin.scale(a, b.d)
        a is Num && b is Mat -> Lin.scale(b, a.d)
        isM(a) || isM(b) || a is QR || b is QR -> throw CalcError(1)
        else -> { val x = cx(a); val y = cx(b); simp(x.re * y.re - x.im * y.im, x.re * y.im + x.im * y.re) }
    }
    fun div(a: V, b: V): V = when {
        a is Num && b is Num -> R.div(a, b)
        a is Mat && b is Num -> { if (b.d == 0.0) throw CalcError(2); Lin.scale(a, 1 / b.d) }
        isM(a) || isM(b) || a is QR || b is QR -> throw CalcError(1)
        else -> {
            val x = cx(a); val y = cx(b)
            val m = y.re * y.re + y.im * y.im
            if (m == 0.0) throw CalcError(2)
            simp((x.re * y.re + x.im * y.im) / m, (x.im * y.re - x.re * y.im) / m)
        }
    }
    fun neg(a: V): V = when (a) {
        is Num -> R.neg(a)
        is Cx -> Cx(-a.re, -a.im)
        is Mat -> Lin.scale(a, -1.0)
        else -> throw CalcError(1)
    }
    fun pow(c: Ctx, a: V, b: V): V {
        if (a is Num && b is Num) {
            if (c.domain == Domain.COMPLEX && a.d < 0 && !isInt(b.d)) return cpow(Cx(a.d, 0.0), b)
            return R.pow(a, b)
        }
        if (a is Mat) {
            val n = (b as? Num)?.d ?: throw CalcError(1)
            if (a.vec) throw CalcError(1)
            return when (n) {
                -1.0 -> Lin.inverse(a)
                2.0 -> Lin.mul(a, a)
                3.0 -> Lin.mul(Lin.mul(a, a), a)
                else -> throw CalcError(1)
            }
        }
        if (a is QR || b is QR || b is Mat) throw CalcError(1)
        return cpow(cx(a), b)
    }
    private fun cpow(a: Cx, b: V): V {
        if (b is Num && isInt(b.d) && abs(b.d) <= 64) {
            var r: V = Num(1.0)
            repeat(abs(b.d).toInt()) { r = mul(r, a) }
            return if (b.d < 0) div(Num(1.0), r) else r
        }
        val e = cx(b)
        val m = Math.hypot(a.re, a.im)
        if (m == 0.0) { if (e.re <= 0) throw CalcError(2); return Num(0.0) }
        val lnr = Math.log(m)
        val th = Math.atan2(a.im, a.re)
        val mag = Math.exp(e.re * lnr - e.im * th)
        val ang = e.im * lnr + e.re * th
        return simp(mag * Math.cos(ang), mag * Math.sin(ang))
    }
    fun sqrt(c: Ctx, a: V): V = when {
        a is Num && (a.d >= 0 || c.domain != Domain.COMPLEX) -> R.sqrt(a)
        a is Num -> Cx(0.0, fin(Math.sqrt(-a.d)))
        a is Cx -> {
            val m = Math.hypot(a.re, a.im)
            val re = Math.sqrt((m + a.re) / 2)
            val im = Math.sqrt((m - a.re) / 2)
            simp(re, if (a.im < 0) -im else im)
        }
        else -> throw CalcError(1)
    }
    fun root(c: Ctx, x: V, a: V): V {
        if (x is Num && a is Num && (a.d >= 0 || c.domain != Domain.COMPLEX)) return R.root(x, a)
        val n = real(x)
        if (n.d == 0.0) throw CalcError(2)
        return cpow(cx(a), Num(1 / n.d))
    }
    fun abs(a: V): V = when (a) {
        is Num -> R.abs(a)
        is Cx -> num(Math.hypot(a.re, a.im))
        is Mat -> if (a.vec) num(Math.sqrt(a.a[0].sumOf { it * it })) else throw CalcError(1)
        else -> throw CalcError(1)
    }
    fun arg(c: Ctx, a: V): V { val z = cx(a); return num(R.convertAngle(Math.atan2(z.im, z.re), Angle.RAD, c.angle)) }
    fun polar(c: Ctx, r: V, th: V): V {
        val m = real(r); val t = real(th)
        return simp(m.d * R.cos(t, c.angle).d, m.d * R.sin(t, c.angle).d)
    }

    /** Named one-argument functions. */
    fun fn1(c: Ctx, name: String, v: V): V {
        when (name) {
            "abs" -> return abs(v)
            "√" -> return sqrt(c, v)
            "³√" -> return root(c, Num(3.0), v)
            "det" -> return num(Lin.det(v as? Mat ?: throw CalcError(1)))
            "trans" -> return Lin.trans(v as? Mat ?: throw CalcError(1))
            "identity" -> return Lin.identity(real(v).d)
        }
        val a = real(v)
        val u = c.angle
        return when (name) {
            "sin" -> R.sin(a, u); "cos" -> R.cos(a, u); "tan" -> R.tan(a, u)
            "sin⁻¹" -> R.asin(a, u); "cos⁻¹" -> R.acos(a, u); "tan⁻¹" -> R.atan(a, u)
            "sinh" -> R.sinh(a); "cosh" -> R.cosh(a); "tanh" -> R.tanh(a)
            "sinh⁻¹" -> R.asinh(a); "cosh⁻¹" -> R.acosh(a); "tanh⁻¹" -> R.atanh(a)
            "log" -> R.log(a); "ln" -> R.ln(a)
            "ipart" -> R.ipart(a); "fpart" -> R.fpart(a); "int" -> R.int(a)
            "NOT" -> Num(R.not(R.baseFix(a.d, c.base), c.base))
            "NEG" -> Num(R.baseFix(-R.baseFix(a.d, c.base), c.base))
            else -> throw CalcError(1)
        }
    }
}

/** Matrix and vector arithmetic (M-7) and the equation solvers (M-6). */
object Lin {
    fun mat(r: Int, c: Int, f: (Int, Int) -> Double) = Mat(Array(r) { i -> DoubleArray(c) { j -> fin(f(i, j)) } })
    fun zip(a: Mat, b: Mat, f: (Double, Double) -> Double): Mat {
        if (a.rows != b.rows || a.cols != b.cols || a.vec != b.vec) throw CalcError(8)
        return Mat(Array(a.rows) { i -> DoubleArray(a.cols) { j -> fin(f(a.a[i][j], b.a[i][j])) } }, a.vec)
    }
    fun scale(a: Mat, k: Double) = Mat(Array(a.rows) { i -> DoubleArray(a.cols) { j -> fin(a.a[i][j] * k) } }, a.vec)
    fun mul(a: Mat, b: Mat): Mat {
        if (a.vec || b.vec) throw CalcError(1)
        if (a.cols != b.rows) throw CalcError(8)
        return mat(a.rows, b.cols) { i, j -> var s = 0.0; for (k in 0 until a.cols) s += a.a[i][k] * b.a[k][j]; s }
    }
    fun trans(a: Mat) = mat(a.cols, a.rows) { i, j -> a.a[j][i] }
    fun identity(n: Double): Mat {
        if (!isInt(n) || n < 1 || n > 4) throw CalcError(7)
        return mat(n.toInt(), n.toInt()) { i, j -> if (i == j) 1.0 else 0.0 }
    }
    private fun dimOk(r: Double, c: Double) { if (!isInt(r) || !isInt(c) || r < 1 || r > 4 || c < 1 || c > 4) throw CalcError(7) }
    fun dim(a: Mat, r: Double, c: Double): Mat { dimOk(r, c); return mat(r.toInt(), c.toInt()) { i, j -> if (i < a.rows && j < a.cols) a.a[i][j] else 0.0 } }
    fun fill(v: Double, r: Double, c: Double): Mat { dimOk(r, c); return mat(r.toInt(), c.toInt()) { _, _ -> v } }
    fun randMat(ctx: Ctx, r: Double, c: Double): Mat { dimOk(r, c); return mat(r.toInt(), c.toInt()) { _, _ -> ctx.rng.nextInt(1000) / 1000.0 } }

    fun det(m: Mat): Double {
        if (m.vec) throw CalcError(1)
        if (m.rows != m.cols) throw CalcError(8)
        val n = m.rows
        val a = Array(n) { m.a[it].clone() }
        var d = 1.0
        for (c in 0 until n) {
            var p = c
            for (r in c + 1 until n) if (abs(a[r][c]) > abs(a[p][c])) p = r
            if (a[p][c] == 0.0) return 0.0
            if (p != c) { val t = a[p]; a[p] = a[c]; a[c] = t; d = -d }
            d *= a[c][c]
            for (r in c + 1 until n) {
                val f = a[r][c] / a[c][c]
                for (k in c until n) a[r][k] -= f * a[c][k]
            }
        }
        val r = r14(d)
        return if (abs(r) < 1e-12 * maxAbs(m)) 0.0 else r
    }
    private fun maxAbs(m: Mat): Double { var x = 0.0; for (r in m.a) for (v in r) x = Math.max(x, abs(v)); return Math.pow(x, m.rows.toDouble()) }

    fun inverse(m: Mat): Mat {
        if (m.rows != m.cols) throw CalcError(8)
        if (det(m) == 0.0) throw CalcError(2)
        val n = m.rows
        val a = Array(n) { i -> DoubleArray(2 * n) { j -> if (j < n) m.a[i][j] else if (j - n == i) 1.0 else 0.0 } }
        elim(a, n, true)
        return mat(n, n) { i, j -> a[i][j + n] }
    }

    /** Gaussian elimination; [reduced] gives rref, otherwise ref with leading ones. */
    private fun elim(a: Array<DoubleArray>, lead: Int, reduced: Boolean) {
        val rows = a.size; val cols = a[0].size
        var r = 0
        for (c in 0 until lead) {
            if (r >= rows) break
            var p = r
            for (i in r + 1 until rows) if (abs(a[i][c]) > abs(a[p][c])) p = i
            if (abs(a[p][c]) < 1e-13) { for (i in r until rows) a[i][c] = 0.0; continue }
            val t = a[p]; a[p] = a[r]; a[r] = t
            val d = a[r][c]
            for (k in 0 until cols) a[r][k] /= d
            for (i in (if (reduced) 0 else r + 1) until rows) if (i != r) {
                val f = a[i][c]
                if (f != 0.0) for (k in 0 until cols) { a[i][k] -= f * a[r][k]; if (abs(a[i][k]) < 1e-13) a[i][k] = 0.0 }
            }
            r++
        }
    }
    fun ref(m: Mat, reduced: Boolean): Mat {
        if (m.vec) throw CalcError(1)
        val a = Array(m.rows) { m.a[it].clone() }
        elim(a, m.cols, reduced)
        return mat(m.rows, m.cols) { i, j -> a[i][j] }
    }

    // ---- vectors ----
    private fun v3(a: Mat) = doubleArrayOf(a.a[0][0], a.a[0][1], if (a.cols > 2) a.a[0][2] else 0.0)
    fun vecOf(vararg x: Double) = Mat(arrayOf(DoubleArray(x.size) { fin(x[it]) }), true)
    fun cross(a: Mat, b: Mat): Mat {
        if (!a.vec || !b.vec) throw CalcError(1)
        if (a.cols != b.cols) throw CalcError(8)
        val x = v3(a); val y = v3(b)
        return vecOf(x[1] * y[2] - x[2] * y[1], x[2] * y[0] - x[0] * y[2], x[0] * y[1] - x[1] * y[0])
    }
    fun dot(a: Mat, b: Mat): Double {
        if (!a.vec || !b.vec) throw CalcError(1)
        if (a.cols != b.cols) throw CalcError(8)
        var s = 0.0
        for (i in 0 until a.cols) s += a.a[0][i] * b.a[0][i]
        return s
    }
    fun norm(a: Mat) = Math.sqrt(dot(a, a))
    fun angle(c: Ctx, a: Mat, b: Mat): Double {
        val n = norm(a) * norm(b)
        if (n == 0.0) throw CalcError(2)
        val cs = Math.max(-1.0, Math.min(1.0, r14(dot(a, b) / n)))
        return R.acos(Num(cs), c.angle).d
    }
    fun unit(a: Mat): Mat {
        if (!a.vec) throw CalcError(1)
        val n = norm(a)
        if (n == 0.0) throw CalcError(2)
        return scale(a, 1 / n)
    }

    // ---- equation solvers ----
    /** Cramer's rule; returns the unknowns followed by the determinant D. */
    fun linear(coef: Array<DoubleArray>): DoubleArray {
        val n = coef.size
        val d = det(mat(n, n) { i, j -> coef[i][j] })
        if (d == 0.0) throw CalcError(2)
        val out = DoubleArray(n + 1)
        for (k in 0 until n) out[k] = fin(det(mat(n, n) { i, j -> if (j == k) coef[i][n] else coef[i][j] }) / d)
        out[n] = d
        return out
    }
    private fun clean(z: Cx): Cx {
        val m = Math.max(abs(z.re), abs(z.im))
        val re = if (abs(z.re) < 1e-11 * m) 0.0 else fin(z.re)
        val im = if (abs(z.im) < 1e-11 * Math.max(m, 1e-90)) 0.0 else fin(z.im)
        return Cx(re, im)
    }
    fun quadratic(a: Double, b: Double, c: Double): List<Cx> {
        if (a == 0.0) throw CalcError(2)
        val disc = r14(b * b - 4 * a * c)
        if (disc >= 0) {
            val s = Math.sqrt(disc)
            val q = -0.5 * (b + if (b < 0) -s else s)
            val x1 = if (q != 0.0) q / a else 0.0
            val x2 = if (q != 0.0) c / q else -b / a
            val hi = Math.max(x1, x2); val lo = Math.min(x1, x2)
            return listOf(clean(Cx(hi, 0.0)), clean(Cx(lo, 0.0)))
        }
        val re = -b / (2 * a); val im = Math.sqrt(-disc) / (2 * abs(a))
        return listOf(clean(Cx(re, im)), clean(Cx(re, -im)))
    }
    fun cubic(a: Double, b0: Double, c0: Double, d0: Double): List<Cx> {
        if (a == 0.0) throw CalcError(2)
        val b = b0 / a; val c = c0 / a; val d = d0 / a
        val f = { x: Double -> ((x + b) * x + c) * x + d }
        val df = { x: Double -> (3 * x + 2 * b) * x + c }
        // one real root by Newton from a bound, then deflate
        var x = -b / 3
        val p = c - b * b / 3
        val q = 2 * b * b * b / 27 - b * c / 3 + d
        val disc = q * q / 4 + p * p * p / 27
        x = if (disc >= 0) {
            val s = Math.sqrt(disc)
            Math.cbrt(-q / 2 + s) + Math.cbrt(-q / 2 - s) - b / 3
        } else {
            val r = Math.sqrt(-p * p * p / 27)
            val phi = Math.acos(Math.max(-1.0, Math.min(1.0, -q / (2 * r))))
            2 * Math.sqrt(-p / 3) * Math.cos(phi / 3) - b / 3
        }
        repeat(8) { val dv = df(x); if (dv != 0.0) x -= f(x) / dv }
        if (abs(x) < 1e-13) x = 0.0
        x = r14(x)
        val qb = b + x
        val qc = c + x * qb
        val rest = quadratic(1.0, r14(qb), r14(qc))
        val all = listOf(clean(Cx(x, 0.0))) + rest
        val reals = all.filter { it.im == 0.0 }.sortedByDescending { it.re }
        return reals + all.filter { it.im != 0.0 }
    }
}
