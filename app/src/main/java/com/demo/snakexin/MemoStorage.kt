package com.demo.snakexin

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 备注的持久化。媒体文件保存在 filesDir/memos/<entryId>/<file> 目录，
 * 列表元数据（id / type / text / mediaPath / createdAt）保存在 SharedPreferences 里。
 */
class MemoStorage(context: Context) {

    private val appCtx = context.applicationContext
    private val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 媒体文件根目录。 */
    fun memoDir(entryId: Long): File {
        val d = File(appCtx.filesDir, "memos/$entryId")
        if (!d.exists()) d.mkdirs()
        return d
    }

    /**
     * 把备忘里存的相对路径 [Memo.mediaPath] 拼成绝对文件。
     * 如果 [mediaPath] 为空，或 [entryId] 为空，返回 null。
     */
    fun fileFor(entryId: Long, memo: Memo): File? {
        val rel = memo.mediaPath ?: return null
        return File(memoDir(entryId), rel)
    }

    fun loadMemos(entryId: Long): MutableList<Memo> {
        val raw = prefs.getString(keyFor(entryId), null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<Memo>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val typeName = o.optString("type", "TEXT")
                val type = runCatching { Memo.Type.valueOf(typeName) }.getOrDefault(Memo.Type.TEXT)
                list.add(
                    Memo(
                        id = o.optLong("id"),
                        type = type,
                        text = if (o.isNull("text")) null else o.optString("text"),
                        mediaPath = if (o.isNull("mediaPath")) null else o.optString("mediaPath"),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (t: Throwable) {
            mutableListOf()
        }
    }

    fun saveMemos(entryId: Long, list: List<Memo>) {
        val arr = JSONArray()
        for (m in list) {
            val o = JSONObject()
            o.put("id", m.id)
            o.put("type", m.type.name)
            o.put("text", m.text ?: JSONObject.NULL)
            o.put("mediaPath", m.mediaPath ?: JSONObject.NULL)
            o.put("createdAt", m.createdAt)
            arr.put(o)
        }
        prefs.edit().putString(keyFor(entryId), arr.toString()).apply()
    }

    /** 删除该条目下的所有备注及其媒体文件。 */
    fun deleteAllForEntry(entryId: Long) {
        val dir = File(appCtx.filesDir, "memos/$entryId")
        if (dir.exists()) dir.deleteRecursively()
        prefs.edit().remove(keyFor(entryId)).apply()
    }

    /** 删除单条备注（文字直接清，媒体同时删文件）。 */
    fun deleteMemo(entryId: Long, memo: Memo) {
        val list = loadMemos(entryId)
        list.removeAll { it.id == memo.id }
        saveMemos(entryId, list)
        memo.mediaPath?.let { rel ->
            val f = File(memoDir(entryId), rel)
            if (f.exists()) f.delete()
        }
    }

    private fun keyFor(entryId: Long) = "memos_$entryId"

    companion object {
        private const val PREFS_NAME = "time_tracker_memos"
    }
}
