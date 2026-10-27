package vn.thangmaytaman.crm

import android.content.Context
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

class UploadResult(val http: Int, val json: JSONObject?)

object Uploader {
    const val MAX_BYTES = 15L * 1024 * 1024

    /** Ném IOException khi lỗi mạng (để WorkManager thử lại). */
    fun upload(ctx: Context, rec: Rec, cid: Int, startMs: Long, durationSec: Long, token: String): UploadResult {
        val boundary = "----TamAn" + System.currentTimeMillis()
        val conn = URL(BuildConfig.CRM_URL + "ai_upload_app.php").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 30_000
            conn.readTimeout = 240_000          // máy chủ chờ AI phân tích xong mới trả lời
            conn.setChunkedStreamingMode(64 * 1024)
            conn.setRequestProperty("X-App-Token", token)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val out = DataOutputStream(conn.outputStream)
            fun field(n: String, v: String) {
                out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"$n\"\r\n\r\n")
                out.write(v.toByteArray(Charsets.UTF_8))
                out.writeBytes("\r\n")
            }
            field("customer_id", cid.toString())
            field("started_at", (startMs / 1000).toString())
            field("duration", durationSec.toString())

            var fname = rec.name.replace("\"", "").replace("\r", "").replace("\n", "")
            if (RecordingFinder.ext(fname) !in RecordingFinder.EXTS) fname += "." + extFromMime(rec.mime)
            out.writeBytes("--$boundary\r\nContent-Disposition: form-data; name=\"audio\"; filename=\"")
            out.write(fname.toByteArray(Charsets.UTF_8))
            out.writeBytes("\"\r\nContent-Type: application/octet-stream\r\n\r\n")
            ctx.contentResolver.openInputStream(rec.uri)?.use { input ->
                val buf = ByteArray(32 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
            } ?: throw java.io.IOException("Không mở được file ghi âm")
            out.writeBytes("\r\n--$boundary--\r\n")
            out.flush()
            out.close()

            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
            val json = try { JSONObject(body) } catch (e: Exception) { null }
            return UploadResult(code, json)
        } finally {
            conn.disconnect()
        }
    }

    private fun extFromMime(m: String): String = when {
        m.contains("mp4") || m.contains("m4a") -> "m4a"
        m.contains("mpeg") || m.contains("mp3") -> "mp3"
        m.contains("amr") -> "amr"
        m.contains("3gpp") -> "3gp"
        m.contains("aac") -> "aac"
        m.contains("wav") -> "wav"
        m.contains("ogg") -> "ogg"
        m.contains("flac") -> "flac"
        else -> "m4a"
    }
}
