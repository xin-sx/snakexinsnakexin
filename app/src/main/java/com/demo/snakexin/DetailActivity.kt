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

        render()
    }

    override fun onResume() {
        super.onResume()
        // 重新读取最新数据（用户可能从编辑页返回）
        entry?.let { e ->
            entry = storage.loadAll().firstOrNull { it.id == e.id }
        }
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
            binding.boxYears.text = "0"
            binding.boxMonths.text = "0"
            binding.boxDays.text = "0"
            binding.boxHours.text = "0"
            binding.boxMinutes.text = "0"
            binding.boxSeconds.text = "0"
            binding.detailSentence.text = ""
            return
        }
        binding.detailName.text = e.name
        binding.detailTime.text = TimeUtils.formatDetailTime(e)
        binding.detailCurrentTime.text = "当前：" + TimeUtils.formatCurrentHms()

        // 完整差值：累计的年、月、日、时、分、秒
        val diff = TimeUtils.diffFull(e)
        binding.boxYears.text = diff.years.toString()
        binding.boxMonths.text = diff.months.toString()
        binding.boxDays.text = diff.days.toString()
        binding.boxHours.text = diff.hours.toString()
        binding.boxMinutes.text = diff.minutes.toString()
        binding.boxSeconds.text = diff.seconds.toString()

        // 完整句子：某某某 至今有 X 年 X 月 X 日 X 时 X 分 X 秒
        binding.detailSentence.text =
            "${e.name} 至今有 ${diff.years} 年 ${diff.months} 月 ${diff.days} 日 " +
                    "${diff.hours} 时 ${diff.minutes} 分 ${diff.seconds} 秒"
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
