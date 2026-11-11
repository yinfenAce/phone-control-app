package com.example.phonecontrol

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Red = Color(0xFFD94A4A)
private val Amber = Color(0xFFD6953C)
private val Green = Color(0xFF4F9A63)
private val Bg = Color(0xFF08090B)
private val Surface2 = Color(0xFF111318)
private val TextMain = Color(0xFFECEEF2)
private val TextDim = Color(0xFF777D88)
private val Line = Color(0x14FFFFFF)

private val darkScheme = darkColorScheme(
    primary = Red,
    onPrimary = Color.White,
    background = Bg,
    onBackground = TextMain,
    surface = Surface2,
    onSurface = TextMain,
)

data class Profile(val name: String = "本机用户", val developer: Boolean = false)
data class AppRow(val label: String, val pkg: String, val permCount: Int, val risky: Int)
data class TaskItem(val id: Long, val title: String, val due: String, val done: Boolean)
data class DocItem(val name: String, val size: Long, val isDir: Boolean)

private val RISKY = setOf(
    "android.permission.CAMERA",
    "android.permission.RECORD_AUDIO",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.READ_CONTACTS",
    "android.permission.READ_SMS",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkScheme) { PhoneControlApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhoneControlApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("phone_control", Context.MODE_PRIVATE) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showProfile by rememberSaveable { mutableStateOf(false) }
    var profile by remember {
        mutableStateOf(
            Profile(
                prefs.getString("profile_name", "本机用户") ?: "本机用户",
                prefs.getBoolean("profile_developer", false)
            )
        )
    }
    val titles = listOf("概览", "终端", "应用", "文件", "任务")

    if (showProfile) {
        ProfileScreen(profile, onBack = { showProfile = false }, onSave = {
            profile = it
            prefs.edit().putString("profile_name", it.name).putBoolean("profile_developer", it.developer).apply()
            showProfile = false
        })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(titles[tab], fontWeight = FontWeight.SemiBold)
                        if (profile.developer) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "开发者",
                                color = Red,
                                fontSize = 10.sp,
                                modifier = Modifier
                                    .background(Color(0x22D94A4A), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showProfile = true }) {
                        Text(profile.name, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Bg) {
                val glyphs = listOf("◈", ">_", "▦", "▤", "◇")
                titles.forEachIndexed { i, title ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Text(glyphs[i], fontSize = 14.sp) },
                        label = { Text(title, fontSize = 9.sp) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Bg)) {
            when (tab) {
                0 -> Dashboard(profile, onOpenProfile = { showProfile = true })
                1 -> TerminalScreen()
                2 -> AppsScreen()
                3 -> FilesScreen()
                4 -> TasksScreen(prefs)
            }
        }
    }
}

@Composable
private fun Section(title: String, action: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = TextDim, fontSize = 10.sp)
        if (action != null) Text(action, color = Red, fontSize = 9.sp)
    }
}

@Composable
private fun RowLine(
    leading: String?,
    title: String,
    sub: String,
    trailing: String? = null,
    trailingColor: Color = TextDim,
    onClick: (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            Box(
                Modifier.width(30.dp).height(30.dp).background(Surface2, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) { Text(leading, fontSize = 10.sp, color = TextDim) }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TextMain, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, color = TextDim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, color = trailingColor, fontSize = 10.sp)
        }
    }
    Divider(color = Line, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun Dashboard(profile: Profile, onOpenProfile: () -> Unit) {
    val context = LocalContext.current
    val battery = remember {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
    val storage = remember {
        val s = StatFs(Environment.getDataDirectory().path)
        val used = (s.blockCountLong - s.availableBlocksLong) * s.blockSizeLong
        val total = s.blockCountLong * s.blockSizeLong
        Pair(used, total)
    }
    val ram = remember {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        Pair(mi.totalMem - mi.availMem, mi.totalMem)
    }
    val appCount = remember { runCatching { context.packageManager.getInstalledApplications(0).size }.getOrDefault(0) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Stat("电量", "$battery%")
                Stat("存储", "${gb(storage.first)}/${gb(storage.second)}G")
                Stat("内存", "${gb(ram.first)}/${gb(ram.second)}G")
            }
            Divider(color = Line, thickness = 1.dp)
            Section("设备", "只读")
        }
        item { RowLine("设", "本机", "PEDM00 · Android ${android.os.Build.VERSION.RELEASE}", "快照") }
        item { RowLine("应", "已安装应用", "$appCount 个 · 只读列表", onClick = onOpenProfile) }
        item { Section("说明", "本地") }
        item {
            Text(
                "首版为本机只读控制台：账号只存本机，不联网；权限修改、Shizuku、终端系统命令后续接入。",
                color = TextDim, fontSize = 10.sp, modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun Stat(k: String, v: String) {
    Column {
        Text(k, color = TextDim, fontSize = 9.sp)
        Text(v, color = TextMain, fontSize = 17.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun AppsScreen() {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val apps by produceState(initialValue = emptyList<AppRow>(), query) {
        value = withContext(Dispatchers.IO) { loadApps(context) }
    }
    val filtered = apps.filter {
        query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true)
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索应用、包名", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp)
        )
        Text("${filtered.size} 个应用 · 只读", color = TextDim, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.pkg }) { app ->
                RowLine(
                    leading = app.label.take(1),
                    title = app.label,
                    sub = "${app.pkg} · ${app.permCount} 项权限",
                    trailing = if (app.risky > 0) "高 ${app.risky}" else "正常",
                    trailingColor = if (app.risky > 0) Red else Green
                )
            }
        }
    }
}

@Composable
private fun FilesScreen() {
    val context = LocalContext.current
    var tree by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            tree = uri
        }
    }
    val docs by produceState(initialValue = emptyList<DocItem>(), tree) {
        val uri = tree
        value = if (uri == null) emptyList() else withContext(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(context, uri)
            root?.listFiles()?.map { DocItem(it.name ?: "未命名", it.length(), it.isDirectory) } ?: emptyList()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Text("SAF 目录 · 只读", color = TextDim, fontSize = 9.sp, modifier = Modifier.padding(16.dp, 10.dp))
        OutlinedButton(onClick = { picker.launch(null) }, modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("选择目录")
        }
        if (tree == null) {
            Text("未选择目录。只访问你授权的目录，不申请全盘权限。", color = TextDim, fontSize = 10.sp, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(docs, key = { it.name + it.size }) { d ->
                    RowLine(
                        leading = if (d.isDir) "▸" else "·",
                        title = d.name,
                        sub = if (d.isDir) "文件夹" else formatBytes(d.size),
                        trailing = null
                    )
                }
            }
        }
    }
}

@Composable
private fun TasksScreen(prefs: android.content.SharedPreferences) {
    val tasks = remember { mutableStateListOf<TaskItem>().apply { addAll(loadTasks(prefs)) } }
    var adding by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    fun persist() = saveTasks(prefs, tasks)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp, 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${tasks.count { !it.done }} 待处理 · 本地", color = TextDim, fontSize = 10.sp)
            TextButton(onClick = { adding = true }) { Text("新建") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(tasks, key = { it.id }) { t ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        val i = tasks.indexOf(t)
                        tasks[i] = t.copy(done = !t.done); persist()
                    }.padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (t.done) "☑" else "☐", color = if (t.done) Green else TextDim)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, color = TextMain, fontSize = 13.sp)
                        Text(if (t.due.isBlank()) "无截止时间" else t.due, color = TextDim, fontSize = 9.sp)
                    }
                    Text(if (t.done) "完成" else "待处理", color = if (t.done) Green else Red, fontSize = 9.sp)
                }
                Divider(color = Line, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("新建任务") },
            text = {
                Column {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("任务") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = due, onValueChange = { due = it }, label = { Text("时间（可选）") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (title.isNotBlank()) {
                        tasks.add(TaskItem(System.currentTimeMillis(), title.trim(), due.trim(), false)); persist()
                    }
                    title = ""; due = ""; adding = false
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun TerminalScreen() {
    val scope = rememberCoroutineScope()
    var command by rememberSaveable { mutableStateOf("id") }
    var output by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }

    LaunchedEffect(output, typing) {
        if (output.isBlank()) { typed = ""; return@LaunchedEffect }
        typed = ""
        if (!typing) { typed = output; return@LaunchedEffect }
        for (i in output.indices) {
            typed = output.substring(0, i + 1)
            delay(4)
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("受限终端 · 应用沙箱 · 非系统 root", color = TextDim, fontSize = 9.sp)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$", color = Red, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                singleLine = true,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            )
        }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !running, onClick = {
                if (command.isBlank()) return@Button
                running = true
                scope.launch {
                    output = withContext(Dispatchers.IO) { runShell(command) }
                    running = false
                }
            }) { Text(if (running) "运行中" else "运行") }
            OutlinedButton(onClick = { typing = false; typed = output }) { Text("跳过动画") }
            OutlinedButton(onClick = { output = ""; typed = "" }) { Text("清空") }
        }
        Divider(color = Line)
        Text("输出", color = TextDim, fontSize = 9.sp, modifier = Modifier.padding(vertical = 6.dp))
        Text(
            typed.ifBlank { "等待命令…" },
            color = TextMain,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ProfileScreen(profile: Profile, onBack: () -> Unit, onSave: (Profile) -> Unit) {
    var name by remember { mutableStateOf(profile.name) }
    var dev by remember { mutableStateOf(profile.developer) }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("本机账号", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { onSave(Profile(name.ifBlank { "本机用户" }, dev)) }) { Text("保存") }
        }
        Divider(color = Line)
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("昵称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("开发者称号", color = TextMain, fontSize = 13.sp)
                    Text("仅本机徽章，不代表系统权限", color = TextDim, fontSize = 9.sp)
                }
                Switch(checked = dev, onCheckedChange = { dev = it })
            }
            Spacer(Modifier.height(18.dp))
            Text("账号只保存在本机，不联网、不上传。", color = TextDim, fontSize = 10.sp)
        }
    }
}

private fun gb(bytes: Long): String = String.format("%.1f", bytes / 1024.0 / 1024.0 / 1024.0)
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
    if (bytes < 1024L * 1024 * 1024) return "${bytes / 1024 / 1024} MB"
    return String.format("%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}

private fun loadApps(context: Context): List<AppRow> {
    val pm = context.packageManager
    return runCatching {
        pm.getInstalledApplications(0).map { ai ->
            val info = runCatching { pm.getPackageInfo(ai.packageName, PackageManager.GET_PERMISSIONS) }.getOrNull()
            val perms = info?.requestedPermissions?.toList() ?: emptyList()
            AppRow(ai.loadLabel(pm).toString(), ai.packageName, perms.size, perms.count { it in RISKY })
        }.sortedBy { it.label }
    }.getOrDefault(emptyList())
}

private fun runShell(command: String): String {
    return runCatching {
        val p = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.take(4000)
    }.getOrElse { "错误: ${it.message}" }
}

private fun loadTasks(prefs: android.content.SharedPreferences): List<TaskItem> {
    val raw = prefs.getString("tasks", "[]") ?: "[]"
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TaskItem(o.optLong("id"), o.optString("title"), o.optString("due"), o.optBoolean("done"))
        }
    }.getOrDefault(emptyList())
}

private fun saveTasks(prefs: android.content.SharedPreferences, tasks: List<TaskItem>) {
    val arr = JSONArray()
    tasks.forEach {
        arr.put(JSONObject().put("id", it.id).put("title", it.title).put("due", it.due).put("done", it.done))
    }
    prefs.edit().putString("tasks", arr.toString()).apply()
}
