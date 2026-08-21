package com.demo.snakexin

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {

    private val ymdFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val ymdHmsFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val hmsFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateFormatNoSec = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /** 主页面顶部当前时间显示：年-月-日 时:分:秒 */
    fun formatNowFull(now: Date = Date()): String = ymdHmsFormat.format(now)

    /** 主页面资料框显示：选了时分秒就带，否则只显示日期 */
    fun formatEntryTime(entry: Entry): String {
        val cal = entry.toCalendar()
        return if (entry.hasTime) ymdHmsFormat.format(cal.time) else ymdFormat.format(cal.time)
    }

    /**
     * 模值差：每个字段是"从输入时间到现在已过去的余数"。
     * 例如 2001-11-09 → 2026-08-10 21:49:19 某时刻：
     *   years=24, months=8, days=10, hours=21, minutes=49, seconds=19
     *
     * 用于主页面卡片：拼成"24 年 8 个月零 10 天零 21 小时 19 秒"这种可读句式。
     */
    data class ModularDiff(
        val years: Int,
        val months: Int,
        val days: Int,
        val hours: Int,
        val minutes: Int,
        val seconds: Int,
        val totalMillis: Long
    )

    fun diffModular(entry: Entry, now: Date = Date()): ModularDiff {
        val start = entry.toCalendar()
        val end = Calendar.getInstance().apply {
            time = now
            set(Calendar.MILLISECOND, 0)
        }

        if (end.timeInMillis < start.timeInMillis) {
            return ModularDiff(0, 0, 0, 0, 0, 0, end.timeInMillis - start.timeInMillis)
        }

        var years = end.get(Calendar.YEAR) - start.get(Calendar.YEAR)
        var months = end.get(Calendar.MONTH) - start.get(Calendar.MONTH)
        var days = end.get(Calendar.DAY_OF_MONTH) - start.get(Calendar.DAY_OF_MONTH)
        var hours = end.get(Calendar.HOUR_OF_DAY) - start.get(Calendar.HOUR_OF_DAY)
        var minutes = end.get(Calendar.MINUTE) - start.get(Calendar.MINUTE)
        var seconds = end.get(Calendar.SECOND) - start.get(Calendar.SECOND)

        // 借位
        if (seconds < 0) { seconds += 60; minutes -= 1 }
        if (minutes < 0) { minutes += 60; hours -= 1 }
        if (hours < 0) { hours += 24; days -= 1 }
        if (days < 0) {
            val prevMonth = (end.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            days += prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
            months -= 1
        }
        if (months < 0) { months += 12; years -= 1 }
        if (years < 0) years = 0

        return ModularDiff(
            years = years,
            months = months,
            days = days,
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            totalMillis = end.timeInMillis - start.timeInMillis
        )
    }

    /**
     * 主页面卡片用的"至今有 …"句子。
     *
     * 例：至今有 24 年 7 个月零 5 天零 13 小时 52 秒
     *  - 第一个非零单位直接显示（不带"零"）
     *  - 后续非零单位用"零 X 天/小时"（零作"和"连接）
     *  - 秒永远在末尾（"52、53、54…" 秒级跳动）
     *  - 0 值的单位自动省略
     *  - 整体在未来时退化为"0 秒"
     */
    fun formatSentence(entry: Entry, now: Date = Date()): String {
        val m = diffModular(entry, now)
        if (m.totalMillis < 0) return "${entry.name} 至今有 0 秒"

        // 按粒度从大到小收集非零单位（标签，含零前缀的占位）
        val pieces = ArrayList<String>(5)
        if (m.years > 0) pieces += "${m.years} 年"
        if (m.months > 0) pieces += "${m.months} 个月"
        if (m.days > 0) pieces += "零 ${m.days} 天"
        if (m.hours > 0) pieces += "零 ${m.hours} 小时"
        // 秒永远在末尾，不加"零"
        val body = buildString {
            if (pieces.isEmpty()) {
                append("${m.seconds} 秒")
            } else {
                // 第一个非零单位去掉"零 "前缀（如果它原本就有）
                val first = pieces[0].removePrefix("零 ")
                append(first)
                for (i in 1 until pieces.size) append(' ').append(pieces[i])
                append(' ').append("${m.seconds} 秒")
            }
        }
        return "${entry.name} 至今有 $body"
    }

    /** 详情页顶部时间显示：选了就带时分秒，否则只显示日期 */
    fun formatDetailTime(entry: Entry): String = formatEntryTime(entry)

    /** 详情页"当前的时分秒"行 */
    fun formatCurrentHms(now: Date = Date()): String = hmsFormat.format(now)

    /**
     * 详情页"年/月/日 差"：
     *  - [entry] 是用户输入的时间；当前时间为 [now]。
     *  - 若未选时分秒，则当作 00:00:00 比较；保证结果始终稳定。
     */
    data class YearMonthDay(val years: Int, val months: Long, val days: Long, val totalDays: Long)

    fun diffYearMonthDay(entry: Entry, now: Date = Date()): YearMonthDay {
        val full = diffFull(entry, now)
        val totalMillis = full.totalMillis.coerceAtLeast(0L)
        val totalDays = totalMillis / (24L * 60 * 60 * 1000)
        return YearMonthDay(full.years, full.months, full.days, totalDays)
    }

    /**
     * 详情页完整差值：6 个框的累计值。
     *
     * 每个字段表示"从输入时间到现在累计经过了多少个该单位"：
     *  - [years]   累计年数（年是最粗的粒度，等价于模值）
     *  - [months]  累计月数  = years * 12 + modular months
     *  - [days]    累计天数  = totalMillis / 一天的毫秒数
     *  - [hours]   累计小时数 = totalMillis / 一小时的毫秒数
     *  - [minutes] 累计分钟数 = totalMillis / 一分钟的毫秒数
     *  - [seconds] 累计秒数  = totalMillis / 1000
     *
     * 例如 2001-11-09 → 2026-08-10 21:49:19：
     *   24 年 / 297 月 / 9040 日 / 216981 时 / 13018909 分 / 781134559 秒
     *
     * 若输入时间在未来（now 早于 entry），全部返回 0。
     */
    data class FullDiff(
        val years: Int,
        val months: Long,
        val days: Long,
        val hours: Long,
        val minutes: Long,
        val seconds: Long,
        val totalMillis: Long
    )

    fun diffFull(entry: Entry, now: Date = Date()): FullDiff {
        val start = entry.toCalendar()
        val end = Calendar.getInstance().apply {
            time = now
            set(Calendar.MILLISECOND, 0)
        }

        // 整体在未来，直接返回 0
        if (end.timeInMillis < start.timeInMillis) {
            return FullDiff(0, 0L, 0L, 0L, 0L, 0L, end.timeInMillis - start.timeInMillis)
        }

        // 先按字段相减得到模值（用于计算"年"和"月"两个粗粒度框）
        var years = end.get(Calendar.YEAR) - start.get(Calendar.YEAR)
        var months = end.get(Calendar.MONTH) - start.get(Calendar.MONTH)
        var days = end.get(Calendar.DAY_OF_MONTH) - start.get(Calendar.DAY_OF_MONTH)
        var hours = end.get(Calendar.HOUR_OF_DAY) - start.get(Calendar.HOUR_OF_DAY)
        var minutes = end.get(Calendar.MINUTE) - start.get(Calendar.MINUTE)
        var seconds = end.get(Calendar.SECOND) - start.get(Calendar.SECOND)

        // 从最小单位开始向上借位
        if (seconds < 0) {
            seconds += 60
            minutes -= 1
        }
        if (minutes < 0) {
            minutes += 60
            hours -= 1
        }
        if (hours < 0) {
            hours += 24
            days -= 1
        }
        if (days < 0) {
            val prevMonth = (end.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            days += prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
            months -= 1
        }
        if (months < 0) {
            months += 12
            years -= 1
        }
        if (years < 0) {
            return FullDiff(0, 0L, 0L, 0L, 0L, 0L, end.timeInMillis - start.timeInMillis)
        }

        // 累计值：每个框显示该单位的总量
        val totalMillis = end.timeInMillis - start.timeInMillis
        val totalSeconds = totalMillis / 1000L
        val totalMinutes = totalSeconds / 60L
        val totalHours = totalMinutes / 60L
        val totalDays = totalHours / 24L
        val totalMonths = years.toLong() * 12L + months.toLong()

        return FullDiff(
            years = years,
            months = totalMonths,
            days = totalDays,
            hours = totalHours,
            minutes = totalMinutes,
            seconds = totalSeconds,
            totalMillis = totalMillis
        )
    }
}
