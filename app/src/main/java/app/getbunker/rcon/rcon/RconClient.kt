package app.getbunker.rcon.rcon

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RconMessage(val identifier: Int, val message: String, val type: String)

class RconException(message: String) : Exception(message)

/**
 * Minimal Rust WebRCON client: a WebSocket to ws://host:port/password that exchanges JSON messages
 * `{"Identifier": 1001, "Message": "serverinfo", "Name": "Bunker"}`.
 */
class RconClient(private val http: OkHttpClient = defaultHttp) {
    private var socket: WebSocket? = null
    private var generation = 0
    private var openedAt = 0L
    private val nextId = AtomicInteger(1000)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<RconMessage>>()

    /** Console output and replies to commands sent with [send] (not [query]). */
    @Volatile var onMessage: ((RconMessage) -> Unit)? = null

    /** The connection ended without [disconnect] being called. The argument says why. */
    @Volatile var onClosed: ((String) -> Unit)? = null

    val isConnected: Boolean @Synchronized get() = socket != null

    suspend fun connect(host: String, port: Int, password: String, tls: Boolean = false, timeoutMs: Long = 10_000) {
        disconnect()
        val url = HttpUrl.Builder()
            .scheme(if (tls) "https" else "http")
            .host(host.trim())
            .port(port)
            .addPathSegment(password)
            .build()
        val request = Request.Builder().url(url).build()
        val gen = synchronized(this) { ++generation }

        try {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine<Unit> { cont ->
                    val ws = http.newWebSocket(request, object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            synchronized(this@RconClient) {
                                if (gen != generation) { webSocket.cancel(); return }
                                socket = webSocket
                                openedAt = System.currentTimeMillis()
                            }
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            if (gen != generation) return
                            val msg = parse(text) ?: return
                            val waiter = pending.remove(msg.identifier)
                            if (waiter != null) waiter.complete(msg) else onMessage?.invoke(msg)
                        }

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(1000, null)
                            ended(gen, webSocket, "Server closed the connection")
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            ended(gen, webSocket, "Server closed the connection")
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            val why = describe(t, response)
                            if (cont.isActive) { cont.resumeWithException(RconException(why)); return }
                            ended(gen, webSocket, "Connection lost: $why")
                        }
                    })
                    cont.invokeOnCancellation { ws.cancel() }
                }
            }
        } catch (e: TimeoutCancellationException) {
            disconnect()
            throw RconException("Timed out. Check the address and port, and that the server's firewall allows it.")
        } catch (e: Exception) {
            synchronized(this) { socket = null }
            throw e
        }
    }

    private fun ended(gen: Int, ws: WebSocket, reason: String) {
        val callback: ((String) -> Unit)?
        val quick: Boolean
        synchronized(this) {
            if (gen != generation || socket !== ws) return
            socket = null
            quick = System.currentTimeMillis() - openedAt < 2500
            callback = onClosed
        }
        pending.values.forEach { it.cancel() }
        pending.clear()
        // Rust accepts the socket and then drops it when the password is wrong, so a connection that dies at once is almost always that.
        val why = if (quick) "The server closed the connection straight away. This usually means the RCON password is wrong." else reason
        callback?.invoke(why)
    }

    /** Sends a command; the reply arrives through [onMessage]. */
    fun send(command: String) {
        val ws = synchronized(this) { socket } ?: throw RconException("Not connected")
        if (!ws.send(encode(nextId.incrementAndGet(), command))) throw RconException("Could not send (connection busy or closed)")
    }

    /** Sends a command and returns its reply, which does not appear in the console. */
    suspend fun query(command: String, timeoutMs: Long = 6000): String {
        val ws = synchronized(this) { socket } ?: throw RconException("Not connected")
        val id = nextId.incrementAndGet()
        val waiter = CompletableDeferred<RconMessage>()
        pending[id] = waiter
        try {
            if (!ws.send(encode(id, command))) throw RconException("Could not send (connection busy or closed)")
            return withTimeout(timeoutMs) { waiter.await() }.message
        } catch (e: TimeoutCancellationException) {
            throw RconException("The server did not answer in time")
        } finally {
            pending.remove(id)
        }
    }

    fun disconnect() {
        val ws: WebSocket?
        synchronized(this) {
            generation++
            ws = socket
            socket = null
        }
        pending.values.forEach { it.cancel() }
        pending.clear()
        try { ws?.close(1000, "bye") } catch (_: Exception) { }
    }

    private fun encode(id: Int, command: String): String = buildJsonObject {
        put("Identifier", id)
        put("Message", command)
        put("Name", "Bunker")
    }.toString()

    private fun parse(text: String): RconMessage? = try {
        val obj: JsonObject = Json.parseToJsonElement(text).jsonObject
        RconMessage(
            identifier = obj["Identifier"]?.jsonPrimitive?.intOrNull ?: 0,
            message = (obj["Message"] as? JsonPrimitive)?.contentOrNull ?: "",
            type = (obj["Type"] as? JsonPrimitive)?.contentOrNull ?: "",
        )
    } catch (_: Exception) {
        null
    }

    private fun describe(t: Throwable, response: Response?): String {
        if (response != null && response.code in 400..499) return "The server refused the connection (HTTP ${response.code}). Check the RCON password."
        val m = t.message.orEmpty()
        return when {
            t is java.net.UnknownHostException -> "Could not find that address. Check the server name or IP."
            t is java.net.ConnectException || m.contains("refused", true) -> "Connection refused. Check the port, and that WebRCON (+rcon.web 1) is on."
            t is java.net.SocketTimeoutException -> "Timed out. Check the address and port, and that the server's firewall allows it."
            t is javax.net.ssl.SSLException -> "Secure connection failed. Turn off 'Use secure connection' unless your server has a TLS proxy."
            m.isNotBlank() -> m
            else -> t.javaClass.simpleName
        }
    }

    companion object {
        val defaultHttp: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
