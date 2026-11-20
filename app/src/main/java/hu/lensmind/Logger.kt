package hu.lensmind

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Egyszerű körpuffer-napló, a 🐞 panel ezt mutatja. */
object Logger {
    private val buf = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun d(tag: String, msg: String) {
        val line = "${fmt.format(Date())} [$tag] $msg"
        buf.addLast(line)
        while (buf.size > 300) buf.removeFirst()
        Log.d("LensMind", line)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        d(tag, "HIBA: $msg${if (t != null) " – ${t.javaClass.simpleName}: ${t.message}" else ""}")
    }

    @Synchronized
    fun dump(): String = buf.joinToString("\n")

    @Synchronized
    fun clear() = buf.clear()
}
