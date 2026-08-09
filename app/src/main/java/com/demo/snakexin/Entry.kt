package com.demo.snakexin

import java.util.Calendar

/**
 * 资料条目
 *
 *  - [year]/[month]/[day] 总是有值（必填的日期）
 *  - [hasTime] 为 true 时 [hour]/[minute]/[second] 才有意义；
 *    为 false 时，主页与详情页都不显示时分秒。
 */
data class Entry(
    val id: Long,
    val name: String,
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val second: Int,
    val hasTime: Boolean
) {
    /** 把条目时间换算成 epoch millis；如果未选时分秒，则视为 00:00:00。 */
    fun toCalendar(): Calendar {
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(
            year,
            month - 1,
            day,
            if (hasTime) hour else 0,
            if (hasTime) minute else 0,
            if (hasTime) second else 0
        )
        cal.set(Calendar.MILLISECOND, 0)
        return cal
    }

    fun toTimeMillis(): Long = toCalendar().timeInMillis
}
