package com.usbcam.app.camera

import android.util.Log
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Minimal zero-dependency HTTP server that serves a live MJPEG stream
 * (multipart/x-mixed-replace) so the Capacitor WebView can render the USB
 * camera feed in a plain <img> element.
 *
 * - Bound to 127.0.0.1 by default (preview inside the app).
 * - When [lanAccessible] is true it binds to 0.0.0.0 so other devices on the
 *   same LAN can open http://<phone-ip>:<port>/stream (no internet involved).
 *
 * The newest JPEG frame is pushed via [pushFrame]; every connected client
 * receives it. This replaces the retired FFmpegKit preview path entirely.
 */
class MjpegStreamServer(
    private val port: Int = 8080,
    private val lanAccessible: Boolean = false
) {
    private val TAG = "MjpegStreamServer"
    private val boundary = "usbcamframe"
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val clients = CopyOnWriteArrayList<OutputStream>()

    @Volatile private var latestFrame: ByteArray? = null

    fun start() {
        if (running.getAndSet(true)) return
        thread(name = "mjpeg-accept") {
            try {
                val bindAddr = if (lanAccessible) "0.0.0.0" else "127.0.0.1"
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(bindAddr, port))
                }
                Log.d(TAG, "MJPEG server listening on $bindAddr:$port")
                while (running.get()) {
                    val socket = try { serverSocket?.accept() } catch (_: Exception) { null } ?: continue
                    handleClient(socket)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
            }
        }
    }

    private fun handleClient(socket: Socket) {
        thread(name = "mjpeg-client") {
            try {
                socket.tcpNoDelay = true
                val out = BufferedOutputStream(socket.getOutputStream())
                val header = buildString {
                    append("HTTP/1.0 200 OK\r\n")
                    append("Cache-Control: no-cache, private\r\n")
                    append("Pragma: no-cache\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("Connection: close\r\n")
                    append("Content-Type: multipart/x-mixed-replace; boundary=$boundary\r\n\r\n")
                }
                out.write(header.toByteArray())
                out.flush()
                clients.add(out)

                // Keep the socket alive; frames are pushed from pushFrame().
                while (running.get() && !socket.isClosed) {
                    Thread.sleep(200)
                }
            } catch (_: Exception) {
                // client disconnected
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    /** Push the newest JPEG frame to every connected client. */
    fun pushFrame(jpeg: ByteArray) {
        if (!running.get()) return
        latestFrame = jpeg
        val dead = ArrayList<OutputStream>()
        for (out in clients) {
            try {
                synchronized(out) {
                    val head = "--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n"
                    out.write(head.toByteArray())
                    out.write(jpeg)
                    out.write("\r\n".toByteArray())
                    out.flush()
                }
            } catch (_: Exception) {
                dead.add(out)
            }
        }
        if (dead.isNotEmpty()) clients.removeAll(dead.toSet())
    }

    fun stop() {
        if (!running.getAndSet(false)) return
        clients.forEach { try { it.close() } catch (_: Exception) {} }
        clients.clear()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        latestFrame = null
    }

    fun isRunning() = running.get()
}
