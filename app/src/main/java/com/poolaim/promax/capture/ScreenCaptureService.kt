package com.poolaim.promax.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.poolaim.promax.domain.vision.GameFrameAnalyzer
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.util.concurrent.atomic.AtomicBoolean

/** Captures and analyzes screen frames locally after explicit Android consent. */
class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var analyzer: GameFrameAnalyzer? = null
    private val stopping = AtomicBoolean(false)
    private var lastFrameAt = 0L
    private var fpsWindowAt = 0L
    private var framesInWindow = 0
    private var displayedFps = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopCapture(); stopSelf(); return START_NOT_STICKY }
        if (projection != null) return START_NOT_STICKY
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
        @Suppress("DEPRECATION")
        val resultData = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            else intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        if (resultCode == null || resultCode == Int.MIN_VALUE || resultData == null) { stopSelf(); return START_NOT_STICKY }
        startCaptureForeground()
        return try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, resultData)
            startProjectionCapture()
            START_NOT_STICKY
        } catch (error: Exception) {
            Log.e(TAG, "Capture startup failed", error)
            publishResult(0, 0, "capture startup failed")
            stopCapture(); stopSelf(); START_NOT_STICKY
        }
    }

    private fun startCaptureForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "PoolAim screen analysis", NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getService(this, 71,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("PoolAim Pro Max")
            .setContentText("On-device live analysis is active").setOngoing(true)
            .addAction(0, "Stop", pending).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NOTIFICATION_ID, notification)
    }

    private fun startProjectionCapture() {
        check(OpenCVLoader.initLocal()) { "OpenCV native library failed to load" }
        analyzer = GameFrameAnalyzer()
        worker = HandlerThread("PoolAimVision").also { it.start() }
        workerHandler = Handler(worker!!.looper)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        val display = (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        val width = metrics.widthPixels.coerceAtLeast(1)
        val height = metrics.heightPixels.coerceAtLeast(1)
        val reader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ source ->
            var frame: Image? = null
            try {
                frame = source.acquireLatestImage()
                if (frame != null) analyzeFrame(frame)
            } catch (error: Exception) {
                Log.w(TAG, "Frame processing failed", error)
                publishResult(0, displayedFps, error.message ?: "frame processing failed")
            } finally { frame?.close() }
        }, workerHandler)
        val mp = projection ?: error("MediaProjection not available")
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopCapture(); stopSelf() }
        }, workerHandler)
        virtualDisplay = mp.createVirtualDisplay("PoolAimLiveAnalysis", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, workerHandler)
    }

    private fun analyzeFrame(image: Image) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastFrameAt < 100L) return
        lastFrameAt = now
        val plane = image.planes.firstOrNull() ?: return
        val paddedWidth = (plane.rowStride / plane.pixelStride).coerceAtLeast(image.width)
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        var crop: Bitmap? = null
        try {
            plane.buffer.rewind()
            padded.copyPixelsFromBuffer(plane.buffer)
            val bitmap = if (paddedWidth == image.width) padded else Bitmap.createBitmap(padded, 0, 0, image.width, image.height).also { crop = it }
            val rgba = Mat()
            val bgr = Mat()
            try {
                Utils.bitmapToMat(bitmap, rgba)
                Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
                val result = analyzer?.analyze(bgr) ?: return
                val t = SystemClock.elapsedRealtime()
                if (fpsWindowAt == 0L) fpsWindowAt = t
                framesInWindow++
                if (t - fpsWindowAt >= 1000L) { displayedFps = framesInWindow; framesInWindow = 0; fpsWindowAt = t }
                val candidates = result.candidates
                publishResult(candidates.size, displayedFps, null,
                    candidates.map { it.centerScreen.x.toFloat() }.toFloatArray(),
                    candidates.map { it.centerScreen.y.toFloat() }.toFloatArray(),
                    candidates.map { it.radiusScreenPixels.toFloat() }.toFloatArray(),
                    candidates.map { it.color.confidence.toFloat() }.toFloatArray(),
                    candidates.map { it.color.family.name }.toTypedArray(),
                    result.frameWidth, result.frameHeight)
            } finally { rgba.release(); bgr.release() }
        } finally {
            crop?.let { if (!it.isRecycled) it.recycle() }
            if (!padded.isRecycled) padded.recycle()
        }
    }

    private fun publishResult(count: Int, fps: Int, warning: String?, xs: FloatArray? = null,
        ys: FloatArray? = null, radii: FloatArray? = null, confidence: FloatArray? = null,
        families: Array<String>? = null, width: Int = 0, height: Int = 0) {
        val intent = Intent(ACTION_ANALYSIS_UPDATE).setPackage(packageName)
            .putExtra(EXTRA_CANDIDATE_COUNT, count).putExtra(EXTRA_ANALYSIS_FPS, fps)
            .putExtra(EXTRA_XS, xs).putExtra(EXTRA_YS, ys).putExtra(EXTRA_RADII, radii)
            .putExtra(EXTRA_CONFIDENCE, confidence).putExtra(EXTRA_FAMILIES, families)
            .putExtra(EXTRA_FRAME_WIDTH, width).putExtra(EXTRA_FRAME_HEIGHT, height)
        if (warning != null) intent.putExtra(EXTRA_ANALYSIS_WARNING, warning)
        sendBroadcast(intent)
    }

    private fun stopCapture() {
        if (!stopping.compareAndSet(false, true)) return
        runCatching { virtualDisplay?.release() }; virtualDisplay = null
        runCatching { imageReader?.setOnImageAvailableListener(null, null) }
        runCatching { imageReader?.close() }; imageReader = null
        runCatching { projection?.stop() }; projection = null
        analyzer = null
        workerHandler?.removeCallbacksAndMessages(null); workerHandler = null
        worker?.quitSafely(); worker = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    override fun onDestroy() { stopCapture(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_RESULT_CODE = "poolaim.capture.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "poolaim.capture.RESULT_DATA"
        const val ACTION_STOP = "poolaim.capture.STOP"
        const val ACTION_ANALYSIS_UPDATE = "com.poolaim.promax.action.ANALYSIS_UPDATE"
        const val EXTRA_CANDIDATE_COUNT = "poolaim.capture.CANDIDATE_COUNT"
        const val EXTRA_ANALYSIS_FPS = "poolaim.capture.ANALYSIS_FPS"
        const val EXTRA_ANALYSIS_WARNING = "poolaim.capture.ANALYSIS_WARNING"
        const val EXTRA_XS = "poolaim.capture.XS"
        const val EXTRA_YS = "poolaim.capture.YS"
        const val EXTRA_RADII = "poolaim.capture.RADII"
        const val EXTRA_CONFIDENCE = "poolaim.capture.CONFIDENCE"
        const val EXTRA_FAMILIES = "poolaim.capture.FAMILIES"
        const val EXTRA_FRAME_WIDTH = "poolaim.capture.FRAME_WIDTH"
        const val EXTRA_FRAME_HEIGHT = "poolaim.capture.FRAME_HEIGHT"
        private const val TAG = "PoolAimCapture"
        private const val CHANNEL_ID = "poolaim_screen_analysis"
        private const val NOTIFICATION_ID = 7102
    }
}
