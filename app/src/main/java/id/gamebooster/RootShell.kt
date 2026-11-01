package id.gamebooster

import java.util.concurrent.TimeUnit

object RootShell {
    data class Res(val code: Int, val out: String)

    fun exec(cmd: String, timeoutMs: Long = 15000): Res {
        return try {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            val sb = StringBuffer()
            val t = Thread {
                try { p.inputStream.bufferedReader().forEachLine { sb.append(it).append('\n') } } catch (_: Exception) {}
            }
            t.start()
            val done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!done) p.destroyForcibly()
            t.join(1000)
            Res(if (done) p.exitValue() else -2, sb.toString().trim())
        } catch (e: Exception) {
            Res(-1, e.message ?: "error")
        }
    }

    fun isRoot(): Boolean = exec("id").out.contains("uid=0")
}

object Scripts {
    private fun read(n: String) = GameApp.instance.assets.open(n).bufferedReader().use { it.readText() }
    val scan by lazy { read("scan.sh") }
    val stats by lazy { read("stats.sh") }
    val watchdog by lazy { read("watchdog.sh") }
}
