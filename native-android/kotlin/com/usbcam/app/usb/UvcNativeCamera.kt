package com.usbcam.app.usb

import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.os.Build
import android.util.Log
import android.view.Surface
import com.herohan.uvcapp.CameraHelper
import com.herohan.uvcapp.ICameraHelper
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.Size
import com.serenegiant.usb.UVCCamera
import kotlin.math.abs

/**
 * Streams UVC-class USB cameras / capture cards through the native libusb +
 * libuvc backend (UVCAndroid). This is the path that can read the isochronous
 * USB transfers most UVC devices use — Android's pure-Java USB Host API cannot.
 *
 * The raw pixels are pulled via the NV21 frame callback and handed to
 * [onNv21Frame]; the plugin turns them into JPEG for the existing MJPEG preview
 * server and the recorder, so the WebView UI stays unchanged.
 *
 * All [CameraHelper] interaction must happen on the main thread; the plugin
 * dispatches the open/preview calls accordingly. The frame callback fires on a
 * library worker thread.
 */
class UvcNativeCamera {

    companion object { private const val TAG = "UvcNativeCamera" }

    var onNv21Frame: ((data: ByteArray, width: Int, height: Int) -> Unit)? = null

    private var helper: CameraHelper? = null
    private var openResult: ((Boolean, String?) -> Unit)? = null
    private var dummyTexture: SurfaceTexture? = null
    private var dummySurface: Surface? = null

    @Volatile var width: Int = UVCCamera.DEFAULT_PREVIEW_WIDTH
        private set
    @Volatile var height: Int = UVCCamera.DEFAULT_PREVIEW_HEIGHT
        private set
    @Volatile var cameraOpened = false
        private set
    @Volatile private var streaming = false

    private val stateCallback = object : ICameraHelper.StateCallback {
        override fun onAttach(device: UsbDevice) { /* discovery handled by UsbDeviceManager */ }

        override fun onDeviceOpen(device: UsbDevice, isFirstOpen: Boolean) {
            try {
                helper?.openCamera()
            } catch (e: Exception) {
                Log.e(TAG, "openCamera failed", e)
                finishOpen(false, e.message ?: "openCamera failed")
            }
        }

        override fun onCameraOpen(device: UsbDevice) {
            cameraOpened = true
            finishOpen(true, null)
        }

        override fun onCameraClose(device: UsbDevice) { cameraOpened = false }
        override fun onDeviceClose(device: UsbDevice) { }
        override fun onDetach(device: UsbDevice) { cameraOpened = false }
        override fun onCancel(device: UsbDevice) {
            finishOpen(false, "USB-Berechtigung abgelehnt oder Verbindung abgebrochen")
        }
    }

    private fun ensureHelper(): CameraHelper =
        helper ?: CameraHelper().also {
            it.setStateCallback(stateCallback)
            helper = it
        }

    /** Open [device]; [onResult] fires once with success or an error message. */
    fun open(device: UsbDevice, onResult: (Boolean, String?) -> Unit) {
        openResult = onResult
        try {
            ensureHelper().selectDevice(device)
        } catch (e: Exception) {
            Log.e(TAG, "selectDevice failed", e)
            finishOpen(false, e.message ?: "selectDevice failed")
        }
    }

    private fun finishOpen(ok: Boolean, err: String?) {
        val cb = openResult ?: return
        openResult = null
        cb(ok, err)
    }

    /** Begin streaming at the supported size closest to the request. */
    fun startPreview(reqW: Int, reqH: Int): Boolean {
        val h = helper ?: return false
        if (!cameraOpened) {
            Log.w(TAG, "startPreview before camera open")
            return false
        }
        return try {
            val sizes = try { h.supportedSizeList } catch (_: Exception) { emptyList<Size>() }
            sizes.minByOrNull { abs(it.width - reqW) + abs(it.height - reqH) }?.let { best ->
                width = best.width
                height = best.height
                try { h.previewSize = best } catch (e: Exception) { Log.w(TAG, "setPreviewSize failed", e) }
            }

            // A throwaway Surface keeps the native preview pipeline running; the
            // pixels we actually use arrive through the NV21 frame callback.
            attachDummySurface(width, height)
            dummySurface?.let {
                try { h.addSurface(it, false) } catch (e: Exception) { Log.w(TAG, "addSurface failed", e) }
            }

            h.setFrameCallback(IFrameCallback { frame ->
                try {
                    val len = frame.remaining()
                    if (len > 0) {
                        val nv21 = ByteArray(len)
                        frame.get(nv21)
                        onNv21Frame?.invoke(nv21, width, height)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "frame callback error", e)
                }
            }, UVCCamera.PIXEL_FORMAT_NV21)

            h.startPreview()
            streaming = true
            try { h.previewSize?.let { width = it.width; height = it.height } } catch (_: Exception) {}
            Log.d(TAG, "UVC preview started ${width}x$height")
            true
        } catch (e: Exception) {
            Log.e(TAG, "startPreview failed", e)
            false
        }
    }

    fun stopPreview() {
        val h = helper ?: return
        if (!streaming) return
        streaming = false
        try { dummySurface?.let { h.removeSurface(it) } } catch (_: Exception) {}
        try { h.stopPreview() } catch (e: Exception) { Log.w(TAG, "stopPreview failed", e) }
        releaseDummySurface()
    }

    fun close() {
        streaming = false
        cameraOpened = false
        openResult = null
        try { helper?.release() } catch (e: Exception) { Log.w(TAG, "release failed", e) }
        helper = null
        releaseDummySurface()
    }

    private fun attachDummySurface(w: Int, h: Int) {
        if (dummySurface != null) return
        try {
            val st = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) SurfaceTexture(false)
                     else SurfaceTexture(0)
            st.setDefaultBufferSize(w.coerceAtLeast(1), h.coerceAtLeast(1))
            dummyTexture = st
            dummySurface = Surface(st)
        } catch (e: Exception) {
            Log.w(TAG, "dummy surface creation failed", e)
        }
    }

    private fun releaseDummySurface() {
        try { dummySurface?.release() } catch (_: Exception) {}
        try { dummyTexture?.release() } catch (_: Exception) {}
        dummySurface = null
        dummyTexture = null
    }
}
