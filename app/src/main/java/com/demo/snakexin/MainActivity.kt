package com.demo.snakexin

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.demo.snakexin.databinding.ActivityMainBinding
import com.demo.snakexin.databinding.ItemEntryBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var storage: EntryStorage
    private val entries: MutableList<Entry> = mutableListOf()
    private lateinit var adapter: EntryAdapter

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            updateCurrentTime()
            // 每秒刷新列表卡片中的完整句子（秒数在变）
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = EntryStorage(this)
        entries.addAll(storage.loadAll())

        adapter = EntryAdapter(
            entries,
            onClick = { entry ->
                startActivity(DetailActivity.newIntent(this, entry.id))
            },
            onLongClick = { entry ->
                startActivity(EditActivity.newIntent(this, entry.id))
                true
            }
        )
        binding.entriesList.layoutManager = LinearLayoutManager(this)
        binding.entriesList.adapter = adapter

        binding.addButton.setOnClickListener {
            startActivity(EditActivity.newIntent(this, null))
        }

        updateCurrentTime()
        updateEntryCount()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // onResume will refresh
    }

    private fun updateCurrentTime() {
        binding.currentTimeView.text = TimeUtils.formatNowFull()
    }

    private fun updateEntryCount() {
        binding.entryCountView.text = getString(R.string.entry_count, entries.size)
    }

    /** 读取最新数据并刷新列表 */
    private fun refreshEntries() {
        entries.clear()
        entries.addAll(storage.loadAll())
        adapter.notifyDataSetChanged()
        updateEntryCount()
    }

    private inner class EntryAdapter(
        private val items: List<Entry>,
        private val onClick: (Entry) -> Unit,
        private val onLongClick: (Entry) -> Boolean
    ) : RecyclerView.Adapter<EntryAdapter.VH>() {

        inner class VH(val b: ItemEntryBinding) : RecyclerView.ViewHolder(b.root) {
            init {
                b.root.setOnLongClickListener {
                    val pos = bindingAdapterPosition
                    if (pos != RecyclerView.NO_POSITION) onLongClick(items[pos]) else false
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemEntryBinding.inflate(layoutInflater, parent, false)
            return VH(b)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val e = items[position]
            holder.b.itemName.text = if (e.name.isBlank()) "（未命名）" else e.name
            holder.b.itemTime.text = TimeUtils.formatEntryTime(e)

            // 完整句子：某某某 至今有 X 年 X 个月零 X 天零 X 小时 X 秒（模值，跳过分）
            holder.b.itemSentence.text = TimeUtils.formatSentence(e)

            holder.b.root.setOnClickListener { onClick(e) }
        }

        override fun getItemCount(): Int = items.size
    }

    /** 进入 / 返回时刷新 */
    override fun onStart() {
        super.onStart()
        refreshEntries()
    }
}
