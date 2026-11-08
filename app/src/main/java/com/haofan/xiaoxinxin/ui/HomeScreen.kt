package com.haofan.xiaoxinxin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.haofan.xiaoxinxin.BuildConfig
import com.haofan.xiaoxinxin.core.DetailMode
import com.haofan.xiaoxinxin.core.EarnRepo
import com.haofan.xiaoxinxin.core.HomeButton
import com.haofan.xiaoxinxin.core.HomeCard
import com.haofan.xiaoxinxin.core.ProgressInfo
import com.haofan.xiaoxinxin.core.countdownProgress
import com.haofan.xiaoxinxin.core.fmt
import com.haofan.xiaoxinxin.core.percentText
import com.haofan.xiaoxinxin.core.recordDayProgress
import kotlinx.coroutines.delay

/**
 * 2.0 首页：三张互相独立的进度卡（Your Money 薪酬 / Your Days 倒计时 / Your Days 纪念日）。
 * 卡片可单独隐藏（设置 → 首页布局），但至少留一张；点卡片进对应二级页。
 */
@Composable
fun HomeScreen(
    onOpenDetail: (DetailMode) -> Unit,
    onOpenSettings: () -> Unit,
    onToggleFloat: () -> Unit,
) {
    val s by EarnRepo.state.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }

    val cards = HomeCard.parse(s.hiddenHomeCards)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimen.PageH),
    ) {
        Spacer(Modifier.height(10.dp))
        TopBar(onToggleFloat, s.floatEnabled, s.hiddenButtons)
        Spacer(Modifier.height(14.dp))

        if (cards.contains(HomeCard.MONEY)) {
            SectionLabel("Your Money", "薪酬进度")
            MoneyCard(
                info = s.progressInfo(DetailMode.SALARY),
                onOpen = { onOpenDetail(DetailMode.SALARY) },
                state = s,
                now = now,
                onPunch = {
                    when {
                        s.salary <= 0 -> onOpenSettings()
                        s.working -> EarnRepo.clockOut()
                        else -> EarnRepo.clockIn()
                    }
                },
                onSettings = onOpenSettings,
            )
            Spacer(Modifier.height(12.dp))
        }

        if (cards.contains(HomeCard.COUNTDOWN) || cards.contains(HomeCard.RECORD_DAY)) {
            SectionLabel("Your Days", "倒计时 · 纪念日")
        }

        if (cards.contains(HomeCard.COUNTDOWN)) {
            CountdownCard(
                info = s.progressInfo(DetailMode.COUNTDOWN),
                hasEvent = s.activeCountdown != null,
                onOpen = { onOpenDetail(DetailMode.COUNTDOWN) },
            )
            Spacer(Modifier.height(12.dp))
        }

        if (cards.contains(HomeCard.RECORD_DAY)) {
            RecordCard(
                info = s.progressInfo(DetailMode.RECORD_DAY),
                hasEvent = s.activeRecordDay != null,
                onOpen = { onOpenDetail(DetailMode.RECORD_DAY) },
            )
            Spacer(Modifier.height(12.dp))
        }

        // 悬浮岛状态提示
        Spacer(Modifier.height(4.dp))
        if (s.floatEnabled) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(XC.teal))
                Spacer(Modifier.width(6.dp))
                Text("顶部悬浮岛已开启 · 单击展开、双击迷你、长按隐藏", color = XC.textFaint, fontSize = 10.5.sp)
            }
        } else {
            Text(
                if (s.salary <= 0) "还没填月薪 · 进设置填一下，30 秒搞定"
                else "开启悬浮岛后，切到别的应用也能看到进账数字",
                color = XC.textFaint,
                fontSize = 10.5.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

// ---------------- 顶栏 ----------------

@Composable
private fun TopBar(onToggleFloat: () -> Unit, floatOn: Boolean, hidden: Set<String>) {
    GlassCard(Modifier.fillMaxWidth(), radius = 40) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(2.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(XC.goldHi, XC.gold, XC.goldDim))),
                contentAlignment = Alignment.Center,
            ) {
                Text("薪", color = XC.onGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(9.dp))
            Text("小薪薪", color = XC.text, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Text("v${BuildConfig.VERSION_NAME}", color = XC.textFaint, fontSize = 10.5.sp)
            Spacer(Modifier.weight(1f))
            if (HomeButton.FLOAT.key !in hidden) {
                GlassRoundButton(active = floatOn, onClick = onToggleFloat) {
                    Text(
                        "浮",
                        color = if (floatOn) XC.gold else XC.textDim,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ---------------- 三张卡 ----------------

@Composable
private fun CardShell(
    tag: String,
    tagColor: androidx.compose.ui.graphics.Color = XC.gold,
    onOpen: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth(), radius = 26, strong = true) {
        Column(
            Modifier
                .fillMaxWidth()
                .liquidPress(
                    onClick = onOpen,
                    shape = RoundedCornerShape(26.dp),
                    weight = PressWeight.Row,
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            Text(tag, color = tagColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(8.dp))
            content()
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("查看详情", color = XC.textFaint, fontSize = 10.sp)
                Spacer(Modifier.width(3.dp))
                Text("›", color = XC.textFaint, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun BigNumber(leading: String, trailing: String, money: Boolean, size: Int = 40) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            leading,
            style = TextStyle(
                color = if (money) XC.gold else XC.text,
                fontSize = size.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = NumFont,
                letterSpacing = (-1).sp,
                shadow = if (money) Shadow(
                    color = XC.gold.copy(alpha = if (XC.isDark) 0.38f else 0.18f),
                    offset = Offset.Zero,
                    blurRadius = 16f,
                ) else null,
            ),
            maxLines = 1,
        )
        if (trailing.isNotEmpty()) {
            Text(
                trailing,
                color = XC.textDim,
                fontSize = (size * 0.42f).sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = (size * 0.12f).dp),
            )
        }
    }
}

@Composable
private fun MoneyCard(
    info: ProgressInfo,
    state: com.haofan.xiaoxinxin.core.EarnState,
    now: Long,
    onOpen: () -> Unit,
    onPunch: () -> Unit,
    onSettings: () -> Unit,
) {
    CardShell(tag = "YOUR MONEY · 薪酬", onOpen = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.salary > 0) {
                BigNumber(info.leading, "", money = info.isMoney)
            } else {
                Text("设置月薪后开始计费", color = XC.textDim, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.weight(1f))
            if (state.privacyEnabled) PrivacyBadge(state.privacyActive)
        }
        Spacer(Modifier.height(12.dp))
        ProgressMeter(info.ratio)
        Spacer(Modifier.height(9.dp))
        ProgressCaption(info)

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            MiniStat("日薪", if (state.salary > 0) state.displayMoney(state.ratePerDay) else "—", Modifier.weight(1f))
            MiniStat("时薪", if (state.salary > 0) state.displayMoney(state.ratePerHour) else "—", Modifier.weight(1f))
            MiniStat(
                "本月",
                if (state.salary > 0) state.displayMoney(state.monthEarned(now)) else "—",
                Modifier.weight(1f),
            )
        }

        // 快捷打卡：只做「上班 / 下班」，加班打卡在薪酬页
        val canPunch = HomeButton.CLOCK_IN.key !in state.hiddenButtons ||
            HomeButton.CLOCK_OUT.key !in state.hiddenButtons
        if (canPunch) {
            Spacer(Modifier.height(11.dp))
            PunchBar(
                working = state.working,
                label = when {
                    state.salary <= 0 -> "去设置月薪"
                    state.working -> "打卡下班"
                    else -> "打卡上班"
                },
                sub = if (state.working) {
                    val m = state.workedMinutes(now)
                    "已工作 ${m / 60} 小时 ${m % 60} 分"
                } else null,
                onClick = { if (state.salary <= 0) onSettings() else onPunch() },
            )
        }
    }
}

@Composable
private fun CountdownCard(info: ProgressInfo, hasEvent: Boolean, onOpen: () -> Unit) {
    CardShell(tag = "YOUR DAYS · 倒计时", tagColor = XC.teal, onOpen = onOpen) {
        if (hasEvent) {
            BigNumber(info.leading, info.trailing, money = false, size = 42)
            Spacer(Modifier.height(12.dp))
            ProgressMeter(info.ratio, height = 8)
            Spacer(Modifier.height(9.dp))
            ProgressCaption(info)
        } else {
            EmptyState(
                title = "还没有倒计时",
                desc = "新年、生日、发薪日、旅行出发日 —— 建一个，天天看还剩几天",
                cta = "去建一个",
            )
        }
    }
}

@Composable
private fun RecordCard(info: ProgressInfo, hasEvent: Boolean, onOpen: () -> Unit) {
    CardShell(tag = "YOUR DAYS · 纪念日", tagColor = XC.gold, onOpen = onOpen) {
        if (hasEvent) {
            BigNumber(info.leading, info.trailing, money = false, size = 42)
            Spacer(Modifier.height(10.dp))
            ProgressCaption(info)
        } else {
            EmptyState(
                title = "还没有纪念日",
                desc = "在一起、入职、搬进新家 —— 从那天起，一共走了多少天",
                cta = "去建一个",
            )
        }
    }
}

@Composable
private fun EmptyState(title: String, desc: String, cta: String) {
    Column {
        Text(title, color = XC.textDim, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Text(desc, color = XC.textFaint, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(XC.gold.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(cta, color = XC.gold, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(XC.fill)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, color = XC.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = NumFont, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(label, color = XC.textFaint, fontSize = 9.5.sp)
    }
}

@Composable
private fun PunchBar(working: Boolean, label: String, sub: String?, onClick: () -> Unit) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (working) Brush.linearGradient(listOf(XC.ghostA, XC.ghostB))
                    else Brush.verticalGradient(listOf(XC.goldHi, XC.gold, XC.goldDim))
                )
                .liquidPress(onClick = onClick, shape = RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (working) XC.gold else XC.onGold,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
        }
        if (sub != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                sub,
                color = XC.textFaint,
                fontSize = 10.5.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}
