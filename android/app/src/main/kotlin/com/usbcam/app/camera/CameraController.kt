package com.usbcam.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.Surface
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.usbcam.app.usb.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class CameraController(private val context: Context) {
    private val TAG = "CameraController"
    private val deviceManager = UsbDeviceManager(context)

    private var currentUvcCamera: UvcCameraDevice? = null
    private var currentEasyCap: EasyCapDevice? = null
    private var currentDeviceInfo: UsbDeviceManager.UsbDeviceInfo? = null

    private var isRecording = AtomicBoolean(false)
    private var recordingPipe: FileOutputStream? = null
    private var recordingPath: String? = null
    private var ffmpegSession: com.arthenica.ffmpegkit.FFmpegSession? = null

    var onDeviceConnected: ((Map<String, Any>) -> Unit)? = null
    var onDeviceDisconnected: ((String) -> Unit)? = null
    var onFrame: ((ByteArray, Int, Int, Int) -> Unit)? = null
    var onRecordingState: ((Boolean, String?) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val outputDir: File by lazy {
        File(context.getExternalFilesDir(null), "").also { it.mkdirs() }
    }

    fun initialize() {
        deviceManager.register()
        deviceManager.setCallbacks(
            onAttach = { info ->
                Log.d(TAG, "Device attached: ${info.displayName}")
                requestPermissionFor(info)
            },
            onDetach = { deviceKey ->
                if (currentDeviceInfo?.deviceKey == deviceKey) {
                    cleanupCurrentDevice()
                    onDeviceDisconnected?.invoke(deviceKey)
                }
            },
            onPermission = { device, granted ->
                if (granted) {
                    val info = deviceManager.getConnectedDevices().find { it.device == device }
                    info?.let { openDevice(it) }
                } else {
                    onError?.invoke("USB permission denied for ${device.deviceName}")
                }
            }
        )
    }

    private fun requestPermissionFor(info: UsbDeviceManager.UsbDeviceInfo) {
        deviceManager.requestPermission(info.device)
    }

    fun getConnectedDevices(): List<Map<String, Any>> {
        return deviceManager.getConnectedDevices().map { info ->
            mapOf(
                "deviceKey" to info.deviceKey,
                "name" to info.displayName,
                "isUvc" to info.isUvc,
                "vendorId" to info.device.vendorId.toString(16).uppercase(),
                "productId" to info.device.productId.toString(16).uppercase(),
                "maxWidth" to (info.profile?.maxResolutionWidth ?: 1920),
                "maxHeight" to (info.profile?.maxResolutionHeight ?: 1080),
                "maxFps" to (info.profile?.maxFps ?: 60),
                "formats" to (info.profile?.supportedFormats ?: listOf("MJPG", "YUY2")),
                "hasAudio" to (info.profile?.supportsAudio ?: false)
            )
        }
    }

    fun openDevice(info: UsbDeviceManager.UsbDeviceInfo): Boolean {
        cleanupCurrentDevice()
        currentDeviceInfo = info

        val connection = deviceManager.openDevice(info.device) ?: run {
            onError?.invoke("Cannot open USB device: ${info.displayName}")
            return false
        }

        val success = if (!info.isUvc) {
            val profile = info.profile ?: return false
            val easyCapDevice = EasyCapDevice(info.device, connection, profile)
            if (easyCapDevice.init()) {
                easyCapDevice.setFrameCallback { data, w, h, fmt ->
                    onFrame?.invoke(data, w, h, fmt)
                }
                currentEasyCap = easyCapDevice
                true
            } else false
        } else {
            val uvcCamera = UvcCameraDevice(info.device, connection, info.profile)
            if (uvcCamera.init()) {
                uvcCamera.setFrameCallback { data, w, h, fmt ->
                    onFrame?.invoke(data, w, h, fmt)
                }
                currentUvcCamera = uvcCamera
                true
            } else false
        }

        if (success) {
            onDeviceConnected?.invoke(mapOf(
                "deviceKey" to info.deviceKey,
                "name" to info.displayName,
                "isUvc" to info.isUvc,
                "vendorId" to info.device.vendorId.toString(16).uppercase(),
                "productId" to info.device.productId.toString(16).uppercase()
            ))
        }
        return success
    }

    fun startPreview(width: Int, height: Int, fps: Int, formatStr: String) {
        val format = when (formatStr.uppercase()) {
            "H264" -> UvcCameraDevice.FORMAT_H264
            "H265" -> UvcCameraDevice.FORMAT_H265
            "NV12" -> UvcCameraDevice.FORMAT_NV12
            "YUY2" -> UvcCameraDevice.FORMAT_YUY2
            else -> UvcCameraDevice.FORMAT_MJPEG
        }
        currentUvcCamera?.apply {
            setResolution(width, height, fps, format)
            startStream()
        }
        currentEasyCap?.startCapture()
    }

    fun stopPreview() {
        currentUvcCamera?.stopStream()
        currentEasyCap?.stopCapture()
    }

    fun startRecording(outputPath: String): Boolean {
        if (isRecording.getAndSet(true)) return false
        recordingPath = outputPath

        // Use named pipe for FFmpeg
        val pipePath = "${context.cacheDir}/video_input.yuv"
        recordingPipe = FileOutputStream(pipePath)

        val ffmpegCmd = "-f rawvideo -pixel_format yuyv422 " +
            "-video_size ${currentUvcCamera?.currentWidth ?: 640}x${currentUvcCamera?.currentHeight ?: 480} " +
            "-framerate ${currentUvcCamera?.currentFps ?: 30} " +
            "-i $pipePath " +
            "-c:v libx264 -preset ultrafast -crf 23 " +
            "-movflags +faststart " +
            "-y $outputPath"

        FFmpegKit.executeAsync(ffmpegCmd) { session ->
            val rc = session.returnCode
            isRecording.set(false)
            if (ReturnCode.isSuccess(rc)) {
                onRecordingState?.invoke(false, outputPath)
            } else {
                Log.e(TAG, "FFmpeg failed: ${session.allLogsAsString}")
                onRecordingState?.invoke(false, null)
            }
        }
        onRecordingState?.invoke(true, outputPath)
        return true
    }

    fun stopRecording() {
        if (!isRecording.get()) return
        FFmpegKit.cancel()
        recordingPipe?.close()
        recordingPipe = null
        isRecording.set(false)
    }

    fun capturePhoto(outputPath: String): Boolean {
        val frame = lastFrame ?: return false
        return try {
            val bitmap = decodeFrameToBitmap(
                frame,
                currentUvcCamera?.currentWidth ?: 720,
                currentUvcCamera?.currentHeight ?: 576
            )
            bitmap?.let {
                FileOutputStream(outputPath).use { out ->
                    it.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Capture photo failed", e)
            false
        }
    }

    private var lastFrame: ByteArray? = null

    private fun decodeFrameToBitmap(data: ByteArray, width: Int, height: Int): Bitmap? {
        return try {
            if (data.size > 3 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()) {
                BitmapFactory.decodeByteArray(data, 0, data.size)
            } else {
                val yuvImage = YuvImage(data, ImageFormat.YUY2, width, height, null)
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
                BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun cleanupCurrentDevice() {
        stopPreview()
        stopRecording()
        currentUvcCamera?.release()
        currentUvcCamera = null
        currentEasyCap?.release()
        currentEasyCap = null
        currentDeviceInfo = null
    }

    fun disconnect() {
        cleanupCurrentDevice()
    }

    fun destroy() {
        cleanupCurrentDevice()
        deviceManager.unregister()
    }
}
