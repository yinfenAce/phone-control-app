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
import android.provider.Settings
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CircularProgressIndicator
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

private const val OWNER_NAME = "银凤Ace"
private const val AUTHOR_NAME = "银凤Ace"
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
    surfaceVariant = Surface2,
    onSurfaceVariant = TextDim,
    surfaceContainer = Surface2,
    surfaceContainerLow = Bg,
    surfaceContainerLowest = Bg,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface2,
    surfaceDim = Bg,
    surfaceBright = Surface2,
    outline = Color(0x33FFFFFF),
)

data class Profile(val name: String = "本机用户", val developer: Boolean = false)
data class AppRow(val label: String, val pkg: String, val permCount: Int, val risky: Int, val perms: List<String> = emptyList())
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
    var tab by remember { mutableIntStateOf(0) }
    var showProfile by remember { mutableStateOf(false) }
    var profile by remember {
        mutableStateOf(
            Profile(
                prefs.getString("profile_name", "本机用户") ?: "本机用户",
                (prefs.getString("profile_name", "本机用户") ?: "本机用户") == OWNER_NAME
            )
        )
    }
    val titles = listOf("概览", "终端", "应用", "文件", "任务")

    if (showProfile) {
        ProfileScreen(profile, onBack = { showProfile = false }, onSave = {
            profile = it
            prefs.edit().putString("profile_name", it.name).putBoolean("profile_developer", it.name == OWNER_NAME).apply()
            showProfile = false
        })
        return
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg, titleContentColor = TextMain, actionIconContentColor = TextDim),
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
                0 -> Dashboard(profile, onOpenProfile = { showProfile = true }, onOpenApps = { tab = 2 })
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
private fun Dashboard(profile: Profile, onOpenProfile: () -> Unit, onOpenApps: () -> Unit) {
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
        item { RowLine("应", "已安装应用", "$appCount 个 · 只读列表", onClick = onOpenApps) }
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
    var apps by remember { mutableStateOf<List<AppRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<AppRow?>(null) }

    LaunchedEffect(Unit) {
        error = null
        apps = withContext(Dispatchers.IO) {
            runCatching { loadApps(context) }.onFailure { error = it.message }.getOrDefault(emptyList())
        }
    }

    val current = selected
    if (current != null) {
        AppDetail(current, onBack = { selected = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索应用、包名", fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp, 8.dp)
        )
        val list = apps
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Red, strokeWidth = 2.dp)
                    Spacer(Modifier.height(10.dp))
                    Text("读取应用列表…", color = TextDim, fontSize = 10.sp)
                }
            }
            error != null -> Text("读取失败：$error", color = Red, fontSize = 10.sp, modifier = Modifier.padding(16.dp))
            list.isEmpty() -> Text("没有读取到应用。请确认系统允许查询应用列表。", color = TextDim, fontSize = 10.sp, modifier = Modifier.padding(16.dp))
            else -> {
                val filtered = list.filter { query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true) }
                Text("${filtered.size} 个应用 · 只读", color = TextDim, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 16.dp))
                LazyColumn(Modifier.fillMaxSize()) {
                    items(filtered, key = { it.pkg }) { app ->
                        RowLine(
                            leading = app.label.take(1),
                            title = app.label,
                            sub = "${app.pkg} · ${app.permCount} 项权限",
                            trailing = if (app.risky > 0) "高 ${app.risky}" else "正常",
                            trailingColor = if (app.risky > 0) Red else Green,
                            onClick = { selected = app }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppDetail(app: AppRow, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Spacer(Modifier.width(4.dp))
            Text(app.label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Divider(color = Line)
        Column(Modifier.padding(16.dp)) {
            Text(app.pkg, color = TextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            Text(if (app.risky > 0) "高风险权限 ${app.risky} 项" else "未发现高风险权限", color = if (app.risky > 0) Red else Green, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            }, modifier = Modifier.fillMaxWidth()) { Text("打开系统应用设置") }
            Spacer(Modifier.height(14.dp))
            Text("权限（只读）", color = TextDim, fontSize = 10.sp)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(app.perms.ifEmpty { listOf("未声明权限") }) { p ->
                val risky = p in RISKY
                RowLine(
                    leading = if (risky) "!" else "·",
                    title = p.substringAfterLast('.').replace('_', ' '),
                    sub = p,
                    trailing = if (risky) "需确认" else null,
                    trailingColor = Red
                )
            }
        }
    }
}

@Composable
private fun FilesScreen() {
    val context = LocalContext.current
    var tree by remember { mutableStateOf<Uri?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
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
            Text(if (tree == null) "选择目录" else "更换目录")
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索文件名", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (tree == null) {
            Text("未选择目录。只访问你授权的目录，不申请全盘权限。", color = TextDim, fontSize = 10.sp, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                val filtered = docs.filter { query.isBlank() || it.name.contains(query, true) }
                items(filtered, key = { it.name + it.size }) { d ->
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
            containerColor = Surface2,
            titleContentColor = TextMain,
            textContentColor = TextMain,
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
    var typing by remember { mutableStateOf(false) }
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
            OutlinedButton(onClick = { typing = !typing }) { Text(if (typing) "动画开" else "动画关") }
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
    val isOwner = name == OWNER_NAME
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("本机账号", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { onSave(Profile(name.ifBlank { "本机用户" }, isOwner)) }) { Text("保存") }
        }
        Divider(color = Line)
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("昵称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            if (isOwner) {
                Text("开发者", color = Red, fontSize = 11.sp)
                Text("仅所有者账号显示；不是权限开关。", color = TextDim, fontSize = 9.sp)
            } else {
                Text("开发者称号仅对所有者账号显示。", color = TextDim, fontSize = 10.sp)
            }
            Spacer(Modifier.height(18.dp))
            Text("作者：$AUTHOR_NAME", color = TextDim, fontSize = 10.sp)
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
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        packages.take(200).map { info ->
            val app = info.applicationInfo
            val perms = info.requestedPermissions?.toList() ?: emptyList()
            AppRow(
                label = runCatching { app?.loadLabel(pm)?.toString() }.getOrNull() ?: info.packageName,
                pkg = info.packageName,
                permCount = perms.size,
                risky = perms.count { it in RISKY },
                perms = perms
            )
        }.sortedBy { it.label.lowercase() }
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
