package com.fongmi.android.tv.utils

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.fragment.app.Fragment
import com.fongmi.android.tv.App
import com.fongmi.android.tv.ui.activity.FileActivity
import com.github.catvod.utils.Path
import java.io.File

class FileChooser private constructor(private val activity: Activity? = null, private val fragment: Fragment? = null) {

    fun show() {
        show("*/*")
    }

    fun show(mimeType: String) {
        show(mimeType, arrayOf("*/*"), REQUEST_PICK_FILE)
    }

    fun show(mimeTypes: Array<String>) {
        show("*/*", mimeTypes, REQUEST_PICK_FILE)
    }

    @Suppress("deprecation")
    fun show(mimeType: String, mimeTypes: Array<String>, code: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            setType(mimeType)
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
            putExtra("android.content.extra.SHOW_ADVANCED", true)
        }
        val resolveInfos = App.get().packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfos.isEmpty() || resolveInfos[0].activityInfo.packageName.contains("frameworkpackagestubs")) {
            activity?.startActivityForResult(Intent(activity, FileActivity::class.java), code)
            fragment?.startActivityForResult(Intent(fragment.activity, FileActivity::class.java), code)
        } else {
            activity?.startActivityForResult(Intent.createChooser(intent, ""), code)
            fragment?.startActivityForResult(Intent.createChooser(intent, ""), code)
        }
    }

    companion object {
        const val REQUEST_PICK_FILE = 9999

        @JvmStatic
        fun from(activity: Activity): FileChooser = FileChooser(activity = activity)

        @JvmStatic
        fun from(fragment: Fragment): FileChooser = FileChooser(fragment = fragment)

        @JvmStatic
        fun isValid(context: Context, uri: Uri?): Boolean {
            return try {
                DocumentsContract.isDocumentUri(context, uri)
                        || ContentResolver.SCHEME_CONTENT == uri?.scheme
                        || ContentResolver.SCHEME_FILE.equals(uri?.scheme, ignoreCase = true)
            } catch (_: Exception) {
                false
            }
        }

        @JvmStatic
        fun getPathFromUri(uri: Uri?): String? {
            return getPathFromUri(App.get(), uri)
        }

        @JvmStatic
        fun getPathFromUri(context: Context, uri: Uri?): String? {
            if (uri == null) return null
            val path = when {
                DocumentsContract.isDocumentUri(context, uri) -> getPathFromDocumentUri(context, uri)
                ContentResolver.SCHEME_CONTENT == uri.scheme -> getDataColumn(context, uri)
                ContentResolver.SCHEME_FILE.equals(uri.scheme, ignoreCase = true) -> uri.path
                else -> null
            }
            return path?.let { Uri.decode(it) } ?: createFileFromUri(context, uri)
        }

        private fun getPathFromDocumentUri(context: Context, uri: Uri): String? {
            val docId = DocumentsContract.getDocumentId(uri)
            val split = docId.split(":")
            return when {
                isExternalStorageDocument(uri) -> getPath(docId, split.toTypedArray())
                isDownloadsDocument(uri) -> getPath(context, uri, docId)
                isMediaDocument(uri) -> getPath(context, split.toTypedArray())
                else -> null
            }
        }

        private fun getPath(docId: String, split: Array<String>): String {
            return if ("primary".equals(split[0], ignoreCase = true)) {
                if (split.size > 1) Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                else Environment.getExternalStorageDirectory().toString() + "/"
            } else {
                "/storage/" + docId.replace(":", "/")
            }
        }

        private fun getPath(context: Context, uri: Uri, docId: String): String? {
            val fileName = getNameColumn(context, uri)
            return when {
                docId.startsWith("raw:") -> docId.replaceFirst("raw:", "")
                fileName != null -> Environment.getExternalStorageDirectory().toString() + "/Download/" + fileName
                else -> getDataColumn(context, ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), docId.toLong()))
            }
        }

        private fun getPath(context: Context, split: Array<String>): String? {
            return when (split[0]) {
                "image" -> getDataColumn(context, ContentUris.withAppendedId(getImageUri(), split[1].toLong()))
                "video" -> getDataColumn(context, ContentUris.withAppendedId(getVideoUri(), split[1].toLong()))
                "audio" -> getDataColumn(context, ContentUris.withAppendedId(getAudioUri(), split[1].toLong()))
                else -> getDataColumn(context, ContentUris.withAppendedId(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), split[1].toLong()))
            }
        }

        private fun createFileFromUri(context: Context, uri: Uri): String? {
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            cursor?.use { c ->
                if (!c.moveToFirst()) return null
                val `is` = context.contentResolver.openInputStream(uri) ?: return null
                val column = c.getColumnIndexOrThrow(projection[0])
                val file = Path.cache(c.getString(column))
                Path.copy(`is`, file)
                return file.absolutePath
            }
            return null
        }

        private fun getDataColumn(context: Context, uri: Uri): String? {
            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(projection[0]))
                }
            }
            return null
        }

        private fun getNameColumn(context: Context, uri: Uri): String? {
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(projection[0]))
                }
            }
            return null
        }

        private fun getImageUri(): Uri {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
        }

        private fun getVideoUri(): Uri {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
        }

        private fun getAudioUri(): Uri {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
        }

        private fun isExternalStorageDocument(uri: Uri): Boolean {
            return "com.android.externalstorage.documents" == uri.authority
        }

        private fun isDownloadsDocument(uri: Uri): Boolean {
            return "com.android.providers.downloads.documents" == uri.authority
        }

        private fun isMediaDocument(uri: Uri): Boolean {
            return "com.android.providers.media.documents" == uri.authority
        }
    }
}
