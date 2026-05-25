package com.usbcam.app

import android.graphics.BitmapFactory
import android.util.Log
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.usbcam.app.camera.FrameConverter
import com.usbcam.app.camera.MjpegStreamServer
import com.usbcam.app.camera.VideoRecorder
import com.usbcam.app.usb.EasyCapDevice
import com.usbcam.app.usb.UsbDeviceInfo
import com.usbcam.app.usb.UsbDeviceManager
import com.usbcam.app.usb.UvcCameraDevice
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@CapacitorPlugin(name = "UsbCamera")
class UsbCameraPlugin : Plugin() {
    private val TAG = "UsbCameraPlugin"
    private val PREVIEW_PORT = 8080

    private lateinit var deviceManager: UsbDeviceManager
    private var uvc: UvcCameraDevice? = null
    private var easyCap: EasyCapDevice? = null
    private var activeKey: String? = null

    private var mjpeg: MjpegStreamServer? = null
    private var recorder: VideoRecorder? = null
    private var recordingPath: String? = null
    @Volatile private var recording = false
    @Volatile private var lastJpeg: ByteArray? = null

    override fun load() {
        deviceManager = UsbDeviceManager(context).apply {
            onAttach = { info -> notifyListeners("deviceConnected", info.toJs()) }
            onDetach = { key ->
                if (key == activeKey) cleanupDevice()
                notifyListeners("deviceDisconnected", JSObject().put("deviceKey", key))
            }
        }
        deviceManager.register()
    }

    // ─── Device discovery ────────────────────────────────────────────────────

    @PluginMethod
    fun getConnectedDevices(call: PluginCall) {
        val arr = JSONArray()
        deviceManager.getConnectedDevices().forEach { arr.put(it.toJs()) }
        call.resolve(JSObject().put("devices", arr))
    }

    @PluginMethod
    fun requestPermission(call: PluginCall) {
        val key = call.getString("deviceKey") ?: return call.reject("Missing deviceKey")
        val info = deviceManager.getConnectedDevices().find { it.deviceKey == key }
            ?: return call.resolve(JSObject().put("granted", false))
        if (deviceManager.hasPermission(info.device)) {
            call.resolve(JSObject().put("granted", true)); return
        }
        deviceManager.onPermission = { _, granted ->
            call.resolve(JSObject().put("granted", granted))
            deviceManager.onPermission = null
        }
        deviceManager.requestPermission(info.device)
    }

    @PluginMethod
    fun openDevice(call: PluginCall) {
        val key = call.getString("deviceKey") ?: return call.reject("Missing deviceKey")
        val info = deviceManager.getConnectedDevices().find { it.deviceKey == key }
            ?: return call.reject("Device not found")

        cleanupDevice()
        val conn = deviceManager.openDevice(info.device) ?: return call.reject("Cannot open device")
        activeKey = key

        val ok = if (!info.isUvc) {
            val profile = info.profile ?: return call.reject("No profile for non-UVC device")
            EasyCapDevice(info.device, conn, profile).also {
                it.frameCallback = ::onFrame; easyCap = it
            }.init()
        } else {
            UvcCameraDevice(info.device, conn, info.profile).also {
                it.frameCallback = ::onFrame; uvc = it
            }.init()
        }
        if (ok) call.resolve(JSObject().put("success", true))
        else { cleanupDevice(); call.reject("Failed to initialize device") }
    }

    // ─── Preview (MJPEG server) ──────────────────────────────────────────────

    @PluginMethod
    fun startPreview(call: PluginCall) {
        val w = call.getInt("width") ?: 640
        val h = call.getInt("height") ?: 480
        val fps = call.getInt("fps") ?: 30
        val lan = call.getBoolean("lanAccessible") ?: false
        val fmt = when (call.getString("format")?.uppercase()) {
            "H264" -> UvcCameraDevice.FORMAT_H264
            "H265" -> UvcCameraDevice.FORMAT_H265
            "NV12" -> UvcCameraDevice.FORMAT_NV12
            "YUY2" -> UvcCameraDevice.FORMAT_YUY2
            else -> UvcCameraDevice.FORMAT_MJPEG
        }

        mjpeg?.stop()
        mjpeg = MjpegStreamServer(PREVIEW_PORT, lan).also { it.start() }

        uvc?.apply { setResolution(w, h, fps, fmt); startStream() }
        easyCap?.startCapture()

        val result = JSObject()
            .put("success", true)
            .put("streamUrl", "http://localhost:$PREVIEW_PORT/stream")
            .put("port", PREVIEW_PORT)
        if (lan) result.put("lanUrl", "http://${localIpAddress()}:$PREVIEW_PORT/stream")
        call.resolve(result)
    }

    @PluginMethod
    fun stopPreview(call: PluginCall) {
        uvc?.stopStream(); easyCap?.stopCapture()
        mjpeg?.stop(); mjpeg = null
        call.resolve(JSObject().put("success", true))
    }

    @PluginMethod
    fun getStreamInfo(call: PluginCall) {
        val running = mjpeg?.isRunning() == true
        call.resolve(JSObject()
            .put("running", running)
            .put("streamUrl", if (running) "http://localhost:$PREVIEW_PORT/stream" else ""))
    }

    // ─── Recording (MediaCodec / MediaMuxer) ─────────────────────────────────

    @PluginMethod
    fun startRecording(call: PluginCall) {
        if (recording) return call.reject("Already recording")
        val w = uvc?.width ?: 720
        val h = uvc?.height ?: 576
        val fps = uvc?.fps ?: 25
        val path = generatePath("mp4", "Videos")

        val rec = VideoRecorder(path, w, h, fps)
        if (!rec.start()) return call.reject("Failed to start encoder")
        recorder = rec
        recordingPath = path
        recording = true
        notifyListeners("recordingState", JSObject().put("isRecording", true).put("outputPath", path))
        call.resolve(JSObject().put("success", true).put("outputPath", path))
    }

    @PluginMethod
    fun stopRecording(call: PluginCall) {
        recording = false
        recorder?.stop()
        recorder = null
        val path = recordingPath ?: ""
        recordingPath = null
        notifyListeners("recordingState", JSObject().put("isRecording", false).put("outputPath", path))
        call.resolve(JSObject().put("success", true).put("outputPath", path))
    }

    // ─── Photo capture ───────────────────────────────────────────────────────

    @PluginMethod
    fun capturePhoto(call: PluginCall) {
        val jpeg = lastJpeg ?: return call.reject("No frame available yet")
        val path = generatePath("jpg", "Images")
        try {
            FileOutputStream(path).use { it.write(jpeg) }
            call.resolve(JSObject().put("success", true).put("outputPath", path))
        } catch (e: Exception) {
            call.reject("Photo capture failed: ${e.message}")
        }
    }

    // ─── Lifecycle / misc ─────────────────────────────────────────────────────

    @PluginMethod
    fun disconnect(call: PluginCall) { cleanupDevice(); call.resolve() }

    @PluginMethod
    fun getStoragePath(call: PluginCall) {
        val path = activity.getExternalFilesDir(null)?.absolutePath ?: activity.filesDir.absolutePath
        call.resolve(JSObject().put("path", path))
    }

    // ─── Internals ─────────────────────────────────────────────────────────────

    private fun onFrame(data: ByteArray, w: Int, h: Int, fmt: Int) {
        val jpeg = FrameConverter.toJpeg(data, w, h, fmt) ?: return
        lastJpeg = jpeg
        mjpeg?.pushFrame(jpeg)
        if (recording) {
            recorder?.let { rec ->
                BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.let { rec.submit(it) }
            }
        }
    }

    private fun cleanupDevice() {
        recording = false
        recorder?.stop(); recorder = null
        recordingPath = null
        mjpeg?.stop(); mjpeg = null
        uvc?.release(); uvc = null
        easyCap?.release(); easyCap = null
        activeKey = null
        lastJpeg = null
    }

    private fun generatePath(ext: String, dir: String): String {
        val d = File(activity.getExternalFilesDir(null), dir).also { it.mkdirs() }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val prefix = if (ext == "mp4") "VID" else "IMG"
        return "${d.absolutePath}/${prefix}_$ts.$ext"
    }

    private fun localIpAddress(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress ?: "127.0.0.1"
        } catch (_: Exception) { "127.0.0.1" }
    }

    override fun handleOnDestroy() {
        cleanupDevice()
        deviceManager.unregister()
    }
}

private fun UsbDeviceInfo.toJs(): JSObject {
    val fmts = JSONArray()
    (profile?.supportedFormats ?: listOf("MJPG", "YUY2")).forEach { fmts.put(it) }
    return JSObject().apply {
        put("deviceKey", deviceKey)
        put("name", displayName)
        put("isUvc", isUvc)
        put("vendorId", device.vendorId.toString(16).uppercase().padStart(4, '0'))
        put("productId", device.productId.toString(16).uppercase().padStart(4, '0'))
        put("maxWidth", profile?.maxResolutionWidth ?: 1920)
        put("maxHeight", profile?.maxResolutionHeight ?: 1080)
        put("maxFps", profile?.maxFps ?: 60)
        put("formats", fmts)
        put("hasAudio", profile?.supportsAudio ?: false)
    }
}
