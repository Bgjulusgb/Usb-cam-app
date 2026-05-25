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
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * USB Video Class (UVC) streaming over the Android USB Host API.
 *
 * Implements PROBE/COMMIT negotiation and parses the UVC payload header to
 * reassemble frames, emitting them via [frameCallback].
 */
class UvcCameraDevice(
    private val device: UsbDevice,
    private val conn: UsbDeviceConnection,
    private val profile: DeviceProfile?
) {
    companion object {
        private const val TAG = "UvcCameraDevice"

        const val FORMAT_MJPEG = 1
        const val FORMAT_YUY2 = 2
        const val FORMAT_H264 = 3
        const val FORMAT_H265 = 4
        const val FORMAT_NV12 = 5

        // UVC video streaming interface subclass.
        private const val SC_VIDEOCONTROL = 0x01
        private const val SC_VIDEOSTREAMING = 0x02

        // Class-specific request codes.
        private const val SET_CUR = 0x01
        private const val GET_CUR = 0x81

        // VideoStreaming control selectors.
        private const val VS_PROBE_CONTROL = 0x01
        private const val VS_COMMIT_CONTROL = 0x02

        // Control transfer request types.
        private const val RT_SET = 0x21  // host-to-device | class | interface
        private const val RT_GET = 0xA1  // device-to-host | class | interface

        // UVC payload header flags.
        private const val HDR_END_OF_FRAME = 0x02
        private const val HDR_ERROR = 0x40

        private const val PROBE_LEN = 34
    }

    var frameCallback: ((data: ByteArray, width: Int, height: Int, format: Int) -> Unit)? = null

    var width: Int = 640
    var height: Int = 480
    var fps: Int = 30
    var format: Int = FORMAT_MJPEG

    private var controlInterface: UsbInterface? = null
    private var streamingInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var streamingClaimed = false

    private val running = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readJob: Job? = null

    fun init(): Boolean {
        try {
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                if (intf.interfaceClass != UsbConstants.USB_CLASS_VIDEO) continue

                when (intf.interfaceSubclass) {
                    SC_VIDEOCONTROL -> {
                        if (controlInterface == null) controlInterface = intf
                    }
                    SC_VIDEOSTREAMING -> {
                        if (streamingInterface == null && intf.endpointCount > 0) {
                            val ep = findInEndpoint(intf)
                            if (ep != null) {
                                streamingInterface = intf
                                inEndpoint = ep
                            }
                        }
                    }
                }
            }

            val streaming = streamingInterface
            val ep = inEndpoint
            if (streaming == null || ep == null) {
                Log.e(TAG, "No UVC streaming interface / IN endpoint found")
                return false
            }

            if (!conn.claimInterface(streaming, true)) {
                Log.e(TAG, "Failed to claim streaming interface ${streaming.id}")
                return false
            }
            streamingClaimed = true
            Log.d(TAG, "Initialized UVC device ${profile?.name ?: device.deviceName}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "init failed", e)
            return false
        }
    }

    private fun findInEndpoint(intf: UsbInterface): UsbEndpoint? {
        for (e in 0 until intf.endpointCount) {
            val ep = intf.getEndpoint(e)
            if (ep.direction == UsbConstants.USB_DIR_IN) return ep
        }
        return null
    }

    fun setResolution(w: Int, h: Int, f: Int, fmt: Int = FORMAT_MJPEG): Boolean {
        width = w
        height = h
        fps = f
        format = fmt

        val streaming = streamingInterface ?: return false
        val index = streaming.id
        val interval = if (fps > 0) 10_000_000 / fps else 333_333

        val probe = buildProbeBuffer(fmt, interval, w, h)

        // SET_CUR on PROBE
        var r = conn.controlTransfer(
            RT_SET, SET_CUR, VS_PROBE_CONTROL shl 8, index, probe, probe.size, 1000
        )
        if (r < 0) {
            Log.w(TAG, "PROBE SET_CUR failed ($r)")
        }

        // GET_CUR on PROBE (device fills in negotiated values)
        val negotiated = ByteArray(PROBE_LEN)
        r = conn.controlTransfer(
            RT_GET, GET_CUR, VS_PROBE_CONTROL shl 8, index, negotiated, negotiated.size, 1000
        )
        if (r < 0) {
            Log.w(TAG, "PROBE GET_CUR failed ($r)")
        }

        // SET_CUR on COMMIT (use negotiated values if we got them, else our probe).
        val commit = if (r >= 0) negotiated else probe
        r = conn.controlTransfer(
            RT_SET, SET_CUR, VS_COMMIT_CONTROL shl 8, index, commit, commit.size, 1000
        )
        Log.d(TAG, "COMMIT result=$r for ${w}x$h@${fps}fps fmt=$fmt")
        return r >= 0
    }

    private fun buildProbeBuffer(fmt: Int, interval: Int, w: Int, h: Int): ByteArray {
        val buf = ByteBuffer.allocate(PROBE_LEN).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0x0001)                 // bmHint
        buf.put((fmt and 0xFF).toByte())     // bFormatIndex
        buf.put(1)                           // bFrameIndex
        buf.putInt(interval)                 // dwFrameInterval
        buf.putShort(0)                      // wKeyFrameRate
        buf.putShort(0)                      // wPFrameRate
        buf.putShort(0)                      // wCompQuality
        buf.putShort(0)                      // wCompWindowSize
        buf.putShort(0)                      // wDelay
        buf.putInt(w * h * 2)                // dwMaxVideoFrameSize
        buf.putInt(w * h / 2)                // dwMaxPayloadTransferSize
        // Remaining bytes (clock/frame-id fields) stay zero-filled.
        return buf.array()
    }

    fun startStream() {
        if (running.get()) return
        val ep = inEndpoint ?: run {
            Log.e(TAG, "Cannot start stream: no IN endpoint")
            return
        }
        running.set(true)
        readJob = scope.launch {
            val bufSize = maxOf(ep.maxPacketSize * 32, 65536)
            val buf = ByteArray(bufSize)
            val frame = ByteArrayOutputStream(width * height * 2)

            while (isActive && running.get()) {
                val read = conn.bulkTransfer(ep, buf, buf.size, 100)
                if (read <= 0) continue
                if (read < 2) continue

                val headerLen = buf[0].toInt() and 0xFF
                val flags = buf[1].toInt() and 0xFF

                if (headerLen <= 0 || headerLen > read) continue
                if ((flags and HDR_ERROR) != 0) {
                    // Discard the partial frame on a device-reported error.
                    frame.reset()
                    continue
                }

                val payloadLen = read - headerLen
                if (payloadLen > 0) {
                    frame.write(buf, headerLen, payloadLen)
                }

                if ((flags and HDR_END_OF_FRAME) != 0 && frame.size() > 0) {
                    val data = frame.toByteArray()
                    frame.reset()
                    try {
                        frameCallback?.invoke(data, width, height, format)
                    } catch (e: Exception) {
                        Log.e(TAG, "frameCallback threw", e)
                    }
                }
            }
        }
        Log.d(TAG, "Stream started")
    }

    fun stopStream() {
        if (!running.getAndSet(false)) return
        readJob?.cancel()
        readJob = null
        Log.d(TAG, "Stream stopped")
    }

    fun release() {
        stopStream()
        try {
            streamingInterface?.let {
                if (streamingClaimed) {
                    conn.releaseInterface(it)
                    streamingClaimed = false
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
