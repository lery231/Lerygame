package id.gamebooster

import android.content.Intent
import android.hardware.display.DisplayManager
import android.view.Display
import kotlinx.coroutines.flow.MutableStateFlow

object Booster {
    private val ctx get() = GameApp.instance
    val active = MutableStateFlow(false)

    fun isActive() = Prefs.bool("active")
    private fun okPkg(p: String) = p.matches(Regex("[A-Za-z0-9_.]+"))
    private fun q(s: String) = s.replace("'", "")

    private fun maxRefresh(): Int {
        val d = ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        return (d?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 60f).toInt()
    }

    fun setupPerms() {
        val p = ctx.packageName
        RootShell.exec("pm grant $p android.permission.POST_NOTIFICATIONS >/dev/null 2>&1; appops set $p SYSTEM_ALERT_WINDOW allow; dumpsys deviceidle whitelist +$p >/dev/null 2>&1")
    }

    /** Skrip pemulihan dari backup nilai asli. Dipakai disable() dan watchdog root. */
    private fun restoreScript(): String {
        val c = StringBuilder()
        Prefs.str("b_sysfs").lines().filter { it.contains("|") }.reversed().forEach {
            val (path, cur) = it.split("|", limit = 2)
            if (cur.isNotBlank()) c.append("echo '${q(cur)}' > '$path' 2>/dev/null\n")
        }
        Prefs.str("b_svc").split(" ").filter { it.isNotBlank() }.forEach { c.append("start $it\n") }
        c.append("cmd thermalservice reset >/dev/null 2>&1\n")
        Prefs.str("b_set").lines().filter { it.count { ch -> ch == '|' } >= 2 }.forEach {
            val p = it.split("|", limit = 3)
            c.append(
                if (p[2] == "null" || p[2].isBlank()) "settings delete ${p[0]} ${p[1]}\n"
                else "settings put ${p[0]} ${p[1]} ${p[2]}\n"
            )
        }
        return c.toString()
    }

    private fun startWatchdog(): Boolean {
        val restore = restoreScript()
        val bl = Prefs.int("batt_limit", 44)
        val cl = Prefs.int("cpu_limit", 90)
        RootShell.exec(
            "cat > /dev/gb_restore.sh <<'GBEOF'\n$restore\nGBEOF\n" +
                "cat > /dev/gb_watch.sh <<'GBEOF'\n${Scripts.watchdog}\nGBEOF\n" +
                "touch /dev/gb_active\n" +
                "(nohup sh /dev/gb_watch.sh $bl $cl >/dev/null 2>&1 &)"
        )
        val ok = RootShell.exec("[ -e /dev/gb_active ] && echo 1").out == "1"
        Prefs.setBool("marker_ok", ok)
        return ok
    }

    private fun clearState() {
        listOf("b_sysfs", "b_svc", "b_set", "gpkg").forEach { Prefs.setStr(it, "") }
        Prefs.setBool("active", false)
        Prefs.setBool("auto_owned", false)
        active.value = false
    }

    /** Dipanggil berkala: jika marker root hilang (reboot / watchdog memulihkan), bersihkan status. */
    @Synchronized
    fun syncState(): Boolean {
        if (isActive() && Prefs.bool("marker_ok") &&
            RootShell.exec("[ -e /dev/gb_active ] && echo 1").out != "1"
        ) {
            clearState()
            return true
        }
        return false
    }

    fun cleanRam(force: Boolean, keep: String?): String {
        var n = 0
        val sb = StringBuilder("am kill-all >/dev/null 2>&1\nsync\necho 3 > /proc/sys/vm/drop_caches 2>/dev/null\n")
        if (force) {
            val ime = RootShell.exec("settings get secure default_input_method").out.substringBefore('/').trim()
            val home = ctx.packageManager
                .resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                ?.activityInfo?.packageName
            val keepSet = setOfNotNull(keep, ime, home, ctx.packageName)
            RootShell.exec("pm list packages -3").out.lines()
                .map { it.removePrefix("package:").trim() }
                .filter { it.isNotEmpty() && okPkg(it) && it !in keepSet }
                .forEach { sb.append("am force-stop $it\n"); n++ }
        }
        RootShell.exec(sb.toString(), 30000)
        return "RAM dibersihkan" + if (force) " (+$n aplikasi di-force-stop)" else ""
    }

    @Synchronized
    fun enable(game: String?, owned: Boolean = false): String {
        if (isActive()) return "Boost sudah aktif"
        if (!RootShell.isRoot()) return "Root tidak tersedia"
        val g = game?.takeIf { okPkg(it) }
        val rep = mutableListOf<String>()

        // ---- Fase 1: kumpulkan nilai asli (hanya membaca) ----
        val flags = listOf("cpu", "minf", "gpu", "thermal")
            .joinToString("\n") { "${it.uppercase()}=${if (Prefs.bool(it)) 1 else 0}" }
        val scan = RootShell.exec(flags + "\n" + Scripts.scan).out.lines()
        val nodes = scan.filter { it.startsWith("F|") }.map { it.split("|") }.filter { it.size >= 4 }
        val svc = scan.filter { it.startsWith("S|") }.map { it.split("|") }
            .filter { it.size >= 3 && it[2].trim() == "running" }.map { it[1] }

        val sets = mutableListOf<Triple<String, String, String>>()
        if (Prefs.bool("refresh")) {
            val hz = maxRefresh().toString()
            sets += Triple("system", "min_refresh_rate", hz)
            sets += Triple("system", "peak_refresh_rate", hz)
        }
        if (Prefs.bool("anim")) {
            listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
                .forEach { sets += Triple("global", it, "0.5") }
        }
        if (Prefs.bool("dnd")) sets += Triple("global", "zen_mode", "1")
        sets += Triple("global", "low_power", "0")

        val readCmd = sets.joinToString("\n") {
            "echo \"V|${it.first}|${it.second}|\$(settings get ${it.first} ${it.second})\""
        }
        val orig = RootShell.exec(readCmd).out.lines().filter { it.startsWith("V|") }
            .map { it.split("|") }.filter { it.size >= 4 }

        Prefs.setStr("b_sysfs", nodes.joinToString("\n") { "${it[1]}|${q(it[2].trim())}" })
        Prefs.setStr("b_svc", svc.joinToString(" "))
        Prefs.setStr("b_set", orig.joinToString("\n") { "${it[1]}|${it[2]}|${q(it[3].trim())}" })
        Prefs.setStr("gpkg", g ?: "")
        Prefs.setBool("active", true)
        Prefs.setBool("auto_owned", owned)

        // ---- Fase 2: pengaman root (watchdog) sebelum apa pun diubah ----
        rep += if (startWatchdog()) "Watchdog suhu (root) aktif"
        else "PERINGATAN: watchdog tidak bisa aktif, pengaman hanya dari aplikasi"

        // ---- Fase 3: terapkan ----
        if (nodes.isNotEmpty()) {
            val apply = nodes.joinToString("\n") {
                "if echo '${q(it[3].trim())}' > '${it[1]}' 2>/dev/null; then echo OK; else echo NO; fi"
            }
            val ok = RootShell.exec(apply).out.lines().count { it == "OK" }
            rep += "Sysfs: $ok/${nodes.size} node diterapkan"
        } else {
            rep += "Sysfs: tidak ada node yang cocok di HP ini"
        }

        if (Prefs.bool("thermal")) {
            val sb = StringBuilder("cmd thermalservice override-status 0 >/dev/null 2>&1\n")
            svc.forEach { sb.append("stop $it\n") }
            RootShell.exec(sb.toString())
            rep += "Thermal: ${svc.size} service dihentikan"
        }

        RootShell.exec(sets.joinToString("\n") { "settings put ${it.first} ${it.second} ${it.third}" })
        rep += "Setting sistem: ${sets.size} diterapkan"

        if (g != null && Prefs.bool("gamemode")) {
            RootShell.exec(
                "cmd game set --mode performance --user 0 $g >/dev/null 2>&1 || " +
                    "cmd game mode performance $g >/dev/null 2>&1 || " +
                    "cmd game set --mode 2 --user 0 $g >/dev/null 2>&1"
            )
            rep += "Game Mode Performance: $g"
        }

        if (Prefs.bool("ram")) rep += cleanRam(Prefs.bool("force"), g)

        active.value = true
        return "Boost AKTIF\n" + rep.joinToString("\n")
    }

    @Synchronized
    fun disable(): String {
        val hasBackup = Prefs.str("b_sysfs").isNotBlank() || Prefs.str("b_svc").isNotBlank() || Prefs.str("b_set").isNotBlank()
        if (!isActive() && !hasBackup) return "Boost tidak aktif"
        val cmds = StringBuilder(restoreScript())
        val g = Prefs.str("gpkg")
        if (g.isNotBlank() && okPkg(g)) cmds.append("cmd game reset $g >/dev/null 2>&1\n")
        cmds.append("rm -f /dev/gb_active\n")
        RootShell.exec(cmds.toString(), 30000)
        clearState()
        return "Boost dimatikan, pengaturan asli dipulihkan"
    }
}
