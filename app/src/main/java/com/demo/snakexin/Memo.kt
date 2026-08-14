package com.demo.snakexin

/**
 * 备注：可以是文字、录音、录像、照片。
 *
 * - [type] 备注类型
 * - [text] 当类型为 [Type.TEXT] 时存文字内容；其他类型为 null
 * - [mediaPath] 当类型为非文字时存文件相对路径（相对于 filesDir/memos）；文字类型为 null
 * - [createdAt] 创建时间戳（毫秒）
 */
data class Memo(
    val id: Long,
    val type: Type,
    val text: String? = null,
    val mediaPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    enum class Type { TEXT, AUDIO, VIDEO, PHOTO }
}
