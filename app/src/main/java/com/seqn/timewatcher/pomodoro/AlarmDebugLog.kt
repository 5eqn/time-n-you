package com.seqn.timewatcher.pomodoro

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Append-only, process-persistent debug log for the pomodoro alarm pipeline. Every step of
 *  scheduling + firing is recorded (status OK/FAIL/INFO + relevant data) so a single test run on
 *  a customer device leaves a complete trace — enough to diagnose a Vivo-style foreground-service
 *  block from one shot. Persisted as JSON-lines under filesDir, so it survives the process being
 *  killed between schedule and fire and reappears when Settings reopens.
 *
 *  Uses org.json (manual, no reflection) so it is R8/minify-safe with no keep rules. */
data class AlarmLogEntry(
    val ts: Long,
    val phase: String,
    val status: String,
    val message: String,
    val data: Map<String, String> = emptyMap(),
)

object AlarmDebugLog {
    const val STATUS_OK = "OK"
    const val STATUS_FAIL = "FAIL"
    const val STATUS_INFO = "INFO"

    private const val MAX_ENTRIES = 400
    private const val FILE_NAME = "alarm_debug_log.jsonl"

    private val io = Executors.newSingleThreadExecutor()
    private val memLock = Any()
    private val fileLock = Any()

    @Volatile private var file: File? = null
    private val _events = MutableStateFlow<List<AlarmLogEntry>>(emptyList())
    val events: StateFlow<List<AlarmLogEntry>> = _events.asStateFlow()

    fun init(context: Context) {
        val f = File(context.applicationContext.filesDir, FILE_NAME)
        synchronized(fileLock) {
            file = f
            runCatching { f.parentFile?.mkdirs() }
            loadLocked(f)
        }
    }

    fun log(phase: String, status: String, message: String, data: Map<String, String> = emptyMap()) {
        val entry = AlarmLogEntry(System.currentTimeMillis(), phase, status, message, data)
        synchronized(memLock) {
            _events.value = (_events.value + entry).takeLast(MAX_ENTRIES)
        }
        io.execute {
            val f = file ?: return@execute
            val line = encode(entry)
            if (line.isEmpty()) return@execute
            try {
                synchronized(fileLock) { f.appendText(line + "\n") }
            } catch (_: Throwable) {
            }
        }
    }

    fun clear() {
        synchronized(memLock) { _events.value = emptyList() }
        io.execute {
            val f = file ?: return@execute
            try {
                synchronized(fileLock) { if (f.exists()) f.writeText("") }
            } catch (_: Throwable) {
            }
        }
    }

    /** Whole log as one shareable text block (newest last), for copy/share. */
    fun asText(): String =
        _events.value.joinToString("\n") { it.formatted() }

    private fun loadLocked(f: File) {
        val list = ArrayList<AlarmLogEntry>()
        try {
            if (!f.exists()) {
                synchronized(memLock) { _events.value = emptyList() }
                return
            }
            f.useLines { lines ->
                lines.forEach { line -> if (line.isNotBlank()) parse(line)?.let { list.add(it) } }
            }
        } catch (_: Throwable) {
        }
        if (list.size > MAX_ENTRIES) {
            val kept = ArrayList(list.takeLast(MAX_ENTRIES))
            synchronized(memLock) { _events.value = kept }
            runCatching { f.writeText(kept.joinToString("\n", postfix = "\n") { encode(it) }) }
        } else {
            synchronized(memLock) { _events.value = ArrayList(list) }
        }
    }

    private fun parse(line: String): AlarmLogEntry? = try {
        val o = JSONObject(line)
        val d = o.optJSONObject("data")
        val data = LinkedHashMap<String, String>()
        if (d != null) {
            val keys = d.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                data[k] = d.optString(k)
            }
        }
        AlarmLogEntry(
            ts = o.optLong("ts"),
            phase = o.optString("phase"),
            status = o.optString("status"),
            message = o.optString("message"),
            data = data,
        )
    } catch (_: Throwable) {
        null
    }

    private fun encode(e: AlarmLogEntry): String = try {
        val d = JSONObject()
        e.data.forEach { (k, v) -> d.put(k, v) }
        JSONObject()
            .put("ts", e.ts)
            .put("phase", e.phase)
            .put("status", e.status)
            .put("message", e.message)
            .put("data", d)
            .toString()
    } catch (_: Throwable) {
        ""
    }
}

private val logTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

/** Single-line, monospace-friendly rendering for the debug panel + shared text. */
fun AlarmLogEntry.formatted(): String {
    val time = logTimeFormat.format(Date(ts))
    val dataStr = if (data.isEmpty()) {
        ""
    } else {
        "  " + data.entries.joinToString(" ") { "${it.key}=${it.value}" }
    }
    return "$time $phase[$status] $message$dataStr"
}
