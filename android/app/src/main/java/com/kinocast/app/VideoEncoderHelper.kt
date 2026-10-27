package com.kinocast.app

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

/**
 * 16:9 formatdagi videoni H.264 uskuna (hardware) tezlatgichi orqali
 * kodlab beruvchi yordamchi sinf.
 */
class VideoEncoderHelper(
    private val mediaProjection: MediaProjection,
    private val screenDensity: Int,
    private val onVideoData: (data: ByteArray, isKeyFrame: Boolean) -> Unit
) {
    companion object {
        private const val TAG = "VideoEncoderHelper"
        private const val MIME_TYPE = "video/avc" // H.264
        const val WIDTH = 1920 // 16:9 Standard Full HD
        const val HEIGHT = 1080
        private const val BIT_RATE = 4_000_000 // 4 Mbps (juda toza va tez)
        private const val FRAME_RATE = 30
        private const val I_FRAME_INTERVAL = 1 // Har 1 soniyada to'liq kadr (IDR)
    }

    private var mediaCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var isEncoding = false
    private var encodeThread: Thread? = null

    // 16:9 rejim nazorati: faqat telefon gorizontal turganda oqim uzatiladi!
    @Volatile
    var isLandscape16x9: Boolean = false

    private var cachedSpsPps: ByteArray? = null

    fun start() {
        if (isEncoding) return

        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, WIDTH, HEIGHT).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            mediaCodec = MediaCodec.createEncoderByType(MIME_TYPE).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = createInputSurface()
                start()
            }

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "KinoCastVirtualDisplay",
                WIDTH,
                HEIGHT,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface,
                null,
                null
            )

            isEncoding = true

            encodeThread = Thread {
                val bufferInfo = MediaCodec.BufferInfo()
                while (isEncoding) {
                    val codec = mediaCodec ?: break
                    val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)

                    if (outputIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            val outData = ByteArray(bufferInfo.size)
                            outputBuffer.get(outData)

                            val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                            if (isConfig) {
                                cachedSpsPps = outData
                            }

                            // MUHIM QOIDA: Faqat telefon 16:9 gorizontal kino rejimida bo'lgandagina
                            // kadrlar kompyuterga yuboriladi!
                            if (isLandscape16x9) {
                                onVideoData(outData, isKeyFrame)
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }.apply {
                name = "VideoEncoderThread"
                priority = Thread.MAX_PRIORITY
                start()
            }

            Log.d(TAG, "Video encoder started (1920x1080 @ 30fps)")
        } catch (e: Exception) {
            Log.e(TAG, "Video encoder ishga tushirishda xatolik: ${e.message}", e)
        }
    }

    fun getCachedConfig(): ByteArray? = cachedSpsPps

    fun stop() {
        isEncoding = false
        try {
            encodeThread?.interrupt()
            encodeThread = null

            virtualDisplay?.release()
            virtualDisplay = null

            inputSurface?.release()
            inputSurface = null

            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null
            Log.d(TAG, "Video encoder stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Video encoder to'xtatishda xatolik: ${e.message}")
        }
    }
}
