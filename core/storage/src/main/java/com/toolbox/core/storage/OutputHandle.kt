package com.toolbox.core.storage

import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.OutputStream

/**
 * 输出目标抽象：把 MediaStore 与旧版文件路径的差异挡在引擎之外。
 * commit() 之前，文件对用户不可见（IS_PENDING），失败就 abort()。
 */
interface OutputHandle {
    val displayName: String
    val uri: Uri?
    val path: String?
    fun openStream(): OutputStream

    /** MediaMuxer 需要 fd；返回 null 表示该实现不支持直写。 */
    fun openFileDescriptor(): ParcelFileDescriptor?
    fun commit()
    fun abort()
}

interface OutputStore {
    fun create(
        displayName: String,
        mime: String,
        relativePath: String = DEFAULT_RELATIVE_PATH
    ): OutputHandle

    /** 返回真正会落盘的文件名：重名时自动变成 "name (1).mp4"，不会覆盖已有文件。 */
    fun resolveAvailableName(
        displayName: String,
        mime: String,
        relativePath: String = DEFAULT_RELATIVE_PATH
    ): String

    companion object {
        const val DEFAULT_RELATIVE_PATH = "Download/工具箱"
    }
}