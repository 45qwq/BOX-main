package com.fongmi.android.tv.utils

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.text.TextUtils
import androidx.core.content.FileProvider
import com.fongmi.android.tv.App
import com.fongmi.android.tv.R
import com.fongmi.android.tv.impl.Callback
import com.github.catvod.utils.Logger
import com.github.catvod.utils.Path
import java.io.*
import java.net.URLConnection
import java.text.DecimalFormat
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object FileUtil {

    @JvmStatic
    fun getWall(index: Int): File {
        return Path.files("wallpaper_$index")
    }

    @JvmStatic
    fun openFile(file: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val mimeType = if (file.name.lowercase().endsWith(".apk")) "application/vnd.android.package-archive"
                else getMimeType(file.name)
                setDataAndType(getShareUri(file), mimeType)
                if (file.name.lowercase().endsWith(".apk")) {
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }
            }
            App.get().startActivity(intent)
        } catch (e: Exception) {
            Logger.e("Failed to open file: " + e.message)
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setDataAndType(getShareUri(file), "*/*")
                }
                App.get().startActivity(intent)
            } catch (ex: Exception) {
                Logger.e("Fallback open file also failed: " + ex.message)
            }
        }
    }

    @JvmStatic
    fun gzipCompress(target: File) {
        val buffer = ByteArray(1024)
        try {
            FileInputStream(target).use { is_ ->
                GZIPOutputStream(FileOutputStream(target.absolutePath + ".gz")).use { os ->
                    var read: Int
                    while (is_.read(buffer).also { read = it } > 0) os.write(buffer, 0, read)
                }
            }
        } catch (e: IOException) {
            Logger.e("Error", e)
        } finally {
            Path.clear(target)
        }
    }

    @JvmStatic
    fun gzipDecompress(target: File, path: File) {
        val buffer = ByteArray(1024)
        try {
            GZIPInputStream(BufferedInputStream(FileInputStream(target))).use { is_ ->
                BufferedOutputStream(FileOutputStream(path)).use { os ->
                    var read: Int
                    while (is_.read(buffer).also { read = it } != -1) os.write(buffer, 0, read)
                }
            }
        } catch (e: Exception) {
            Logger.e("Error", e)
        }
    }

    fun zipDecompress(target: File, path: File) {
        try {
            ZipFile(target).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement() as ZipEntry
                    val out = File(path, entry.name)
                    if (entry.isDirectory) out.mkdirs()
                    else Path.copy(zip.getInputStream(entry), out)
                }
            }
        } catch (e: Exception) {
            Logger.e("Error", e)
        }
    }

    @JvmStatic
    fun clearCache(callback: Callback) {
        App.execute {
            Path.clear(Path.cache())
            App.post { callback.success() }
        }
    }

    @JvmStatic
    fun getCacheSize(callback: Callback) {
        App.execute {
            val usage = byteCountToDisplaySize(getDirectorySize(Path.cache()))
            App.post { callback.success(usage) }
        }
    }

    @JvmStatic
    fun getDirectorySize(dir: File?): Long {
        if (dir == null) return 0
        return if (dir.isDirectory) Path.list(dir).sumOf { getDirectorySize(it) }
        else dir.length()
    }

    @JvmStatic
    fun getAvailableStorageSpace(file: File): Long {
        return try {
            val stat = StatFs(file.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0
        }
    }

    @JvmStatic
    fun getShareUri(path: String): Uri {
        return getShareUri(File(path.replace("file://", "")))
    }

    @JvmStatic
    fun getShareUri(file: File): Uri {
        return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) Uri.fromFile(file)
        else FileProvider.getUriForFile(App.get(), App.get().packageName + ".provider", file)
    }

    private fun getMimeType(fileName: String): String {
        val mimeType = URLConnection.guessContentTypeFromName(fileName)
        return if (TextUtils.isEmpty(mimeType)) "*/*" else mimeType
    }

    @JvmStatic
    fun byteCountToDisplaySize(size: Long): String {
        if (size <= 0) return ResUtil.getString(R.string.none)
        val units = arrayOf("bytes", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
}
