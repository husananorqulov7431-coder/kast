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
 * Telefonning Wi-Fi Hotspot tarmog'ida ishlovchi lokal server.
 * Hech qanday tashqi internet yoki harajat talab qilmaydi.
 * Port 8080: Web player (Brauzer uchun)
 * Port 8081: WebSocket stream (FilmReceiver.exe va Web player uchun)
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
    private var isHttpRunning = false
    private val httpExecutor = Executors.newFixedThreadPool(4)

    private var wsServer: CustomWebSocketServer? = null
    private var initialVideoConfig: ByteArray? = null

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
            httpServerSocket = ServerSocket(httpPort)
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
                val path = if (parts.size > 1) parts[1] else "/"

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
                    bodyBytes = getFallbackHtml().toByteArray(Charsets.UTF_8)
                }

                val out: OutputStream = s.getOutputStream()
                val responseHeaders = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: $contentType\r\n" +
                        "Content-Length: ${bodyBytes.size}\r\n" +
                        "Access-Control-Allow-Origin: *\r\n" +
                        "Connection: close\r\n\r\n"

                out.write(responseHeaders.toByteArray(Charsets.UTF_8))
                out.write(bodyBytes)
                out.flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP mijoziga javob berishda xatolik: ${e.message}")
        }
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

            wsServer?.stop()
            wsServer = null
            Log.d(TAG, "Lokal serverlar to'xtatildi")
        } catch (e: Exception) {
            Log.e(TAG, "Serverlarni to'xtatishda xatolik: ${e.message}")
        }
    }

    private inner class CustomWebSocketServer(address: InetSocketAddress) : WebSocketServer(address) {
        override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
            Log.d(TAG, "Kompyuter ulandi: ${conn?.remoteSocketAddress}")
            onClientCountChanged(connections.size)

            initialVideoConfig?.let { config ->
                val buffer = ByteBuffer.allocate(1 + config.size)
                buffer.put(TYPE_VIDEO)
                buffer.put(config)
                buffer.flip()
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
            Log.e(TAG, "WebSocket xatosi: ${ex?.message}")
        }

        override fun onStart() {
            Log.d(TAG, "WebSocket server faol")
        }
    }

    private fun getFallbackHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"><title>KinoCast</title></head>
            <body style="background:#111;color:#fff;font-family:sans-serif;text-align:center;padding-top:100px;">
                <h1>🎬 KinoCast Cinema Receiver</h1>
                <p>Iltimos, FilmReceiver.exe ilovasidan foydalaning yoki HTML pleerni yuklang.</p>
            </body>
            </html>
        """.trimIndent()
    }
}
