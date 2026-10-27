package vn.thangmaytaman.crm

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.io.IOException

class UploadWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        val cid = inputData.getInt("cid", 0)
        val name = inputData.getString("name") ?: ""
        val start = inputData.getLong("start", 0L)
        val end = inputData.getLong("end", System.currentTimeMillis())
        val label = if (name.isNotEmpty()) name else inputData.getString("phone") ?: "khách"
        val token = prefs.token
        if (cid <= 0 || token.isEmpty()) return Result.failure()

        val rec = waitForRecording(ctx, prefs, start)
        if (rec == null) {
            // Cuộc gọi nhỡ/không nghe máy, hoặc điện thoại chưa bật ghi âm / chưa chọn đúng thư mục
            Notify.show(ctx, "Không thấy file ghi âm", "Cuộc gọi tới $label: không tìm thấy bản ghi âm mới. Nếu khách có nghe máy, hãy kiểm tra Cài đặt ghi âm trong app.")
            return Result.success()
        }
        if (rec.size > Uploader.MAX_BYTES) {
            Notify.show(ctx, "File ghi âm quá lớn", "${rec.name} vượt 15 MB nên không gửi được.")
            prefs.markUploaded(rec.key)
            return Result.success()
        }

        return try {
            val dur = maxOf(0L, (rec.modifiedMs - start) / 1000)
            val r = Uploader.upload(ctx, rec, cid, start, dur, token)
            val j = r.json
            when {
                r.http == 200 && j != null && j.optBoolean("ok") -> {
                    prefs.markUploaded(rec.key)
                    val ten = j.optString("ten", label)
                    val text = if (j.optBoolean("duplicate")) "Ghi âm của $ten đã có trên CRM." else {
                        val tt = j.optString("tom_tat", "")
                        "AI đã phân tích cuộc gọi với $ten." + if (tt.isNotEmpty()) " $tt" else ""
                    }
                    Notify.show(ctx, "Đã gửi ghi âm lên CRM", text)
                    Result.success()
                }
                r.http == 401 -> {
                    Notify.show(ctx, "Mất kết nối tài khoản", j?.optString("error") ?: "Hãy tạo lại mã kết nối trong CRM.")
                    Result.failure()
                }
                r.http >= 500 && runAttemptCount < 4 -> Result.retry()
                else -> {
                    // lỗi từ phía dữ liệu (khách không thuộc bạn, file hỏng, chưa có khóa Gemini...) → không thử lại
                    if (r.http >= 500) Notify.show(ctx, "Gửi ghi âm thất bại", j?.optString("error") ?: "Máy chủ lỗi (${r.http}).")
                    else Notify.show(ctx, "Không gửi được ghi âm", j?.optString("error") ?: "Lỗi ${r.http}.")
                    Result.success()
                }
            }
        } catch (e: IOException) {
            if (runAttemptCount < 5) Result.retry()
            else {
                Notify.show(ctx, "Gửi ghi âm thất bại", "Không kết nối được máy chủ CRM. Hãy mở app khi có mạng.")
                Result.failure()
            }
        }
    }

    /** Chờ file ghi âm xuất hiện và ngừng ghi (kích thước không đổi). */
    private fun waitForRecording(ctx: Context, prefs: Prefs, sinceMs: Long): Rec? {
        repeat(8) {
            val cand = RecordingFinder.find(ctx, sinceMs).firstOrNull { !prefs.isUploaded(it.key) }
            if (cand != null && cand.size > 2048) {
                Thread.sleep(3000)
                val again = RecordingFinder.find(ctx, sinceMs).firstOrNull { it.key == cand.key }
                if (again != null && again.size == cand.size) return again
            } else {
                Thread.sleep(5000)
            }
        }
        return null
    }
}
