package com.xiaozhi

import android.content.Context
import android.media.*
import android.os.Build
import android.os.Process
import android.util.Log
import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusDecoder
import io.github.jaredmdobson.concentus.OpusEncoder
import io.github.jaredmdobson.concentus.OpusException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class XiaoZhiAudioManager(context: Context) {
    companion object {
        private const val TAG = "XiaoZhiAudio"
        private const val OUTPUT_SAMPLE_RATE = 48000
        private const val INPUT_SAMPLE_RATE = 16000
        private const val AUDIO_TRACK_BUFFER_MS = 80
        private const val AUDIO_TRACK_BUFFER_SIZE = OUTPUT_SAMPLE_RATE * 2 * AUDIO_TRACK_BUFFER_MS / 1000
        private const val PLAYBACK_QUEUE_CAPACITY = 60
        private const val OPUS_FRAME_SIZE_MS = 60
        private const val PCM_FRAME_SIZE = INPUT_SAMPLE_RATE * 2 * OPUS_FRAME_SIZE_MS / 1000
    }

    // Playback
    private var audioTrack: AudioTrack? = null
    private var decoder: OpusDecoder? = null
    private val running = AtomicBoolean(false)
    private val playbackStarted = AtomicBoolean(false)
    private val decoderNeedsReset = AtomicBoolean(false)
    private val audioTrackLock = Any()
    private val decoderLock = Any()
    private val playbackQueue: BlockingQueue<ByteArray> = ArrayBlockingQueue(PLAYBACK_QUEUE_CAPACITY)
    private var playbackThread: Thread? = null

    // Recording
    private var audioRecord: AudioRecord? = null
    private var encoder: OpusEncoder? = null
    private val isRecording = AtomicBoolean(false)
    private var recordThread: Thread? = null
    private var onAudioRecorded: ((opusData: ByteArray) -> Unit)? = null

    private val appContext = context.applicationContext
    private val systemAudioManager: AudioManager? = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val pcmByteBuffer = ByteBuffer.allocateDirect(AUDIO_TRACK_BUFFER_SIZE)
        .order(ByteOrder.LITTLE_ENDIAN)

    init {
        initOpus()
    }

    private fun initOpus() {
        try {
            decoder = OpusDecoder(OUTPUT_SAMPLE_RATE, 1)
            encoder = OpusEncoder(INPUT_SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP)
            encoder?.bitrate = 24000
            Log.i(TAG, "Opus codec initialized")
        } catch (e: OpusException) {
            Log.e(TAG, "Opus init failed", e)
        }
    }

    fun resetDecoder() {
        synchronized(decoderLock) {
            try {
                decoder?.resetState()
                decoderNeedsReset.set(false)
            } catch (e: Exception) {
                decoderNeedsReset.set(true)
            }
        }
    }

    // ==================== Playback ====================
    @Synchronized
    fun start() {
        if (running.get()) return
        if (!initAudioTrack()) {
            releaseResources()
            return
        }
        routeToSpeaker()
        running.set(true)
        startPlaybackThread()
        Log.i(TAG, "Audio manager (playback) started")
    }

    private fun initAudioTrack(): Boolean {
        synchronized(audioTrackLock) {
            val minBuf = AudioTrack.getMinBufferSize(OUTPUT_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val bufferSize = maxOf(minBuf, AUDIO_TRACK_BUFFER_SIZE)
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(OUTPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY) }
                .build()
            return audioTrack?.state == AudioTrack.STATE_INITIALIZED
        }
    }

    private fun routeToSpeaker() {
        systemAudioManager?.apply {
            mode = AudioManager.MODE_NORMAL
            isSpeakerphoneOn = true
        }
    }

    private fun startPlaybackThread() {
        playbackThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val decodeBuffer = ShortArray(5760)
            synchronized(audioTrackLock) {
                audioTrack?.play()
                playbackStarted.set(true)
            }
            while (running.get()) {
                val opusData = try {
                    playbackQueue.poll(10, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    break
                }
                if (opusData == null) {
                    synchronized(audioTrackLock) {
                        if (audioTrack != null && playbackStarted.get()) {
                            val silence = ShortArray(OUTPUT_SAMPLE_RATE * 10 / 1000)
                            audioTrack?.write(silence, 0, silence.size)
                        }
                    }
                    continue
                }
                if (opusData.size < 1) continue
                try {
                    var decodedSamples = 0
                    synchronized(decoderLock) {
                        if (decoderNeedsReset.get()) {
                            decoder = OpusDecoder(OUTPUT_SAMPLE_RATE, 1)
                            decoderNeedsReset.set(false)
                        }
                        decoder?.let {
                            decodedSamples = it.decode(opusData, 0, opusData.size, decodeBuffer, 0, decodeBuffer.size, false)
                        }
                    }
                    if (decodedSamples > 0) {
                        pcmByteBuffer.clear()
                        pcmByteBuffer.asShortBuffer().put(decodeBuffer, 0, decodedSamples)
                        val byteCount = decodedSamples * 2
                        pcmByteBuffer.limit(byteCount)
                        synchronized(audioTrackLock) {
                            if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) {
                                val written = audioTrack?.write(pcmByteBuffer, byteCount, AudioTrack.WRITE_BLOCKING) ?: 0
                                if (written == AudioTrack.ERROR_DEAD_OBJECT) restartAudioTrack()
                            }
                        }
                    } else if (decodedSamples < 0) {
                        decoderNeedsReset.set(true)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Playback decode error", e)
                    decoderNeedsReset.set(true)
                }
            }
            synchronized(audioTrackLock) {
                audioTrack?.stop()
                playbackStarted.set(false)
            }
        }, "AudioPlayer").apply { start() }
    }

    private fun restartAudioTrack() {
        synchronized(audioTrackLock) {
            audioTrack?.stop(); audioTrack?.release(); audioTrack = null
            if (initAudioTrack()) { audioTrack?.play(); playbackStarted.set(true) }
            else playbackStarted.set(false)
        }
    }

    fun playAudio(opusData: ByteArray) {
        if (opusData.isEmpty() || !running.get()) return
        val dec = decoder
        if (dec == null) {
            Log.e(TAG, "Decoder not ready, cannot play audio")
            return
        }
        if (!playbackQueue.offer(opusData)) {
            playbackQueue.poll()
            playbackQueue.offer(opusData)
        }
    }

    fun isPlaybackFinished(): Boolean = playbackQueue.isEmpty()

    // ==================== Recording ====================
    fun startRecordingForServer(callback: (opusData: ByteArray) -> Unit): Boolean {
        if (isRecording.get()) {
            Log.w(TAG, "Already recording – stopping previous session first")
            stopRecording()
            try { Thread.sleep(100) } catch (_: InterruptedException) {}
        }

        val enc = encoder
        if (enc == null) {
            Log.e(TAG, "Encoder not initialized")
            return false
        }

        onAudioRecorded = callback
        val bufferSize = AudioRecord.getMinBufferSize(INPUT_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord init failed")
            return false
        }
        audioRecord?.startRecording()
        isRecording.set(true)
        Log.i(TAG, "Recording started")

        recordThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val pcmBuffer = ShortArray(PCM_FRAME_SIZE / 2)
            val pcmByteArray = ByteArray(PCM_FRAME_SIZE)
            while (isRecording.get()) {
                try {
                    val read = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: 0
                    if (read > 0) {
                        ByteBuffer.wrap(pcmByteArray).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcmBuffer, 0, read)
                        synchronized(decoderLock) {
                            encoder?.let { enc ->
                                val encoded = ByteArray(4096)
                                val encodedLength = enc.encode(pcmBuffer, 0, read, encoded, 0, encoded.size)
                                if (encodedLength > 0) {
                                    val opusFrame = encoded.copyOf(encodedLength)
                                    Log.v(TAG, "Encoded opus frame $encodedLength bytes")
                                    onAudioRecorded?.invoke(opusFrame)
                                }
                            }
                        }
                    } else {
                        if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                            Log.e(TAG, "AudioRecord read error: $read")
                        }
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Unhandled exception in recording thread", e)
                    break
                }
            }
            try {
                audioRecord?.stop()
                audioRecord?.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing audio record in record thread", e)
            } finally {
                audioRecord = null
            }
            Log.i(TAG, "Recording stopped")
        }, "AudioRecorder").apply { start() }
        return true
    }

    fun stopRecording() {
        if (!isRecording.getAndSet(false)) return
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audio record", e)
        } finally {
            audioRecord = null
        }
        recordThread?.join(500)
        recordThread = null
        Log.i(TAG, "Recording fully stopped")
    }

    fun isCurrentlyRecording(): Boolean = isRecording.get()

    @Synchronized
    fun shutdown() {
        running.set(false)
        playbackThread?.interrupt(); playbackThread?.join(500)
        stopRecording()
        releaseResources()
    }

    private fun releaseResources() {
        synchronized(audioTrackLock) {
            audioTrack?.stop(); audioTrack?.release(); audioTrack = null
        }
        synchronized(decoderLock) { decoder = null; encoder = null }
        playbackStarted.set(false); playbackQueue.clear()
    }
}