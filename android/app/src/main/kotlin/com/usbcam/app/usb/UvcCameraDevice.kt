package com.usbcam.app.usb

import android.graphics.SurfaceTexture
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*

class UvcCameraDevice(
    private val device: UsbDevice,
    private val connection: UsbDeviceConnection,
    private val profile: DeviceProfile?
) {
    private val TAG = "UvcCameraDevice"

    // UVC request codes
    companion object {
        const val UVC_RC_UNDEFINED = 0x00
        const val UVC_SET_CUR = 0x01
        const val UVC_GET_CUR = 0x81
        const val UVC_GET_MIN = 0x82
        const val UVC_GET_MAX = 0x83
        const val UVC_GET_RES = 0x84
        const val UVC_GET_LEN = 0x85
        const val UVC_GET_DEF = 0x87

        // VS selectors
        const val UVC_VS_PROBE_CONTROL = 0x01
        const val UVC_VS_COMMIT_CONTROL = 0x02

        // Frame formats
        const val FORMAT_MJPEG = 1
        const val FORMAT_YUY2 = 2
        const val FORMAT_H264 = 3
        const val FORMAT_H265 = 4
        const val FORMAT_NV12 = 5

        const val UVC_PROBE_SIZE = 34
    }

    private var videoInterface: UsbInterface? = null
    private var videoEndpoint: UsbEndpoint? = null
    private var controlInterface: UsbInterface? = null
    private var isStreaming = AtomicBoolean(false)
    private var streamingJob: Job? = null
    private var frameCallback: ((ByteArray, Int, Int, Int) -> Unit)? = null

    var currentWidth = 640
    var currentHeight = 480
    var currentFps = 30
    var currentFormat = FORMAT_MJPEG

    fun init(): Boolean {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            when (iface.interfaceClass) {
                0x0E -> when (iface.interfaceSubclass) {
                    0x01 -> controlInterface = iface
                    0x02 -> if (iface.endpointCount > 0) videoInterface = iface
                }
            }
        }
        if (videoInterface == null) {
            Log.e(TAG, "No video streaming interface found")
            return false
        }
        for (i in 0 until videoInterface!!.endpointCount) {
            val ep = videoInterface!!.getEndpoint(i)
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_ISOC && ep.direction == UsbConstants.USB_DIR_IN) {
                videoEndpoint = ep
                break
            }
            if (ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK && ep.direction == UsbConstants.USB_DIR_IN) {
                videoEndpoint = ep
            }
        }
        return connection.claimInterface(videoInterface, true)
    }

    fun setFrameCallback(callback: (ByteArray, Int, Int, Int) -> Unit) {
        frameCallback = callback
    }

    fun setResolution(width: Int, height: Int, fps: Int, format: Int = FORMAT_MJPEG): Boolean {
        currentWidth = width
        currentHeight = height
        currentFps = fps
        currentFormat = format
        return negotiateFormat(width, height, fps, format)
    }

    private fun negotiateFormat(width: Int, height: Int, fps: Int, format: Int): Boolean {
        val probe = buildProbeCommitData(width, height, fps, format)

        // SET_CUR probe
        val result = connection.controlTransfer(
            0x21, UVC_SET_CUR,
            (UVC_VS_PROBE_CONTROL shl 8).toShort().toInt(),
            getVideoStreamingInterfaceIndex(),
            probe, probe.size, 5000
        )
        if (result < 0) {
            Log.w(TAG, "Probe SET_CUR failed: $result, continuing anyway")
        }

        // GET_CUR probe
        val probeResult = ByteArray(UVC_PROBE_SIZE)
        connection.controlTransfer(
            0xA1, UVC_GET_CUR,
            (UVC_VS_PROBE_CONTROL shl 8).toShort().toInt(),
            getVideoStreamingInterfaceIndex(),
            probeResult, probeResult.size, 5000
        )

        // COMMIT
        val commitResult = connection.controlTransfer(
            0x21, UVC_SET_CUR,
            (UVC_VS_COMMIT_CONTROL shl 8).toShort().toInt(),
            getVideoStreamingInterfaceIndex(),
            probeResult, probeResult.size, 5000
        )
        Log.d(TAG, "Format negotiation commit: $commitResult")
        return commitResult >= 0
    }

    private fun buildProbeCommitData(width: Int, height: Int, fps: Int, format: Int): ByteArray {
        val buf = ByteBuffer.allocate(UVC_PROBE_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(0x0001) // bmHint: dwFrameInterval
        buf.put(format.toByte()) // bFormatIndex
        buf.put(1) // bFrameIndex
        val interval = (10_000_000 / fps).toLong()
        buf.putInt(interval.toInt()) // dwFrameInterval
        buf.putShort(0) // wKeyFrameRate
        buf.putShort(0) // wPFrameRate
        buf.putShort(0) // wCompQuality
        buf.putShort(0) // wCompWindowSize
        buf.putShort(100) // wDelay
        val frameSize = width * height * 2
        buf.putInt(frameSize) // dwMaxVideoFrameSize
        buf.putInt(frameSize / 4) // dwMaxPayloadTransferSize
        buf.putInt(0) // dwClockFrequency
        buf.put(0) // bmFramingInfo
        buf.put(0) // bPreferedVersion
        buf.put(0) // bMinVersion
        buf.put(0) // bMaxVersion
        buf.put(0) // bUsage
        buf.put(0) // bBitDepthLuma
        buf.put(0) // bmSettings
        buf.put(0) // bMaxNumberOfRefFramesPlus1
        buf.putShort(0) // bmRateControlModes
        buf.putLong(0) // bmLayoutPerStream
        return buf.array()
    }

    fun startStream() {
        if (isStreaming.getAndSet(true)) return
        val ep = videoEndpoint ?: run {
            Log.e(TAG, "No endpoint for streaming")
            isStreaming.set(false)
            return
        }
        streamingJob = CoroutineScope(Dispatchers.IO).launch {
            val bufSize = maxOf(ep.maxPacketSize * 32, 16384)
            val buffer = ByteArray(bufSize)
            val frameBuffer = mutableListOf<Byte>()
            var inFrame = false

            while (isStreaming.get()) {
                val transferred = connection.bulkTransfer(ep, buffer, bufSize, 100)
                if (transferred > 0) {
                    processUvcPayload(buffer, transferred, frameBuffer) { frame ->
                        frameCallback?.invoke(frame, currentWidth, currentHeight, currentFormat)
                    }
                }
            }
        }
    }

    fun stopStream() {
        isStreaming.set(false)
        streamingJob?.cancel()
        streamingJob = null
    }

    private fun processUvcPayload(
        data: ByteArray, length: Int,
        frameBuffer: MutableList<Byte>,
        onFrame: (ByteArray) -> Unit
    ) {
        if (length < 2) return
        val headerLen = data[0].toInt() and 0xFF
        if (headerLen < 2 || headerLen > length) return

        val headerFlags = data[1].toInt() and 0xFF
        val endOfFrame = (headerFlags and 0x02) != 0
        val frameError = (headerFlags and 0x40) != 0

        if (frameError) {
            frameBuffer.clear()
            return
        }

        val payloadStart = headerLen
        val payloadLength = length - headerLen

        if (payloadLength > 0) {
            for (i in payloadStart until payloadStart + payloadLength) {
                frameBuffer.add(data[i])
            }
        }

        if (endOfFrame && frameBuffer.isNotEmpty()) {
            onFrame(frameBuffer.toByteArray())
            frameBuffer.clear()
        }
    }

    private fun getVideoStreamingInterfaceIndex(): Int {
        return videoInterface?.id ?: 1
    }

    fun release() {
        stopStream()
        videoInterface?.let { connection.releaseInterface(it) }
        connection.close()
    }
}
