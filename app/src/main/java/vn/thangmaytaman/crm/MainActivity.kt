package vn.thangmaytaman.crm

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var fileCb: ValueCallback<Array<Uri>>? = null

    private val chooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        fileCb = null
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        val s = web.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = false
        s.userAgentString = s.userAgentString + " TamAnApp/1.0"
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.addJavascriptInterface(CallBridge(this), "TamAnApp")

        val crmHost = Uri.parse(BuildConfig.CRM_URL).host
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                val scheme = u.scheme ?: ""
                if (scheme == "http" || scheme == "https") {
                    if (u.host == crmHost) return false
                    openExternal(Intent(Intent.ACTION_VIEW, u)); return true
                }
                // tel:, mailto:, zalo: ... → mở ứng dụng tương ứng (tel: do cầu nối xử lý trước nếu biết khách)
                openExternal(Intent(if (scheme == "tel") Intent.ACTION_DIAL else Intent.ACTION_VIEW, u))
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView, cb: ValueCallback<Array<Uri>>, params: FileChooserParams
            ): Boolean {
                fileCb?.onReceiveValue(null)
                fileCb = cb
                return try {
                    chooser.launch(params.createIntent()); true
                } catch (e: Exception) {
                    fileCb = null; false
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        if (!Perms.allGranted(this)) permLauncher.launch(Perms.needed())

        if (savedInstanceState == null) web.loadUrl(BuildConfig.CRM_URL) else web.restoreState(savedInstanceState)
    }

    private fun openExternal(i: Intent) {
        try { startActivity(i) } catch (e: ActivityNotFoundException) { }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        super.onPause()
    }
}
