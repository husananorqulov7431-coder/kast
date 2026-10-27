package com.kinocast.app

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer

/**
 * Faqat Kino/Media tovushini yozib oluvchi yordamchi sinf.
 * Bildirishnomalar, qo'ng'iroqlar va tizim signallari AVTOMATIK ravishda filtrlanadi.
 */
class AudioCaptureHelper(
    private val mediaProjection: MediaProjection,
    private val onAudioData: (data: ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "AudioCaptureHelper"
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_COUNT = 2
        private const val BIT_RATE = 128000
    }

    private var audioRecord: AudioRecord? = null
    private var audioCodec: MediaCodec? = null
    private var isRecording = false
    private var recordThread: Thread? = null

    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.Q)
    fun start() {
        if (isRecording) return

        try {
            // 1. AudioPlaybackCapture sozlash: Faqat kino va o'yin tovushlari, hech qanday bildirishnomasiz!
            val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val channelConfig = AudioFormat.CHANNEL_IN_STEREO
            val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, channelConfig, audioEncoding)
            val bufferSize = minBufferSize * 2

            val audioFormat = AudioFormat.Builder()
                .setEncoding(audioEncoding)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(channelConfig)
                .build()

            audioRecord = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .build()

            // 2. AAC enkoderini sozlash
            setupEncoder()

            audioRecord?.startRecording()
            isRecording = true

            // 3. Audio o'qish va kodlash oqimi
            recordThread = Thread {
                val pcmBuffer = ByteArray(bufferSize)
                while (isRecording) {
                    val readBytes = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: -1
                    if (readBytes > 0) {
                        encodePcmToAac(pcmBuffer, readBytes)
                    }
                }
            }.apply {
                name = "AudioCaptureThread"
                priority = Thread.MAX_PRIORITY
                start()
            }

            Log.d(TAG, "Audio capture started (Notification-free media audio)")
        } catch (e: Exception) {
            Log.e(TAG, "Audio capture ishga tushirishda xatolik: ${e.message}", e)
        }
    }

    private fun setupEncoder() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNEL_COUNT).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        audioCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
    }

    private fun encodePcmToAac(pcmData: ByteArray, length: Int) {
        val codec = audioCodec ?: return
        try {
            val inputIndex = codec.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputIndex) ?: return
                inputBuffer.clear()
                inputBuffer.put(pcmData, 0, length)
                codec.queueInputBuffer(inputIndex, 0, length, System.nanoTime() / 1000, 0)
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            while (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                    // ADTS header qo'shish (pleerlar oson o'qishi uchun)
                    val outPacketSize = bufferInfo.size + 7
                    val outPacket = ByteArray(outPacketSize)
                    addAdtsHeader(outPacket, outPacketSize)
                    outputBuffer.get(outPacket, 7, bufferInfo.size)

                    onAudioData(outPacket)
                }
                codec.releaseOutputBuffer(outputIndex, false)
                outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio kodlashda xatolik: ${e.message}")
        }
    }

    private fun addAdtsHeader(packet: ByteArray, packetLen: Int) {
        val profile = 2 // AAC LC
        val freqIdx = 4 // 44.1KHz
        val chanCfg = 2 // 2 Channel (Stereo)

        packet[0] = 0xFF.toByte()
        packet[1] = 0xF9.toByte()
        packet[2] = (((profile - 1) shl 6) + (freqIdx shl 2) + (chanCfg shr 2)).toByte()
        packet[3] = (((chanCfg and 3) shl 6) + (packetLen shr 11)).toByte()
        packet[4] = ((packetLen and 0x7FF) shr 3).toByte()
        packet[5] = (((packetLen and 7) shl 5) + 0x1F).toByte()
        packet[6] = 0xFC.toByte()
    }

    fun stop() {
        isRecording = false
        try {
            recordThread?.interrupt()
            recordThread = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            audioCodec?.stop()
            audioCodec?.release()
            audioCodec = null
            Log.d(TAG, "Audio capture stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Audio capture to'xtatishda xatolik: ${e.message}")
        }
    }
}
