package com.kuryelog.app.remote

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import com.kuryelog.app.core.Store
import com.kuryelog.logic.core.NotificationIds
import com.kuryelog.logic.remote.CaptureGeometry
import com.kuryelog.logic.remote.Geometry
import com.kuryelog.logic.remote.RemoteText

/**
 * Ekran paylaşımı (REMOTE.md 1.2, 1.5): MediaProjection → sanal ekran (`AUTO_MIRROR`) → H.264 donanım kodlayıcı (yüzey girişi).
 * Onaydan sonra [ConsentActivity] başlatır; tür `mediaProjection` (onaydan sonra, `getMediaProjection`'dan önce ön plana geçer).
 * Sanal ekran paylaşım boyunca bir kez kurulur (Android 14: projeksiyon başına bir kez); kodlayıcı yalnız izleyen bağlıyken çalışır,
 * boşta yüzey `null`. Kodlayıcı çıktısı [RemoteHub]'a gider; eski kodlayıcının geç gelen çıktısı nesil numarasıyla atılır.
 * Cihazda doğrulanmadı.
 */
class CaptureService : Service() {
    private val thread = HandlerThread("weem-capture").apply { start() }
    private val handler = Handler(thread.looper)
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var codec: MediaCodec? = null
    private var input: Surface? = null
    @Volatile private var generation = 0
    private var displayListener: DisplayManager.DisplayListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        current = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            handler.post { release() }
            stopSelf()
            return START_NOT_STICKY
        }
        // startForegroundService ile gelen her başlatış önce ön plana geçer (CrashGuardTest).
        val ok = runCatching { startForeground(NotificationIds.REMOTE_FGS, notification(this, watched = false), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) }.isSuccess
        when (intent?.action) {
            ACTION_USER_STOP -> RemoteHub.stopShare()
            ACTION_START -> {
                val code = intent.getIntExtra(EXTRA_CODE, Activity.RESULT_CANCELED)
                val data = intent.dataIntent()
                if (!ok || data == null || projection != null) {
                    if (projection == null) refuse()
                } else {
                    handler.post { begin(code, data) }
                }
            }
            else -> if (projection == null) refuse()
        }
        return START_NOT_STICKY
    }

    private fun refuse() {
        RemoteHub.consentDenied()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun begin(code: Int, data: Intent) {
        try {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val p = mpm.getMediaProjection(code, data) ?: throw IllegalStateException("no projection")
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    handler.post {
                        if (projection === p) {
                            release()
                            RemoteHub.projectionStopped()
                            stopSelf()
                        }
                    }
                }

                override fun onCapturedContentResize(width: Int, height: Int) {
                    RemoteHub.screenSize(width, height)
                }
            }, handler)
            projection = p
            val m = metrics()
            display = p.createVirtualDisplay("WEEM uzaktan", m.widthPixels, m.heightPixels, m.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, null, null, handler)
            watchRotation()
            RemoteHub.consentGranted(m.widthPixels, m.heightPixels)
        } catch (e: Exception) {
            Store.sink(this).failure("remote_projection", e)
            release()
            refuse()
        }
    }

    /** Döndürme (Android 14 altı `onCapturedContentResize` yok): varsayılan ekranın gerçek boyutu. */
    private fun watchRotation() {
        val dm = getSystemService(DisplayManager::class.java)
        val l = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId != Display.DEFAULT_DISPLAY) return
                val m = metrics()
                RemoteHub.screenSize(m.widthPixels, m.heightPixels)
            }
        }
        dm.registerDisplayListener(l, handler)
        displayListener = l
    }

    @Suppress("DEPRECATION")
    private fun metrics(): DisplayMetrics = DisplayMetrics().also {
        getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY).getRealMetrics(it)
    }

    /** Kodlayıcıyı [g] boyutunda açar; sanal ekran bu boyuta iner ve yüzeyi kodlayıcının girişi olur. [gen]: [RemoteHub]'ın nesli. */
    fun startEncoder(g: Geometry, gen: Int) = handler.post {
        stopEncoderNow()
        val vd = display ?: return@post
        generation = gen
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, g.width, g.height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, g.bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, CaptureGeometry.FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEY_FRAME_INTERVAL_S)
                setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, REPEAT_AFTER_US)
                setFloat(MediaFormat.KEY_MAX_FPS_TO_ENCODER, CaptureGeometry.FPS.toFloat())
                setInteger(MediaFormat.KEY_PRIORITY, 0)
                setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setInteger(MediaFormat.KEY_LATENCY, 1)
            }
            val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec = c
            c.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = Unit

                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                    if (gen != generation) return
                    try {
                        val buf = codec.getOutputBuffer(index)
                        if (buf != null && info.size > 0) {
                            val bytes = ByteArray(info.size)
                            buf.position(info.offset)
                            buf.get(bytes, 0, info.size)
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                RemoteHub.encoderConfig(gen, g.width, g.height, bytes)
                            } else {
                                RemoteHub.encoderFrame(gen, bytes, info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0)
                            }
                        }
                        codec.releaseOutputBuffer(index, false)
                    } catch (e: Exception) {
                        failed(gen, e)
                    }
                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) = failed(gen, e)

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) = Unit
            }, handler)
            c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = c.createInputSurface()
            input = surface
            c.start()
            vd.resize(g.width, g.height, metrics().densityDpi)
            vd.surface = surface
        } catch (e: Exception) {
            failed(gen, e)
        }
    }

    private fun failed(gen: Int, e: Exception) {
        handler.post {
            if (gen != generation) return@post
            Store.sink(this).failure("remote_encoder", e)
            stopEncoderNow()
            RemoteHub.encoderFailed()
        }
    }

    fun stopEncoder() = handler.post { stopEncoderNow() }

    fun setBitrate(bps: Int) = handler.post {
        runCatching { codec?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bps) }) }
    }

    fun requestKey() = handler.post {
        runCatching { codec?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) }) }
    }

    private fun stopEncoderNow() {
        generation = 0
        display?.surface = null
        codec?.let { c -> runCatching { c.stop() }; runCatching { c.release() } }
        codec = null
        input?.release()
        input = null
    }

    private fun release() {
        stopEncoderNow()
        displayListener?.let { l -> runCatching { getSystemService(DisplayManager::class.java).unregisterDisplayListener(l) } }
        displayListener = null
        display?.release()
        display = null
        projection?.let { p -> runCatching { p.stop() } }
        projection = null
    }

    override fun onDestroy() {
        handler.post { release() }
        thread.quitSafely()
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.kuryelog.app.remote.CAPTURE_START"
        private const val ACTION_STOP = "com.kuryelog.app.remote.CAPTURE_STOP"
        private const val ACTION_USER_STOP = "com.kuryelog.app.remote.CAPTURE_USER_STOP"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val KEY_FRAME_INTERVAL_S = 10
        private const val REPEAT_AFTER_US = 100_000L

        @Volatile var current: CaptureService? = null
            private set

        /** Onay alındı ([ConsentActivity]): ön plan servisi + MediaProjection. */
        fun start(ctx: Context, code: Int, data: Intent) {
            val i = Intent(ctx, CaptureService::class.java).setAction(ACTION_START).putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data)
            runCatching { ctx.startForegroundService(i) }.onFailure { RemoteHub.consentDenied() }
        }

        fun stop(ctx: Context) {
            runCatching { ctx.startService(Intent(ctx, CaptureService::class.java).setAction(ACTION_STOP)) }
        }

        /** Bildirim metnini yeniler (izleniyor mu). */
        fun refresh(ctx: Context, watched: Boolean) {
            if (current == null) return
            runCatching { ctx.getSystemService(NotificationManager::class.java).notify(NotificationIds.REMOTE_FGS, notification(ctx, watched)) }
        }

        private fun notification(ctx: Context, watched: Boolean): Notification {
            val lang = Store.lang(ctx)
            RemoteNotices.channel(ctx, lang)
            val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                ?.let { PendingIntent.getActivity(ctx, NotificationIds.REMOTE_FGS, it, PendingIntent.FLAG_IMMUTABLE) }
            val stop = PendingIntent.getService(
                ctx, NotificationIds.REMOTE_FGS, Intent(ctx, CaptureService::class.java).setAction(ACTION_USER_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val text = (if (watched) RemoteText.N_WATCHED else RemoteText.H_SHARING).get(lang)
            val b = Notification.Builder(ctx, NotificationIds.CHANNEL_REMOTE)
                .setContentTitle(RemoteText.CHANNEL.get(lang))
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentIntent(open)
                .addAction(Notification.Action.Builder(null, RemoteText.NOTIF_STOP.get(lang), stop).build())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
            return b.build()
        }

        private fun Intent.dataIntent(): Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(EXTRA_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") getParcelableExtra(EXTRA_DATA)
    }
}
