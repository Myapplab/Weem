package com.kuryelog.app.remote

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import java.nio.ByteBuffer

/**
 * İzleyende H.264 çözücü (REMOTE.md 1.5): `MediaCodec` → ekrandaki `SurfaceView` yüzeyi. Kendi iş parçacığında, eşzamanlı kip:
 * her karede giriş kuyruğa, çıkışlar hemen çizilir (düşük gecikme). Yüzey yokken yapılandırma saklanır; yüzey gelince çözücü kurulur
 * ve [onError] anahtar kare ister (sekme değişince yüzey gider, dönünce yeniden başlar). Cihazda doğrulanmadı.
 */
internal class Player(private val onError: () -> Unit) {
    private class Config(val width: Int, val height: Int, val sps: ByteArray, val pps: ByteArray)

    private val thread = HandlerThread("weem-remote-player").apply { start() }
    private val handler = Handler(thread.looper)
    private var surface: Surface? = null
    private var config: Config? = null
    private var codec: MediaCodec? = null
    private val info = MediaCodec.BufferInfo()

    fun setSurface(s: Surface?) = handler.post {
        if (s === surface) return@post
        stopCodec()
        surface = s
        if (s != null && config != null) {
            start()
            onError()
        }
    }

    fun configure(width: Int, height: Int, sps: ByteArray, pps: ByteArray) = handler.post {
        config = Config(width, height, sps, pps)
        stopCodec()
        if (surface != null) start()
    }

    fun decode(data: ByteArray, ptsUs: Long, key: Boolean) = handler.post {
        val c = codec ?: return@post
        try {
            val i = c.dequeueInputBuffer(INPUT_WAIT_US)
            if (i < 0) {
                // Çözücü yetişemiyor: kare atıldı, sonraki referanslar bozuk → anahtar kare.
                onError()
            } else {
                val buf: ByteBuffer = c.getInputBuffer(i) ?: return@post
                buf.clear()
                if (buf.capacity() < data.size) {
                    c.queueInputBuffer(i, 0, 0, ptsUs, 0)
                    onError()
                    return@post
                }
                buf.put(data)
                c.queueInputBuffer(i, 0, data.size, ptsUs, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            }
            drain(c)
        } catch (e: Exception) {
            stopCodec()
            if (surface != null && config != null) start()
            onError()
        }
    }

    fun release() = handler.post {
        stopCodec()
        config = null
    }

    private fun start() {
        val cfg = config ?: return
        val s = surface ?: return
        try {
            val f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, cfg.width, cfg.height).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(cfg.sps))
                setByteBuffer("csd-1", ByteBuffer.wrap(cfg.pps))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                setInteger(MediaFormat.KEY_PRIORITY, 0)
            }
            val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec = c
            c.configure(f, s, null, 0)
            c.start()
        } catch (e: Exception) {
            stopCodec()
        }
    }

    private fun drain(c: MediaCodec) {
        while (true) {
            val o = c.dequeueOutputBuffer(info, 0)
            if (o >= 0) c.releaseOutputBuffer(o, true) else if (o == MediaCodec.INFO_TRY_AGAIN_LATER) break
        }
        // Bazı çözücüler çıktıyı biraz geç verir: bir kez daha bak.
        handler.removeCallbacks(lateDrain)
        handler.postDelayed(lateDrain, LATE_DRAIN_MS)
    }

    private val lateDrain = Runnable {
        val c = codec ?: return@Runnable
        runCatching {
            while (true) {
                val o = c.dequeueOutputBuffer(info, 0)
                if (o >= 0) c.releaseOutputBuffer(o, true) else if (o == MediaCodec.INFO_TRY_AGAIN_LATER) break
            }
        }
    }

    private fun stopCodec() {
        handler.removeCallbacks(lateDrain)
        codec?.let { c -> runCatching { c.stop() }; runCatching { c.release() } }
        codec = null
    }

    private companion object {
        const val INPUT_WAIT_US = 10_000L
        const val LATE_DRAIN_MS = 15L
    }
}
