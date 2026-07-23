package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var edUrl: TextInputEditText
    private lateinit var edLoop: TextInputEditText
    private lateinit var edDelayMs: TextInputEditText
    private lateinit var urlInputLayout: TextInputLayout
    private lateinit var btnSend: MaterialButton
    private lateinit var btnAuto: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var statusChip: Chip
    private lateinit var progressBar: ProgressBar

    private var isAutoLoopRunning = false
    private var autoLoadCount = 0
    private var loopJob: Job? = null
    private var activeUrl: String = DEFAULT_URL
    private var activeDelayMs: Long = DEFAULT_DELAY_MS
    private var pendingAutoReload: Job? = null

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        webView = findViewById(R.id.webView)
        edUrl = findViewById(R.id.edURl)
        edLoop = findViewById(R.id.edLoop)
        edDelayMs = findViewById(R.id.edPreSecond)
        urlInputLayout = findViewById(R.id.urlInputLayout)
        btnSend = findViewById(R.id.btnSend)
        btnAuto = findViewById(R.id.btnAuto)
        btnStop = findViewById(R.id.btnStop)
        statusChip = findViewById(R.id.statusChip)
        progressBar = findViewById(R.id.progressBar)

        applyWindowInsets()

        configureWebView(webView)
        activeUrl = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        edUrl.setText(activeUrl)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                progressBar.isVisible = true
            }

            override fun onPageFinished(view: WebView, url: String) {
                progressBar.isVisible = false
                if (!isAutoLoopRunning) return

                autoLoadCount++
                Log.d(TAG, "auto load #$autoLoadCount -> $url, next in ${activeDelayMs}ms")
                setStatus(getString(R.string.status_auto_running_count, autoLoadCount))

                pendingAutoReload?.cancel()
                pendingAutoReload = lifecycleScope.launch {
                    delay(activeDelayMs)
                    if (isActive && isAutoLoopRunning) {
                        loadUrlWithRandomUserAgent(view, activeUrl)
                    }
                }
            }
        }

        btnSend.setOnClickListener {
            it.isEnabled = false
            startTimedLoop()
        }
        btnAuto.setOnClickListener {
            it.isEnabled = false
            startAutoLoop()
        }
        btnStop.setOnClickListener { stopAll() }
    }

    private fun applyWindowInsets() {
        val appBar = findViewById<android.view.View>(R.id.appBar)
        val content = findViewById<android.view.View>(R.id.controlCard).parent as android.view.View

        ViewCompat.setOnApplyWindowInsetsListener(appBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    private fun startAutoLoop() {
        val url = urlFromInput() ?: run {
            btnAuto.isEnabled = true
            return
        }
        activeUrl = url
        activeDelayMs = edDelayMs.text.toString().toLongOrNull() ?: DEFAULT_DELAY_MS
        saveUrl(url)
        loopJob?.cancel()
        pendingAutoReload?.cancel()
        isAutoLoopRunning = true
        autoLoadCount = 0
        setInputsEnabled(false)
        showStopMode()
        setStatus(getString(R.string.status_auto_running_count, 0))
        loadUrlWithRandomUserAgent(webView, url)
    }

    private fun stopAll() {
        loopJob?.cancel()
        loopJob = null
        pendingAutoReload?.cancel()
        pendingAutoReload = null
        isAutoLoopRunning = false
        autoLoadCount = 0
        setInputsEnabled(true)
        showIdleMode()
        setStatus(getString(R.string.status_idle))
        progressBar.isVisible = false
    }

    private fun setInputsEnabled(enabled: Boolean) {
        edUrl.isEnabled = enabled
        edLoop.isEnabled = enabled
        edDelayMs.isEnabled = enabled
    }

    private fun showStopMode() {
        btnSend.visibility = View.GONE
        btnAuto.visibility = View.GONE
        btnStop.visibility = View.VISIBLE
    }

    private fun showIdleMode() {
        btnSend.visibility = View.VISIBLE
        btnAuto.visibility = View.VISIBLE
        btnSend.isEnabled = true
        btnAuto.isEnabled = true
        btnStop.visibility = View.GONE
    }

    private fun startTimedLoop() {
        val url = urlFromInput() ?: run {
            btnSend.isEnabled = true
            return
        }
        activeUrl = url
        saveUrl(url)

        val loopCount = edLoop.text.toString().toIntOrNull() ?: DEFAULT_LOOP_COUNT
        val delayMs = edDelayMs.text.toString().toLongOrNull() ?: DEFAULT_DELAY_MS

        loopJob?.cancel()
        setInputsEnabled(false)
        showStopMode()
        loopJob = lifecycleScope.launch {
            repeat(loopCount) { index ->
                loadUrlWithRandomUserAgent(webView, url)
                setStatus(getString(R.string.status_timed_running_count, index + 1, loopCount))
                Log.d(TAG, "timed load #${index + 1}/$loopCount -> $url")
                if (index < loopCount - 1) {
                    delay(delayMs)
                }
            }
            if (!isAutoLoopRunning) {
                setInputsEnabled(true)
                showIdleMode()
                setStatus(getString(R.string.status_idle), isRunning = false)
            }
        }
    }

    private fun setStatus(status: String, isRunning: Boolean = isAutoLoopRunning || loopJob?.isActive == true) {
        statusChip.text = status
        val tintRes = if (isRunning) R.color.primary else R.color.on_surface_variant
        val bgRes = if (isRunning) R.color.primary_container else R.color.surface_variant
        statusChip.chipIconTint = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, tintRes))
        statusChip.chipBackgroundColor = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, bgRes))
    }

    private fun urlFromInput(): String? {
        val url = edUrl.text.toString().trim()
        if (url.isEmpty()) {
            urlInputLayout.error = getString(R.string.error_url_required)
            return null
        }
        urlInputLayout.error = null
        return url
    }

    private fun saveUrl(url: String) {
        prefs.edit().putString(KEY_URL, url).apply()
    }

    private fun configureWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = false
            setGeolocationEnabled(false)
            allowContentAccess = false
            cacheMode = WebSettings.LOAD_NO_CACHE
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(false)
            setAcceptThirdPartyCookies(webView, false)
        }
    }

    private fun loadUrlWithRandomUserAgent(webView: WebView, url: String) {
        val deviceInfo = buildRandomUserAgent()
        webView.settings.userAgentString = deviceInfo.userAgent
        Log.d(TAG, "URL: $url")
        Log.d(TAG, "Device -> Android: ${deviceInfo.androidVersion}, Model: ${deviceInfo.model}, Chrome: ${deviceInfo.chromeVersion}")
        Log.d(TAG, "User-Agent: ${deviceInfo.userAgent}")
        webView.loadUrl(url)
    }

    private data class DeviceInfo(
        val userAgent: String,
        val androidVersion: Int,
        val model: String,
        val chromeVersion: String
    )

    private fun buildRandomUserAgent(): DeviceInfo {
        val androidVersion = Random.nextInt(8, 13)
        val buildNumber = Random.nextInt(40, 59)
        val webkitPatch = Random.nextDouble(499.0, 599.0).toFloat()
        val chromeMajor = Random.nextDouble(888.888, 999.999)
        val mozillaMinor = Random.nextFloat()
        val model = buildString {
            append(('A'..'Z').random())
            append(('A'..'Z').random())
            append('-')
            append(('A'..'Z').random())
            append(buildNumber)
            append(('A'..'Z').random())
            append(('A'..'Z').random())
        }
        val chromeVersion = "$chromeMajor.$buildNumber"
        val userAgent = "Mozilla/${buildNumber + mozillaMinor}" +
            "(Linux; Android $androidVersion; $model) " +
            "AppleWebKit/$webkitPatch " +
            "(KHTML, like Gecko) Chrome/$chromeVersion Mobile " +
            "Safari/$webkitPatch"

        return DeviceInfo(userAgent, androidVersion, model, chromeVersion)
    }

    override fun onDestroy() {
        loopJob?.cancel()
        pendingAutoReload?.cancel()
        isAutoLoopRunning = false
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WebViewLoop"
        private const val PREFS_NAME = "kotlinsharedpreference"
        private const val KEY_URL = "nUrl"
        private const val DEFAULT_URL = "https://www.geeksforgeeks.org/"
        private const val DEFAULT_LOOP_COUNT = 10
        private const val DEFAULT_DELAY_MS = 1000L
    }
}
