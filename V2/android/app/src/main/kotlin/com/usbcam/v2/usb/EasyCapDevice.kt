package com.usbcam.v2.usb

import android.hardware.usb.*
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class EasyCapDevice(
    private val device: UsbDevice,
    private val conn: UsbDeviceConnection,
    private val profile: DeviceProfile
) {
    private val TAG = "EasyCap"
    private val streaming = AtomicBoolean(false)
    private var job: Job? = null
    private var videoIface: UsbInterface? = null
    private var videoEp: UsbEndpoint? = null
    var frameCallback: ((ByteArray, Int, Int, Int) -> Unit)? = null

    fun init(): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                if (ep.direction == UsbConstants.USB_DIR_IN &&
                    (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK || ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC)) {
                    videoEp = ep; videoIface = iface; break
                }
            }
            if (videoEp != null) break
        }
        val claimed = videoIface?.let { conn.claimInterface(it, true) } ?: false
        if (claimed) initChip()
        return claimed
    }

    private fun initChip() {
        when (profile.deviceType) {
            DeviceType.EASYCAP_STK1160 -> {
                cw(0x0002, 0x0098); cw(0x0110, 0x0080)
                cw(0x0112, 0x0040); cw(0x0113, 0x0040)
                cw(0x0100, 0x0033)
                Log.d(TAG, "STK1160 init done (PAL 720×576)")
            }
            DeviceType.EASYCAP_EM2860 -> {
                cw(0x08, 0xFF); Thread.sleep(50); cw(0x08, 0x00)
                cw(0x42, 0x22); cw(0x43, 0x00)
                Log.d(TAG, "EM2860 init done")
            }
            DeviceType.EASYCAP_SMI2021 -> {
                cw(0x0001, 0x00); Thread.sleep(100); cw(0x0001, 0x10); cw(0x0002, 0x04)
                Log.d(TAG, "SMI2021 init done")
            }
            DeviceType.EASYCAP_UTVF007 -> {
                cw(0x0000, 0x0100); Thread.sleep(50)
                Log.d(TAG, "UTVF007 init done")
            }
            else -> {}
        }
    }

    private fun cw(value: Int, index: Int) =
        conn.controlTransfer(0x40, 0x01, value, index, null, 0, 1000)

    fun startCapture() {
        if (streaming.getAndSet(true)) return
        val ep = videoEp ?: run { streaming.set(false); return }
        cw(0x0100, 0x0037)
        job = CoroutineScope(Dispatchers.IO).launch {
            val frameSize = 720 * 576 * 2
            val buf = ByteArray(ep.maxPacketSize * 16)
            val acc = ByteArray(frameSize + 4096)
            var pos = 0
            while (streaming.get()) {
                val n = conn.bulkTransfer(ep, buf, buf.size, 200)
                if (n > 0) {
                    val copy = minOf(n, frameSize - pos)
                    System.arraycopy(buf, 0, acc, pos, copy); pos += copy
                    if (pos >= frameSize) {
                        frameCallback?.invoke(acc.copyOf(frameSize), 720, 576, 2)
                        pos = 0
                    }
                }
            }
        }
    }

    fun stopCapture() {
        streaming.set(false); job?.cancel()
        cw(0x0100, 0x0033)
    }

    fun release() {
        stopCapture()
        videoIface?.let { conn.releaseInterface(it) }
        conn.close()
    }
}
