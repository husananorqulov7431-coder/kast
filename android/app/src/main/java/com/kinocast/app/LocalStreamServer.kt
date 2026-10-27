package com.kinocast.app

import android.content.Context
import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * Oddiy, to'g'ridan-to'g'ri va tezkor lokal oqim serveri.
 * Hech qanday PIN to'sig'isiz - kompyuter ulansa bas, darhol video va audio oqimini beradi.
 */
class LocalStreamServer(
    private val context: Context,
    private val httpPort: Int = 8080,
    private val wsPort: Int = 8081,
    private val onClientCountChanged: (count: Int) -> Unit
) {
    companion object {
        private const val TAG = "LocalStreamServer"
        const val TYPE_VIDEO: Byte = 0x01
        const val TYPE_AUDIO: Byte = 0x02
        const val TYPE_STATUS: Byte = 0x03
    }

    private var httpServerSocket: ServerSocket? = null
    @Volatile
    private var isHttpRunning = false
    private var httpExecutor = Executors.newCachedThreadPool()

    private var wsServer: CustomWebSocketServer? = null
    private var initialVideoConfig: ByteArray? = null

    @Volatile
    var isLandscape: Boolean = false

    fun setInitialVideoConfig(config: ByteArray?) {
        this.initialVideoConfig = config
    }

    fun start() {
        startHttpServer()
        startWebSocketServer()
    }

    private fun startHttpServer() {
        try {
            isHttpRunning = true
            httpServerSocket = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(httpPort))
            }

            Thread {
                while (isHttpRunning) {
                    try {
                        val clientSocket = httpServerSocket?.accept() ?: break
                        httpExecutor.execute { handleHttpClient(clientSocket) }
                    } catch (e: Exception) {
                        if (!isHttpRunning) break
                    }
                }
            }.apply {
                name = "HttpServerThread"
                start()
            }
            Log.d(TAG, "Lokal HTTP server $httpPort portida ishga tushdi")
        } catch (e: Exception) {
            Log.e(TAG, "HTTP server ishga tushirishda xatolik: ${e.message}")
        }
    }

    private fun handleHttpClient(socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                val fullPath = if (parts.size > 1) parts[1] else "/"
                val path = fullPath.split("?")[0]

                val out: OutputStream = s.getOutputStream()

                if (path == "/info") {
                    val json = "{\"status\":\"ok\",\"device\":\"KinoCast\",\"isLandscape\":$isLandscape}"
                    sendHttpResponse(out, 200, "application/json", json.toByteArray(Charsets.UTF_8))
                    return
                }

                val assetPath = if (path == "/" || path.isEmpty()) "web/index.html" else "web" + path
                var bodyBytes: ByteArray
                var contentType = "text/html; charset=utf-8"

                try {
                    context.assets.open(assetPath).use { input ->
                        bodyBytes = input.readBytes()
                    }
                    if (assetPath.endsWith(".js")) contentType = "application/javascript"
                    else if (assetPath.endsWith(".css")) contentType = "text/css"
                } catch (e: Exception) {
                    bodyBytes = "KinoCast Server Ishlamoqda".toByteArray(Charsets.UTF_8)
                }

                sendHttpResponse(out, 200, contentType, bodyBytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP javob berishda xatolik: ${e.message}")
        }
    }

    private fun sendHttpResponse(out: OutputStream, code: Int, contentType: String, data: ByteArray) {
        val responseHeaders = "HTTP/1.1 $code OK\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${data.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        out.write(responseHeaders.toByteArray(Charsets.UTF_8))
        out.write(data)
        out.flush()
    }

    private fun startWebSocketServer() {
        try {
            wsServer = CustomWebSocketServer(InetSocketAddress(wsPort)).apply {
                isReuseAddr = true
                start()
            }
            Log.d(TAG, "WebSocket server $wsPort portida ishga tushdi")
        } catch (e: Exception) {
            Log.e(TAG, "WebSocket server ishga tushirishda xatolik: ${e.message}")
        }
    }

    fun broadcastVideo(data: ByteArray) {
        val server = wsServer ?: return
        if (server.connections.isEmpty()) return

        val buffer = ByteBuffer.allocate(1 + data.size)
        buffer.put(TYPE_VIDEO)
        buffer.put(data)
        buffer.flip()

        server.broadcast(buffer)
    }

    fun broadcastAudio(data: ByteArray) {
        val server = wsServer ?: return
        if (server.connections.isEmpty()) return

        val buffer = ByteBuffer.allocate(1 + data.size)
        buffer.put(TYPE_AUDIO)
        buffer.put(data)
        buffer.flip()

        server.broadcast(buffer)
    }

    fun broadcastStatus(isLandscape16x9: Boolean) {
        this.isLandscape = isLandscape16x9
        val server = wsServer ?: return
        val statusMsg = "{\"isLandscape\": $isLandscape16x9}"
        val bytes = statusMsg.toByteArray(Charsets.UTF_8)

        val buffer = ByteBuffer.allocate(1 + bytes.size)
        buffer.put(TYPE_STATUS)
        buffer.put(bytes)
        buffer.flip()

        server.broadcast(buffer)
    }

    fun stop() {
        try {
            isHttpRunning = false
            httpServerSocket?.close()
            httpServerSocket = null
            httpExecutor.shutdownNow()
            httpExecutor = Executors.newCachedThreadPool()

            wsServer?.stop(200)
            wsServer = null
            Log.d(TAG, "Lokal serverlar to'xtatildi")
        } catch (e: Exception) {
            Log.e(TAG, "To'xtatishda xatolik: ${e.message}")
        }
    }

    private inner class CustomWebSocketServer(address: InetSocketAddress) : WebSocketServer(address) {
        override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
            Log.d(TAG, "Kompyuter ulandi: ${conn?.remoteSocketAddress}")
            onClientCountChanged(connections.size)

            // 1. Yangi ulangan kompyuterga darhol holatni yuborish
            val statusMsg = "{\"isLandscape\": $isLandscape}"
            val bytes = statusMsg.toByteArray(Charsets.UTF_8)
            val statusBuf = ByteBuffer.allocate(1 + bytes.size).apply {
                put(TYPE_STATUS)
                put(bytes)
                flip()
            }
            conn?.send(statusBuf)

            // 2. Dastlabki SPS/PPS kadrni berish
            initialVideoConfig?.let { config ->
                val buffer = ByteBuffer.allocate(1 + config.size).apply {
                    put(TYPE_VIDEO)
                    put(config)
                    flip()
                }
                conn?.send(buffer)
            }
        }

        override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
            Log.d(TAG, "Kompyuter uzildi: ${conn?.remoteSocketAddress}")
            onClientCountChanged(connections.size)
        }

        override fun onMessage(conn: WebSocket?, message: String?) {}
        override fun onMessage(conn: WebSocket?, message: ByteBuffer?) {}
        override fun onError(conn: WebSocket?, ex: Exception?) {
            Log.e(TAG, "WS xatolik: ${ex?.message}")
        }
        override fun onStart() {
            Log.d(TAG, "WebSocket server faol")
        }
    }
}
