package com.example.myapplication

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var edUrl: EditText
    private lateinit var edLoop: EditText
    private lateinit var edDelayMs: EditText
    private lateinit var btnAuto: Button

    private var isAutoLoopRunning = false
    private var autoLoadCount = 0
    private var loopJob: Job? = null
    private var activeUrl: String = DEFAULT_URL

    private val prefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        edUrl = findViewById(R.id.edURl)
        edLoop = findViewById(R.id.edLoop)
        edDelayMs = findViewById(R.id.edPreSecond)
        btnAuto = findViewById(R.id.btnAuto)

        configureWebView(webView)
        activeUrl = prefs.getString(KEY_URL, DEFAULT_URL) ?: DEFAULT_URL
        edUrl.setText(activeUrl)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                if (!isAutoLoopRunning) return
                Log.d(TAG, "auto load #$autoLoadCount -> $url")
                autoLoadCount++
                loadUrlWithRandomUserAgent(view, activeUrl)
            }
        }

        btnAuto.setOnClickListener { toggleAutoLoop() }
        findViewById<Button>(R.id.btnSend).setOnClickListener { startTimedLoop() }
    }

    private fun toggleAutoLoop() {
        if (isAutoLoopRunning) {
            stopAutoLoop()
            return
        }

        val url = urlFromInput() ?: return
        activeUrl = url
        saveUrl(url)
        isAutoLoopRunning = true
        autoLoadCount = 0
        btnAuto.text = getString(R.string.stop)
        loadUrlWithRandomUserAgent(webView, url)
    }

    private fun stopAutoLoop() {
        isAutoLoopRunning = false
        btnAuto.text = getString(R.string.start)
    }

    private fun startTimedLoop() {
        val url = urlFromInput() ?: return
        activeUrl = url
        saveUrl(url)

        val loopCount = edLoop.text.toString().toIntOrNull() ?: DEFAULT_LOOP_COUNT
        val delayMs = edDelayMs.text.toString().toLongOrNull() ?: DEFAULT_DELAY_MS

        loopJob?.cancel()
        loopJob = lifecycleScope.launch {
            repeat(loopCount) { index ->
                loadUrlWithRandomUserAgent(webView, url)
                Log.d(TAG, "timed load #$index -> $url")
                if (index < loopCount - 1) {
                    delay(delayMs)
                }
            }
        }
    }

    private fun urlFromInput(): String? {
        val url = edUrl.text.toString().trim()
        if (url.isEmpty()) {
            edUrl.error = getString(R.string.error_url_required)
            return null
        }
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
        webView.settings.userAgentString = buildRandomUserAgent()
        webView.loadUrl(url)
    }

    private fun buildRandomUserAgent(): String {
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

        return "Mozilla/${buildNumber + mozillaMinor}" +
            "(Linux; Android $androidVersion; $model) " +
            "AppleWebKit/$webkitPatch " +
            "(KHTML, like Gecko) Chrome/$chromeMajor.$buildNumber Mobile " +
            "Safari/$webkitPatch"
    }

    override fun onDestroy() {
        loopJob?.cancel()
        stopAutoLoop()
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
