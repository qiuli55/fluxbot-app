package top.qiuli55.fluxbot.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.qiuli55.fluxbot.data.DemoData
import top.qiuli55.fluxbot.data.Store
import top.qiuli55.fluxbot.nav.AppUi
import top.qiuli55.fluxbot.nav.LocalStore
import top.qiuli55.fluxbot.nav.LocalUi
import top.qiuli55.fluxbot.ui.FIcon
import top.qiuli55.fluxbot.ui.Flux
import top.qiuli55.fluxbot.ui.PageHead

@Composable
fun AppsScreen() {
    val store = LocalStore.current
    val ui = LocalUi.current
    val connected = store.current().connected
    var cat by remember { mutableStateOf("全部") }
    var query by remember { mutableStateOf("") }

    val all = DemoData.apps
    val q = query.trim().lowercase()
    val byCat = if (cat == "全部") all else all.filter { it.cat == cat }
    val visible = if (q.isEmpty()) byCat else byCat.filter { it.name.lowercase().contains(q) }
    val featured = if (cat == "全部") visible.filter { it.featured } else emptyList()
    val rest = if (cat == "全部") visible.filterNot { it.featured } else visible
    val connectedCount = all.count { connected.contains(it.id) }

    Box(Modifier.fillMaxSize().background(Flux.bg)) {
        Column(Modifier.fillMaxSize()) {
            PageHead("连接应用", onBack = { ui.back() }) {
                ConnectedPill(connectedCount)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DemoData.appCategories.forEach { name ->
                    val on = name == cat
                    Box(
                        Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (on) Color(0xFFF1F4F2) else Color(0xFF0C0F12))
                            .border(1.dp, if (on) Color(0xFFF1F4F2) else Flux.line, RoundedCornerShape(16.dp))
                            .clickable { cat = name }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            name,
                            fontSize = 12.5.sp,
                            color = if (on) Color(0xFF0B0E11) else Flux.text3,
                        )
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
            ) {
                if (featured.isNotEmpty()) {
                    SectionTitle("推荐 · 常用")
                    featured.forEach { app ->
                        AppRow(app, connected.contains(app.id)) {
                            toggleApp(store, ui, app)
                        }
                    }
                }
                SectionTitle(if (cat == "全部") "其他应用" else "$cat · ${rest.size} 个")
                if (rest.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (q.isNotEmpty()) "没有找到匹配的应用。" else "这个分类下暂时没有应用。",
                            fontSize = 13.sp,
                            color = Flux.text3,
                        )
                    }
                } else {
                    rest.forEach { app ->
                        AppRow(app, connected.contains(app.id)) {
                            toggleApp(store, ui, app)
                        }
                    }
                }
                Text(
                    "连接后，Bot 可以在任务里读取和写入这些应用。权限随时可以收回。",
                    fontSize = 11.5.sp,
                    color = Flux.text4,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
                )
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                .height(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(Color(0xFF0D1013))
                .border(1.dp, Flux.line, RoundedCornerShape(23.dp))
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FIcon(FIcon.Search, size = 17.dp, color = Flux.text3)
            Spacer(Modifier.width(9.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("搜索应用", fontSize = 14.sp, color = Flux.text4)
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = Flux.text),
                    cursorBrush = SolidColor(Flux.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun toggleApp(store: Store, ui: AppUi, app: DemoData.AppInfo) {
    val on = store.current().connected.contains(app.id)
    store.toggleApp(app.id)
    ui.haptic(8)
    ui.showToast(if (on) "已断开 ${app.name}" else "已连接 ${app.name}")
}

@Composable
private fun ConnectedPill(count: Int) {
    Box(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(Color(0xFF0C0F12))
            .border(1.dp, Flux.line, RoundedCornerShape(17.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("已连接 $count", fontSize = 12.sp, color = Flux.text2)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = Flux.text2,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 14.dp, bottom = 8.dp),
    )
}

@Composable
private fun AppRow(app: DemoData.AppInfo, connected: Boolean, onToggle: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(app.bg)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    app.glyph,
                    fontSize = 16.sp,
                    fontWeight = FontWeight(660),
                    color = Color(app.fg),
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, fontSize = 14.5.sp, fontWeight = FontWeight(460), color = Flux.text)
                Spacer(Modifier.height(3.dp))
                Text(
                    app.desc,
                    fontSize = 11.5.sp,
                    color = Flux.text3,
                    lineHeight = 16.7.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (connected) Flux.accentSoft else Color(0xFF0C0F12))
                    .border(1.dp, if (connected) Flux.accentLine else Flux.line, RoundedCornerShape(16.dp))
                    .clickable { onToggle() }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (connected) "已连接" else "连接",
                    fontSize = 12.sp,
                    color = if (connected) Flux.accent else Flux.text2,
                )
            }
        }
        Hairline()
    }
}
