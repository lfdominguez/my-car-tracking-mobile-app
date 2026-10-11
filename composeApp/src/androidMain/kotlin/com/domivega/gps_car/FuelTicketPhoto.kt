package com.domivega.gps_car

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Receipt photo as sent to the server: upright, at most [MAX_SIDE] px on the long
 * side, JPEG. Keeps small print legible while staying well under the server's
 * 8 MiB photo limit, whatever the phone's camera resolution.
 */
object FuelTicketPhoto {
    private const val MAX_SIDE = 3000
    private const val QUALITY = 92

    fun prepare(file: File): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The photo could not be read" }

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("The photo could not be read")

        val matrix = Matrix()
        val longSide = maxOf(decoded.width, decoded.height)
        if (longSide > MAX_SIDE) {
            val scale = MAX_SIDE.toFloat() / longSide
            matrix.postScale(scale, scale)
        }
        // Camera apps store rotation as an EXIF tag; decoding ignores it.
        when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        val upright = if (matrix.isIdentity) {
            decoded
        } else {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }
        return ByteArrayOutputStream().use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
            if (upright !== decoded) upright.recycle()
            decoded.recycle()
            out.toByteArray()
        }
    }
}
