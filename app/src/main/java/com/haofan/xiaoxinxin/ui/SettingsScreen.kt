package com.haofan.xiaoxinxin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haofan.xiaoxinxin.core.EarnRepo
import com.haofan.xiaoxinxin.core.IslandDoctor
import com.haofan.xiaoxinxin.core.IslandReport
import com.haofan.xiaoxinxin.core.dateKey
import com.haofan.xiaoxinxin.core.fmt
import com.haofan.xiaoxinxin.core.isValidDayKey
import com.haofan.xiaoxinxin.floatx.FloatService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val s by EarnRepo.state.collectAsState()
    var startKey by remember { mutableStateOf("") }
    var endKey by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf("") }
    var newEarned by remember { mutableStateOf("") }
    var newOT by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }

    val ctx = LocalContext.current
    val uiScope = rememberCoroutineScope()
    var probe by remember { mutableStateOf<IslandReport?>(null) }
    var probing by remember { mutableStateOf(false) }
    var probeHint by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { probe = IslandDoctor.probe(ctx, postTest = false) }
    }

    val rows = s.dayLogRows(startKey, endKey)

    LiquidBackground {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(XC.line)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("‹", color = XC.textDim, fontSize = 20.sp) }
            Spacer(Modifier.width(10.dp))
            Text("设置", color = XC.text, fontSize = 16.sp, fontWeight = W500)
            Spacer(Modifier.weight(1f))
            Text("v1.9.4", color = XC.textFaint, fontSize = 11.sp)
        }

        Spacer(Modifier.height(18.dp))

        Group("薪酬与工作天数") {
            Field("月薪（税后）", s.salaryText, { EarnRepo.setSalary(it) }, "元", KeyboardType.Number)
            Spacer(Modifier.height(10.dp))
            Field(
                "每月工作日",
                s.workDaysText,
                { EarnRepo.setWorkDays(it) },
                if (s.isAutoDays) "自动 ${s.autoDays} 天" else "天",
                KeyboardType.Number,
            )
            Spacer(Modifier.height(8.dp))
            Hint("留空即按法定节假日自动推算（本月 ${s.autoDays} 天）")
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("日薪", "¥${fmt(s.ratePerDay)}", Modifier.weight(1f))
                MiniStat("时薪", "¥${fmt(s.ratePerHour)}", Modifier.weight(1f))
                MiniStat("每秒", "¥${fmt(s.ratePerSec, 4)}", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(12.dp))

        Group("工作时间 · 自动下班") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    Field("上班", s.workStart, { EarnRepo.setWorkStart(it) }, "", KeyboardType.Text)
                }
                Box(Modifier.weight(1f)) {
                    Field("下班", s.workEnd, { EarnRepo.setWorkEnd(it) }, "", KeyboardType.Text)
                }
                Box(Modifier.weight(1f)) {
                    Field("休息", s.breakMin, { EarnRepo.setBreakMin(it) }, "分", KeyboardType.Number)
                }
            }
            Spacer(Modifier.height(8.dp))
            Hint("实际工时 ${fmt(s.effectiveHours, 1)} 小时/天 · 支持跨零点班次（如 22:00–06:00）")
            Spacer(Modifier.height(12.dp))
            ToggleRow("到点自动下班", "下班时间一到自动停表，加班段落不会被切断", s.autoClockOut) {
                EarnRepo.setAutoClockOut(it)
            }
        }

        Spacer(Modifier.height(12.dp))

        Group("显示与通知") {
            ToggleRow(
                "悬浮窗",
                "自己画的顶部药丸：可拖动、贴边吸附、点开看今日和本月。任何机型都能用",
                s.floatEnabled,
            ) {
                EarnRepo.setFloatEnabled(it)
                FloatService.applyToggle(ctx)
            }
            Spacer(Modifier.height(10.dp))
            // probe 是异步来的，没回来之前不能直接判「不支持」，否则会闪一下灰 + 误报提示
            val checking = probe == null && IslandDoctor.platformCapable()
            val islandOk = probe?.canPromote == true
            ToggleRow(
                "灵动岛",
                when {
                    checking -> "正在检测系统支持…"
                    islandOk -> "交给系统渲染的实时通知，能进状态栏、锁屏和通知栏置顶"
                    else -> IslandDoctor.brandHint()
                },
                s.islandEnabled,
                enabled = islandOk,
            ) {
                EarnRepo.setIslandEnabled(it)
                FloatService.applyToggle(ctx)
            }
            if (!checking && !islandOk) {
                Spacer(Modifier.height(8.dp))
                Hint("这个开关灰掉不是 bug：系统没开放标准实时通知接口，改设置也打不开。上面的悬浮窗就是替代方案。")
            }
            Spacer(Modifier.height(10.dp))
            Field("通知栏金额前缀", s.barSymbol, { EarnRepo.setBarSymbol(it) }, "可留空", KeyboardType.Text)
        }

        Spacer(Modifier.height(12.dp))

        Group("系统岛能力（实时通知）") {
            val r = probe
            if (r == null) {
                Hint("正在检测…")
            } else {
                Text(r.verdict, color = if (r.promoted) XC.teal else XC.orange, fontSize = 12.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(10.dp))
                r.rows.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(k, color = XC.textFaint, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Text(v, color = XC.textDim, fontSize = 11.sp, fontFamily = NumFont)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallChip(if (probing) "检测中…" else "发一条测试岛通知") {
                        if (probing) return@SmallChip
                        probing = true
                        probeHint = ""
                        uiScope.launch {
                            val res = withContext(Dispatchers.IO) { IslandDoctor.probe(ctx, postTest = true) }
                            probe = res
                            probing = false
                            probeHint = if (res.promoted)
                                "去看状态栏顶部 / 锁屏，应该出现胶囊了"
                            else
                                "系统没提升。这属于厂商白名单，只能走悬浮岛"
                        }
                    }
                    SmallChip("复制报告") {
                        val text = r.asText()
                        runCatching {
                            val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("小薪薪体检", text))
                            probeHint = "已复制，可以直接发给我"
                        }
                    }
                }
                if (probeHint.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Hint(probeHint, XC.teal)
                }
                Spacer(Modifier.height(8.dp))
                Hint("说明：Android 16 起系统支持把常驻通知提升为状态栏胶囊。小米 HyperOS 3 / OPPO ColorOS 16 会直接吃这套标准；vivo 的原子岛要求应用先上架并申请白名单。")
            }
        }

        Spacer(Modifier.height(12.dp))

        Group("流水明细") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    Field("起始日期", startKey, { startKey = it }, "yyyy-MM-dd", KeyboardType.Text)
                }
                Box(Modifier.weight(1f)) {
                    Field("结束日期", endKey, { endKey = it }, "yyyy-MM-dd", KeyboardType.Text)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallChip("本月") {
                    val now = LocalDate.now()
                    startKey = dateKey(now.withDayOfMonth(1))
                    endKey = dateKey(now)
                }
                SmallChip("清空") { startKey = ""; endKey = "" }
                Spacer(Modifier.weight(1f))
                Text("${rows.size} 条", color = XC.textFaint, fontSize = 11.sp)
            }
            Spacer(Modifier.height(10.dp))
            if (rows.isEmpty()) {
                Hint("这个区间还没有记录。今日数据以实时打卡为准，不入流水。")
            } else {
                rows.forEach { (key, v) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(key, color = XC.textDim, fontSize = 12.sp, fontFamily = NumFont)
                        Spacer(Modifier.width(10.dp))
                        Text("¥${fmt(v.earned)}", color = XC.gold, fontSize = 13.sp, fontFamily = NumFont)
                        if (v.otSeconds > 0) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "加班 ${(v.otSeconds / 60).toInt()} 分",
                                color = XC.orange,
                                fontSize = 11.sp,
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "删除",
                            color = Color(0xFFE24B4A),
                            fontSize = 11.sp,
                            modifier = Modifier.clickable {
                                EarnRepo.removeDayLog(key)
                            }.padding(4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("手动补记", color = XC.textDim, fontSize = 12.sp, fontWeight = W500)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1.4f)) {
                    Field("日期", newKey, { newKey = it }, "yyyy-MM-dd", KeyboardType.Text)
                }
                Box(Modifier.weight(1f)) {
                    Field("金额", newEarned, { newEarned = it }, "元", KeyboardType.Number)
                }
                Box(Modifier.weight(0.9f)) {
                    Field("加班", newOT, { newOT = it }, "分", KeyboardType.Number)
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(XC.gold)
                    .clickable {
                        val err = EarnRepo.insertDayLog(
                            newKey.trim(),
                            newEarned.toDoubleOrNull() ?: 0.0,
                            newOT.toDoubleOrNull() ?: 0.0,
                        )
                        if (err == null) {
                            hint = "已补记 ${newKey.trim()}"
                            newKey = ""; newEarned = ""; newOT = ""
                        } else hint = err
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("写入流水", color = Color(0xFF1A1200), fontSize = 14.sp, fontWeight = W600)
            }
            if (hint.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Hint(hint, if (hint.startsWith("已")) XC.teal else Color(0xFFE24B4A))
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "数据全部保存在本机，App 不联网、不上传。",
            color = XC.textFaint,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(30.dp))
    }
    }
}

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), radius = 22) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = XC.gold, fontSize = 11.5.sp, fontWeight = W500, letterSpacing = 0.6.sp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    suffix: String,
    kb: KeyboardType,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(label, color = XC.textFaint, fontSize = 10.sp)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = XC.text,
                    fontSize = 14.sp,
                    fontFamily = if (kb == KeyboardType.Number) NumFont else null,
                ),
                cursorBrush = SolidColor(XC.gold),
                keyboardOptions = KeyboardOptions(keyboardType = kb),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text("—", color = XC.textFaint, fontSize = 14.sp)
                    }
                    inner()
                },
            )
            if (suffix.isNotEmpty()) {
                Text(suffix, color = XC.textFaint, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun Hint(text: String, color: Color = XC.textFaint) {
    Text(text, color = color, fontSize = 11.sp, lineHeight = 16.sp)
}

@Composable
private fun ToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val titleColor = if (enabled) XC.text else XC.textFaint
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 13.sp)
            Spacer(Modifier.height(2.dp))
            Text(desc, color = XC.textFaint, fontSize = 10.5.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .width(46.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when {
                        !enabled -> Brush.linearGradient(listOf(Color(0x14FFFFFF), Color(0x0AFFFFFF)))
                        checked -> Brush.linearGradient(listOf(XC.gold, Color(0xFFFFE08A)))
                        else -> Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x1FFFFFFF)))
                    }
                ),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !enabled -> Color(0x66F4F7FA)
                            checked -> Color(0xFF241A00)
                            else -> Color(0xE6F4F7FA)
                        }
                    ),
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x0FFFFFFF))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = XC.text, fontSize = 12.sp, fontFamily = NumFont)
        Spacer(Modifier.height(2.dp))
        Text(label, color = XC.textFaint, fontSize = 10.sp)
    }
}

@Composable
private fun SmallChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x14FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, color = XC.textDim, fontSize = 11.sp)
    }
}
