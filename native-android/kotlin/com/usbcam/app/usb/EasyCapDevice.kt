package com.usbcam.app.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Non-UVC EasyCap-style analog capture devices (STK1160, EM2860, SMI2021,
 * UTVF007). These need chipset-specific vendor register writes to initialize
 * the bridge + SAA711x decoder before streaming raw YUY2 (720x576).
 */
class EasyCapDevice(
    private val device: UsbDevice,
    private val conn: UsbDeviceConnection,
    private val profile: DeviceProfile
) {
    companion object {
        private const val TAG = "EasyCapDevice"

        // PAL frame geometry common to these analog bridges.
        const val FRAME_WIDTH = 720
        const val FRAME_HEIGHT = 576
        const val FORMAT_YUY2 = 2

        private val FRAME_SIZE = FRAME_WIDTH * FRAME_HEIGHT * 2

        // Vendor request type for register writes (host-to-device | vendor | device).
        private const val RT_VENDOR_WRITE = 0x40
        private const val VENDOR_REQ = 0x01
    }

    var frameCallback: ((data: ByteArray, width: Int, height: Int, format: Int) -> Unit)? = null

    private var streamInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var interfaceClaimed = false

    private val running = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readJob: Job? = null

    fun init(): Boolean {
        try {
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                val ep = findInEndpoint(intf)
                if (ep != null) {
                    streamInterface = intf
                    inEndpoint = ep
                    break
                }
            }

            val intf = streamInterface
            if (intf == null || inEndpoint == null) {
                Log.e(TAG, "No IN bulk/isoc endpoint found")
                return false
            }

            if (!conn.claimInterface(intf, true)) {
                Log.e(TAG, "Failed to claim interface ${intf.id}")
                return false
            }
            interfaceClaimed = true

            runInitSequence()
            Log.d(TAG, "Initialized ${profile.name}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "init failed", e)
            return false
        }
    }

    private fun findInEndpoint(intf: UsbInterface): UsbEndpoint? {
        for (e in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(e)
            if (ep.direction != UsbConstants.USB_DIR_IN) continue
            val type = ep.type
            if (type == UsbConstants.USB_ENDPOINT_XFER_BULK ||
                type == UsbConstants.USB_ENDPOINT_XFER_ISOC
            ) {
                return ep
            }
        }
        return null
    }

    /** Vendor control write helper: conn.controlTransfer(0x40, 0x01, value, index, null, 0, 1000). */
    private fun cw(value: Int, index: Int): Int {
        val r = conn.controlTransfer(RT_VENDOR_WRITE, VENDOR_REQ, value, index, null, 0, 1000)
        if (r < 0) Log.w(TAG, "cw(0x%04X,0x%04X) failed (%d)".format(value, index, r))
        return r
    }

    private fun runInitSequence() {
        when (profile.deviceType) {
            DeviceType.EASYCAP_STK1160 -> {
                cw(0x0002, 0x0098)
                cw(0x0110, 0x0080)
                cw(0x0112, 0x0040)
                cw(0x0113, 0x0040)
                cw(0x0100, 0x0033)
            }
            DeviceType.EASYCAP_EM2860 -> {
                cw(0x08, 0xFF)
                Thread.sleep(50)
                cw(0x08, 0x00)
                cw(0x42, 0x22)
                cw(0x43, 0x00)
            }
            DeviceType.EASYCAP_SMI2021 -> {
                cw(0x0001, 0x00)
                Thread.sleep(100)
                cw(0x0001, 0x10)
                cw(0x0002, 0x04)
            }
            DeviceType.EASYCAP_UTVF007 -> {
                cw(0x0000, 0x0100)
                Thread.sleep(50)
            }
            else -> {
                Log.w(TAG, "No specific init sequence for ${profile.deviceType}")
            }
        }
    }

    fun startCapture() {
        if (running.get()) return
        val ep = inEndpoint ?: run {
            Log.e(TAG, "Cannot start capture: no IN endpoint")
            return
        }

        // Start streaming on the bridge.
        cw(0x0100, 0x0037)

        running.set(true)
        readJob = scope.launch {
            val buf = ByteArray(ep.maxPacketSize * 16)
            val frame = ByteArray(FRAME_SIZE)
            var pos = 0

            while (isActive && running.get()) {
                val read = conn.bulkTransfer(ep, buf, buf.size, 100)
                if (read <= 0) continue

                var offset = 0
                while (offset < read) {
                    val copy = minOf(read - offset, FRAME_SIZE - pos)
                    System.arraycopy(buf, offset, frame, pos, copy)
                    pos += copy
                    offset += copy

                    if (pos >= FRAME_SIZE) {
                        val data = frame.copyOf(FRAME_SIZE)
                        try {
                            frameCallback?.invoke(data, FRAME_WIDTH, FRAME_HEIGHT, FORMAT_YUY2)
                        } catch (e: Exception) {
                            Log.e(TAG, "frameCallback threw", e)
                        }
                        pos = 0
                    }
                }
            }
        }
        Log.d(TAG, "Capture started")
    }

    fun stopCapture() {
        if (!running.getAndSet(false)) return
        readJob?.cancel()
        readJob = null
        try {
            cw(0x0100, 0x0033)
        } catch (e: Exception) {
            Log.w(TAG, "stop register write failed", e)
        }
        Log.d(TAG, "Capture stopped")
    }

    fun release() {
        stopCapture()
        try {
            streamInterface?.let {
                if (interfaceClaimed) {
                    conn.releaseInterface(it)
                    interfaceClaimed = false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "releaseInterface failed", e)
        }
        try {
            scope.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "scope cancel failed", e)
        }
        try {
            conn.close()
        } catch (e: Exception) {
            Log.w(TAG, "conn close failed", e)
        }
        Log.d(TAG, "Released")
    }
}
