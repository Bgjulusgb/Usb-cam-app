package com.usbcam.v2.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
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
    private val TAG = "UsbDeviceManager"
    private val ACTION_USB_PERMISSION = "com.usbcam.v2.USB_PERMISSION"
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    var onAttach: ((UsbDeviceInfo) -> Unit)? = null
    var onDetach: ((String) -> Unit)? = null
    var onPermission: ((UsbDevice, Boolean) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val device = getDevice(intent) ?: return
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> buildInfo(device)?.let { onAttach?.invoke(it) }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> onDetach?.invoke(device.deviceName)
                ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    onPermission?.invoke(device, granted)
                }
            }
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    fun unregister() = try { context.unregisterReceiver(receiver) } catch (_: Exception) {}

    fun getConnectedDevices() = usbManager.deviceList.values.mapNotNull { buildInfo(it) }

    fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) { onPermission?.invoke(device, true); return }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), flags)
        usbManager.requestPermission(device, pi)
    }

    fun openDevice(device: UsbDevice) = usbManager.openDevice(device)
    fun hasPermission(device: UsbDevice) = usbManager.hasPermission(device)

    private fun buildInfo(device: UsbDevice): UsbDeviceInfo? {
        val profile = DeviceDatabase.findByVidPid(device.vendorId, device.productId)
        val isUvc = profile?.isUvc ?: isUvcClass(device)
        if (!isUvc && profile == null) {
            Log.d(TAG, "Skipping unknown non-UVC device ${hex4(device.vendorId)}:${hex4(device.productId)}")
        }
        val name = profile?.name ?: buildName(device)
        return UsbDeviceInfo(device, profile, isUvc || profile != null, name, device.deviceName)
    }

    private fun isUvcClass(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            if (device.getInterface(i).interfaceClass == 0x0E) return true
        }
        return false
    }

    private fun buildName(device: UsbDevice): String {
        val mfr = device.manufacturerName ?: "Unknown"
        val prod = device.productName ?: "USB Device"
        return "$mfr $prod (${hex4(device.vendorId)}:${hex4(device.productId)})"
    }

    private fun getDevice(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        else
            @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

    private fun hex4(v: Int) = v.toString(16).uppercase().padStart(4, '0')
}
