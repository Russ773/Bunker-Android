package app.getbunker.rcon.core

import app.getbunker.rcon.rcon.LogEntry
import app.getbunker.rcon.rcon.LogKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Everything the console has shown. New lines are collected for a moment and published together, so a server that
 * prints hundreds of lines a second does not make the screen redraw hundreds of times.
 */
class ConsoleLog(scope: CoroutineScope) {
    private val incoming = Channel<LogEntry>(Channel.UNLIMITED)
    private val ids = AtomicLong()
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    init {
        scope.launch {
            for (first in incoming) {
                val batch = ArrayList<LogEntry>()
                batch += first
                delay(70)
                while (true) batch += incoming.tryReceive().getOrNull() ?: break
                _entries.update { old ->
                    val merged = if (old.size + batch.size > MAX) old.takeLast(MAX - batch.size) + batch else old + batch
                    merged
                }
            }
        }
    }

    fun add(
        text: String,
        kind: LogKind = LogKind.Normal,
        userId: String? = null,
        userName: String? = null,
        message: String? = null,
        color: String? = null,
        channel: Int = 0,
        tags: String? = null,
    ): LogEntry {
        val e = LogEntry(ids.incrementAndGet(), System.currentTimeMillis(), text, kind, userId, userName, message, color, channel, tags)
        incoming.trySend(e)
        return e
    }

    fun clear() {
        _entries.value = emptyList()
    }

    companion object {
        const val MAX = 5000
    }
}
