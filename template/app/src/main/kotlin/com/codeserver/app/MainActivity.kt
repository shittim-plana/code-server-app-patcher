package com.codeserver.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.*
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var container: FrameLayout
    private var backPressedTime = 0L
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>

    private val prefs by lazy { getSharedPreferences("codeserver", MODE_PRIVATE) }

    companion object {
        private const val BACK_PRESS_INTERVAL_MS = 2000L
        private const val PREF_SHOW_NAV_BAR = "show_nav_bar"
        private const val PREF_USE_CUTOUT = "use_cutout"
        private const val JS_BRIDGE_NAME = "NativeDownloader"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        filePickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val uris = mutableListOf<Uri>()
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val clipData = data?.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        uris.add(clipData.getItemAt(i).uri)
                    }
                } else {
                    data?.data?.let { uris.add(it) }
                }
            }
            fileUploadCallback?.onReceiveValue(if (uris.isNotEmpty()) uris.toTypedArray() else null)
            fileUploadCallback = null
        }

        buildUI()
        applyDisplaySettings()
        registerNavBarReceiver()

        val savedUrl = prefs.getString("last_url", null)
        if (savedUrl != null) {
            loadUrl(savedUrl)
        } else {
            showAddUrlDialog()
        }

        requestPermissions()
        ConnectionService.start(this)
    }

    private fun buildUI() {
        container = FrameLayout(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(android.graphics.Color.BLACK)
        }

        webView = WebView(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                @Suppress("DEPRECATION")
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = settings.userAgentString
                    .replace("; wv)", ")")
                    .replace("Version/4.0 ", "")
            }

            try {
                val clazz = Class.forName("androidx.webkit.WebSettingsCompat")
                val method = clazz.getMethod("setRequestedWithHeaderMode", android.webkit.WebSettings::class.java, Int::class.javaPrimitiveType)
                val noHeader = clazz.getField("REQUESTED_WITH_HEADER_MODE_NO_HEADER").getInt(null)
                method.invoke(null, settings, noHeader)
            } catch (_: Exception) {}

            addJavascriptInterface(BlobDownloadBridge(), JS_BRIDGE_NAME)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectBlobCaptureScript(view)
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView,
                    callback: ValueCallback<Array<Uri>>,
                    params: FileChooserParams
                ): Boolean {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = callback
                    val intent = params.createIntent()
                    filePickerLauncher.launch(intent)
                    return true
                }

                override fun onPermissionRequest(request: PermissionRequest) {
                    request.grant(request.resources)
                }
            }
        }

        container.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        setContentView(container)
        setupDownloadListener()
    }

    fun loadUrl(url: String) {
        val host = Uri.parse(url).host
        if (host != null) {
            val hosts = getSavedHosts().toMutableSet()
            if (hosts.add(host)) {
                prefs.edit().putStringSet("registered_hosts", hosts).apply()
            }
        }
        prefs.edit().putString("last_url", url).apply()
        webView.loadUrl(url)
    }

    private fun getSavedHosts(): Set<String> =
        prefs.getStringSet("registered_hosts", emptySet()) ?: emptySet()

    private fun requestPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1000)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (intent.action) {
            "com.codeserver.app.SETTINGS" -> showSettingsDialog()
            "com.codeserver.app.REFRESH" -> webView.reload()
            "com.codeserver.app.COPY_URL" -> {
                val clip = android.content.ClipData.newPlainText("url", webView.url ?: "")
                (getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
                Toast.makeText(this, "URL 복사됨", Toast.LENGTH_SHORT).show()
            }
            Intent.ACTION_VIEW -> intent.data?.toString()?.let { loadUrl(it) }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
                Regex("https?://[^\\s]+").find(text)?.value?.let { loadUrl(it) }
            }
        }
    }

    private fun applyDisplaySettings() {
        applyNavBarVisibility(prefs.getBoolean(PREF_SHOW_NAV_BAR, true))
        applyCutoutMode(prefs.getBoolean(PREF_USE_CUTOUT, true))
    }

    private fun applyNavBarVisibility(show: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return
            if (show) {
                controller.show(WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_DEFAULT
            } else {
                controller.hide(WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    private fun applyCutoutMode(useCutout: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                if (useCutout) WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
        }
    }

    private fun showAddUrlDialog() {
        val input = EditText(this).apply {
            hint = "Code Server URL"
            setText("https://")
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("URL 입력")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("열기") { _, _ ->
                val url = input.text?.toString()?.trim() ?: return@setPositiveButton
                if (url.isNotEmpty() && url != "https://") loadUrl(url)
            }
            .show()
    }

    private fun showSettingsDialog() {
        val currentUrl = prefs.getString("last_url", "") ?: ""
        val items = arrayOf("새로고침", "URL 변경")

        AlertDialog.Builder(this)
            .setTitle(currentUrl)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> webView.reload()
                    1 -> showAddUrlDialog()
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
            return
        }
        if (System.currentTimeMillis() - backPressedTime < BACK_PRESS_INTERVAL_MS) {
            super.onBackPressed()
        } else {
            backPressedTime = System.currentTimeMillis()
            Toast.makeText(this, "한 번 더 누르면 종료됩니다", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        if (!ConnectionService.isRunning) webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        if (ConnectionService.navBarHidden) applyNavBarVisibility(false)
    }

    override fun onDestroy() {
        try { unregisterReceiver(navBarReceiver) } catch (_: Exception) {}
        webView.destroy()
        ConnectionService.stop(this)
        super.onDestroy()
    }

    private var navBarReceiver: android.content.BroadcastReceiver? = null

    private fun registerNavBarReceiver() {
        navBarReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                applyNavBarVisibility(!ConnectionService.navBarHidden)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(navBarReceiver, android.content.IntentFilter("com.codeserver.app.NAVBAR_CHANGED"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(navBarReceiver, android.content.IntentFilter("com.codeserver.app.NAVBAR_CHANGED"))
        }
    }

    // --- Download ---

    private fun setupDownloadListener() {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (url.startsWith("blob:")) {
                downloadBlobUrl(url, contentDisposition, mimeType ?: "application/octet-stream")
            } else if (URLUtil.isNetworkUrl(url)) {
                downloadHttpUrl(url, userAgent, contentDisposition, mimeType)
            }
        }
    }

    private fun downloadHttpUrl(url: String, userAgent: String, contentDisposition: String, mimeType: String?) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
            return
        }
        try {
            val cookies = CookieManager.getInstance().getCookie(url) ?: ""
            val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimeType ?: "application/octet-stream")
                addRequestHeader("User-Agent", userAgent)
                addRequestHeader("Cookie", cookies)
                setTitle(filename)
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            }
            val dm = getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, "다운로드: $filename", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("CodeServer", "Download failed: ${e.message}", e)
            Toast.makeText(this, "다운로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun injectBlobCaptureScript(view: WebView?) {
        val js = """
            (function() {
                if (window.__blobStore) return;
                window.__blobStore = {};
                window.__blobNames = {};
                var origCreate = URL.createObjectURL;
                URL.createObjectURL = function(obj) {
                    var url = origCreate.call(URL, obj);
                    if (obj instanceof Blob) {
                        window.__blobStore[url] = obj;
                    }
                    return url;
                };
                var origRevoke = URL.revokeObjectURL;
                URL.revokeObjectURL = function(url) {
                    origRevoke.call(URL, url);
                };
                document.addEventListener('click', function(e) {
                    var a = e.target.closest ? e.target.closest('a[download]') : null;
                    if (a && a.href && a.href.startsWith('blob:') && a.download) {
                        window.__blobNames[a.href] = a.download;
                    }
                }, true);
            })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    private fun downloadBlobUrl(blobUrl: String, contentDisposition: String, mimeType: String) {
        val fallbackFilename = URLUtil.guessFileName(blobUrl, contentDisposition, mimeType)
        Toast.makeText(this, "다운로드 준비 중...", Toast.LENGTH_SHORT).show()
        val js = """
            (function() {
                var blob = (window.__blobStore || {})[${escapedJsString(blobUrl)}];
                if (!blob) {
                    window.${JS_BRIDGE_NAME}.onBlobError('Blob not found: ' + ${escapedJsString(blobUrl)});
                    return;
                }
                var filename = (window.__blobNames || {})[${escapedJsString(blobUrl)}] || ${escapedJsString(fallbackFilename)};
                var reader = new FileReader();
                reader.onloadend = function() {
                    var data = reader.result;
                    var base64 = data.indexOf(',') > -1 ? data.split(',')[1] : data;
                    window.${JS_BRIDGE_NAME}.onBlobData(base64, filename, ${escapedJsString(mimeType)});
                    delete window.__blobStore[${escapedJsString(blobUrl)}];
                    delete window.__blobNames[${escapedJsString(blobUrl)}];
                };
                reader.onerror = function() {
                    window.${JS_BRIDGE_NAME}.onBlobError('FileReader error');
                };
                reader.readAsDataURL(blob);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun escapedJsString(s: String): String {
        val escaped = s
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
        return "'$escaped'"
    }

    inner class BlobDownloadBridge {
        @JavascriptInterface
        fun onBlobData(base64: String, filename: String, mimeType: String) {
            try {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val outFile = generateUniqueFile(downloadsDir, filename)
                outFile.writeBytes(bytes)
                MediaScannerConnection.scanFile(this@MainActivity, arrayOf(outFile.absolutePath), arrayOf(mimeType), null)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "다운로드 완료: ${outFile.name}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("CodeServer", "Blob save failed: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "다운로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        @JavascriptInterface
        fun onBlobError(message: String) {
            android.util.Log.e("CodeServer", "Blob download error: $message")
            runOnUiThread {
                Toast.makeText(this@MainActivity, "다운로드 실패: $message", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateUniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (file.exists()) {
            file = File(dir, "${base}($i)$ext")
            i++
        }
        return file
    }
}
