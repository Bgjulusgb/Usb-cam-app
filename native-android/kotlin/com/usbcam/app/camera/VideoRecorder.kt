package com.usbcam.app.camera

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Native H.264/MP4 recorder built on Android's MediaCodec + MediaMuxer.
 *
 * This replaces the retired arthenica FFmpegKit dependency: it has zero
 * external libraries, works on Android 8+ and writes a standard .mp4 the
 * gallery and any player can open.
 *
 * Frames arrive as Bitmaps (already decoded by FrameConverter), are scaled to
 * the encoder resolution, converted to NV12 and fed through a ByteBuffer
 * input queue on a dedicated worker thread.
 */
class VideoRecorder(
    private val outputPath: String,
    private val width: Int,
    private val height: Int,
    private val fps: Int,
    bitRate: Int = (width * height * fps * 0.12).toInt().coerceIn(1_000_000, 25_000_000)
) {
    private val TAG = "VideoRecorder"
    private val running = AtomicBoolean(false)
    private val frameQueue = LinkedBlockingQueue<Bitmap>(8)
    private var worker: Thread? = null

    private lateinit var encoder: MediaCodec
    private lateinit var muxer: MediaMuxer
    private var trackIndex = -1
    private var muxerStarted = false
    private var frameIndex = 0L
    private val bufferInfo = MediaCodec.BufferInfo()

    private val format: MediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
        setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)
        setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
    }

    fun start(): Boolean {
        return try {
            val codecName = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
                ?: MediaFormat.MIMETYPE_VIDEO_AVC.let { MediaCodec.createEncoderByType(it).name }
            encoder = MediaCodec.createByCodecName(codecName)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            running.set(true)
            worker = thread(name = "video-encoder") { encodeLoop() }
            true
        } catch (e: Exception) {
            Log.e(TAG, "start failed: ${e.message}")
            cleanup()
            false
        }
    }

    /** Offer a decoded frame for encoding (non-blocking; drops if queue is full). */
    fun submit(bitmap: Bitmap) {
        if (!running.get()) return
        val scaled = if (bitmap.width != width || bitmap.height != height)
            Bitmap.createScaledBitmap(bitmap, width, height, false) else bitmap
        if (!frameQueue.offer(scaled)) {
            // Queue full: drop oldest to keep latency bounded.
            frameQueue.poll()
            frameQueue.offer(scaled)
        }
    }

    private fun encodeLoop() {
        try {
            while (running.get() || frameQueue.isNotEmpty()) {
                val bmp = frameQueue.poll() ?: run {
                    if (!running.get()) return@run null
                    Thread.sleep(5); null
                } ?: continue
                queueInput(bmp)
                drainOutput(false)
            }
            // Signal end-of-stream and flush.
            signalEndOfStream()
            drainOutput(true)
        } catch (e: Exception) {
            Log.e(TAG, "encodeLoop error: ${e.message}")
        } finally {
            cleanup()
        }
    }

    private fun queueInput(bmp: Bitmap) {
        val index = encoder.dequeueInputBuffer(10_000)
        if (index < 0) return
        val input: ByteBuffer = encoder.getInputBuffer(index) ?: return
        input.clear()
        val nv12 = bitmapToNv12(bmp)
        input.put(nv12)
        val pts = frameIndex * 1_000_000L / fps
        encoder.queueInputBuffer(index, 0, nv12.size, pts, 0)
        frameIndex++
    }

    private fun signalEndOfStream() {
        val index = encoder.dequeueInputBuffer(10_000)
        if (index >= 0) {
            encoder.queueInputBuffer(index, 0, 0, frameIndex * 1_000_000L / fps,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        }
    }

    private fun drainOutput(endOfStream: Boolean) {
        while (true) {
            val outIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        trackIndex = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }
                outIndex >= 0 -> {
                    val encoded = encoder.getOutputBuffer(outIndex)
                    if (encoded != null && bufferInfo.size > 0 && muxerStarted &&
                        (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                    }
                    encoder.releaseOutputBuffer(outIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        worker?.join(3000)
    }

    private fun cleanup() {
        try { if (::encoder.isInitialized) { encoder.stop(); encoder.release() } } catch (_: Exception) {}
        try {
            if (::muxer.isInitialized) {
                if (muxerStarted) muxer.stop()
                muxer.release()
            }
        } catch (_: Exception) {}
        frameQueue.clear()
    }

    /** ARGB Bitmap -> NV12 (Y plane followed by interleaved U,V). */
    private fun bitmapToNv12(bitmap: Bitmap): ByteArray {
        val w = width; val h = height
        val argb = IntArray(w * h)
        bitmap.getPixels(argb, 0, w, 0, 0, w, h)
        val yuv = ByteArray(w * h * 3 / 2)
        var yIndex = 0
        var uvIndex = w * h
        for (j in 0 until h) {
            for (i in 0 until w) {
                val c = argb[j * w + i]
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
        return yuv
    }
}
