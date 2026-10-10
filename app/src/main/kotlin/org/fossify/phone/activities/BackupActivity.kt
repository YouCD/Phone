package org.fossify.phone.activities

import android.app.Activity
import android.content.ContentValues
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import kotlinx.serialization.json.Json
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.isQPlus
import org.fossify.phone.R
import org.fossify.phone.helpers.RecentsHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        performBackup()
    }

    private val customOutputPath by lazy {
        intent.getStringExtra("output_path")
    }

    private fun performBackup() {
        RecentsHelper(this).getRecentCalls(queryLimit = Int.MAX_VALUE) { recents ->
            try {
                val jsonString = Json.encodeToString(recents)
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val filename = "call_history_$timestamp.json"

                val savedToCustom = customOutputPath != null && saveToCustomPath(filename, jsonString)
                if (!savedToCustom) {
                    if (isQPlus()) {
                        saveViaMediaStore(filename, jsonString)
                    } else {
                        saveToAppDir(filename, jsonString)
                    }
                }

                runOnUiThread {
                    toast("${getString(R.string.backup_completed)}: $filename")
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showErrorToast(e)
                    finish()
                }
            }
        }
    }

    private fun saveToCustomPath(filename: String, content: String): Boolean {
        val path = customOutputPath ?: return false
        return try {
            val dir = File(path)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            File(dir, filename).writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun saveViaMediaStore(filename: String, content: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("Failed to create file via MediaStore")

        contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(content.toByteArray())
        } ?: throw Exception("Failed to write file via MediaStore")
    }

    private fun saveToAppDir(filename: String, content: String) {
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw Exception("Failed to get external files directory")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        File(dir, filename).writeText(content)
    }
}
