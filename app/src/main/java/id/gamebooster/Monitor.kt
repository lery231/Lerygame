package id.gamebooster

data class Core(val name: String, val mhz: Int, val gov: String, val online: Boolean)

data class SysStats(
    val battTemp: Float,
    val battLevel: Int,
    val cpuTemp: Float,
    val cores: List<Core>,
    val gpuMhz: Int,
    val gpuLoad: Int,
    val ramUsedMb: Int,
    val ramTotalMb: Int,
    val cpuLoad: Int,
)

object Monitor {
    private var prevTotal = 0L
    private var prevIdle = 0L
    private val cpuZone = Regex("cpu|soc|tsens|cluster|apss|mtk")

    private fun norm(s: String?): Float {
        val v = s?.trim()?.toFloatOrNull() ?: return 0f
        return when {
            v >= 1000f -> v / 1000f
            v >= 150f -> v / 10f
            else -> v
        }
    }

    fun read(): SysStats? {
        val out = RootShell.exec(Scripts.stats).out
        if (out.isBlank()) return null
        var bt = 0f
        var bl = 0
        var cpuT = 0f
        var gpuHz = 0L
        var gpuLoad = 0
        var memT = 0L
        var memA = 0L
        var load = 0
        val cores = mutableListOf<Core>()
        for (line in out.lines()) {
            val p = line.split("|")
            when (p[0]) {
                "BATT" -> {
                    bt = norm(p.getOrNull(1))
                    bl = p.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
                }
                "CPU" -> {
                    val khz = p.getOrNull(2)?.trim()?.toLongOrNull()
                    cores += Core(p.getOrElse(1) { "cpu?" }, ((khz ?: 0L) / 1000).toInt(), p.getOrNull(3)?.trim() ?: "", khz != null)
                }
                "TZ" -> {
                    val t = norm(p.getOrNull(2))
                    if (cpuZone.containsMatchIn(p.getOrElse(1) { "" }.lowercase()) && t in 1f..125f && t > cpuT) cpuT = t
                }
                "KGSL" -> {
                    gpuHz = p.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
                    gpuLoad = p.getOrNull(2)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
                }
                "DEVF" -> if (gpuHz == 0L) gpuHz = p.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
                "MEM" -> {
                    val l = p.getOrElse(1) { "" }
                    memT = Regex("MemTotal:\\s*(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    memA = Regex("MemAvailable:\\s*(\\d+)").find(l)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                }
                "STAT" -> {
                    val n = p.getOrElse(1) { "" }.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
                    if (n.size >= 5) {
                        val total = n.take(8).sum()
                        val idle = n[3] + n[4]
                        val dt = total - prevTotal
                        val di = idle - prevIdle
                        if (prevTotal > 0 && dt > 0) load = (100L * (dt - di) / dt).toInt().coerceIn(0, 100)
                        prevTotal = total
                        prevIdle = idle
                    }
                }
            }
        }
        val gMhz = when {
            gpuHz >= 10_000_000L -> gpuHz / 1_000_000L
            gpuHz >= 10_000L -> gpuHz / 1000L
            else -> gpuHz
        }.toInt()
        return SysStats(bt, bl, cpuT, cores, gMhz, gpuLoad, ((memT - memA) / 1024).toInt(), (memT / 1024).toInt(), load)
    }

    /** Paket aplikasi yang sedang di layar depan. */
    fun foreground(): String? {
        val lines = RootShell.exec("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'").out.lines()
        val rx = Regex("([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+)/[A-Za-z0-9_.\$]+")
        for (key in listOf("mCurrentFocus", "mFocusedApp")) {
            val line = lines.firstOrNull { it.contains(key) && it.contains("/") } ?: continue
            rx.find(line)?.let { return it.groupValues[1] }
        }
        return null
    }
}
