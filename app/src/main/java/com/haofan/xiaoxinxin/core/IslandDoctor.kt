package com.haofan.xiaoxinxin.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * 「系统岛」能力体检。
 *
 * 同一份代码在不同厂商机器上结果完全不同，靠猜没意义 —— 直接把结果读出来，
 * 让用户截图/复制回传，就能区分「App 没设对」还是「系统不放行」。
 */
data class IslandReport(
    val sdk: Int,
    val rom: String,
    val model: String,
    val canPromote: Boolean,      // 系统是否放行本应用
    val promotable: Boolean,      // 通知本身是否满足提升条件
    val promoted: Boolean,        // 实际发出去后有没有真的变成岛
    val notifEnabled: Boolean,
    val overlayGranted: Boolean,
    val verdict: String,
    val rows: List<Pair<String, String>>,
) {
    fun asText(): String = buildString {
        appendLine("【分刻 · 系统岛体检】")
        rows.forEach { (k, v) -> appendLine("$k：$v") }
        appendLine("结论：$verdict")
    }
}

object IslandDoctor {

    private const val CHANNEL = "island_probe"
    private const val TEST_ID = 9001

    /**
     * 各厂商对「标准实时通知」的放行情况。
     * 小米 / 荣耀 / vivo 三条是 2026-11-05 真机实测，其余来自厂商公开文档。
     */
    private fun brandOf(): String {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> "xiaomi"
            m.contains("honor") -> "honor"
            m.contains("vivo") || m.contains("iqoo") -> "vivo"
            m.contains("oppo") || m.contains("oneplus") || m.contains("realme") -> "oppo"
            m.contains("huawei") -> "huawei"
            m.contains("samsung") -> "samsung"
            else -> "other"
        }
    }

    private fun brandNote(): String = when (brandOf()) {        "xiaomi" -> "小米系：HyperOS 3 / Android 16 起走标准 API，实测可用"
        "honor" -> "荣耀：灵动胶囊是自研通道，需白名单，实测标准 API 不生效"
        "vivo" -> "vivo/iQOO：原子岛自研协议，个人开发者无接入路径"
        "oppo" -> "OPPO 系：ColorOS 16 起宣称兼容标准实时通知，待实测"
        "huawei" -> "华为：新机为 HarmonyOS NEXT，不装 APK，需另做 ArkTS 版"
        "samsung" -> "三星：One UI 8 起支持标准实时通知"
        else -> "其他品牌：按标准 API 行为，以实测结果为准"
    }

    /** 给设置页用的一句话说明：这台机器为什么用不了/能用系统岛。 */
    fun brandHint(): String = brandNote()

    /** 系统岛在当前机器上是否可能有戏（版本 + 厂商放行，未发起探测时为乐观值）。 */
    fun platformCapable(): Boolean = Build.VERSION.SDK_INT >= 36

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL, "系统岛体检", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "仅用于检测系统是否支持实时更新通知"
                setShowBadge(false)
            }
        )
    }

    fun clearTest(ctx: Context) {
        runCatching { ctx.getSystemService(NotificationManager::class.java).cancel(TEST_ID) }
    }

    fun probe(ctx: Context, postTest: Boolean = true): IslandReport {
        val sdk = Build.VERSION.SDK_INT
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val canPromote = LiveUpdate.allowed(ctx)
        val notifEnabled = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        val overlay = Settings.canDrawOverlays(ctx)

        var promotable = false
        var promoted = false
        var testNote = "未执行"

        if (sdk < 36) {
            testNote = "系统低于 Android 16，无此能力"
        } else if (!postTest) {
            testNote = "未执行"
        } else {
            ensureChannel(ctx)
            val n = LiveUpdate.build(
                ctx, CHANNEL,
                "分刻 · 岛体检",
                "今日已到账 ¥52.77",
                "¥52.77",
                32, null,
            )
            promotable = runCatching { n.hasPromotableCharacteristics() }.getOrDefault(false)
            val ok = runCatching { nm.notify(TEST_ID, n) }.isSuccess
            if (!ok) {
                testNote = "通知发不出去（缺通知权限）"
            } else {
                Thread.sleep(700)
                val snb = runCatching { nm.activeNotifications }
                    .getOrNull()?.firstOrNull { it.id == TEST_ID }
                promoted = snb?.let {
                    it.notification.flags and Notification.FLAG_PROMOTED_ONGOING != 0
                } ?: false
                testNote = if (promoted) "已被提升为系统岛" else "发出去了，但没被提升"
            }
        }

        val verdict = when {
            sdk < 36 -> when (brandOf()) {
                "vivo", "honor" -> "这台机器只可能用悬浮岛：系统版本不够且厂商通道不对第三方开放"
                else -> "系统版本不够，只能靠悬浮岛"
            }
            !notifEnabled -> "通知总开关关着，先打开通知"
            !canPromote && brandOf() in listOf("vivo", "honor") ->
                "${Build.MANUFACTURER} 未放行标准实时通知，这是厂商策略，改设置也打不开；悬浮岛是长久方案"
            !canPromote -> "系统未放行本应用：必须走厂商白名单/合作，悬浮岛兜底"
            promoted -> "标准 API 已生效，系统岛上能看到"
            !promotable -> "通知形态不满足提升条件（被系统改写过？）"
            else -> "API 放行但没提升，多试几次或在系统通知设置里打开「实时通知」"
        }

        val rows = listOf(
            "机型" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "系统" to "${Build.VERSION.RELEASE}（API $sdk）· ${Build.DISPLAY}",
            "厂商通道" to brandNote(),
            "实时通知权限" to if (sdk >= 36) (if (canPromote) "已放行" else "未放行") else "系统不支持",
            "能否提升" to if (sdk >= 36) (if (promotable) "满足条件" else "不满足") else "—",
            "实测结果" to testNote,
            "通知总开关" to if (notifEnabled) "开" else "关",
            "悬浮窗权限" to if (overlay) "已授予" else "未授予",
        )

        return IslandReport(
            sdk = sdk,
            rom = Build.DISPLAY ?: "",
            model = "${Build.MANUFACTURER} ${Build.MODEL}",
            canPromote = canPromote,
            promotable = promotable,
            promoted = promoted,
            notifEnabled = notifEnabled,
            overlayGranted = overlay,
            verdict = verdict,
            rows = rows,
        )
    }
}
