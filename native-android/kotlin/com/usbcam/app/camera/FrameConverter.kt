package com.usbcam.app.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream

/**
 * Converts raw USB camera frames (MJPEG / YUY2 / NV21) into JPEG bytes for the
 * MJPEG preview server and photo capture, and into Bitmaps for the recorder.
 *
 * Format codes: 1=MJPEG, 2=YUY2, 5=NV12, 6=NV21 (native UVC backend).
 */
object FrameConverter {
    const val FORMAT_MJPEG = 1
    const val FORMAT_YUY2 = 2
    const val FORMAT_NV12 = 5
    const val FORMAT_NV21 = 6

    private fun isJpeg(data: ByteArray): Boolean =
        data.size > 3 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()

    /** Return JPEG bytes for any supported input frame, or null on failure. */
    fun toJpeg(data: ByteArray, width: Int, height: Int, format: Int, quality: Int = 85): ByteArray? {
        return try {
            when {
                format == FORMAT_MJPEG || isJpeg(data) -> data
                format == FORMAT_YUY2 -> yuy2ToJpeg(data, width, height, quality)
                // The native UVC backend (UVCAndroid) delivers NV21 directly.
                format == FORMAT_NV21 || format == FORMAT_NV12 -> nv21ToJpeg(data, width, height, quality)
                else -> if (isJpeg(data)) data else yuy2ToJpeg(data, width, height, quality)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Decode any supported input frame into a Bitmap (used by the recorder). */
    fun toBitmap(data: ByteArray, width: Int, height: Int, format: Int): Bitmap? {
        return try {
            val jpeg = toJpeg(data, width, height, format) ?: return null
            BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun yuy2ToJpeg(data: ByteArray, width: Int, height: Int, quality: Int): ByteArray {
        // Convert YUY2 (YUYV 4:2:2) -> NV21 then let YuvImage encode to JPEG.
        val nv21 = yuy2ToNv21(data, width, height)
        return nv21ToJpeg(nv21, width, height, quality)
    }

    private fun nv21ToJpeg(nv21: ByteArray, width: Int, height: Int, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        YuvImage(nv21, ImageFormat.NV21, width, height, null)
            .compressToJpeg(Rect(0, 0, width, height), quality, out)
        return out.toByteArray()
    }

    /** YUY2/YUYV (2 bytes per pixel) -> NV21 (Y plane + interleaved VU). */
    private fun yuy2ToNv21(yuy2: ByteArray, width: Int, height: Int): ByteArray {
        val frameSize = width * height
        val nv21 = ByteArray(frameSize + frameSize / 2)
        var yIndex = 0
        var uvIndex = frameSize
        var src = 0
        for (row in 0 until height) {
            var col = 0
            while (col < width) {
                // Two pixels packed as Y0 U Y1 V
                if (src + 3 >= yuy2.size) break
                val y0 = yuy2[src].toInt() and 0xFF
                val u = yuy2[src + 1].toInt() and 0xFF
                val y1 = yuy2[src + 2].toInt() and 0xFF
                val v = yuy2[src + 3].toInt() and 0xFF
                src += 4
                nv21[yIndex++] = y0.toByte()
                nv21[yIndex++] = y1.toByte()
                // NV21 stores chroma once per 2x2 block: sample on even rows only.
                if (row % 2 == 0) {
                    nv21[uvIndex++] = v.toByte()
                    nv21[uvIndex++] = u.toByte()
                }
                col += 2
            }
        }
        return nv21
    }
}
