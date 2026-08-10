package com.demo.snakexin

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.demo.snakexin.databinding.ActivityEditBinding
import java.util.Calendar

class EditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditBinding
    private lateinit var storage: EntryStorage
    private var existing: Entry? = null

    private var pickedYear: Int = 0
    private var pickedMonth: Int = 0 // 1-12
    private var pickedDay: Int = 0
    private var pickedHour: Int = 0
    private var pickedMinute: Int = 0
    private var pickedSecond: Int = 0
    private var hasTime: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = EntryStorage(this)

        val editId = intent.getLongExtra(EXTRA_ID, -1L).takeIf { it > 0 }
        if (editId != null) {
            existing = storage.loadAll().firstOrNull { it.id == editId }
            binding.deleteButton.visibility = android.view.View.VISIBLE
        }

        existing?.let { e ->
            binding.nameInput.setText(e.name)
            pickedYear = e.year
            pickedMonth = e.month
            pickedDay = e.day
            hasTime = e.hasTime
            pickedHour = e.hour
            pickedMinute = e.minute
            pickedSecond = e.second
        } ?: run {
            // 默认日期 = 今天
            val now = Calendar.getInstance()
            pickedYear = now.get(Calendar.YEAR)
            pickedMonth = now.get(Calendar.MONTH) + 1
            pickedDay = now.get(Calendar.DAY_OF_MONTH)
        }

        renderDate()
        renderTime()

        binding.dateButton.setOnClickListener { showDatePicker() }
        binding.timeButton.setOnClickListener { showTimePicker() }
        binding.clearTimeButton.setOnClickListener {
            hasTime = false
            pickedHour = 0; pickedMinute = 0; pickedSecond = 0
            renderTime()
        }

        binding.saveButton.setOnClickListener { onSave() }
        binding.deleteButton.setOnClickListener { confirmDelete() }
    }

    private fun renderDate() {
        binding.dateButtonText.text = String.format(
            java.util.Locale.getDefault(),
            "%04d-%02d-%02d",
            pickedYear, pickedMonth, pickedDay
        )
        // 已选日期：文字变为黑色（实心）
        binding.dateButtonText.setTextColor(0xFF000000.toInt())
    }

    private fun renderTime() {
        if (hasTime) {
            binding.timeButtonText.text = String.format(
                java.util.Locale.getDefault(),
                "%02d:%02d:%02d",
                pickedHour, pickedMinute, pickedSecond
            )
            binding.timeButtonText.setTextColor(0xFF000000.toInt())
        } else {
            binding.timeButtonText.setText(R.string.no_time_selected)
            binding.timeButtonText.setTextColor(0xFF888888.toInt())
        }
    }

    private fun showDatePicker() {
        val dlg = DatePickerDialog(
            this,
            { _, y, m, d ->
                pickedYear = y
                pickedMonth = m + 1
                pickedDay = d
                renderDate()
            },
            pickedYear,
            pickedMonth - 1,
            pickedDay
        )
        dlg.show()
    }

    private fun showTimePicker() {
        val startH = if (hasTime) pickedHour else 0
        val startM = if (hasTime) pickedMinute else 0
        val wasTime = hasTime
        TimePickerDialog(
            this,
            { _, h, m ->
                hasTime = true
                pickedHour = h
                pickedMinute = m
                // 如果之前没选过时分秒，则秒默认为 0；否则保留旧秒
                if (!wasTime) pickedSecond = 0
                renderTime()
                // 再弹一个"秒"选择 - 用简单对话框避免引入 TimePickerDialog 秒字段
                askSecond()
            },
            startH,
            startM,
            true
        ).show()
    }

    private fun askSecond() {
        val options = (0..59).map { String.format(java.util.Locale.US, "%02d", it) }
        AlertDialog.Builder(this)
            .setTitle("选择秒（可选）")
            .setItems(options.toTypedArray()) { _, which ->
                pickedSecond = which
                renderTime()
            }
            .setNegativeButton("保持 00 秒") { _, _ ->
                pickedSecond = 0
                renderTime()
            }
            .show()
    }

    private fun onSave() {
        val name = binding.nameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.name_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (pickedYear <= 0 || pickedMonth <= 0 || pickedDay <= 0) {
            Toast.makeText(this, R.string.date_required, Toast.LENGTH_SHORT).show()
            return
        }

        val list = storage.loadAll()
        val current = existing
        val newEntry = Entry(
            id = current?.id ?: System.currentTimeMillis(),
            name = name,
            year = pickedYear,
            month = pickedMonth,
            day = pickedDay,
            hour = pickedHour,
            minute = pickedMinute,
            second = pickedSecond,
            hasTime = hasTime
        )
        if (current != null) {
            val idx = list.indexOfFirst { it.id == current.id }
            if (idx >= 0) list[idx] = newEntry else list.add(newEntry)
        } else {
            list.add(newEntry)
        }
        storage.saveAll(list)
        finish()
    }

    private fun confirmDelete() {
        val current = existing ?: run { finish(); return }
        AlertDialog.Builder(this)
            .setTitle("删除")
            .setMessage("确定要删除『${current.name}』？")
            .setPositiveButton(R.string.action_delete) { _, _ ->
                val list = storage.loadAll().filter { it.id != current.id }
                storage.saveAll(list)
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    companion object {
        private const val EXTRA_ID = "extra_id"
        fun newIntent(ctx: Context, id: Long?): Intent {
            return Intent(ctx, EditActivity::class.java).apply {
                if (id != null) putExtra(EXTRA_ID, id)
            }
        }
    }
}
