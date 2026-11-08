package com.haofan.xiaoxinxin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * 4 位密码弹窗。隐私模式解锁 / 开启隐私都走它 —— 和 macOS 的 requirePIN 行为一致：
 * 开启时必须设密码，解锁时校验，输错留在原地提示。
 */
@Composable
fun PinDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    verify: (String) -> Boolean = { true },
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(XC.bg)
                .padding(20.dp),
        ) {
            Column {
                Text(title, color = XC.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(message, color = XC.textFaint, fontSize = 11.5.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(14.dp))

                Box(Modifier.fillMaxWidth().height(48.dp)) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        (0 until 4).forEach { i ->
                            val filled = pin.length > i
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(XC.fill),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (filled) "●" else "", color = XC.gold, fontSize = 18.sp)
                            }
                        }
                    }
                    // 真正接收输入的透明输入框：盖在四格上，点哪都能唤起键盘
                    BasicTextField(
                        value = pin,
                        onValueChange = {
                            pin = it.filter { c -> c.isDigit() }.take(4)
                            error = ""
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                        cursorBrush = SolidColor(Color.Transparent),
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }

                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(error, color = XC.danger, fontSize = 11.sp)
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.verticalGradient(listOf(XC.ghostA, XC.ghostB)))
                            .liquidPress(onClick = onDismiss, shape = RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("取消", color = XC.textDim, fontSize = 13.sp)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.verticalGradient(listOf(XC.goldHi, XC.gold, XC.goldDim)))
                            .liquidPress(
                                onClick = {
                                    if (pin.length != 4) {
                                        error = "请输入 4 位数字"
                                    } else if (!verify(pin)) {
                                        error = "密码不对，再试一次"
                                        pin = ""
                                    } else {
                                        onConfirm(pin)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(confirmText, color = XC.onGold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** 通用确认弹窗（删除事件之类） */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "删除",
    danger: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(XC.bg)
                .padding(20.dp),
        ) {
            Column {
                Text(title, color = XC.text, fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(message, color = XC.textFaint, fontSize = 11.5.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.verticalGradient(listOf(XC.ghostA, XC.ghostB)))
                            .liquidPress(onClick = onDismiss, shape = RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("取消", color = XC.textDim, fontSize = 13.sp)
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (danger) Brush.verticalGradient(listOf(XC.danger, XC.danger.copy(alpha = 0.82f)))
                                else Brush.verticalGradient(listOf(XC.goldHi, XC.gold))
                            )
                            .liquidPress(onClick = onConfirm, shape = RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(confirmText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** 一行错误 / 成功提示 */
@Composable
fun FormHint(text: String, ok: Boolean = false) {
    Text(
        text,
        color = if (ok) XC.teal else XC.danger,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start,
    )
}
