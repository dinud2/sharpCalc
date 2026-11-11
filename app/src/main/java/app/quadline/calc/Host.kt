package app.quadline.calc

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import sharpcalc.calc.Calculator
import sharpcalc.calc.Display
import sharpcalc.calc.Key
import java.util.concurrent.Executors

/**
 * Owns the calculator for the life of the process. Key presses run on a worker thread so that long
 * calculations (Σ, ∫, Solver) can show BUSY and be cancelled with ON/C (N-1). The state is written to
 * preferences after every key, so it survives backgrounding and process death (N-2).
 */
object Host {
    private val calc = Calculator()
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var prefs: SharedPreferences? = null
    @Volatile private var running = false

    var display: Display by mutableStateOf(calc.display())
        private set
    var busy: Boolean by mutableStateOf(false)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("calculator", Context.MODE_PRIVATE)
        prefs?.getString("state", null)?.let { calc.load(it) }
        display = calc.display()
    }

    fun press(key: Key) {
        if (running) {
            if (key == Key.ONC) calc.cancel()
            return
        }
        running = true
        val showBusy = Runnable { busy = true }
        main.postDelayed(showBusy, 150)
        exec.execute {
            try { calc.press(key) } catch (t: Throwable) { }
            val d = calc.display()
            val saved = try { calc.save() } catch (t: Throwable) { null }
            main.post {
                main.removeCallbacks(showBusy)
                busy = false
                running = false
                display = d
                if (saved != null) prefs?.edit()?.putString("state", saved)?.apply()
            }
        }
    }
}
