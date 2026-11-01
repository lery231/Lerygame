package id.gamebooster

/** Mengukur FPS game lewat SurfaceFlinger (butuh root). */
object FpsMeter {
    private var layer: String? = null
    private var layerPkg: String? = null

    private fun findLayer(pkg: String): String? {
        val all = RootShell.exec("dumpsys SurfaceFlinger --list").out.lines().map { it.trim() }
        val c = all.filter { it.contains(pkg) && !it.startsWith("Bounds for") }
        return c.firstOrNull { it.startsWith("SurfaceView") && it.contains("BLAST") }
            ?: c.firstOrNull { it.startsWith("SurfaceView") }
            ?: c.firstOrNull { it.contains("BLAST") }
            ?: c.firstOrNull()
    }

    fun sample(pkg: String): Float? {
        if (layer == null || layerPkg != pkg) {
            layer = findLayer(pkg)
            layerPkg = pkg
        }
        val l = layer?.replace("'", "") ?: return null
        val out = RootShell.exec("dumpsys SurfaceFlinger --latency '$l'").out
        val ts = out.lines().drop(1).mapNotNull { ln ->
            val c = ln.trim().split(Regex("\\s+"))
            if (c.size >= 3) c[1].toLongOrNull() else null
        }.filter { it > 0L && it != Long.MAX_VALUE }
        if (ts.size < 2) {
            layer = null
            return null
        }
        val last = ts.max()
        val win = ts.filter { it >= last - 1_000_000_000L }.sorted()
        if (win.size < 2) return 0f
        val span = (win.last() - win.first()) / 1e9
        return if (span > 0) ((win.size - 1) / span).toFloat() else 0f
    }
}
