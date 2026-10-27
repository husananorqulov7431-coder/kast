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
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * PIN-kod himoyali va qayta ulanishga chidamli lokal server.
 */
class LocalStreamServer(
    private val context: Context,
    private val httpPort: Int = 8080,
    private val wsPort: Int = 8081,
    val pinCode: String,
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

    private val authenticatedClients = Collections.newSetFromMap(ConcurrentHashMap<WebSocket, Boolean>())

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
            Log.d(TAG, "Lokal HTTP server $httpPort portida ishga tushdi (PIN: $pinCode)")
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
                val uriParts = fullPath.split("?")
                val path = uriParts[0]
                val query = if (uriParts.size > 1) uriParts[1] else ""

                val out: OutputStream = s.getOutputStream()

                // 1. Info endpoint (Kompyuter avtomatik kashf qilishi uchun)
                if (path == "/info") {
                    val json = "{\"status\":\"ok\",\"device\":\"KinoCast\",\"pinRequired\":true,\"isLandscape\":$isLandscape}"
                    sendHttpResponse(out, 200, "application/json", json.toByteArray(Charsets.UTF_8))
                    return
                }

                // 2. PIN tekshirish endpointi: /auth?pin=XXXX
                if (path == "/auth") {
                    val enteredPin = getQueryParam(query, "pin")
                    val isMatch = (enteredPin == pinCode)
                    val json = if (isMatch) "{\"auth\":true}" else "{\"auth\":false,\"error\":\"PIN noto'g'ri\"}"
                    sendHttpResponse(out, if (isMatch) 200 else 401, "application/json", json.toByteArray(Charsets.UTF_8))
                    return
                }

                // 3. Web player assetlari
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

                sendHttpResponse(out, 200, contentType, bodyBytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP mijoziga javob berishda xatolik: ${e.message}")
        }
    }

    private fun sendHttpResponse(out: OutputStream, code: Int, contentType: String, data: ByteArray) {
        val statusText = if (code == 200) "OK" else if (code == 401) "Unauthorized" else "Error"
        val responseHeaders = "HTTP/1.1 $code $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${data.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        out.write(responseHeaders.toByteArray(Charsets.UTF_8))
        out.write(data)
        out.flush()
    }

    private fun getQueryParam(query: String, paramName: String): String? {
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = pair.substring(0, idx)
                if (key == paramName) {
                    return pair.substring(idx + 1)
                }
            }
        }
        return null
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
        if (authenticatedClients.isEmpty()) return

        val buffer = ByteBuffer.allocate(1 + data.size)
        buffer.put(TYPE_VIDEO)
        buffer.put(data)
        buffer.flip()

        for (client in authenticatedClients) {
            try {
                if (client.isOpen) {
                    client.send(buffer.duplicate())
                }
            } catch (e: Exception) {}
        }
    }

    fun broadcastAudio(data: ByteArray) {
        val server = wsServer ?: return
        if (authenticatedClients.isEmpty()) return

        val buffer = ByteBuffer.allocate(1 + data.size)
        buffer.put(TYPE_AUDIO)
        buffer.put(data)
        buffer.flip()

        for (client in authenticatedClients) {
            try {
                if (client.isOpen) {
                    client.send(buffer.duplicate())
                }
            } catch (e: Exception) {}
        }
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

        for (client in authenticatedClients) {
            try {
                if (client.isOpen) {
                    client.send(buffer.duplicate())
                }
            } catch (e: Exception) {}
        }
    }

    fun stop() {
        try {
            isHttpRunning = false
            httpServerSocket?.close()
            httpServerSocket = null
            httpExecutor.shutdownNow()
            httpExecutor = Executors.newCachedThreadPool()

            authenticatedClients.clear()
            wsServer?.stop(500)
            wsServer = null
            Log.d(TAG, "Lokal serverlar to'liq to'xtatildi")
        } catch (e: Exception) {
            Log.e(TAG, "Serverlarni to'xtatishda xatolik: ${e.message}")
        }
    }

    private inner class CustomWebSocketServer(address: InetSocketAddress) : WebSocketServer(address) {
        override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
            val resourceDescriptor = handshake?.resourceDescriptor ?: ""
            Log.d(TAG, "Yangi ulanish so'rovi: $resourceDescriptor")

            // Agar ulanishda ?pin=XXXX berilgan bo'lsa
            val queryIdx = resourceDescriptor.indexOf("?")
            var clientAuthorized = false
            if (queryIdx >= 0) {
                val query = resourceDescriptor.substring(queryIdx + 1)
                val pin = getQueryParam(query, "pin")
                if (pin == pinCode) {
                    clientAuthorized = true
                }
            }

            if (clientAuthorized && conn != null) {
                authenticatedClients.add(conn)
                Log.d(TAG, "PIN orqali kompyuter ulandi: ${conn.remoteSocketAddress}")
                onClientCountChanged(authenticatedClients.size)

                // Dastlabki video kadrni yuborish
                initialVideoConfig?.let { config ->
                    val buffer = ByteBuffer.allocate(1 + config.size)
                    buffer.put(TYPE_VIDEO)
                    buffer.put(config)
                    buffer.flip()
                    conn.send(buffer)
                }
            } else {
                Log.d(TAG, "PIN kutilmoqda...")
            }
        }

        override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
            if (conn != null) {
                authenticatedClients.remove(conn)
            }
            onClientCountChanged(authenticatedClients.size)
        }

        override fun onMessage(conn: WebSocket?, message: String?) {
            // Agar mijoz ochilgandan keyin PIN yuborsa: {"pin": "XXXX"}
            if (conn == null || message == null) return
            try {
                if (message.contains(pinCode)) {
                    authenticatedClients.add(conn)
                    Log.d(TAG, "Mijoz xabar orqali tasdiqlandi!")
                    onClientCountChanged(authenticatedClients.size)
                    initialVideoConfig?.let { config ->
                        val buffer = ByteBuffer.allocate(1 + config.size)
                        buffer.put(TYPE_VIDEO)
                        buffer.put(config)
                        buffer.flip()
                        conn.send(buffer)
                    }
                    conn.send("{\"auth\": true}")
                } else {
                    conn.send("{\"auth\": false, \"error\": \"PIN noto'g'ri\"}")
                    conn.close(4001, "Noto'g'ri PIN")
                }
            } catch (e: Exception) {}
        }

        override fun onMessage(conn: WebSocket?, message: ByteBuffer?) {}

        override fun onError(conn: WebSocket?, ex: Exception?) {
            Log.e(TAG, "WebSocket xatosi: ${ex?.message}")
        }

        override fun onStart() {
            Log.d(TAG, "WebSocket server muvaffaqiyatli ishga tushdi")
        }
    }

    private fun getFallbackHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head><meta charset="utf-8"><title>KinoCast</title></head>
            <body style="background:#111;color:#fff;font-family:sans-serif;text-align:center;padding-top:100px;">
                <h1>🎬 KinoCast Cinema Receiver</h1>
                <p>PIN kod: <b>$pinCode</b></p>
            </body>
            </html>
        """.trimIndent()
    }
}
