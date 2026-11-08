package com.haofan.xiaoxinxin.core

/**
 * 悬浮岛 / 通知等「系统层部件」用的颜色包。
 *
 * 全部是 ARGB int —— 部件层（FloatService / FloatingView / Notification）不走 Compose，
 * 也不能 import ui 包，所以由 ui 层在主题变化时把 Palette 换算成这一份推过来。
 *
 * 主题、自定义背景 / 文字 / 重点色、背景透明度，全部反映在这几个值里：
 * 部件配色因此和 App 内完全同源，换主题立刻跟着变。
 */
data class WidgetColors(
    /** 主题指纹（与 Palette.sig 同源），用来判断「配色是否真的变了」。 */
    val sig: String,
    /** 悬浮岛底色。可含 alpha：Liquid Glass 的背景透明度在这里生效。 */
    val bg: Int,
    /** 外描边。 */
    val edge: Int,
    /** 内侧一圈高光。 */
    val edgeInner: Int,
    /** 重点色：金额、呼吸点、进度条填充。 */
    val accent: Int,
    /** 主文字。 */
    val text: Int,
    /** 次级文字（副行）。 */
    val textDim: Int,
    /** 弱文字（说明行）。 */
    val textFaint: Int,
    /** 进度条轨道。 */
    val track: Int,
) {
    companion object {
        /**
         * 兜底色：进程刚起、ui 层还没来得及推主题时的第一帧。
         * 只求不闪花，正常情况下 FloatService onCreate 会立刻用 EarnState 覆盖。
         */
        val FALLBACK = WidgetColors(
            sig = "fallback",
            bg = 0xFF000000.toInt(),
            edge = 0x33FFFFFF,
            edgeInner = 0x14FFFFFF,
            accent = 0xFF2985E6.toInt(),
            text = 0xFFF2F5FA.toInt(),
            textDim = 0x99F2F5FA.toInt(),
            textFaint = 0x66F2F5FA.toInt(),
            track = 0x26FFFFFF,
        )
    }
}

/** 部件层的颜色单例。写入只来自 ui.widgetColorsOf()，读取各部件自己来。 */
object WidgetTheme {
    @Volatile
    var current: WidgetColors = WidgetColors.FALLBACK
        private set

    fun apply(c: WidgetColors) {
        current = c
    }
}
