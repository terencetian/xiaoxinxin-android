package com.haofan.xiaoxinxin.core

// 三个二级页的信息模块（macOS 叫 chip），显隐互不影响，设置页逐项开关。

/** 薪酬页 8 个模块 */
enum class MoneyChip(val key: String, val title: String, val detail: String) {
    SALARY("salary", "月薪", "每月税后工资（隐私模式下显示 --）"),
    WORKDAYS("workdays", "工作日/月", "按自定义周几 + 法定节假日实时算"),
    DAY_RATE("dayRate", "日薪", "月薪 ÷ 当月工作日"),
    HOUR_RATE("hourRate", "时薪", "日薪 ÷ 每日计划工时"),
    PER_SEC("perSec", "每秒进账", "时薪 ÷ 3600"),
    WORKED("worked", "今日已工作", "今日已工作时长 / 隐私模式改显示今日进度"),
    MONTH_EARNED("monthEarned", "本月累计进账", "本月累计 / 隐私模式改显示月度进度"),
    MONTH_OT("monthOT", "本月加班时长", "本月加班 / 隐私模式改显示超出 100% 部分"),
    ;

    companion object {
        fun parse(hidden: Set<String>): List<MoneyChip> = entries.filter { it.key !in hidden }
    }
}

/** 倒计时页 6 个模块 */
enum class CountdownChip(val key: String, val title: String, val detail: String) {
    ELAPSED("elapsed", "累计天数", "从起点到今天已走过的自然日"),
    REMAINING("remaining", "剩余天数", "距目标日还剩几天，到期后显示「已到期」"),
    TARGET("target", "目标日期", "下一个目标日（每年重复会自动顺延）"),
    PERCENT("percent", "完成进度", "已走天数 ÷ 总跨度"),
    SPAN("span", "总跨度", "起点到目标日的总天数"),
    NAME("name", "倒计时名称", "名称与重复方式（每年 / 仅一次）"),
    ;

    companion object {
        fun parse(hidden: Set<String>): List<CountdownChip> = entries.filter { it.key !in hidden }
    }
}

/** 纪念日页 6 个模块（2.4 起围绕「累计时长」给信息，不再走里程碑） */
enum class RecordDayChip(val key: String, val title: String, val detail: String) {
    ELAPSED("elapsed", "累计天数", "从起始日到今天一共多少天"),
    DURATION("duration", "累计时长", "拆成「X 年 Y 个月 Z 天」的累计时长"),
    START("start", "起始日期", "纪念日从哪一天开始算"),
    MONTHS("months", "累计月数", "从起始日到今天累计几个月"),
    YEARS("years", "累计年数", "从起始日到今天累计几年"),
    NAME("name", "纪念日名称", "名称与显示方式"),
    ;

    companion object {
        fun parse(hidden: Set<String>): List<RecordDayChip> = entries.filter { it.key !in hidden }
    }
}

/** 首页三张进度卡 */
enum class HomeCard(val key: String, val title: String, val detail: String) {
    MONEY("money", "Your Money · 薪酬", "薪酬/工时进度、当日与本月摘要，点击进入薪酬详情页"),
    COUNTDOWN("countdown", "Your Days · 倒计时", "目标日剩余天数或完成百分比，点击进入倒计时详情页"),
    RECORD_DAY("recordDay", "Your Days · 纪念日", "从纪念日起累计的天数，点击进入纪念日详情页"),
    ;

    val mode: DetailMode
        get() = when (this) {
            MONEY -> DetailMode.SALARY
            COUNTDOWN -> DetailMode.COUNTDOWN
            RECORD_DAY -> DetailMode.RECORD_DAY
        }

    companion object {
        fun parse(hidden: Set<String>): List<HomeCard> {
            val shown = entries.filter { it.key !in hidden }
            return shown.ifEmpty { listOf(MONEY) }
        }
    }
}

/** 首页 / 二级页上可显隐的小按钮 */
enum class HomeButton(val key: String, val title: String, val detail: String) {
    CLOCK_IN("clockIn", "打卡上班", "隐藏后仍可从二级页打卡，不会丢入口"),
    CLOCK_OUT("clockOut", "打卡下班", "隐藏后仍可从二级页打卡，不会丢入口"),
    OVERTIME("overtime", "加班打卡", "只在 Your Money 薪酬页出现；首页与 Your Days 两页不显示"),
    FLOAT("float", "悬浮岛开关", "首页顶栏那颗「浮」字按钮"),
    LEDGER("ledger", "流水入口", "薪酬页底部的流水明细入口"),
    METRIC("metric", "切换统计内容", "薪酬页顶部的口径切换"),
    ;
}
