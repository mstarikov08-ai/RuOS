package com.ruos.share.transfer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

const val SHARE_PORT = 8988

/** One file in a transfer manifest. */
data class FileMeta(val name: String, val size: Long)

/** The transfer header sent before the bytes. */
data class TransferHeader(val sender: String, val files: List<FileMeta>) {
    val totalBytes: Long get() = files.sumOf { it.size }
    fun toJson(): String = JSONObject().apply {
        put("sender", sender)
        put("files", JSONArray().apply { files.forEach { put(JSONObject().apply { put("name", it.name); put("size", it.size) }) } })
    }.toString()

    companion object {
        fun parse(line: String): TransferHeader? = runCatching {
            val o = JSONObject(line); val arr = o.getJSONArray("files")
            val files = (0 until arr.length()).map { val f = arr.getJSONObject(it); FileMeta(f.getString("name"), f.getLong("size")) }
            TransferHeader(o.optString("sender", "RuOS"), files)
        }.getOrNull()
    }
}

/** Sends content URIs to a host (the Wi-Fi Direct group owner at 192.168.49.1). */
class SendTask(
    private val context: Context,
    private val host: String,
    private val uris: List<Uri>,
    private val senderName: String
) {
    fun run(onProgress: (sent: Long, total: Long) -> Unit, onResult: (Boolean, String) -> Unit) {
        val metas = uris.map { meta(it) }
        val header = TransferHeader(senderName, metas)
        val total = header.totalBytes
        var sent = 0L
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, SHARE_PORT), 8000)
                val out = DataOutputStream(socket.getOutputStream().buffered())
                out.writeBytes(header.toJson() + "\n")
                val buf = ByteArray(64 * 1024)
                uris.forEach { uri ->
                    context.contentResolver.openInputStream(uri)?.use { ins ->
                        while (true) {
                            val n = ins.read(buf); if (n < 0) break
                            out.write(buf, 0, n); sent += n; onProgress(sent, total)
                        }
                    }
                }
                out.flush()
            }
            onResult(true, "Отправлено")
        }.onFailure { onResult(false, "Ошибка передачи: ${it.message}") }
    }

    private fun meta(uri: Uri): FileMeta {
        var name = "файл"; var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni) ?: name
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        return FileMeta(name, size)
    }
}

/**
 * Listens on [SHARE_PORT] for an incoming transfer. On a new connection it reads the
 * header, asks [accept] (a blocking prompt the caller wires to an accept/decline UI), and,
 * if accepted, writes each file into Downloads/RuOS Share via MediaStore.
 */
class ReceiveServer(
    private val context: Context,
    private val accept: (TransferHeader) -> Boolean,
    private val onProgress: (header: TransferHeader, received: Long) -> Unit,
    private val onComplete: (TransferHeader, Boolean) -> Unit
) {
    @Volatile private var running = false
    private var server: ServerSocket? = null

    fun start() {
        if (running) return
        running = true
        Thread {
            runCatching {
                server = ServerSocket(SHARE_PORT)
                while (running) {
                    val socket = server!!.accept()
                    handle(socket)
                }
            }
        }.start()
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
        server = null
    }

    private fun handle(socket: Socket) {
        runCatching {
            socket.use { s ->
                val ins = DataInputStream(s.getInputStream())
                val headerLine = readLine(ins) ?: return
                val header = TransferHeader.parse(headerLine) ?: return
                if (!accept(header)) return
                var received = 0L
                for (file in header.files) {
                    val uri = createDownload(file.name) ?: continue
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        var remaining = file.size
                        val buf = ByteArray(64 * 1024)
                        while (remaining > 0) {
                            val n = ins.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                            if (n < 0) break
                            out.write(buf, 0, n); remaining -= n; received += n
                            onProgress(header, received)
                        }
                    }
                    finalizeDownload(uri)
                }
                onComplete(header, true)
            }
        }.onFailure { onComplete(TransferHeader("", emptyList()), false) }
    }

    private fun readLine(ins: DataInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = ins.read(); if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString()
            sb.append(b.toChar())
        }
    }

    private fun createDownload(name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/RuOS Share")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        return runCatching { context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) }.getOrNull()
    }

    private fun finalizeDownload(uri: Uri) {
        runCatching {
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        }
    }
}
