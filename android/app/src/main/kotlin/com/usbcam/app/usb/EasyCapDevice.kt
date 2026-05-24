package com.usbcam.app.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class EasyCapDevice(
    private val device: UsbDevice,
    private val connection: UsbDeviceConnection,
    private val profile: DeviceProfile
) {
    private val TAG = "EasyCapDevice"
    private var isStreaming = AtomicBoolean(false)
    private var streamJob: Job? = null
    private var frameCallback: ((ByteArray, Int, Int, Int) -> Unit)? = null
    private var videoInterface: UsbInterface? = null
    private var videoEndpoint: UsbEndpoint? = null
    private var audioEndpoint: UsbEndpoint? = null

    companion object {
        // SAA7113 register addresses
        const val SAA7113_ADDR = 0x4A
        const val REG_STATUS_BYTE_1 = 0x1F
        const val REG_GLOBAL_RESET = 0x88

        // STK1160 specific
        const val STK1160_BRIGHTNESS = 0x0110
        const val STK1160_CONTRAST = 0x0112
        const val STK1160_SATURATION = 0x0113

        // Video standards
        const val VIDEO_NTSC = 0
        const val VIDEO_PAL = 1
        const val VIDEO_SECAM = 2

        const val FORMAT_YUY2 = 2
    }

    fun init(): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == UsbConstants.USB_CLASS_VIDEO ||
                iface.interfaceClass == UsbConstants.USB_CLASS_VENDOR_SPEC) {
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    when {
                        ep.direction == UsbConstants.USB_DIR_IN &&
                        (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK ||
                         ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC) -> {
                            videoEndpoint = ep
                            videoInterface = iface
                        }
                    }
                }
            }
            if (iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO) {
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.direction == UsbConstants.USB_DIR_IN) audioEndpoint = ep
                }
            }
        }

        val claimed = videoInterface?.let { connection.claimInterface(it, true) } ?: false
        if (claimed) initDevice()
        return claimed
    }

    private fun initDevice() {
        when (profile.deviceType) {
            DeviceType.EASYCAP_STK1160 -> initStk1160()
            DeviceType.EASYCAP_EM2860 -> initEm2860()
            DeviceType.EASYCAP_SMI2021 -> initSmi2021()
            DeviceType.EASYCAP_UTVF007 -> initUtvf007()
            else -> {}
        }
    }

    private fun initStk1160() {
        Log.d(TAG, "Initializing STK1160")
        // Set video format - PAL 720x576 @ 25fps
        controlWrite(0x0002, 0x0098)  // Video mode: PAL
        controlWrite(0x0110, 0x0080)  // Brightness
        controlWrite(0x0112, 0x0040)  // Contrast
        controlWrite(0x0113, 0x0040)  // Saturation
        controlWrite(0x0114, 0x0000)  // Hue
        // Enable analog video
        controlWrite(0x0100, 0x0033)
        Log.d(TAG, "STK1160 initialized for PAL")
    }

    private fun initEm2860() {
        Log.d(TAG, "Initializing EM2860")
        // EM2860 initialization sequence
        controlWrite(0x08, 0xFF)  // Reset
        Thread.sleep(50)
        controlWrite(0x08, 0x00)
        controlWrite(0x42, 0x22)  // Format: PAL
        controlWrite(0x43, 0x00)  // Input: composite
        Log.d(TAG, "EM2860 initialized")
    }

    private fun initSmi2021() {
        Log.d(TAG, "Initializing SMI2021")
        // SMI2021 setup
        controlWrite(0x0001, 0x00)  // Reset
        Thread.sleep(100)
        controlWrite(0x0001, 0x10)  // Enable
        controlWrite(0x0002, 0x04)  // PAL
        Log.d(TAG, "SMI2021 initialized")
    }

    private fun initUtvf007() {
        Log.d(TAG, "Initializing UTVF007")
        // UTVF007 basic init
        controlWrite(0x0000, 0x0100)
        Thread.sleep(50)
        Log.d(TAG, "UTVF007 initialized")
    }

    private fun controlWrite(value: Int, index: Int): Int {
        return connection.controlTransfer(
            0x40, 0x01,
            value, index,
            null, 0, 1000
        )
    }

    fun setFrameCallback(callback: (ByteArray, Int, Int, Int) -> Unit) {
        frameCallback = callback
    }

    fun startCapture() {
        if (isStreaming.getAndSet(true)) return
        val ep = videoEndpoint ?: run {
            Log.e(TAG, "No video endpoint")
            isStreaming.set(false)
            return
        }

        // Signal start to device
        controlWrite(0x0100, 0x0037)

        streamJob = CoroutineScope(Dispatchers.IO).launch {
            val frameSize = 720 * 576 * 2  // YUY2 frame
            val bufSize = ep.maxPacketSize * 16
            val buffer = ByteArray(bufSize)
            val accumulator = ByteArray(frameSize + 4096)
            var accPos = 0

            while (isStreaming.get()) {
                val read = connection.bulkTransfer(ep, buffer, buffer.size, 200)
                if (read > 0) {
                    val remaining = minOf(read, frameSize - accPos)
                    System.arraycopy(buffer, 0, accumulator, accPos, remaining)
                    accPos += remaining

                    if (accPos >= frameSize) {
                        val frame = accumulator.copyOf(frameSize)
                        frameCallback?.invoke(frame, 720, 576, FORMAT_YUY2)
                        accPos = 0
                    }
                }
            }
        }
    }

    fun stopCapture() {
        isStreaming.set(false)
        streamJob?.cancel()
        controlWrite(0x0100, 0x0033)
    }

    fun release() {
        stopCapture()
        videoInterface?.let { connection.releaseInterface(it) }
        connection.close()
    }
}
