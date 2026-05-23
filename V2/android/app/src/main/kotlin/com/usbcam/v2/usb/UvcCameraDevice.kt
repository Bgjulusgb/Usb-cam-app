package com.usbcam.v2.usb

import android.hardware.usb.*
import android.util.Log
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class UvcCameraDevice(
    private val device: UsbDevice,
    private val conn: UsbDeviceConnection,
    val profile: DeviceProfile?
) {
    private val TAG = "UvcCamera"

    companion object {
        const val FORMAT_MJPEG = 1; const val FORMAT_YUY2 = 2
        const val FORMAT_H264 = 3;  const val FORMAT_H265 = 4; const val FORMAT_NV12 = 5
        private const val UVC_SET_CUR = 0x01; private const val UVC_GET_CUR = 0x81
        private const val UVC_VS_PROBE = 0x01; private const val UVC_VS_COMMIT = 0x02
        private const val PROBE_SIZE = 34
    }

    private var controlIface: UsbInterface? = null
    private var streamIface: UsbInterface? = null
    private var endpoint: UsbEndpoint? = null
    private val streaming = AtomicBoolean(false)
    private var job: Job? = null

    var frameCallback: ((ByteArray, Int, Int, Int) -> Unit)? = null
    var width = 640; var height = 480; var fps = 30; var format = FORMAT_MJPEG

    fun init(): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            when {
                iface.interfaceClass == 0x0E && iface.interfaceSubclass == 1 -> controlIface = iface
                iface.interfaceClass == 0x0E && iface.interfaceSubclass == 2 && iface.endpointCount > 0 -> streamIface = iface
            }
        }
        val si = streamIface ?: run { Log.e(TAG, "No streaming interface"); return false }
        for (i in 0 until si.endpointCount) {
            val ep = si.getEndpoint(i)
            if (ep.direction == UsbConstants.USB_DIR_IN) {
                endpoint = ep; break
            }
        }
        return conn.claimInterface(si, true).also { if (!it) Log.e(TAG, "Cannot claim interface") }
    }

    fun setResolution(w: Int, h: Int, f: Int, fmt: Int = FORMAT_MJPEG): Boolean {
        width = w; height = h; fps = f; format = fmt
        return negotiateFormat(w, h, f, fmt)
    }

    private fun negotiateFormat(w: Int, h: Int, f: Int, fmt: Int): Boolean {
        val probe = buildProbe(w, h, f, fmt)
        conn.controlTransfer(0x21, UVC_SET_CUR, (UVC_VS_PROBE shl 8), streamIface?.id ?: 1, probe, probe.size, 5000)
        val resp = ByteArray(PROBE_SIZE)
        conn.controlTransfer(0xA1, UVC_GET_CUR, (UVC_VS_PROBE shl 8), streamIface?.id ?: 1, resp, resp.size, 5000)
        val r = conn.controlTransfer(0x21, UVC_SET_CUR, (UVC_VS_COMMIT shl 8), streamIface?.id ?: 1, resp, resp.size, 5000)
        return r >= 0
    }

    private fun buildProbe(w: Int, h: Int, fps: Int, fmt: Int): ByteArray {
        val buf = ByteBuffer.allocate(PROBE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0x0001); buf.put(fmt.toByte()); buf.put(1)
        buf.putInt((10_000_000 / fps).toInt())
        repeat(6) { buf.putShort(0) }
        buf.putInt(w * h * 2); buf.putInt(w * h / 2)
        repeat(8) { buf.put(0) }
        return buf.array()
    }

    fun startStream() {
        if (streaming.getAndSet(true)) return
        val ep = endpoint ?: run { streaming.set(false); return }
        job = CoroutineScope(Dispatchers.IO).launch {
            val bufSize = maxOf(ep.maxPacketSize * 32, 65536)
            val buf = ByteArray(bufSize)
            val frame = mutableListOf<Byte>()
            while (streaming.get()) {
                val n = conn.bulkTransfer(ep, buf, bufSize, 100)
                if (n > 1) {
                    val hLen = buf[0].toInt() and 0xFF
                    val flags = buf[1].toInt() and 0xFF
                    if (hLen in 2..n && (flags and 0x40) == 0) {
                        for (i in hLen until n) frame.add(buf[i])
                        if ((flags and 0x02) != 0 && frame.isNotEmpty()) {
                            frameCallback?.invoke(frame.toByteArray(), width, height, format)
                            frame.clear()
                        }
                    }
                }
            }
        }
    }

    fun stopStream() { streaming.set(false); job?.cancel(); job = null }

    fun release() {
        stopStream()
        streamIface?.let { conn.releaseInterface(it) }
        conn.close()
    }
}
