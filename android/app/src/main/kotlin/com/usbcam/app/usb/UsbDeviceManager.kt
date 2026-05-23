package com.usbcam.app.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log

class UsbDeviceManager(private val context: Context) {

    private val TAG = "UsbDeviceManager"
    private val ACTION_USB_PERMISSION = "com.usbcam.app.USB_PERMISSION"
    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var deviceAttachCallback: ((UsbDeviceInfo) -> Unit)? = null
    private var deviceDetachCallback: ((String) -> Unit)? = null
    private var permissionCallback: ((UsbDevice, Boolean) -> Unit)? = null

    data class UsbDeviceInfo(
        val device: UsbDevice,
        val profile: DeviceProfile?,
        val isUvc: Boolean,
        val displayName: String,
        val deviceKey: String
    )

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    device?.let { onDeviceAttached(it) }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    device?.let {
                        Log.d(TAG, "Device detached: ${it.deviceName}")
                        deviceDetachCallback?.invoke(it.deviceName)
                    }
                }
                ACTION_USB_PERMISSION -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    device?.let { permissionCallback?.invoke(it, granted) }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
    }

    fun unregister() {
        try { context.unregisterReceiver(usbReceiver) } catch (_: Exception) {}
    }

    fun setCallbacks(
        onAttach: (UsbDeviceInfo) -> Unit,
        onDetach: (String) -> Unit,
        onPermission: (UsbDevice, Boolean) -> Unit
    ) {
        deviceAttachCallback = onAttach
        deviceDetachCallback = onDetach
        permissionCallback = onPermission
    }

    fun getConnectedDevices(): List<UsbDeviceInfo> {
        return usbManager.deviceList.values.mapNotNull { device ->
            buildDeviceInfo(device)
        }
    }

    fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            permissionCallback?.invoke(device, true)
            return
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(ACTION_USB_PERMISSION),
            flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    fun openDevice(device: UsbDevice) = usbManager.openDevice(device)

    fun hasPermission(device: UsbDevice) = usbManager.hasPermission(device)

    private fun onDeviceAttached(device: UsbDevice) {
        Log.d(TAG, "Device attached: VID=${device.vendorId.toString(16).uppercase()} PID=${device.productId.toString(16).uppercase()} name=${device.deviceName}")
        val info = buildDeviceInfo(device) ?: return
        deviceAttachCallback?.invoke(info)
    }

    private fun buildDeviceInfo(device: UsbDevice): UsbDeviceInfo? {
        val vid = device.vendorId
        val pid = device.productId
        val profile = DeviceDatabase.findDevice(vid, pid)
        val isUvc = profile?.isUvc ?: isUvcDevice(device)
        val displayName = profile?.name ?: buildGenericName(device)

        return UsbDeviceInfo(
            device = device,
            profile = profile,
            isUvc = isUvc || profile != null,
            displayName = displayName,
            deviceKey = device.deviceName
        )
    }

    private fun isUvcDevice(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == 0x0E) return true
        }
        return false
    }

    private fun buildGenericName(device: UsbDevice): String {
        val manufacturer = device.manufacturerName ?: "Unknown"
        val product = device.productName ?: "USB Device"
        val vid = device.vendorId.toString(16).uppercase().padStart(4, '0')
        val pid = device.productId.toString(16).uppercase().padStart(4, '0')
        return "$manufacturer $product ($vid:$pid)"
    }
}
