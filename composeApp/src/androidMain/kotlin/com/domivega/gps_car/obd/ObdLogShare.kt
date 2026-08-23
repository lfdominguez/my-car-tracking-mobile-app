package com.domivega.gps_car.obd

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ObdLogShare {
    private const val FILE_NAME = "obd-debug-log.txt"
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    fun share(context: Context, entries: List<ObdLogEntry>) {
        shareText(context, formatObdLogAsText(entries), "OBD debug log")
    }

    fun shareText(context: Context, text: String, subject: String = "OBD debug log") {
        val file = File(context.cacheDir, FILE_NAME)
        try {
            file.writeText(text)
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + AUTHORITY_SUFFIX,
                file,
            )
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                clipData = ClipData.newRawUri(FILE_NAME, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share log"))
        } catch (_: Exception) {
            Toast.makeText(context, "Could not share log", Toast.LENGTH_SHORT).show()
        }
    }
}
