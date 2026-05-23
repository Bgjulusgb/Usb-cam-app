package com.usbcam.app

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.usbcam.app.camera.CameraController
import com.usbcam.app.usb.UsbDeviceManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : FlutterActivity() {
    private val TAG = "MainActivity"
    private val CHANNEL = "com.usbcam.app/camera"
    private val EVENT_CHANNEL = "com.usbcam.app/camera/events"

    private lateinit var cameraController: CameraController
    private var eventSink: EventChannel.EventSink? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        cameraController = CameraController(this).apply {
            onDeviceConnected = { info ->
                runOnUiThread {
                    eventSink?.success(mapOf("type" to "deviceConnected", "data" to info))
                }
            }
            onDeviceDisconnected = { key ->
                runOnUiThread {
                    eventSink?.success(mapOf("type" to "deviceDisconnected", "deviceKey" to key))
                }
            }
            onFrame = { data, width, height, format ->
                // Frames go directly to texture - not through event channel
                // This is handled by the texture renderer
            }
            onRecordingState = { isRecording, path ->
                runOnUiThread {
                    eventSink?.success(mapOf(
                        "type" to "recordingState",
                        "isRecording" to isRecording,
                        "path" to (path ?: "")
                    ))
                }
            }
            onError = { message ->
                runOnUiThread {
                    eventSink?.success(mapOf("type" to "error", "message" to message))
                }
            }
        }
        cameraController.initialize()

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(args: Any?, sink: EventChannel.EventSink?) {
                    eventSink = sink
                }
                override fun onCancel(args: Any?) { eventSink = null }
            })

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getConnectedDevices" -> {
                        result.success(cameraController.getConnectedDevices())
                    }
                    "openDevice" -> {
                        val deviceKey = call.argument<String>("deviceKey") ?: ""
                        val matchingInfo = findDeviceInfoByKey(deviceKey)
                        if (matchingInfo != null) {
                            val success = cameraController.openDevice(matchingInfo)
                            result.success(success)
                        } else {
                            result.error("NOT_FOUND", "Device not found: $deviceKey", null)
                        }
                    }
                    "startPreview" -> {
                        val width = call.argument<Int>("width") ?: 640
                        val height = call.argument<Int>("height") ?: 480
                        val fps = call.argument<Int>("fps") ?: 30
                        val format = call.argument<String>("format") ?: "MJPG"
                        cameraController.startPreview(width, height, fps, format)
                        result.success(true)
                    }
                    "stopPreview" -> {
                        cameraController.stopPreview()
                        result.success(true)
                    }
                    "startRecording" -> {
                        val path = generateVideoPath()
                        val success = cameraController.startRecording(path)
                        result.success(if (success) path else null)
                    }
                    "stopRecording" -> {
                        cameraController.stopRecording()
                        result.success(true)
                    }
                    "capturePhoto" -> {
                        val path = generatePhotoPath()
                        val success = cameraController.capturePhoto(path)
                        result.success(if (success) path else null)
                    }
                    "disconnect" -> {
                        cameraController.disconnect()
                        result.success(true)
                    }
                    "getStoragePath" -> {
                        result.success(getExternalFilesDir(null)?.absolutePath ?: filesDir.absolutePath)
                    }
                    "requestPermission" -> {
                        val deviceKey = call.argument<String>("deviceKey") ?: ""
                        val info = findDeviceInfoByKey(deviceKey)
                        if (info != null) {
                            val usbManager = getSystemService(USB_SERVICE) as UsbManager
                            usbManager.requestPermission(
                                info.device,
                                android.app.PendingIntent.getBroadcast(
                                    this,
                                    0,
                                    Intent("com.usbcam.app.USB_PERMISSION"),
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                        android.app.PendingIntent.FLAG_MUTABLE
                                    else 0
                                )
                            )
                            result.success(true)
                        } else {
                            result.error("NOT_FOUND", "Device not found", null)
                        }
                    }
                    else -> result.notImplemented()
                }
            }

        // Handle USB device attached via intent
        handleUsbIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUsbIntent(intent)
    }

    private fun handleUsbIntent(intent: Intent) {
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            device?.let {
                Log.d(TAG, "USB attached via intent: ${it.deviceName}")
            }
        }
    }

    private fun findDeviceInfoByKey(key: String): UsbDeviceManager.UsbDeviceInfo? {
        val deviceManager = UsbDeviceManager(this)
        return deviceManager.getConnectedDevices().find { it.deviceKey == key }
    }

    private fun generateVideoPath(): String {
        val dir = File(getExternalFilesDir(null), "Videos").also { it.mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${dir.absolutePath}/VID_$timestamp.mp4"
    }

    private fun generatePhotoPath(): String {
        val dir = File(getExternalFilesDir(null), "Images").also { it.mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "${dir.absolutePath}/IMG_$timestamp.jpg"
    }

    override fun onDestroy() {
        cameraController.destroy()
        super.onDestroy()
    }
}
