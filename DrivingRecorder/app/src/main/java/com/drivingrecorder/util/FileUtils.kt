package com.drivingrecorder.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * 文件IO工具
 */
object FileUtils {

    /**
     * 在应用缓存目录创建临时文件
     */
    fun createTempFile(context: Context, fileName: String): File {
        val dir = File(context.cacheDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName)
    }

    /**
     * 写入文本到文件
     */
    fun writeText(file: File, content: String, append: Boolean = false) {
        FileOutputStream(file, append).use { fos ->
            fos.write(content.toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * 添加 UTF-8 BOM（Excel 兼容中文 CSV）
     */
    fun writeBom(file: File) {
        FileOutputStream(file).use { fos ->
            fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
        }
    }

    /**
     * 清理导出临时文件
     */
    fun cleanExportDir(context: Context) {
        val dir = File(context.cacheDir, "exports")
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    /**
     * 获取导出文件 Uri
     */
    fun getFileUri(context: Context, file: File): Uri {
        return Uri.fromFile(file)
    }

    /**
     * 安全文件名（移除非法字符）
     */
    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_")
    }
}
