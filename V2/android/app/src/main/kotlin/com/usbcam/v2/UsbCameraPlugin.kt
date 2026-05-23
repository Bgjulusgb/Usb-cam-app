package com.usbcam.v2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.getcapacitor.*
import com.getcapacitor.annotation.CapacitorPlugin
import com.usbcam.v2.usb.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

@CapacitorPlugin(name = "UsbCamera")
class UsbCameraPlugin : Plugin() {
    private val TAG = "UsbCameraPlugin"

    private lateinit var deviceManager: UsbDeviceManager
    private var currentUvc: UvcCameraDevice? = null
    private var currentEasyCap: EasyCapDevice? = null
    private var currentDeviceInfo: UsbDeviceInfo? = null
    private var isRecording = false
    private var lastFrame: ByteArray? = null
    private var lastFrameW = 640; private var lastFrameH = 480; private var lastFrameFmt = 1

    override fun load() {
        deviceManager = UsbDeviceManager(context).apply {
            onAttach = { info ->
                val data = info.toJso()
                notifyListeners("deviceConnected", data)
            }
            onDetach = { key ->
                if (currentDeviceInfo?.deviceKey == key) cleanupDevice()
                notifyListeners("deviceDisconnected", JSObject().put("deviceKey", key))
            }
            onPermission = { device, granted ->
                // handled via requestPermission call result
            }
        }
        deviceManager.register()
    }

    @PluginMethod
    fun getConnectedDevices(call: PluginCall) {
        val arr = JSONArray()
        deviceManager.getConnectedDevices().forEach { arr.put(it.toJso()) }
        call.resolve(JSObject().put("devices", arr))
    }

    @PluginMethod
    fun openDevice(call: PluginCall) {
        val key = call.getString("deviceKey") ?: run { call.reject("Missing deviceKey"); return }
        val info = deviceManager.getConnectedDevices().find { it.deviceKey == key }
            ?: run { call.reject("Device not found"); return }

        cleanupDevice()
        val conn = deviceManager.openDevice(info.device) ?: run { call.reject("Cannot open device"); return }
        currentDeviceInfo = info

        val ok = if (!info.isUvc) {
            val p = info.profile ?: run { call.reject("No profile for non-UVC device"); return }
            EasyCapDevice(info.device, conn, p).also { it.frameCallback = ::onFrame; currentEasyCap = it }.init()
        } else {
            UvcCameraDevice(info.device, conn, info.profile).also { it.frameCallback = ::onFrame; currentUvc = it }.init()
        }

        if (ok) call.resolve(JSObject().put("success", true))
        else call.reject("Failed to initialize device")
    }

    @PluginMethod
    fun startPreview(call: PluginCall) {
        val w = call.getInt("width", 640)!!
        val h = call.getInt("height", 480)!!
        val fps = call.getInt("fps", 30)!!
        val fmt = when (call.getString("format", "MJPG")?.uppercase()) {
            "H264" -> UvcCameraDevice.FORMAT_H264; "H265" -> UvcCameraDevice.FORMAT_H265
            "NV12" -> UvcCameraDevice.FORMAT_NV12; "YUY2" -> UvcCameraDevice.FORMAT_YUY2
            else -> UvcCameraDevice.FORMAT_MJPEG
        }
        currentUvc?.apply { setResolution(w, h, fps, fmt); startStream() }
        currentEasyCap?.startCapture()
        call.resolve(JSObject().put("success", true))
    }

    @PluginMethod
    fun stopPreview(call: PluginCall) {
        currentUvc?.stopStream(); currentEasyCap?.stopCapture()
        call.resolve(JSObject().put("success", true))
    }

    @PluginMethod
    fun startRecording(call: PluginCall) {
        if (isRecording) { call.reject("Already recording"); return }
        val path = generatePath("mp4", "Videos")
        isRecording = true

        val w = currentUvc?.width ?: 720; val h = currentUvc?.height ?: 576
        val fps = currentUvc?.fps ?: 30
        val pipePath = "${context.cacheDir}/video_pipe.yuv"

        FFmpegKit.executeAsync(
            "-f rawvideo -pixel_format yuyv422 -video_size ${w}x${h} -framerate $fps -i $pipePath " +
            "-c:v libx264 -preset ultrafast -crf 23 -movflags +faststart -y $path"
        ) { session ->
            isRecording = false
            val ok = ReturnCode.isSuccess(session.returnCode)
            notifyListeners("recordingState", JSObject().put("isRecording", false).put("outputPath", if (ok) path else ""))
        }
        call.resolve(JSObject().put("success", true).put("outputPath", path))
    }

    @PluginMethod
    fun stopRecording(call: PluginCall) {
        FFmpegKit.cancel(); isRecording = false
        call.resolve(JSObject().put("success", true))
    }

    @PluginMethod
    fun capturePhoto(call: PluginCall) {
        val frame = lastFrame ?: run { call.reject("No frame available"); return }
        val path = generatePath("jpg", "Images")
        try {
            val bmp = decodeToBitmap(frame, lastFrameW, lastFrameH)
            if (bmp != null) {
                FileOutputStream(path).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                call.resolve(JSObject().put("success", true).put("outputPath", path))
            } else call.reject("Failed to decode frame")
        } catch (e: Exception) {
            call.reject("Photo capture failed: ${e.message}")
        }
    }

    @PluginMethod
    fun disconnect(call: PluginCall) {
        cleanupDevice()
        call.resolve()
    }

    @PluginMethod
    fun getStoragePath(call: PluginCall) {
        val path = activity.getExternalFilesDir(null)?.absolutePath ?: activity.filesDir.absolutePath
        call.resolve(JSObject().put("path", path))
    }

    @PluginMethod
    fun requestPermission(call: PluginCall) {
        val key = call.getString("deviceKey") ?: run { call.reject("Missing deviceKey"); return }
        val info = deviceManager.getConnectedDevices().find { it.deviceKey == key }
            ?: run { call.resolve(JSObject().put("granted", false)); return }

        if (deviceManager.hasPermission(info.device)) {
            call.resolve(JSObject().put("granted", true))
        } else {
            deviceManager.onPermission = { _, granted ->
                call.resolve(JSObject().put("granted", granted))
                deviceManager.onPermission = null
            }
            deviceManager.requestPermission(info.device)
        }
    }

    private fun onFrame(data: ByteArray, w: Int, h: Int, fmt: Int) {
        lastFrame = data; lastFrameW = w; lastFrameH = h; lastFrameFmt = fmt
    }

    private fun decodeToBitmap(data: ByteArray, w: Int, h: Int): Bitmap? = try {
        if (data.size > 2 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()) {
            BitmapFactory.decodeByteArray(data, 0, data.size)
        } else {
            val yuv = YuvImage(data, ImageFormat.YUY2, w, h, null)
            val out = ByteArrayOutputStream()
            yuv.compressToJpeg(Rect(0, 0, w, h), 90, out)
            BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        }
    } catch (_: Exception) { null }

    private fun cleanupDevice() {
        currentUvc?.release(); currentUvc = null
        currentEasyCap?.release(); currentEasyCap = null
        currentDeviceInfo = null
    }

    private fun generatePath(ext: String, dir: String): String {
        val d = File(activity.getExternalFilesDir(null), dir).also { it.mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val prefix = if (ext == "mp4") "VID" else "IMG"
        return "${d.absolutePath}/${prefix}_${ts}.${ext}"
    }

    override fun handleOnDestroy() {
        cleanupDevice(); deviceManager.unregister()
    }
}

private fun UsbDeviceInfo.toJso(): JSObject = JSObject().apply {
    put("deviceKey", deviceKey)
    put("name", displayName)
    put("isUvc", isUvc)
    put("vendorId", device.vendorId.toString(16).uppercase().padStart(4, '0'))
    put("productId", device.productId.toString(16).uppercase().padStart(4, '0'))
    put("maxWidth", profile?.maxResolutionWidth ?: 1920)
    put("maxHeight", profile?.maxResolutionHeight ?: 1080)
    put("maxFps", profile?.maxFps ?: 60)
    val fmts = JSONArray(); (profile?.supportedFormats ?: listOf("MJPG", "YUY2")).forEach { fmts.put(it) }
    put("formats", fmts)
    put("hasAudio", profile?.supportsAudio ?: false)
}
