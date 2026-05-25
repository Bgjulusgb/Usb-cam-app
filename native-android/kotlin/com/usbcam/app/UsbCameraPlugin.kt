package com.usbcam.app

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import com.usbcam.app.camera.FrameConverter
import com.usbcam.app.camera.MjpegStreamServer
import com.usbcam.app.camera.VideoRecorder
import com.usbcam.app.usb.EasyCapDevice
import com.usbcam.app.usb.UsbDeviceInfo
import com.usbcam.app.usb.UsbDeviceManager
import com.usbcam.app.usb.UvcNativeCamera
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@CapacitorPlugin(
    name = "UsbCamera",
    permissions = [
        Permission(strings = [Manifest.permission.RECORD_AUDIO], alias = "microphone"),
        Permission(strings = [Manifest.permission.READ_MEDIA_VIDEO], alias = "mediaVideo"),
        Permission(strings = [Manifest.permission.READ_MEDIA_IMAGES], alias = "mediaImages"),
        Permission(strings = [Manifest.permission.POST_NOTIFICATIONS], alias = "notifications"),
    ]
)
class UsbCameraPlugin : Plugin() {
    private val TAG = "UsbCameraPlugin"
    private val PREVIEW_PORT = 8080
    private val OPEN_TIMEOUT_MS = 12_000L

    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var deviceManager: UsbDeviceManager
    private var uvcNative: UvcNativeCamera? = null
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
        activeKey = key

        if (info.isUvc) {
            openUvcNative(info, call)
        } else {
            openEasyCap(info, call)
        }
    }

    /** UVC devices stream through the native libusb/libuvc backend. */
    private fun openUvcNative(info: UsbDeviceInfo, call: PluginCall) {
        val cam = UvcNativeCamera().also { it.onNv21Frame = ::onNativeFrame }
        uvcNative = cam

        val resolved = AtomicBoolean(false)
        val timeout = Runnable {
            if (resolved.compareAndSet(false, true)) {
                cleanupDevice()
                notifyError("Zeitüberschreitung beim Öffnen der USB-Kamera")
                call.reject("Timeout opening camera")
            }
        }
        mainHandler.postDelayed(timeout, OPEN_TIMEOUT_MS)

        // CameraHelper requires main-thread interaction.
        activity.runOnUiThread {
            cam.open(info.device) { ok, err ->
                if (!resolved.compareAndSet(false, true)) return@open
                mainHandler.removeCallbacks(timeout)
                if (ok) {
                    call.resolve(JSObject().put("success", true))
                } else {
                    cleanupDevice()
                    val msg = err ?: "Kamera konnte nicht geöffnet werden"
                    notifyError(msg)
                    call.reject(msg)
                }
            }
        }
    }

    /** Non-UVC analog EasyCap path (best-effort; see note in README). */
    private fun openEasyCap(info: UsbDeviceInfo, call: PluginCall) {
        val profile = info.profile ?: run {
            cleanupDevice(); return call.reject("No profile for non-UVC device")
        }
        // Honest expectation management: raw analog bridges (STK1160/SMI2021/
        // EM2860) are not UVC devices and usually need a kernel-level driver, so
        // they may not produce an image on a non-rooted phone.
        notifyError(
            "Hinweis: „${profile.name}“ ist ein analoger Stick (kein UVC). " +
            "Auf Android ohne Root liefert er oft kein Bild."
        )
        val conn = deviceManager.openDevice(info.device) ?: run {
            cleanupDevice(); return call.reject("Cannot open device")
        }
        val dev = EasyCapDevice(info.device, conn, profile).also {
            it.frameCallback = ::onFrame; easyCap = it
        }
        if (dev.init()) {
            call.resolve(JSObject().put("success", true))
        } else {
            cleanupDevice()
            call.reject("Failed to initialize device")
        }
    }

    // ─── Preview (MJPEG server) ──────────────────────────────────────────────

    @PluginMethod
    fun startPreview(call: PluginCall) {
        val w   = call.getInt("width")  ?: 640
        val h   = call.getInt("height") ?: 480
        val lan = call.getBoolean("lanAccessible") ?: false

        mjpeg?.stop()
        mjpeg = MjpegStreamServer(PREVIEW_PORT, lan).also { it.start() }

        uvcNative?.let { cam -> activity.runOnUiThread { cam.startPreview(w, h) } }
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
        uvcNative?.let { cam -> activity.runOnUiThread { cam.stopPreview() } }
        easyCap?.stopCapture()
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
        val w   = uvcNative?.width  ?: EasyCapDevice.FRAME_WIDTH
        val h   = uvcNative?.height ?: EasyCapDevice.FRAME_HEIGHT
        val fps = 30
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

    // ─── Runtime permissions (Android 13+ / API 33+) ─────────────────────────

    @PluginMethod
    fun requestAppPermissions(call: PluginCall) {
        val aliases = mutableListOf("microphone")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            aliases += listOf("notifications", "mediaVideo", "mediaImages")
        }
        requestPermissionForAliases(aliases.toTypedArray(), call, "appPermissionCallback")
    }

    @PermissionCallback
    private fun appPermissionCallback(call: PluginCall) {
        val mic = getPermissionState("microphone") == PermissionState.GRANTED
        call.resolve(JSObject().put("granted", mic).put("microphone", mic))
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

    private fun onNativeFrame(data: ByteArray, w: Int, h: Int) {
        onFrame(data, w, h, FrameConverter.FORMAT_NV21)
    }

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

    private fun notifyError(message: String) {
        notifyListeners("error", JSObject().put("message", message))
    }

    private fun cleanupDevice() {
        recording = false
        recorder?.stop(); recorder = null
        recordingPath = null
        mjpeg?.stop(); mjpeg = null
        uvcNative?.let { cam -> activity.runOnUiThread { cam.close() } }
        uvcNative = null
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
