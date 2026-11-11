package app.quadline.calc

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import kotlinx.coroutines.delay
import sharpcalc.calc.Display
import sharpcalc.calc.Key
import sharpcalc.calc.Lcd

// The calculator always looks like the device, in light and dark system themes alike (N-7).
private val Backdrop = Color(0xFF050506)
private val CaseDark = Color(0xFF17181B)
private val CaseEdge = Brush.verticalGradient(listOf(Color(0xFFE4E6EA), Color(0xFF9A9DA3), Color(0xFFD2D4D8)))
private val LcdBack = Color(0xFFB8C4A8)
private val LcdInk = Color(0xFF1B2A4A)
private val Orange = Color(0xFFF0962A)
private val Teal = Color(0xFF3CC4BE)
private val KeyFn = Color(0xFF2C2E33)
private val KeyNum = Color(0xFF45484E)
private val KeyOp = Color(0xFF35383D)
private val KeyRed = Color(0xFFB7403B)
private val KeyLight = Color(0xFFC9CCD1)
private val Legend = Color(0xFFF2F3F5)

/** Text size tied to the layout, not to the system font scale, so legends never clip (N-4). */
@Composable
private fun Dp.fixed(): TextUnit = with(LocalDensity.current) { this@fixed.toSp() }

/** Renders small raised and lowered letters (x², logₐx) with spans, so no font needs those glyphs. */
private fun legend(s: String): AnnotatedString = buildAnnotatedString {
    val up = mapOf('ˣ' to "x", '⁻' to "−", '¹' to "1", '²' to "2", '³' to "3")
    for (ch in s) {
        val u = up[ch]
        when {
            u != null -> withStyle(SpanStyle(baselineShift = BaselineShift(0.45f), fontSize = 0.7.em)) { append(u) }
            ch == 'ₐ' -> withStyle(SpanStyle(baselineShift = BaselineShift(-0.25f), fontSize = 0.7.em)) { append("a") }
            else -> append(ch)
        }
    }
}

private fun spoken(s: String) = s
    .replace("⁻¹", " inverse").replace("ˣ√", "x-th root").replace("³√", "cube root").replace("√", "square root")
    .replace("yˣ", "y to the x").replace("10ˣ", "10 to the x").replace("eˣ", "e to the x").replace("x²", "x squared").replace("x³", "x cubed")
    .replace("→", "to ").replace("⇔", "to and from ").replace("π", "pi").replace("θ", "theta").replace("Σ", "sum").replace("Π", "product")
    .replace("∫dx", "integral").replace("∠", "angle").replace("◄", "decrease ").replace("►", " increase").replace("÷", " divide")
    .replace("logₐx", "log base a").replace("′", " prime").replace("−", "minus")

@Composable
fun CalculatorScreen() {
    var about by remember { mutableStateOf(false) }
    val d = Host.display
    Box(Modifier.fillMaxSize().background(Backdrop).systemBarsPadding(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
            // the real body is 80 mm x 166 mm; keep that shape at any phone width (L-6)
            val aspect = 80f / 166f
            val w = if (maxWidth / maxHeight < aspect) maxWidth else maxHeight * aspect
            val h = w / aspect
            val u = w / 100
            Box(Modifier.size(w, h).clip(RoundedCornerShape(u * 6)).background(CaseEdge).padding(u * 1.2f)) {
                Column(Modifier.fillMaxSize().clip(RoundedCornerShape(u * 5)).background(CaseDark).padding(horizontal = u * 3.5f, vertical = u * 2.5f)) {
                    Row(Modifier.fillMaxWidth().height(u * 6), verticalAlignment = Alignment.CenterVertically) {
                        Text("QUADLINE", color = Color(0xFFB9BCC2), fontSize = (u * 3.2f).fixed(), fontWeight = FontWeight.Bold, letterSpacing = 0.25.em)
                        Spacer(Modifier.width(u * 2))
                        Text("4-LINE SCIENTIFIC", color = Color(0xFF7D8087), fontSize = (u * 2.2f).fixed(), modifier = Modifier.weight(1f))
                        val aboutLabel = stringResource(R.string.about_button)
                        Box(
                            Modifier.size(u * 6).clip(CircleShape).clickable { about = true }.semantics { contentDescription = aboutLabel; role = Role.Button },
                            contentAlignment = Alignment.Center,
                        ) { Text("i", color = Color(0xFF7D8087), fontSize = (u * 3f).fixed(), fontFamily = FontFamily.Serif) }
                    }
                    LcdPanel(d, Host.busy, u)
                    Spacer(Modifier.height(u * 2))
                    Keypad(d, u, Modifier.weight(1f))
                }
            }
        }
    }
    if (about) AlertDialog(
        onDismissRequest = { about = false },
        confirmButton = { TextButton(onClick = { about = false }) { Text(stringResource(R.string.about_close)) } },
        title = { Text(stringResource(R.string.about_title)) },
        text = { Text(stringResource(R.string.about_text)) },
    )
}

@Composable
private fun LcdPanel(d: Display, busy: Boolean, u: Dp) {
    var blink by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { delay(500); blink = !blink } }
    // after OFF the stored name stays for a moment, then the glass goes blank
    var offName by remember { mutableStateOf(false) }
    LaunchedEffect(d.off) { if (d.off) { offName = true; delay(1500); offName = false } }
    val show = !d.off || offName
    val ann = if (busy) d.ann + "BUSY" else d.ann
    val order = listOf("←", "2ndF", "HYP", "ALPHA", "STO", "RCL", "FIX", "SCI", "ENG", "N1", "N2", "DEG", "RAD", "GRAD",
        "BIN", "PEN", "OCT", "HEX", "xy", "rθ", "STAT", "TABLE", "COMPLEX", "EQUATION", "MATRIX", "VECTOR", "DISTRIBUTION", "DRILL",
        "BUSY", "W-VIEW", "M", "↑", "↓", "→")
    val shown = order.filter { it in ann }
    val reading = if (d.off) "Calculator is off" else (d.lines.filter { it.isNotBlank() }.joinToString(". ").ifEmpty { "Display empty" } + ". " + shown.joinToString(" "))
    val ink = LcdInk.copy(alpha = 0.55f + 0.05f * d.contrast)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(u * 2)).background(Color(0xFF0B0C0E)).padding(u * 1.5f)
            .clip(RoundedCornerShape(u)).background(LcdBack).padding(horizontal = u * 2, vertical = u * 1.2f)
            .clearAndSetSemantics { contentDescription = reading; liveRegion = LiveRegionMode.Polite },
    ) {
        Row(Modifier.fillMaxWidth().height(u * 4.2f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (!d.off) for (a in shown) Text(if (a == "DISTRIBUTION") "DIST" else if (a == "EQUATION") "EQN" else if (a == "COMPLEX") "CPLX" else a,
                color = ink, fontSize = (u * 2.6f).fixed(), fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
        Canvas(Modifier.fillMaxWidth().aspectRatio(Lcd.W.toFloat() / Lcd.H)) {
            val s = size.width / Lcd.W
            val dot = Size(s * 0.88f, s * 0.88f)
            if (show) {
                for (y in 0 until Lcd.H) for (x in 0 until Lcd.W) if (d.px[y * Lcd.W + x]) drawRect(ink, Offset(x * s, y * s), dot)
                if (d.cursor && blink) for (y in d.curTop.coerceAtLeast(0)..d.curBot.coerceAtMost(Lcd.H - 1)) drawRect(ink, Offset(d.curX * s, y * s), dot)
            }
        }
    }
}

@Composable
private fun Keypad(d: Display, u: Dp, modifier: Modifier) {
    val shift2 = "2ndF" in d.ann
    val alpha = "ALPHA" in d.ann
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().weight(2.3f)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                KeyCell(Key.HOME, u, shift2, alpha, Modifier.weight(1f))
                KeyCell(Key.SECOND, u, shift2, alpha, Modifier.weight(1f))
            }
            ArrowPad(u, Modifier.weight(1.5f).fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight()) {
                KeyCell(Key.ONC, u, shift2, alpha, Modifier.weight(1f))
                KeyCell(Key.MODE, u, shift2, alpha, Modifier.weight(1f))
            }
        }
        for ((i, row) in Key.ROWS.drop(2).withIndex()) {
            val big = i >= 4
            Row(Modifier.fillMaxWidth().weight(if (big) 1.3f else 1f)) {
                for (k in row) KeyCell(k, u, shift2, alpha, Modifier.weight(if (k == Key.EQ) 2f else 1f).fillMaxHeight(), big)
            }
        }
    }
}

private fun keyColor(k: Key): Color = when (k) {
    Key.SECOND -> Orange
    Key.ALPHA -> Teal
    Key.ONC -> KeyRed
    Key.HOME, Key.MODE -> KeyLight
    Key.N0, Key.N1, Key.N2, Key.N3, Key.N4, Key.N5, Key.N6, Key.N7, Key.N8, Key.N9, Key.DOT, Key.NEG -> KeyNum
    Key.ADD, Key.SUB, Key.MUL, Key.DIV, Key.EQ, Key.LP, Key.RP -> KeyOp
    else -> KeyFn
}

@Composable
private fun KeyCell(k: Key, u: Dp, shift2: Boolean, alpha: Boolean, modifier: Modifier, big: Boolean = false) {
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val lit = k == Key.SECOND && shift2 || k == Key.ALPHA && alpha
    // content description: the primary function, or the shifted one while 2ndF / ALPHA is active (N-3)
    val say = when {
        shift2 && k.second.isNotEmpty() -> spoken(k.second)
        alpha && k.alpha.isNotEmpty() -> spoken(k.alpha)
        else -> k.say + (if (k.second.isNotEmpty()) ", second function " + spoken(k.second) else "") + (if (k.alpha.isNotEmpty()) ", alpha " + spoken(k.alpha) else "")
    }
    Column(
        modifier.padding(horizontal = u * 0.9f).clickable(interactionSource = source, indication = null) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            Host.press(k)
        }.clearAndSetSemantics { contentDescription = say; role = Role.Button },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // legends above the key: 2ndF in orange on the left, ALPHA in teal on the right (L-4)
        val chars = k.second.length + k.alpha.length
        val base = if (big) u * 3.1f else u * 2.9f
        val room = if (big) u * 15f else u * 12f
        val size = if (chars > 0 && room / (chars * 0.75f + 0.5f) < base) room / (chars * 0.75f + 0.5f) else base
        Row(Modifier.fillMaxWidth().weight(0.36f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Text(
                legend(k.second), color = Orange, fontSize = size.fixed(), maxLines = 1, softWrap = false, fontWeight = FontWeight.Medium,
                modifier = if (k.boxed) Modifier.border(Dp.Hairline, Orange, RoundedCornerShape(u * 0.5f)).padding(horizontal = u * 0.5f) else Modifier,
            )
            Text(legend(k.alpha), color = Teal, fontSize = size.fixed(), maxLines = 1, softWrap = false, fontWeight = FontWeight.Medium)
        }
        val body = keyColor(k)
        val dark = body == Orange || body == Teal || body == KeyLight
        Box(
            Modifier.fillMaxWidth().weight(0.64f).padding(top = u * 0.4f, bottom = u * 0.9f)
                .clip(RoundedCornerShape(u * 1.6f))
                .background(if (pressed) body.copy(alpha = 0.55f) else body)
                .then(if (lit) Modifier.border(u * 0.5f, Color.White, RoundedCornerShape(u * 1.6f)) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val len = k.label.length
                val fs = if (big) (if (len > 2) u * 4.2f else u * 5.6f) else (if (len > 5) u * 2.7f else if (len > 3) u * 3.1f else u * 3.7f)
                Text(legend(k.label), color = if (dark) Color(0xFF111214) else Legend, fontSize = fs.fixed(), fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                if (k.below.isNotEmpty()) Text(k.below, color = Legend.copy(alpha = 0.7f), fontSize = (u * 1.9f).fixed(), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ArrowPad(u: Dp, modifier: Modifier) {
    val view = LocalView.current
    Box(modifier.padding(u * 1.5f), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxHeight().aspectRatio(1.25f).clip(RoundedCornerShape(50)).background(Color(0xFF26282C))) {
            @Composable
            fun arrow(k: Key, align: Alignment, rot: Float) {
                val source = remember { MutableInteractionSource() }
                val pressed by source.collectIsPressedAsState()
                Box(
                    Modifier.align(align).fillMaxWidth(0.36f).fillMaxHeight(0.42f)
                        .clickable(interactionSource = source, indication = null) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            Host.press(k)
                        }.clearAndSetSemantics { contentDescription = "cursor " + k.say; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(u * 4.2f)) {
                        val p = Path()
                        val s = size.width
                        when (rot) {
                            0f -> { p.moveTo(s / 2, 0f); p.lineTo(s, s); p.lineTo(0f, s) }
                            180f -> { p.moveTo(0f, 0f); p.lineTo(s, 0f); p.lineTo(s / 2, s) }
                            270f -> { p.moveTo(0f, s / 2); p.lineTo(s, 0f); p.lineTo(s, s) }
                            else -> { p.moveTo(0f, 0f); p.lineTo(s, s / 2); p.lineTo(0f, s) }
                        }
                        p.close()
                        drawPath(p, if (pressed) Color(0xFF7D8087) else Legend)
                    }
                }
            }
            arrow(Key.UP, Alignment.TopCenter, 0f)
            arrow(Key.DOWN, Alignment.BottomCenter, 180f)
            arrow(Key.LEFT, Alignment.CenterStart, 270f)
            arrow(Key.RIGHT, Alignment.CenterEnd, 90f)
        }
    }
}
