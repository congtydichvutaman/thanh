package vn.thangmaytaman.crm

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.webkit.JavascriptInterface
import androidx.core.content.ContextCompat
import org.json.JSONObject

/** Cầu nối JavaScript: trang CRM gọi window.TamAnApp.xxx(). */
class CallBridge(private val act: Activity) {
    private val prefs = Prefs(act)

    @JavascriptInterface
    fun call(cid: Int, phone: String, name: String) {
        val num = phone.filter { it.isDigit() || it == '+' }
        if (cid <= 0 || num.isEmpty()) return
        prefs.setPending(Pending(cid, num, name, System.currentTimeMillis()))
        act.runOnUiThread {
            val uri = Uri.parse("tel:$num")
            val can = ContextCompat.checkSelfPermission(act, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
            try {
                act.startActivity(Intent(if (can) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
            } catch (e: Exception) {
                act.startActivity(Intent(Intent.ACTION_DIAL, uri))
            }
        }
    }

    @JavascriptInterface
    fun saveToken(token: String) {
        val t = token.trim()
        if (Regex("^[a-f0-9]{40}$").matches(t)) prefs.token = t
    }

    @JavascriptInterface
    fun hasToken(): Boolean = prefs.token.isNotEmpty()

    @JavascriptInterface
    fun openSettings() {
        act.runOnUiThread { act.startActivity(Intent(act, SettingsActivity::class.java)) }
    }

    @JavascriptInterface
    fun version(): String = "1.0"

    @JavascriptInterface
    fun status(): String {
        val o = JSONObject()
        o.put("token", prefs.token.isNotEmpty())
        o.put("perms", Perms.allGranted(act))
        o.put("folder", prefs.folder.isNotEmpty())
        o.put("version", "1.0")
        return o.toString()
    }
}

object Perms {
    fun needed(): Array<String> {
        val l = mutableListOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= 33) {
            l.add(Manifest.permission.POST_NOTIFICATIONS)
            l.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            l.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return l.toTypedArray()
    }

    fun allGranted(act: Activity): Boolean =
        needed().all { ContextCompat.checkSelfPermission(act, it) == PackageManager.PERMISSION_GRANTED }
}
