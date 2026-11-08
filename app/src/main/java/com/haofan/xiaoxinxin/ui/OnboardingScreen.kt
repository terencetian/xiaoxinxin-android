package com.haofan.xiaoxinxin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import com.haofan.xiaoxinxin.core.EarnRepo

/**
 * 首次启动引导（对齐 macOS OnboardingView）：讲清三个模块是干什么的，一键进主页。
 * 只在 `onboardingDone == false` 时由 MainActivity 展示，点「开始使用」后落盘标记、自动切到主页。
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val modules = listOf(
        OnboardModule(
            tag = "YOUR MONEY · 薪酬",
            title = "上班也能看见钱进账",
            desc = "填个月薪，时薪、日薪、每秒进账实时算；上班打卡开始计费，进度条一路涨到下班。",
            color = XC.gold,
        ),
        OnboardModule(
            tag = "YOUR DAYS · 倒计时",
            title = "想知道的事，天天看还剩几天",
            desc = "新年、生日、发薪日、旅行出发日 —— 建一个，自动算剩余天数和完成百分比。",
            color = XC.teal,
        ),
        OnboardModule(
            tag = "YOUR DAYS · 纪念日",
            title = "从那天起，一共走了多少天",
            desc = "在一起、入职、搬进新家 —— 从起始日累计成「X 年 Y 个月 Z 天」。",
            color = XC.gold,
        ),
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(XC.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Dimen.PageH, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        // ── Logo ──
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(XC.goldHi, XC.gold, XC.goldDim))),
            contentAlignment = Alignment.Center,
        ) {
            Text("薪", color = XC.onGold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        Text("小薪薪", color = XC.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
        Spacer(Modifier.height(4.dp))
        Text("让每一秒都看得见价值", color = XC.textFaint, fontSize = 12.5.sp)
        Spacer(Modifier.height(26.dp))

        // ── 三个模块 ──
        modules.forEach { m ->
            GlassCard(Modifier.fillMaxWidth(), radius = 22) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(m.tag, color = m.color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(m.title, color = XC.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(m.desc, color = XC.textFaint, fontSize = 11.5.sp, lineHeight = 16.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.weight(1f))

        Text(
            "所有数据只存在你这台手机上，不联网、不上传。",
            color = XC.textFaint,
            fontSize = 10.5.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(XC.goldHi, XC.gold, XC.goldDim)))
                .liquidPress(
                    onClick = {
                        EarnRepo.completeOnboarding()
                        onDone()
                    },
                    shape = RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("开始使用", color = XC.onGold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
        }
    }
}

private data class OnboardModule(val tag: String, val title: String, val desc: String, val color: androidx.compose.ui.graphics.Color)
