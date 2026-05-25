package com.usbcam.app.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

data class UsbDeviceInfo(
    val device: UsbDevice,
    val profile: DeviceProfile?,
    val isUvc: Boolean,
    val displayName: String,
    val deviceKey: String
)

class UsbDeviceManager(private val context: Context) {

    companion object {
        private const val TAG = "UsbDeviceManager"
        const val ACTION_USB_PERMISSION = "com.usbcam.app.USB_PERMISSION"
    }

    private val usbManager: UsbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager

    var onAttach: ((UsbDeviceInfo) -> Unit)? = null
    var onDetach: ((String) -> Unit)? = null
    var onPermission: ((UsbDevice, Boolean) -> Unit)? = null

    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            val device = getDeviceExtra(intent)
            when (action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    if (device != null) {
                        val info = buildInfo(device)
                        if (info != null) {
                            Log.d(TAG, "Attached: ${info.displayName}")
                            onAttach?.invoke(info)
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    if (device != null) {
                        Log.d(TAG, "Detached: ${device.deviceName}")
                        onDetach?.invoke(device.deviceName)
                    }
                }
                ACTION_USB_PERMISSION -> {
                    val granted =
                        intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (device != null) {
                        Log.d(TAG, "Permission for ${device.deviceName}: $granted")
                        onPermission?.invoke(device, granted)
                    }
                }
            }
        }
    }

    private fun getDeviceExtra(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        registered = true
    }

    fun unregister() {
        if (!registered) return
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver not registered", e)
        }
        registered = false
    }

    fun getConnectedDevices(): List<UsbDeviceInfo> {
        return usbManager.deviceList.values.mapNotNull { buildInfo(it) }
    }

    fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            onPermission?.invoke(device, true)
            return
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
        }
        val pending = PendingIntent.getBroadcast(context, 0, intent, flags)
        usbManager.requestPermission(device, pending)
    }

    fun openDevice(device: UsbDevice): UsbDeviceConnection? {
        return try {
            usbManager.openDevice(device)
        } catch (e: Exception) {
            Log.e(TAG, "openDevice failed for ${device.deviceName}", e)
            null
        }
    }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    private fun buildInfo(device: UsbDevice): UsbDeviceInfo? {
        val profile = DeviceDatabase.findByVidPid(device.vendorId, device.productId)

        var hasUvcInterface = false
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == UsbConstants.USB_CLASS_VIDEO) {
                hasUvcInterface = true
                break
            }
        }
        val isUvc = profile?.isUvc ?: hasUvcInterface

        // Skip unrelated devices (keyboards, hubs, etc.) but keep known non-UVC profiles.
        if (!isUvc && profile == null) {
            return null
        }

        val displayName = profile?.name ?: run {
            val manufacturer = device.manufacturerName ?: "Unknown"
            val product = device.productName ?: "USB Device"
            val vid = String.format("%04X", device.vendorId and 0xFFFF)
            val pid = String.format("%04X", device.productId and 0xFFFF)
            "$manufacturer $product ($vid:$pid)"
        }

        return UsbDeviceInfo(
            device = device,
            profile = profile,
            isUvc = isUvc,
            displayName = displayName,
            deviceKey = device.deviceName
        )
    }
}
