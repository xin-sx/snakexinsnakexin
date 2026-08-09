package com.demo.snakexin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.demo.snakexin.databinding.ActivityDetailBinding

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var storage: EntryStorage
    private var entry: Entry? = null

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = EntryStorage(this)
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        entry = storage.loadAll().firstOrNull { it.id == id }

        binding.detailEditButton.setOnClickListener {
            entry?.let { startActivity(EditActivity.newIntent(this, it.id)) }
        }

        render()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun render() {
        val e = entry ?: run {
            binding.detailName.text = "（已删除）"
            binding.detailTime.text = ""
            binding.detailCurrentTime.text = ""
            binding.detailDiff.text = ""
            binding.detailTotalDays.text = ""
            binding.detailEditButton.isEnabled = false
            return
        }
        binding.detailName.text = e.name
        binding.detailTime.text = TimeUtils.formatDetailTime(e)
        binding.detailCurrentTime.text = "当前：" + TimeUtils.formatCurrentHms()
        val diff = TimeUtils.diffYearMonthDay(e)
        binding.detailDiff.text = "已过 ${diff.years} 年 ${diff.months} 月 ${diff.days} 天"
        binding.detailTotalDays.text = "合计 ${diff.totalDays} 天"
    }

    companion object {
        private const val EXTRA_ID = "extra_id"
        fun newIntent(ctx: Context, id: Long): Intent {
            return Intent(ctx, DetailActivity::class.java).apply {
                putExtra(EXTRA_ID, id)
            }
        }
    }
}
