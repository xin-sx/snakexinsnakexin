package com.demo.snakexin

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import coil.load
import coil.size.Size
import com.demo.snakexin.databinding.ActivityDetailBinding
import java.io.File

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private lateinit var storage: EntryStorage
    private lateinit var memoStorage: MemoStorage
    private var entry: Entry? = null

    private lateinit var memoAdapter: MemoAdapter
    private val memos: MutableList<Memo> = mutableListOf()

    // 录音 / 录像
    private var recorder: MediaRecorder? = null
    private var recorderFile: File? = null
    private var recorderIsVideo: Boolean = false
    private var recordingDialog: AlertDialog? = null

    // 拍照
    private var pendingPhotoFile: File? = null

    // 从存储选择媒体（照片 / 视频 / 音频）
    private var pendingPickType: Memo.Type? = null

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            render()
            handler.postDelayed(this, 1000L)
        }
    }

    /* ------------ 权限申请 ------------ */

    private var pendingAction: (() -> Unit)? = null

    private val requestRecordAudioPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> handlePermResult(granted) }

    private val requestCameraPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> handlePermResult(granted) }

    private val requestVideoPerms = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.RECORD_AUDIO] == true &&
            granted[Manifest.permission.CAMERA] == true
        handlePermResult(ok)
    }

    private fun handlePermResult(granted: Boolean) {
        val a = pendingAction
        pendingAction = null
        if (granted) {
            a?.invoke()
        } else {
            Toast.makeText(this, R.string.memo_permission_need, Toast.LENGTH_SHORT).show()
        }
    }

    /* ------------ 拍照回调 ------------ */

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val file = pendingPhotoFile
        pendingPhotoFile = null
        if (result.resultCode == Activity.RESULT_OK && file != null && file.exists() && file.length() > 0L) {
            saveMediaMemo(Memo.Type.PHOTO, file)
        } else {
            // 用户取消，清理空文件
            file?.delete()
        }
    }

    /* ------------ 从存储选媒体回调 ------------ */

    // 照片 / 视频：走 Android 13+ 的 Photo Picker（旧系统自动 fallback 到 OpenDocument）
    private val pickVisualMedia = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> handlePickedUri(uri) }

    // 音频：Photo Picker 不支持音频，用 OpenDocument
    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> handlePickedUri(uri) }

    /* ------------ Activity 生命周期 ------------ */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        storage = EntryStorage(this)
        memoStorage = MemoStorage(this)
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        entry = storage.loadAll().firstOrNull { it.id == id }

        setupMemoList()
        render()
    }

    override fun onResume() {
        super.onResume()
        // 重新读取最新数据（用户可能从编辑页返回）
        entry?.let { e ->
            entry = storage.loadAll().firstOrNull { it.id == e.id }
        }
        reloadMemos()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放录音 / 播放
        stopRecorder(deleteFile = true)
        memoAdapter.release()
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
    }

    /* ------------ 备注列表 ------------ */

    private fun setupMemoList() {
        val e = entry ?: return
        memoAdapter = MemoAdapter(
            storage = memoStorage,
            entryId = e.id,
            items = memos,
            onDelete = { memo -> confirmDelete(memo) },
            onPhotoClick = { memo -> showPhotoFull(memo) }
        )
        binding.memoList.layoutManager = LinearLayoutManager(this)
        binding.memoList.adapter = memoAdapter

        binding.addMemoBtn.setOnClickListener { showTypePicker() }
    }

    private fun reloadMemos() {
        val e = entry ?: return
        memos.clear()
        memos.addAll(memoStorage.loadMemos(e.id))
        // 新的在前
        memos.sortByDescending { it.createdAt }
        memoAdapter.submit(memos)
        binding.memoEmpty.visibility = if (memos.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showPhotoFull(memo: Memo) {
        val e = entry ?: return
        val file = memoStorage.fileFor(e.id, memo) ?: return
        if (!file.exists()) return

        // 取屏幕尺寸，确保大图能完整显示在对话框里
        val dm = resources.displayMetrics
        val targetW = dm.widthPixels
        val targetH = (dm.heightPixels * 0.75f).toInt()

        val view = ImageView(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            scaleType = ImageView.ScaleType.FIT_CENTER
            // 关键：必须给 ImageView 明确尺寸。默认 WRAP_CONTENT 在 FIT_CENTER 下
            // 会变成 0x0，导致整张图看不见。
            layoutParams = ViewGroup.LayoutParams(targetW, targetH)
            // 让 Coil 直接按 view 尺寸解码，避免超大图 OOM
            load(file) {
                size(Size(targetW, targetH))
                crossfade(true)
            }
        }
        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun confirmDelete(memo: Memo) {
        val e = entry ?: return
        AlertDialog.Builder(this)
            .setMessage(R.string.memo_delete_confirm)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                memoStorage.deleteMemo(e.id, memo)
                reloadMemos()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /* ------------ 类型选择 + 录入 ------------ */

    private fun showTypePicker() {
        val e = entry ?: return
        val view = LayoutInflater.from(this)
            .inflate(R.layout.dialog_memo_pick_type, null)
        val dlg = AlertDialog.Builder(this).setView(view).create()

        view.findViewById<TextView>(R.id.pickText).setOnClickListener {
            dlg.dismiss()
            showTextInput()
        }
        // 录音 / 录像 / 照片：先弹"新建 / 从存储选择"二级菜单
        view.findViewById<TextView>(R.id.pickAudio).setOnClickListener {
            dlg.dismiss()
            showMediaModePicker(Memo.Type.AUDIO)
        }
        view.findViewById<TextView>(R.id.pickVideo).setOnClickListener {
            dlg.dismiss()
            showMediaModePicker(Memo.Type.VIDEO)
        }
        view.findViewById<TextView>(R.id.pickPhoto).setOnClickListener {
            dlg.dismiss()
            showMediaModePicker(Memo.Type.PHOTO)
        }
        dlg.show()
    }

    /**
     * 二级菜单：选"新建"（拍照/录像/录音）还是"从存储选择"。
     * 文字类型不需要这个对话框，所以 showTypePicker 直接进 showTextInput。
     */
    private fun showMediaModePicker(type: Memo.Type) {
        val labels = arrayOf(
            getString(R.string.memo_mode_new),
            getString(R.string.memo_mode_pick)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.memo_pick_mode_title)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> when (type) {
                        Memo.Type.AUDIO -> ensureRecordAudioPerm { showRecordingDialog(isVideo = false) }
                        Memo.Type.VIDEO -> ensureVideoPerms { showRecordingDialog(isVideo = true) }
                        Memo.Type.PHOTO -> ensureCameraPerm { launchCameraForPhoto() }
                        else -> {}
                    }
                    1 -> launchMediaPicker(type)
                    else -> {}
                }
            }
            .show()
    }

    /** 启动系统选择器：照片 / 视频走 Photo Picker，音频走 OpenDocument。 */
    private fun launchMediaPicker(type: Memo.Type) {
        pendingPickType = type
        when (type) {
            Memo.Type.PHOTO -> pickVisualMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
            Memo.Type.VIDEO -> pickVisualMedia.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
            Memo.Type.AUDIO -> pickAudioLauncher.launch(arrayOf("audio/*"))
            else -> {}
        }
    }

    /**
     * 把选中的文件复制到应用私有目录 [memoDir]，然后保存为 [Memo]。
     * 复制走 ContentResolver.openInputStream，应用外 URI 也能读。
     */
    private fun handlePickedUri(uri: Uri?) {
        val e = entry ?: return
        val type = pendingPickType ?: return
        pendingPickType = null

        if (uri == null) return  // 用户取消

        val ext = extForType(type, uri)
        val outFile = File(memoStorage.memoDir(e.id), "memo_${System.currentTimeMillis()}.$ext")

        try {
            contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (t: Throwable) {
            outFile.delete()
            Toast.makeText(
                this,
                getString(R.string.memo_pick_fail, t.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (outFile.exists() && outFile.length() > 0L) {
            saveMediaMemo(type, outFile)
        } else {
            outFile.delete()
            Toast.makeText(this, getString(R.string.memo_pick_fail, ""), Toast.LENGTH_SHORT).show()
        }
    }

    /** 根据类型 + URI MIME 决定扩展名，尽量保持原文件类型 */
    private fun extForType(type: Memo.Type, uri: Uri): String {
        val mime = contentResolver.getType(uri)
        return when (type) {
            Memo.Type.PHOTO -> when {
                mime == null -> "jpg"
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("gif") -> "gif"
                else -> "jpg"
            }
            Memo.Type.VIDEO -> when {
                mime == null -> "mp4"
                mime.contains("3gpp") -> "3gp"
                mime.contains("quicktime") -> "mov"
                mime.contains("matroska") -> "mkv"
                else -> "mp4"
            }
            Memo.Type.AUDIO -> when {
                mime == null -> "m4a"
                mime.contains("mpeg") -> "mp3"
                mime.contains("ogg") -> "ogg"
                mime.contains("wav") -> "wav"
                mime.contains("3gpp") -> "3gp"
                else -> "m4a"
            }
            else -> "bin"
        }
    }

    private fun showTextInput() {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.dialog_memo_text, null)
        val edit = view.findViewById<EditText>(R.id.memoTextInput)
        AlertDialog.Builder(this)
            .setView(view)
            .setPositiveButton(R.string.memo_text_save) { _, _ ->
                val txt = edit.text?.toString()?.trim().orEmpty()
                if (txt.isNotEmpty()) {
                    saveTextMemo(txt)
                }
            }
            .setNegativeButton(R.string.memo_text_cancel, null)
            .show()
    }

    /* ------------ 录音 / 录像 ------------ */

    private fun showRecordingDialog(isVideo: Boolean) {
        val e = entry ?: return
        val view = LayoutInflater.from(this)
            .inflate(R.layout.dialog_memo_recording, null)
        val hint = view.findViewById<TextView>(R.id.recordingHint)
        val timer = view.findViewById<Chronometer>(R.id.recordingTimer)
        hint.text = if (isVideo) "录像中…" else getString(R.string.memo_recording)

        // 准备文件
        val ext = if (isVideo) "mp4" else "m4a"
        val relName = "memo_${System.currentTimeMillis()}.$ext"
        val outFile = File(memoStorage.memoDir(e.id), relName)

        recorderIsVideo = isVideo
        recorderFile = outFile
        try {
            startRecorderInternal(outFile, isVideo)
        } catch (t: Throwable) {
            Toast.makeText(this, getString(R.string.memo_record_fail, t.message ?: ""), Toast.LENGTH_LONG).show()
            recorderFile = null
            return
        }

        timer.base = SystemClock.elapsedRealtime()
        timer.start()

        val dlg = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(false)
            .create()
        recordingDialog = dlg

        view.findViewById<TextView>(R.id.recordingStop).setOnClickListener {
            timer.stop()
            stopRecorder(deleteFile = false)
            if (outFile.exists() && outFile.length() > 0L) {
                saveMediaMemo(if (isVideo) Memo.Type.VIDEO else Memo.Type.AUDIO, outFile)
            } else {
                outFile.delete()
                Toast.makeText(this, R.string.memo_record_fail, Toast.LENGTH_SHORT).show()
            }
            dlg.dismiss()
            recordingDialog = null
        }
        dlg.show()
    }

    private fun startRecorderInternal(outFile: File, isVideo: Boolean) {
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        if (isVideo) {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setVideoSource(MediaRecorder.VideoSource.CAMERA)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            r.setVideoSize(720, 1280)
            r.setVideoEncodingBitRate(2_500_000)
            r.setVideoFrameRate(30)
        } else {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
        }
        r.setOutputFile(outFile.absolutePath)
        // 不限制时长 / 大小，让用户自己决定何时停止
        r.prepare()
        r.start()
        recorder = r
    }

    private fun stopRecorder(deleteFile: Boolean) {
        try {
            recorder?.stop()
        } catch (_: Throwable) {
            // 录制时间过短可能 stop 失败
        }
        try {
            recorder?.reset()
        } catch (_: Throwable) {
        }
        try {
            recorder?.release()
        } catch (_: Throwable) {
        }
        recorder = null
        if (deleteFile) {
            recorderFile?.delete()
        }
        recorderFile = null
    }

    /* ------------ 拍照（系统相机 + FileProvider） ------------ */

    private fun launchCameraForPhoto() {
        val e = entry ?: return
        val relName = "photo_${System.currentTimeMillis()}.jpg"
        val outFile = File(memoStorage.memoDir(e.id), relName)
        pendingPhotoFile = outFile

        val authority = "$packageName.fileprovider"
        val uri: Uri = try {
            FileProvider.getUriForFile(this, authority, outFile)
        } catch (t: Throwable) {
            Toast.makeText(this, getString(R.string.memo_photo_fail, t.message ?: ""), Toast.LENGTH_LONG).show()
            pendingPhotoFile = null
            return
        }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, R.string.memo_no_camera, Toast.LENGTH_SHORT).show()
            pendingPhotoFile = null
            return
        }
        takePhotoLauncher.launch(intent)
    }

    /* ------------ 保存到 MemoStorage ------------ */

    private fun saveTextMemo(text: String) {
        val e = entry ?: return
        val memo = Memo(
            id = System.currentTimeMillis(),
            type = Memo.Type.TEXT,
            text = text,
            mediaPath = null,
            createdAt = System.currentTimeMillis()
        )
        val list = memoStorage.loadMemos(e.id)
        list.add(memo)
        memoStorage.saveMemos(e.id, list)
        reloadMemos()
    }

    private fun saveMediaMemo(type: Memo.Type, file: File) {
        val e = entry ?: return
        val rel = file.name
        val memo = Memo(
            id = System.currentTimeMillis(),
            type = type,
            text = null,
            mediaPath = rel,
            createdAt = System.currentTimeMillis()
        )
        val list = memoStorage.loadMemos(e.id)
        list.add(memo)
        memoStorage.saveMemos(e.id, list)
        reloadMemos()
    }

    /* ------------ 权限封装 ------------ */

    private fun ensureRecordAudioPerm(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            onGranted()
        } else {
            pendingAction = onGranted
            requestRecordAudioPerm.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun ensureVideoPerms(onGranted: () -> Unit) {
        val need = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.RECORD_AUDIO)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) need.add(Manifest.permission.CAMERA)
        if (need.isEmpty()) {
            onGranted()
        } else {
            pendingAction = onGranted
            requestVideoPerms.launch(need.toTypedArray())
        }
    }

    private fun ensureCameraPerm(onGranted: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            onGranted()
        } else {
            pendingAction = onGranted
            requestCameraPerm.launch(Manifest.permission.CAMERA)
        }
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
