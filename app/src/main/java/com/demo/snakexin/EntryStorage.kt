package com.demo.snakexin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 轻量级持久化。把 [Entry] 列表以 JSON 数组的形式存入 SharedPreferences。
 */
class EntryStorage(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadAll(): MutableList<Entry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<Entry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Entry(
                        id = o.optLong("id", System.currentTimeMillis()),
                        name = o.optString("name", ""),
                        year = o.optInt("year"),
                        month = o.optInt("month"),
                        day = o.optInt("day"),
                        hour = o.optInt("hour"),
                        minute = o.optInt("minute"),
                        second = o.optInt("second"),
                        hasTime = o.optBoolean("hasTime", false)
                    )
                )
            }
            list
        } catch (t: Throwable) {
            mutableListOf()
        }
    }

    fun saveAll(list: List<Entry>) {
        val arr = JSONArray()
        for (e in list) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("name", e.name)
            o.put("year", e.year)
            o.put("month", e.month)
            o.put("day", e.day)
            o.put("hour", e.hour)
            o.put("minute", e.minute)
            o.put("second", e.second)
            o.put("hasTime", e.hasTime)
            arr.put(o)
        }
        prefs.edit().putString(KEY_ENTRIES, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "time_tracker_prefs"
        private const val KEY_ENTRIES = "entries"
    }
}
