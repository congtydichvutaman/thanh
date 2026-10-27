package vn.thangmaytaman.crm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/** Phát hiện cuộc gọi kết thúc (OFFHOOK → IDLE) rồi giao việc tìm + gửi file ghi âm cho UploadWorker. */
class CallStateReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val prefs = Prefs(ctx)
        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                prefs.offhook = true
                val p = prefs.peekPending()
                // Bấm gọi nhưng quá 3 phút mới có cuộc gọi → không phải cuộc gọi này
                if (p != null && System.currentTimeMillis() - p.startMs > 3 * 60_000L) prefs.clearPending()
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                if (!prefs.offhook) return
                prefs.offhook = false
                val p = prefs.peekPending() ?: return
                prefs.clearPending()
                if (prefs.token.isEmpty()) {
                    Notify.show(ctx, "Chưa kết nối tài khoản", "Mở app, vào trang Kết nối app để tạo mã rồi gọi lại.")
                    return
                }
                val req = OneTimeWorkRequestBuilder<UploadWorker>()
                    .setInitialDelay(6, TimeUnit.SECONDS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                    .setInputData(
                        workDataOf(
                            "cid" to p.cid,
                            "name" to p.name,
                            "phone" to p.phone,
                            "start" to p.startMs,
                            "end" to System.currentTimeMillis()
                        )
                    )
                    .build()
                WorkManager.getInstance(ctx.applicationContext)
                    .enqueueUniqueWork("up-" + p.startMs, ExistingWorkPolicy.KEEP, req)
            }
        }
    }
}
