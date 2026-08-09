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

    /** 详情页顶部时间显示：选了就带时分秒，否则只显示日期 */
    fun formatDetailTime(entry: Entry): String = formatEntryTime(entry)

    /** 详情页"当前的时分秒"行 */
    fun formatCurrentHms(now: Date = Date()): String = hmsFormat.format(now)

    /**
     * 详情页"年/月/日 差"：
     *  - [entry] 是用户输入的时间；当前时间为 [now]。
     *  - 若未选时分秒，则当作 00:00:00 比较；保证结果始终稳定。
     */
    data class YearMonthDay(val years: Int, val months: Int, val days: Int, val totalDays: Long)

    fun diffYearMonthDay(entry: Entry, now: Date = Date()): YearMonthDay {
        val full = diffFull(entry, now)
        val totalMillis = full.totalMillis.coerceAtLeast(0L)
        val totalDays = totalMillis / (24L * 60 * 60 * 1000)
        return YearMonthDay(full.years, full.months, full.days, totalDays)
    }

    /**
     * 详情页完整差值：年、月、日、时、分、秒。
     *
     * 采用"逐级借位"的日历算法：先按字段相减，再从最小单位（秒）开始向上借位，
     * 保证结果在每秒钟刷新时都能稳定地 +1s 递增，不会出现跳变。
     *
     * 若输入时间在未来（now 早于 entry），全部返回 0。
     */
    data class FullDiff(
        val years: Int,
        val months: Int,
        val days: Int,
        val hours: Int,
        val minutes: Int,
        val seconds: Int,
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
            return FullDiff(0, 0, 0, 0, 0, 0, end.timeInMillis - start.timeInMillis)
        }

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
            // 借上一个月的天数
            val prevMonth = (end.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            days += prevMonth.getActualMaximum(Calendar.DAY_OF_MONTH)
            months -= 1
        }
        if (months < 0) {
            months += 12
            years -= 1
        }
        if (years < 0) {
            return FullDiff(0, 0, 0, 0, 0, 0, end.timeInMillis - start.timeInMillis)
        }

        val totalMillis = end.timeInMillis - start.timeInMillis
        return FullDiff(years, months, days, hours, minutes, seconds, totalMillis)
    }
}
