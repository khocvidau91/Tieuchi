package com.xiaozhi

import android.content.Context
import android.graphics.*
import android.media.Image
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.xiaozhi.ai.AIEvent
import com.xiaozhi.ai.EventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

class EyeManager(
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView
) {
    companion object {
        private const val TAG = "EyeManager"
        private const val DETECTION_SKIP_FRAMES = 2
        private const val EMOTION_SKIP_FRAMES = 12
        private val emotions = arrayOf("happy", "sad", "surprise", "angry", "disgust", "fear")
        private const val MIN_EMOTION_CONFIDENCE = 0.5f
        private const val INPUT_SIZE = 112
        private const val EMBEDDING_SIZE = 192
        private const val MODEL_FILE = "mobilefacenet.tflite"
    }

    var onFacePresenceChanged: ((isLooking: Boolean, trackingId: Int?) -> Unit)? = null
    var onNewFaceAppeared: ((trackingId: Int?) -> Unit)? = null
    var onBlinkDetected: (() -> Unit)? = null

    private var cameraExecutor: java.util.concurrent.ExecutorService? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private var faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .setMinFaceSize(0.1f)
            .build()
    )

    private var faceEmbedderInterpreter: Interpreter? = null
    private var emotionInterpreter: Interpreter? = null
    private val emotionInputSize = 48
    private val emotionModelName = "emotion_ferplus.tflite"

    private var frameCount = 0
    private var wasFacePresent = false
    private var currentTrackingId: Int? = null
    private val knownTrackingIds = mutableSetOf<Int>()

    var leftEyeOpenProb: Float? = null
    var rightEyeOpenProb: Float? = null
    var blinkDetected = false

    private val eventScope = CoroutineScope(Dispatchers.Main)
    private var lifecycleObserver: LifecycleEventObserver? = null

    fun bindToLifecycle(lifecycle: Lifecycle) {
        lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "App resumed, starting EyeManager")
                    start()
                }
                // KHÔNG dừng EyeManager khi app tạm dừng
                else -> {}
            }
        }
        lifecycle.addObserver(lifecycleObserver!!)
    }

    fun unbindFromLifecycle(lifecycle: Lifecycle) {
        lifecycleObserver?.let { lifecycle.removeObserver(it) }
        lifecycleObserver = null
    }

    fun start() {
        Log.d(TAG, "EyeManager starting...")
        resetFacePresence()
        knownTrackingIds.clear()
        cameraExecutor = Executors.newSingleThreadExecutor()
        loadEmotionModel()
        loadFaceEmbedderModel()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(lifecycleOwner as Context)
        cameraProviderFuture.addListener(Runnable {
            cameraProvider = cameraProviderFuture.get()
            bindUseCases()
        }, ContextCompat.getMainExecutor(lifecycleOwner as Context))
    }

    private fun loadFaceEmbedderModel() {
        try {
            val context = lifecycleOwner as Context
            val fd = context.assets.openFd(MODEL_FILE)
            val inputStream = FileInputStream(fd.fileDescriptor)
            val fileChannel = inputStream.channel
            val buffer: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            val options = Interpreter.Options().apply { setUseXNNPACK(false); setNumThreads(4) }
            faceEmbedderInterpreter = Interpreter(buffer, options)
            Log.d(TAG, "Face embedder model loaded")
        } catch (e: Exception) {
            Log.w(TAG, "Face embedder model not found – face recognition disabled", e)
            faceEmbedderInterpreter = null
        }
    }

    fun getEmbedding(faceBitmap: Bitmap): FloatArray? {
        val model = faceEmbedderInterpreter ?: return null
        return try {
            val resized = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true)
            val inputBuffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4)
            inputBuffer.order(ByteOrder.nativeOrder())
            for (y in 0 until INPUT_SIZE) {
                for (x in 0 until INPUT_SIZE) {
                    val pixel = resized.getPixel(x, y)
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    inputBuffer.putFloat((r / 127.5f) - 1.0f)
                    inputBuffer.putFloat((g / 127.5f) - 1.0f)
                    inputBuffer.putFloat((b / 127.5f) - 1.0f)
                }
            }
            val output = Array(1) { FloatArray(EMBEDDING_SIZE) }
            model.run(inputBuffer, output)
            val embedding = output[0]
            val norm = Math.sqrt(embedding.map { it * it }.sum().toDouble()).toFloat()
            if (norm > 0f) {
                for (i in embedding.indices) embedding[i] /= norm
            }
            embedding
        } catch (e: Exception) {
            Log.e(TAG, "Error getting embedding", e)
            null
        }
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build()
        preview = Preview.Builder().setTargetResolution(Size(1, 1)).build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetResolution(Size(320, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setImageQueueDepth(2)
            .build()
        imageAnalyzer?.setAnalyzer(cameraExecutor ?: return, ImageAnalysis.Analyzer { imageProxy ->
            Log.d("FACE_DEBUG", "Analyzer frame received")
            try { processFrame(imageProxy) } catch (e: Exception) { imageProxy.close() }
        })
        try { provider.unbindAll(); provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalyzer) }
        catch (e: Exception) { Log.e(TAG, "Bind failed", e) }
    }

    private fun processFrame(imageProxy: ImageProxy) {
        frameCount++
        if (frameCount % DETECTION_SKIP_FRAMES != 0) { imageProxy.close(); return }
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        faceDetector.process(InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees))
            .addOnSuccessListener { faces -> handleFaces(faces, mediaImage, imageProxy) }
            .addOnFailureListener { imageProxy.close() }
    }

    private fun handleFaces(faces: List<Face>, mediaImage: Image, imageProxy: ImageProxy) {
        try {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val isLooking = faces.isNotEmpty()
            var activeTrackingId: Int? = null

            if (isLooking) {
                val f = faces[0]
                activeTrackingId = f.trackingId
                leftEyeOpenProb = f.leftEyeOpenProbability
                rightEyeOpenProb = f.rightEyeOpenProbability
                val wasBlink = blinkDetected
                if ((leftEyeOpenProb ?: 1f) < 0.1f && (rightEyeOpenProb ?: 1f) < 0.1f) {
                    blinkDetected = true
                    if (!wasBlink) {
                        mainHandler.post { onBlinkDetected?.invoke() }
                    }
                }
                Log.v(TAG, "Face detected, trackingId=$activeTrackingId")
            }

            if (isLooking != wasFacePresent) {
                wasFacePresent = isLooking
                mainHandler.post { onFacePresenceChanged?.invoke(isLooking, activeTrackingId) }
            }

            if (isLooking && activeTrackingId != null) {
                if (activeTrackingId != currentTrackingId) {
                    currentTrackingId = activeTrackingId
                    if (!knownTrackingIds.contains(activeTrackingId)) {
                        knownTrackingIds.add(activeTrackingId)
                        mainHandler.post { onNewFaceAppeared?.invoke(activeTrackingId) }
                    }
                }
                if (frameCount % EMOTION_SKIP_FRAMES == 0) {
                    var bmp: Bitmap? = null
                    try {
                        bmp = cropFaceSafe(mediaImage, faces[0], rotationDegrees)
                        if (bmp != null) {
                            val result = classifyEmotion(bmp)
                            if (result != null) {
                                val (emotion, confidence) = result
                                Log.d(TAG, "Emotion result: $emotion (conf=$confidence)")
                                if (confidence >= MIN_EMOTION_CONFIDENCE) {
                                    eventScope.launch {
                                        EventBus.emit(AIEvent.EmotionDetected(emotion, confidence))
                                        Log.d(TAG, "Emitted EmotionDetected event for $emotion")
                                    }
                                } else {
                                    Log.d(TAG, "Emotion confidence too low, skip")
                                }
                            } else {
                                Log.d(TAG, "classifyEmotion returned null")
                            }
                        } else {
                            Log.d(TAG, "cropFaceSafe returned null")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during emotion detection", e)
                    } finally {
                        bmp?.recycle()
                    }
                }
            } else {
                currentTrackingId = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleFaces error", e)
        } finally {
            imageProxy.close()
        }
    }

    private fun cropFaceSafe(image: Image, face: Face, rotationDegrees: Int): Bitmap? {
        return try {
            val bitmap = imageToBitmap(image, rotationDegrees)
            val box = face.boundingBox
            val l = box.left.coerceAtLeast(0)
            val t = box.top.coerceAtLeast(0)
            val r = box.right.coerceAtMost(bitmap.width)
            val b = box.bottom.coerceAtMost(bitmap.height)
            if (r - l <= 0 || b - t <= 0) {
                bitmap.recycle()
                return null
            }
            val crop = Bitmap.createBitmap(bitmap, l, t, r - l, b - t)
            bitmap.recycle()
            val scaled = Bitmap.createScaledBitmap(crop, emotionInputSize, emotionInputSize, true)
            crop.recycle()
            scaled
        } catch (e: Exception) {
            Log.e(TAG, "cropFaceSafe error", e)
            null
        }
    }

    private fun imageToBitmap(image: Image, rotationDegrees: Int): Bitmap {
        val y = image.planes[0].buffer
        val u = image.planes[1].buffer
        val v = image.planes[2].buffer
        val ySize = y.remaining()
        val uSize = u.remaining()
        val vSize = v.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        y.get(nv21, 0, ySize)
        v.get(nv21, ySize, vSize)
        u.get(nv21, ySize + vSize, uSize)
        val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = java.io.ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, image.width, image.height), 70, out)
        out.close()
        var bmp = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
        if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            bmp.recycle()
            bmp = rotated
        }
        return bmp
    }

    private fun classifyEmotion(faceBitmap: Bitmap): Pair<String, Float>? {
        val model = emotionInterpreter ?: return null
        return try {
            val byteBuffer = ByteBuffer.allocateDirect(4 * emotionInputSize * emotionInputSize * 1)
            byteBuffer.order(ByteOrder.nativeOrder())
            for (y in 0 until emotionInputSize) {
                for (x in 0 until emotionInputSize) {
                    val pixel = faceBitmap.getPixel(x, y)
                    val gray = (0.299 * ((pixel shr 16) and 0xFF) +
                            0.587 * ((pixel shr 8) and 0xFF) +
                            0.114 * (pixel and 0xFF)).toFloat()
                    byteBuffer.putFloat(gray / 255.0f)
                }
            }
            val output = Array(1) { FloatArray(emotions.size) }
            model.run(byteBuffer, output)
            val scores = output[0]
            val maxIndex = scores.indices.maxByOrNull { scores[it] } ?: return null
            val emotion = emotions[maxIndex]
            val confidence = scores[maxIndex]
            Pair(emotion, confidence)
        } catch (e: Exception) {
            Log.e(TAG, "classifyEmotion error", e)
            null
        }
    }

    private fun loadEmotionModel() {
        try {
            val context = lifecycleOwner as Context
            val fd = context.assets.openFd(emotionModelName)
            val inputStream = FileInputStream(fd.fileDescriptor)
            val fileChannel = inputStream.channel
            val buffer: MappedByteBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            emotionInterpreter = Interpreter(buffer)
            Log.d(TAG, "Emotion model loaded successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Emotion model not found – emotion detection disabled", e)
            emotionInterpreter = null
        }
    }

    fun stop() {
        Log.d(TAG, "EyeManager stopping")
        faceDetector.close()
        cameraProvider?.unbindAll()
        emotionInterpreter?.close()
        faceEmbedderInterpreter?.close()
        cameraExecutor?.shutdown()
        cameraExecutor = null
    }

    fun release() {
        stop()
    }

    fun resetFacePresence() {
        wasFacePresent = false
        currentTrackingId = null
        blinkDetected = false
        leftEyeOpenProb = null
        rightEyeOpenProb = null
        knownTrackingIds.clear()
        Log.d(TAG, "Face presence state reset - wasFacePresent = false, knownTrackingIds cleared")
    }

    fun isFacePresent(): Boolean = wasFacePresent
}