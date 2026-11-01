package id.gamebooster

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/** Overlay kecil di atas game: FPS, beban CPU/GPU, suhu. Harus dipakai dari main thread. */
class Overlay(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val tv = TextView(ctx).apply {
        setTextColor(Color.WHITE)
        textSize = 11f
        typeface = Typeface.MONOSPACE
        setBackgroundColor(Color.argb(150, 0, 0, 0))
        setPadding(14, 8, 14, 8)
    }
    private var shown = false

    fun show() {
        if (shown || !Settings.canDrawOverlays(ctx)) return
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 60
        }
        try { wm.addView(tv, lp); shown = true } catch (_: Exception) {}
    }

    fun update(t: String) { if (shown) tv.text = t }

    fun remove() {
        if (shown) {
            try { wm.removeView(tv) } catch (_: Exception) {}
            shown = false
        }
    }
}
