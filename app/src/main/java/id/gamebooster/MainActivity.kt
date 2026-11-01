package id.gamebooster

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { GBTheme { App() } }
    }
}

@Composable
fun GBTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFF6B35), onPrimary = Color.White,
            secondary = Color(0xFF00E5A0),
            background = Color(0xFF0E0F13), onBackground = Color.White,
            surface = Color(0xFF14161B), onSurface = Color.White,
            surfaceVariant = Color(0xFF1E2128), onSurfaceVariant = Color.White,
        ),
        content = content
    )
}

@Composable
fun App() {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(bottomBar = {
        NavigationBar {
            listOf("⚡" to "Boost", "🎮" to "Game", "⚙️" to "Opsi").forEachIndexed { i, (ic, lb) ->
                NavigationBarItem(
                    selected = tab == i,
                    onClick = { tab = i },
                    icon = { Text(ic, fontSize = 20.sp) },
                    label = { Text(lb) }
                )
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> Home()
                1 -> Games()
                else -> Options()
            }
        }
    }
}

// ---------------------------------------------------------------- komponen kecil

@Composable
fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
            content()
        }
    }
}

@Composable
fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (desc.isNotBlank()) Text(desc, fontSize = 11.sp, color = Color.Gray)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun Stat(label: String, value: String, color: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}

fun tempColor(t: Float, warn: Float, hot: Float): Color = when {
    t >= hot -> Color(0xFFFF4D4D)
    t >= warn -> Color(0xFFFFC107)
    else -> Color(0xFF00E5A0)
}

// ---------------------------------------------------------------- tab Boost

@Composable
fun Home() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val stats by StateHub.stats.collectAsState()
    val fps by StateHub.fps.collectAsState()
    val fg by StateHub.fg.collectAsState()
    val active by Booster.active.collectAsState()
    val mon by StateHub.monitoring.collectAsState()
    val root by StateHub.root.collectAsState()
    val msg by StateHub.msg.collectAsState()
    var auto by remember { mutableStateOf(Prefs.bool("auto")) }
    var ov by remember { mutableStateOf(Prefs.bool("overlay")) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(root) {
        if (root == null) withContext(Dispatchers.IO) {
            val r = RootShell.isRoot()
            if (r) {
                Booster.syncState()
                Booster.setupPerms()
            }
            Booster.active.value = Prefs.bool("active")
            StateHub.root.value = r
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Game Booster", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        when (root) {
            null -> Text("Memeriksa akses root…", color = Color.Gray)
            false -> Panel("Root tidak terdeteksi") {
                Text("Beri izin Superuser (Magisk / KernelSU / APatch) ke aplikasi ini, lalu coba lagi.")
                Button(onClick = { StateHub.root.value = null }) { Text("Cek ulang") }
            }
            else -> {}
        }

        Button(
            onClick = {
                scope.launch {
                    busy = true
                    val m = withContext(Dispatchers.IO) {
                        if (active) Booster.disable()
                        else {
                            BoostService.start(ctx)
                            Booster.enable(null)
                        }
                    }
                    StateHub.msg.value = m
                    busy = false
                }
            },
            enabled = root == true && !busy,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (active) Color(0xFF00B37A) else MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        ) {
            Text(if (active) "BOOST AKTIF — ketuk untuk matikan" else "NYALAKAN BOOST", fontWeight = FontWeight.Bold)
        }
        if (msg.isNotBlank()) Text(msg, fontSize = 12.sp, color = Color.Gray)

        Panel("Otomatis") {
            SwitchRow(
                "Auto-boost saat game dibuka",
                "Boost menyala saat game dari tab Game jadi aplikasi aktif, mati saat ditinggalkan",
                auto
            ) {
                auto = it
                Prefs.setBool("auto", it)
                if (it) BoostService.start(ctx)
            }
            SwitchRow("Overlay FPS & suhu", "Tampilan melayang di atas game", ov) {
                ov = it
                Prefs.setBool("overlay", it)
                if (it) BoostService.start(ctx)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (mon) "Monitor: aktif" else "Monitor: mati", Modifier.weight(1f))
                TextButton(onClick = {
                    if (mon) ctx.startService(Intent(ctx, BoostService::class.java).setAction(BoostService.ACTION_STOP))
                    else BoostService.start(ctx)
                }) { Text(if (mon) "Hentikan" else "Mulai") }
            }
        }

        stats?.let { s ->
            Panel("Status live") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("FPS", fps?.let { "%.0f".format(it) } ?: "—")
                    Stat("CPU", "${s.cpuLoad}%")
                    Stat("Suhu CPU", "%.0f°C".format(s.cpuTemp), tempColor(s.cpuTemp, 60f, 75f))
                    Stat("Baterai", "%.1f°C".format(s.battTemp), tempColor(s.battTemp, 38f, 42f))
                }
                Text("GPU: ${s.gpuMhz} MHz • beban ${s.gpuLoad}%", fontSize = 13.sp)
                Text("RAM: ${s.ramUsedMb} / ${s.ramTotalMb} MB", fontSize = 13.sp)
                LinearProgressIndicator(
                    progress = { s.ramUsedMb.toFloat() / s.ramTotalMb.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Baterai ${s.battLevel}% • App aktif: ${fg ?: "-"}", fontSize = 12.sp, color = Color.Gray)
                s.cores.forEach { c ->
                    Text(
                        "${c.name.padEnd(5)} ${if (c.online) "${c.mhz} MHz" else "offline"}  ${c.gov}",
                        fontSize = 11.sp, fontFamily = FontFamily.Monospace
                    )
                }
            }
            OutlinedButton(
                onClick = {
                    scope.launch {
                        StateHub.msg.value = withContext(Dispatchers.IO) {
                            Booster.cleanRam(Prefs.bool("force"), null)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Bersihkan RAM sekarang") }
        }
    }
}

// ---------------------------------------------------------------- tab Game

data class AppInfo(val label: String, val pkg: String, val isGame: Boolean)

fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0).map {
        AppInfo(
            it.loadLabel(pm).toString(),
            it.activityInfo.packageName,
            it.activityInfo.applicationInfo.category == ApplicationInfo.CATEGORY_GAME
        )
    }.filter { it.pkg != ctx.packageName }.distinctBy { it.pkg }
}

@Composable
fun Games() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var sel by remember { mutableStateOf(Prefs.games) }
    val initSel = remember { Prefs.games }
    var q by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(ctx) } }

    val list = apps
        .filter { q.isBlank() || it.label.contains(q, true) || it.pkg.contains(q, true) }
        .sortedWith(
            compareByDescending<AppInfo> { it.pkg in initSel }
                .thenByDescending { it.isGame }
                .thenBy { it.label.lowercase() }
        )

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(
            "Pilih game untuk auto-boost", fontSize = 18.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp)
        )
        OutlinedTextField(
            value = q, onValueChange = { q = it },
            label = { Text("Cari aplikasi") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.pkg }) { a ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = a.pkg in sel, onCheckedChange = { c ->
                        sel = if (c) sel + a.pkg else sel - a.pkg
                        Prefs.games = sel
                    })
                    Column(Modifier.weight(1f)) {
                        Text((if (a.isGame) "🎮 " else "") + a.label, maxLines = 1)
                        Text(a.pkg, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
                    }
                    TextButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                BoostService.start(ctx)
                                if (!Prefs.bool("active")) Booster.enable(a.pkg, owned = true)
                            }
                            ctx.packageManager.getLaunchIntentForPackage(a.pkg)?.let { ctx.startActivity(it) }
                        }
                    }) { Text("▶ Main") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- tab Opsi

@Composable
fun Options() {
    val states = remember {
        mutableStateMapOf<String, Boolean>().apply { Prefs.options.forEach { put(it.key, Prefs.bool(it.key)) } }
    }
    var bl by remember { mutableFloatStateOf(Prefs.int("batt_limit", 44).toFloat()) }
    var cl by remember { mutableFloatStateOf(Prefs.int("cpu_limit", 90).toFloat()) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Opsi", fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Panel("Yang dilakukan saat boost") {
            Prefs.options.forEach { o ->
                SwitchRow(o.title, o.desc, states[o.key] == true) {
                    states[o.key] = it
                    Prefs.setBool(o.key, it)
                }
            }
        }

        Panel("Batas suhu aman (boost otomatis berhenti)") {
            Text("Baterai: ${bl.toInt()}°C")
            Slider(
                value = bl, onValueChange = { bl = it },
                valueRange = 38f..50f, steps = 11,
                onValueChangeFinished = { Prefs.setInt("batt_limit", bl.toInt()) }
            )
            Text("CPU: ${cl.toInt()}°C")
            Slider(
                value = cl, onValueChange = { cl = it },
                valueRange = 70f..105f, steps = 34,
                onValueChangeFinished = { Prefs.setInt("cpu_limit", cl.toInt()) }
            )
            Text(
                "Dijaga dua lapis: aplikasi dan skrip watchdog di level root. " +
                    "Batas baru berlaku pada boost berikutnya.",
                fontSize = 11.sp, color = Color.Gray
            )
        }

        Panel("Peringatan") {
            Text(
                "Mematikan thermal throttling membuat HP bisa jauh lebih panas, memperpendek umur baterai, " +
                    "dan berisiko merusak perangkat. Gunakan dengan sadar. Nilai asli disimpan dan dipulihkan " +
                    "saat boost dimatikan; reboot juga mengembalikan semuanya.",
                fontSize = 12.sp
            )
        }
    }
}
