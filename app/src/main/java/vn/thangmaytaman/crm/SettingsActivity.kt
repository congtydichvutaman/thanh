package vn.thangmaytaman.crm

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateFormat
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.Date

class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var diag: TextView

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    private val folderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.folder = uri.toString()
            } catch (e: Exception) {
                diag.text = "Không giữ được quyền truy cập thư mục này, hãy chọn lại."
            }
            refresh()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun text(t: String, bold: Boolean = false): TextView = TextView(this).apply {
        text = t
        textSize = 15f
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun button(t: String, onClick: () -> Unit): Button = Button(this).apply {
        text = t
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }
        root.addView(text("Cài đặt ghi âm cuộc gọi", true).apply { textSize = 20f })
        status = text("")
        root.addView(status)

        root.addView(button("1. Cấp quyền (gọi, trạng thái cuộc gọi, thông báo, âm thanh)") {
            permLauncher.launch(Perms.needed())
        })
        root.addView(button("2. Chọn thư mục chứa file ghi âm cuộc gọi") { folderLauncher.launch(null) })
        root.addView(button("3. Cho phép chạy nền (tắt tối ưu pin)") {
            try {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } else diag.text = "Đã được phép chạy nền."
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        })
        root.addView(button("Kiểm tra: xem file ghi âm mới nhất") { checkNewest() })
        diag = text("")
        root.addView(diag)

        root.addView(text("Mã kết nối (nếu app chưa tự nhận)", true))
        val et = EditText(this).apply { hint = "Dán mã 40 ký tự từ trang Kết nối app trong CRM" }
        root.addView(et)
        root.addView(button("Lưu mã") {
            val t = et.text.toString().trim()
            if (Regex("^[a-f0-9]{40}$").matches(t)) { prefs.token = t; et.setText(""); diag.text = "Đã lưu mã kết nối."; refresh() }
            else diag.text = "Mã không đúng định dạng (40 ký tự a-f, 0-9)."
        })

        root.addView(text("Bật ghi âm cuộc gọi trên máy", true))
        root.addView(text(tips()))

        setContentView(ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(root)
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        fun row(ok: Boolean, t: String) = (if (ok) "✅ " else "❌ ") + t + "\n"
        status.text = row(prefs.token.isNotEmpty(), "Đã kết nối tài khoản CRM") +
            row(Perms.allGranted(this), "Đã cấp đủ quyền") +
            row(prefs.folder.isNotEmpty(), "Đã chọn thư mục ghi âm" + if (prefs.folder.isEmpty()) " (app sẽ tự quét âm thanh mới trên máy)" else "")
    }

    private fun checkNewest() {
        diag.text = "Đang kiểm tra..."
        Thread {
            val list = try { RecordingFinder.find(this, 0L, 3) } catch (e: Exception) { emptyList() }
            val msg = if (list.isEmpty()) "Không thấy file âm thanh nào. Hãy bật ghi âm cuộc gọi của máy, thử gọi một cuộc, rồi chọn đúng thư mục."
            else "File mới nhất:\n" + list.joinToString("\n") {
                "• ${it.name} (${it.size / 1024} KB, " + DateFormat.format("dd/MM HH:mm", Date(it.modifiedMs)) + ")"
            }
            runOnUiThread { diag.text = msg }
        }.start()
    }

    private fun tips(): String {
        val m = Build.MANUFACTURER.lowercase()
        val base = "App không tự ghi âm (Android chặn app thứ ba). App dùng tính năng ghi âm có sẵn của máy rồi tự gửi file lên CRM.\n\n"
        val brand = when {
            m.contains("samsung") -> "Samsung: Điện thoại > ⋮ > Cài đặt > Ghi âm cuộc gọi > bật 'Tự động ghi âm'. File thường nằm ở thư mục Recordings/Call (hoặc Call) trong bộ nhớ trong."
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> "Xiaomi/Redmi/Poco: Điện thoại > Cài đặt > Ghi âm cuộc gọi > bật 'Ghi âm tự động'. File thường ở MIUI/sound_recorder/call_rec."
            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") -> "Oppo/Realme/OnePlus: Điện thoại > Cài đặt > Ghi âm cuộc gọi > bật ghi tự động. File thường ở Music/Recordings/Call Recording hoặc Recordings/Call."
            m.contains("vivo") || m.contains("iqoo") -> "Vivo: Điện thoại > Cài đặt cuộc gọi > Ghi âm cuộc gọi > bật tự động. File thường ở thư mục Record/Call."
            m.contains("google") -> "Pixel: Điện thoại > Cài đặt > Ghi âm cuộc gọi (tuỳ vùng/nhà mạng, có thể không có)."
            else -> "Mở Cài đặt của ứng dụng Điện thoại, tìm 'Ghi âm cuộc gọi' và bật tự động ghi âm. Tên thư mục lưu file khác nhau tuỳ hãng; dùng nút 'Kiểm tra' để biết app có thấy file không."
        }
        return base + brand + "\n\nTên thư mục có thể khác tuỳ đời máy. Nên thông báo cho khách biết cuộc gọi được ghi âm."
    }
}
