package id.biz.id.cumi.proxy

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.concurrent.thread

/**
 * Minimal SOCKS5 server (RFC 1928) supporting the CONNECT command with optional
 * username/password authentication (RFC 1929). Uses one worker thread per relay
 * direction, which keeps the code simple and performs well for LAN sharing.
 */
class Socks5Server(
    private val port: Int,
    private val credentials: Credentials?,
    private val events: Events,
) {
    data class Credentials(val username: String, val password: String)

    interface Events {
        fun onConnectionOpened()
        fun onConnectionClosed(client: String, target: String, bytesUp: Long, bytesDown: Long, durationMs: Long)
        fun onConnectionFailed(client: String, target: String?, reason: String)
        fun onTraffic(bytesUp: Long, bytesDown: Long)
    }

    private val executor: ExecutorService = Executors.newCachedThreadPool { r ->
        Thread(r, "socks-worker").apply { isDaemon = true }
    }
    private val openSockets = ConcurrentHashMap.newKeySet<Socket>()

    @Volatile
    private var serverSocket: ServerSocket? = null

    /** Binds the listening socket. Throws [IOException] if the port is unavailable. */
    fun start() {
        val ss = ServerSocket()
        ss.reuseAddress = true
        try {
            ss.bind(InetSocketAddress(port), BACKLOG)
        } catch (e: IOException) {
            ss.closeQuietly()
            throw e
        }
        serverSocket = ss
        thread(name = "socks-acceptor", isDaemon = true) { acceptLoop(ss) }
    }

    fun stop() {
        serverSocket?.closeQuietly()
        serverSocket = null
        openSockets.forEach { it.closeQuietly() }
        openSockets.clear()
        executor.shutdownNow()
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (!ss.isClosed) {
            val client = try {
                ss.accept()
            } catch (_: IOException) {
                break
            }
            openSockets += client
            try {
                executor.execute { handle(client) }
            } catch (_: RejectedExecutionException) {
                client.closeQuietly()
                break
            }
        }
    }

    private fun handle(client: Socket) {
        val clientAddr = client.inetAddress?.hostAddress ?: "unknown"
        var remote: Socket? = null
        var target: String? = null
        var opened = false
        var bytesUp = 0L
        var bytesDown = 0L
        val startedAt = System.currentTimeMillis()

        try {
            client.soTimeout = HANDSHAKE_TIMEOUT_MS
            client.tcpNoDelay = true
            // The relay must keep reading from this same buffered stream so no
            // bytes that arrived together with the handshake are lost.
            val input = DataInputStream(BufferedInputStream(client.getInputStream()))
            val output = client.getOutputStream()

            negotiateMethod(input, output)
            credentials?.let { authenticate(input, output, it) }

            if (input.readUnsignedByte() != VERSION) throw ProtocolException("Invalid request version")
            val command = input.readUnsignedByte()
            input.readUnsignedByte() // reserved
            val host = when (input.readUnsignedByte()) {
                ATYP_IPV4 -> InetAddress.getByAddress(input.readBytes(4)).hostAddress!!
                ATYP_DOMAIN -> String(input.readBytes(input.readUnsignedByte()), Charsets.US_ASCII)
                ATYP_IPV6 -> InetAddress.getByAddress(input.readBytes(16)).hostAddress!!
                else -> {
                    sendReply(output, REP_ATYP_NOT_SUPPORTED)
                    throw ProtocolException("Unsupported address type")
                }
            }
            val targetPort = input.readUnsignedShort()
            target = formatTarget(host, targetPort)

            if (command != CMD_CONNECT) {
                sendReply(output, REP_COMMAND_NOT_SUPPORTED)
                throw ProtocolException("Command 0x%02x is not supported".format(command))
            }

            val upstream = Socket().also { remote = it }
            openSockets += upstream
            try {
                upstream.connect(InetSocketAddress(host, targetPort), CONNECT_TIMEOUT_MS)
            } catch (e: IOException) {
                sendReply(output, replyCodeFor(e))
                throw e
            }
            upstream.tcpNoDelay = true
            sendReply(output, REP_SUCCEEDED, upstream.localAddress, upstream.localPort)

            client.soTimeout = 0
            opened = true
            events.onConnectionOpened()

            val downstream = executor.submit<Long> {
                pump(upstream.getInputStream(), output, client, upstreamDirection = false)
            }
            bytesUp = pump(input, upstream.getOutputStream(), upstream, upstreamDirection = true)
            bytesDown = try {
                downstream.get()
            } catch (_: Exception) {
                0L
            }
        } catch (e: Exception) {
            if (!opened) events.onConnectionFailed(clientAddr, target, describe(e))
        } finally {
            client.closeQuietly()
            remote?.closeQuietly()
            openSockets -= client
            remote?.let { openSockets -= it }
            if (opened) {
                events.onConnectionClosed(
                    clientAddr, target.orEmpty(), bytesUp, bytesDown,
                    System.currentTimeMillis() - startedAt,
                )
            }
        }
    }

    private fun negotiateMethod(input: DataInputStream, output: OutputStream) {
        if (input.readUnsignedByte() != VERSION) throw ProtocolException("Not a SOCKS5 client")
        val methods = input.readBytes(input.readUnsignedByte())
        val wanted = if (credentials != null) METHOD_USERNAME_PASSWORD else METHOD_NO_AUTH
        if (methods.none { (it.toInt() and 0xFF) == wanted }) {
            output.write(byteArrayOf(VERSION.toByte(), METHOD_NONE_ACCEPTABLE.toByte()))
            output.flush()
            throw ProtocolException(
                if (credentials != null) "Client did not offer username/password authentication"
                else "Client requires authentication but it is disabled"
            )
        }
        output.write(byteArrayOf(VERSION.toByte(), wanted.toByte()))
        output.flush()
    }

    private fun authenticate(input: DataInputStream, output: OutputStream, expected: Credentials) {
        val version = input.readUnsignedByte()
        val username = String(input.readBytes(input.readUnsignedByte()), Charsets.UTF_8)
        val password = String(input.readBytes(input.readUnsignedByte()), Charsets.UTF_8)
        val ok = version == AUTH_VERSION &&
            constantTimeEquals(username, expected.username) &&
            constantTimeEquals(password, expected.password)
        output.write(byteArrayOf(AUTH_VERSION.toByte(), if (ok) 0 else 1))
        output.flush()
        if (!ok) throw ProtocolException("Authentication failed for user \"$username\"")
    }

    /** Copies [src] to [dst] until EOF and returns the number of bytes copied. */
    private fun pump(src: InputStream, dst: OutputStream, dstSocket: Socket, upstreamDirection: Boolean): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0L
        try {
            while (true) {
                val n = src.read(buffer)
                if (n < 0) break
                dst.write(buffer, 0, n)
                total += n
                if (upstreamDirection) events.onTraffic(n.toLong(), 0) else events.onTraffic(0, n.toLong())
            }
            // Propagate the half-close so the peer sees EOF while the other direction drains.
            try {
                dstSocket.shutdownOutput()
            } catch (_: IOException) {
            }
        } catch (_: IOException) {
            // A reset on either side ends the session; closing unblocks the opposite pump.
            dstSocket.closeQuietly()
        }
        return total
    }

    private fun sendReply(output: OutputStream, code: Int, address: InetAddress? = null, port: Int = 0) {
        val addr = address?.address ?: ByteArray(4)
        val atyp = if (addr.size == 16) ATYP_IPV6 else ATYP_IPV4
        val packet = ByteBuffer.allocate(6 + addr.size)
            .put(VERSION.toByte())
            .put(code.toByte())
            .put(0)
            .put(atyp.toByte())
            .put(addr)
            .putShort(port.toShort())
        try {
            output.write(packet.array())
            output.flush()
        } catch (_: IOException) {
        }
    }

    private fun replyCodeFor(e: IOException): Int = when (e) {
        is UnknownHostException, is NoRouteToHostException, is SocketTimeoutException -> REP_HOST_UNREACHABLE
        is ConnectException ->
            if (e.message?.contains("ENETUNREACH") == true) REP_NETWORK_UNREACHABLE else REP_CONNECTION_REFUSED
        else -> REP_GENERAL_FAILURE
    }

    private fun describe(e: Exception): String = when (e) {
        is EOFException -> "Client disconnected during handshake"
        is SocketTimeoutException -> "Timed out"
        is UnknownHostException -> "Unknown host"
        else -> e.message?.substringAfter(": ")?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
    }

    private fun formatTarget(host: String, port: Int) =
        if (host.contains(':')) "[$host]:$port" else "$host:$port"

    private fun DataInputStream.readBytes(count: Int): ByteArray = ByteArray(count).also { readFully(it) }

    private fun constantTimeEquals(a: String, b: String) =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    private fun Closeable.closeQuietly() {
        try {
            close()
        } catch (_: IOException) {
        }
    }

    private companion object {
        const val VERSION = 5
        const val AUTH_VERSION = 1
        const val BACKLOG = 128
        const val BUFFER_SIZE = 32 * 1024
        const val HANDSHAKE_TIMEOUT_MS = 30_000
        const val CONNECT_TIMEOUT_MS = 15_000

        const val METHOD_NO_AUTH = 0x00
        const val METHOD_USERNAME_PASSWORD = 0x02
        const val METHOD_NONE_ACCEPTABLE = 0xFF

        const val CMD_CONNECT = 0x01

        const val ATYP_IPV4 = 0x01
        const val ATYP_DOMAIN = 0x03
        const val ATYP_IPV6 = 0x04

        const val REP_SUCCEEDED = 0x00
        const val REP_GENERAL_FAILURE = 0x01
        const val REP_NETWORK_UNREACHABLE = 0x03
        const val REP_HOST_UNREACHABLE = 0x04
        const val REP_CONNECTION_REFUSED = 0x05
        const val REP_COMMAND_NOT_SUPPORTED = 0x07
        const val REP_ATYP_NOT_SUPPORTED = 0x08
    }
}
