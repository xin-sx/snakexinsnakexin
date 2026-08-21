package com.demo.snakexin

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备注列表适配器。同一布局根据 [Memo.type] 显示不同内容：
 * - TEXT: 文字
 * - PHOTO: 图片缩略图（点击全屏查看由 Activity 处理）
 * - AUDIO / VIDEO: ExoPlayer 播放
 *
 * 媒体文件存放在私有目录的相对路径 [Memo.mediaPath] 里，构造时传入
 * [MemoStorage] 和 [entryId]，由 [MemoStorage.fileFor] 拼成绝对路径。
 */
class MemoAdapter(
    private val storage: MemoStorage,
    private val entryId: Long,
    private var items: List<Memo>,
    private val onDelete: (Memo) -> Unit,
    private val onPhotoClick: (Memo) -> Unit,
    private val onVideoClick: (Memo) -> Unit
) : RecyclerView.Adapter<MemoAdapter.VH>() {

    private val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    /** 当前正在播放的 ExoPlayer（同时只允许一个） */
    private var activePlayer: ExoPlayer? = null
    private var activeView: PlayerView? = null

    fun submit(newItems: List<Memo>) {
        releaseActivePlayer()
        items = newItems
        notifyDataSetChanged()
    }

    /** Activity 销毁时务必调用，避免泄露。 */
    fun release() {
        releaseActivePlayer()
    }

    private fun releaseActivePlayer() {
        activePlayer?.let {
            it.stop()
            it.release()
        }
        activePlayer = null
        activeView?.player = null
        activeView?.visibility = View.GONE
        activeView = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_memo, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = items[position]
        val typeLabel = when (m.type) {
            Memo.Type.TEXT -> "📝 文字 · ${df.format(Date(m.createdAt))}"
            Memo.Type.AUDIO -> "🎤 录音 · ${df.format(Date(m.createdAt))}"
            Memo.Type.VIDEO -> "🎬 录像 · ${df.format(Date(m.createdAt))}"
            Memo.Type.PHOTO -> "📷 照片 · ${df.format(Date(m.createdAt))}"
        }
        holder.type.text = typeLabel

        // 文字
        holder.text.visibility = if (m.type == Memo.Type.TEXT) View.VISIBLE else View.GONE
        holder.text.text = m.text.orEmpty()

        // 照片
        holder.photo.visibility = if (m.type == Memo.Type.PHOTO) View.VISIBLE else View.GONE
        if (m.type == Memo.Type.PHOTO) {
            val file = storage.fileFor(entryId, m)
            if (file != null && file.exists()) {
                holder.photo.load(file) {
                    crossfade(true)
                }
            } else {
                holder.photo.setImageDrawable(null)
            }
            holder.photo.setOnClickListener { onPhotoClick(m) }
        } else {
            holder.photo.setOnClickListener(null)
        }

        // 音视频
        val isMedia = m.type == Memo.Type.AUDIO || m.type == Memo.Type.VIDEO
        holder.mediaRow.visibility = if (isMedia) View.VISIBLE else View.GONE
        holder.playerContainer.visibility = View.GONE
        if (isMedia) {
            val file = storage.fileFor(entryId, m)
            val exists = file != null && file.exists()
            holder.play.isEnabled = exists
            holder.mediaLabel.text = if (exists) {
                val kb = file!!.length() / 1024
                if (m.type == Memo.Type.AUDIO) "音频 · ${kb}KB" else "视频 · ${kb}KB"
            } else {
                "文件已丢失"
            }
            holder.play.setOnClickListener {
                if (!exists) return@setOnClickListener
                // 视频点击进入全屏播放；音频继续走内嵌播放（音频无画面）
                if (m.type == Memo.Type.VIDEO) {
                    onVideoClick(m)
                } else {
                    togglePlayback(holder, file!!)
                }
            }
        }

        holder.delete.setOnClickListener { onDelete(m) }
    }

    private fun togglePlayback(holder: VH, file: File) {
        if (activePlayer != null && activeView === holder.playerContainer) {
            // 同一个 → 停止
            releaseActivePlayer()
            holder.play.setImageResource(android.R.drawable.ic_media_play)
            return
        }
        // 切换到新的
        releaseActivePlayer()
        val ctx = holder.itemView.context
        val player = ExoPlayer.Builder(ctx).build()
        val view = holder.playerContainer
        view.player = player
        view.visibility = View.VISIBLE
        // 用 file:// URI；ExoPlayer 走 content sniffing，自动识别 mp4/m4a/mp3/3gp/...
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
        player.playWhenReady = true
        activePlayer = player
        activeView = view

        holder.play.setImageResource(android.R.drawable.ic_media_pause)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    releaseActivePlayer()
                    holder.play.setImageResource(android.R.drawable.ic_media_play)
                }
            }
        })
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val type: TextView = view.findViewById(R.id.memoType)
        val delete: ImageButton = view.findViewById(R.id.memoDelete)
        val text: TextView = view.findViewById(R.id.memoText)
        val photo: ImageView = view.findViewById(R.id.memoPhoto)
        val mediaRow: LinearLayout = view.findViewById(R.id.memoMediaRow)
        val play: ImageButton = view.findViewById(R.id.memoPlay)
        val mediaLabel: TextView = view.findViewById(R.id.memoMediaLabel)
        val playerContainer: PlayerView = view.findViewById(R.id.memoPlayerContainer)
    }
}
