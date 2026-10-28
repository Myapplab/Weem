package com.kuryelog.app.remote

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Akış soketleri (REMOTE.md 1.4), ince: paylaşan tarafta dinleyen TCP (yalnız yerel ağdan, [Listener.allowed]), izleyen tarafta
 * bağlanan TCP (Wi-Fi ağına bağlı, [Listener.bind]). Bağlantı başına bir yazma (kuyruk) ve bir okuma iş parçacığı. Karar saf
 * makinelerde; burası yalnız bayt taşır.
 */
internal class StreamNet(private val name: String, private val listener: Listener) {
    interface Listener {
        fun accepted(conn: Int) {}
        fun opened(conn: Int) {}
        fun bytes(conn: Int, bytes: ByteArray)
        fun closed(conn: Int)
        fun allowed(remote: String): Boolean = true
        fun bind(s: Socket, host: String) {}
    }

    private val pipes = ConcurrentHashMap<Int, Pipe>()
    private val inbound = AtomicInteger(0)
    @Volatile private var server: ServerSocket? = null

    @Synchronized
    fun listen(on: Boolean, port: Int) {
        if (!on) {
            server?.closeQuietly()
            server = null
            return
        }
        if (server != null) return
        val s = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
        } catch (e: IOException) {
            return
        }
        server = s
        thread("accept") { acceptLoop(s) }
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val sock = try { s.accept() } catch (e: IOException) { break }
            val remote = sock.inetAddress?.hostAddress ?: ""
            if (!listener.allowed(remote)) {
                sock.closeQuietly()
                continue
            }
            runCatching { sock.tcpNoDelay = true }
            // Bağlanıp hemen kopan eş: akış alınamazsa soket kapanır (iş parçacığında yakalanmayan hata uygulamayı çökertir; LinkRuntime.acceptTcp gibi).
            val streams = try { sock.getInputStream() to sock.getOutputStream() } catch (e: IOException) { sock.closeQuietly(); continue }
            val id = inbound.incrementAndGet()
            val p = Pipe(id, sock)
            pipes[id] = p
            listener.accepted(id)
            p.run(streams.first, streams.second)
        }
    }

    fun connect(conn: Int, host: String, port: Int) {
        val sock = Socket()
        val p = Pipe(conn, sock)
        pipes[conn] = p
        thread("connect") {
            try {
                listener.bind(sock, host)
                sock.tcpNoDelay = true
                sock.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                if (p.closed) { sock.closeQuietly(); return@thread }
                listener.opened(conn)
                p.run(sock.getInputStream(), sock.getOutputStream())
            } catch (e: Exception) {
                sock.closeQuietly()
                if (pipes.remove(conn, p)) listener.closed(conn)
            }
        }
    }

    fun write(conn: Int, bytes: ByteArray) {
        pipes[conn]?.write(bytes)
    }

    fun close(conn: Int) {
        pipes.remove(conn)?.close()
    }

    private fun thread(what: String, block: () -> Unit) {
        Thread(block, "weem-remote-$name-$what").apply { isDaemon = true }.start()
    }

    private inner class Pipe(val id: Int, private val socket: Socket) {
        private val queue = LinkedBlockingQueue<ByteArray>()
        @Volatile var closed = false
            private set

        fun run(input: InputStream, output: OutputStream) {
            thread("write") { writeLoop(output) }
            thread("read") { readLoop(input) }
        }

        fun write(b: ByteArray) {
            if (!closed) queue.offer(b)
        }

        /** Kuyruktakiler (ör. `BYE`) yazılır, sonra kapanır. */
        fun close() {
            if (closed) return
            closed = true
            queue.offer(CLOSE)
            if (!socket.isConnected) socket.closeQuietly()
        }

        private fun writeLoop(out: OutputStream) {
            try {
                while (true) {
                    val b = queue.take()
                    if (b === CLOSE) break
                    out.write(b)
                    if (queue.isEmpty()) out.flush()
                }
                out.flush()
            } catch (e: Exception) {
                // Okuma döngüsü kopuşu bildirir.
            } finally {
                socket.closeQuietly()
            }
        }

        private fun readLoop(input: InputStream) {
            val buf = ByteArray(READ_BUF)
            try {
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) listener.bytes(id, buf.copyOf(n))
                }
            } catch (e: Exception) {
                // kopuş
            }
            closed = true
            queue.offer(CLOSE)
            socket.closeQuietly()
            if (pipes.remove(id, this)) listener.closed(id)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 3_000
        const val READ_BUF = 64 * 1024
        val CLOSE = ByteArray(0)
    }
}

private fun Closeable.closeQuietly() {
    try { close() } catch (e: Exception) { /* yok say */ }
}
