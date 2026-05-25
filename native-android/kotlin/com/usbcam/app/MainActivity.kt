package com.usbcam.app

import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.view.WindowCompat
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Register our Capacitor plugin BEFORE super.onCreate so the bridge picks it up.
        registerPlugin(UsbCameraPlugin::class.java)
        super.onCreate(savedInstanceState)

        // Android 15 enforces edge-to-edge for apps targeting API 35.
        // Capacitor 7's BridgeActivity already calls this internally, but we
        // set it explicitly here so the WebView's env(safe-area-inset-*) values
        // are populated correctly on all API levels ≥ 23.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        logUsbAttachIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        logUsbAttachIntent(intent)
    }

    private fun logUsbAttachIntent(intent: Intent?) {
        if (intent?.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
        val device = getDeviceExtra(intent)
        if (device != null) {
            Log.d(
                TAG,
                "USB attached via intent: VID=0x%04X PID=0x%04X (%s)".format(
                    device.vendorId and 0xFFFF,
                    device.productId and 0xFFFF,
                    device.deviceName,
                )
            )
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
}
