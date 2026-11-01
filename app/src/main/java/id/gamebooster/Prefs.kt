package id.gamebooster

import android.content.Context
import android.content.SharedPreferences

data class Opt(val key: String, val title: String, val desc: String, val def: Boolean)

object Prefs {
    private lateinit var sp: SharedPreferences

    val options = listOf(
        Opt("cpu", "CPU governor → performance", "Semua core CPU dikunci ke mode performa", true),
        Opt("minf", "Kunci frekuensi CPU ke maksimum", "FPS paling stabil, tapi suhu naik cepat", false),
        Opt("gpu", "GPU governor → performance", "Frekuensi GPU tidak turun saat game", true),
        Opt("thermal", "Matikan limit suhu (thermal throttling)", "Hentikan thermal-engine & nonaktifkan thermal zone. Ada batas aman otomatis", true),
        Opt("refresh", "Kunci refresh rate tertinggi", "Pakai refresh rate maksimum layar", true),
        Opt("gamemode", "Game Mode: Performance", "Android 12+, untuk game yang dipilih", true),
        Opt("anim", "Percepat animasi sistem (0.5x)", "Transisi lebih cepat", false),
        Opt("dnd", "Mode Jangan Ganggu", "Blokir notifikasi saat bermain", true),
        Opt("ram", "Bersihkan RAM", "Kill proses latar + drop cache", true),
        Opt("force", "Force-stop semua aplikasi pihak ketiga", "Kecuali game, keyboard & launcher. Chat/musik ikut tertutup", false),
    )
    private val defs = options.associate { it.key to it.def }

    fun init(ctx: Context) {
        if (!::sp.isInitialized) sp = ctx.applicationContext.getSharedPreferences("gb", Context.MODE_PRIVATE)
    }

    fun bool(k: String, d: Boolean = defs[k] ?: false): Boolean = sp.getBoolean(k, d)
    fun setBool(k: String, v: Boolean) = sp.edit().putBoolean(k, v).commit()
    fun int(k: String, d: Int): Int = sp.getInt(k, d)
    fun setInt(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    fun str(k: String, d: String = ""): String = sp.getString(k, d) ?: d
    fun setStr(k: String, v: String) = sp.edit().putString(k, v).commit()

    var games: Set<String>
        get() = sp.getStringSet("games", emptySet()) ?: emptySet()
        set(v) { sp.edit().putStringSet("games", HashSet(v)).apply() }
}
