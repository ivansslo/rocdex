package com.rocdex.mobile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CodexMainActivity"
    }

    private lateinit var webView: WebView
    private lateinit var loadingOverlay: View
    private lateinit var statusText: TextView
    private lateinit var statusDetail: TextView
    private lateinit var serverInfoPanel: LinearLayout
    private lateinit var serverUrlText: TextView
    private lateinit var networkUrlText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var settingsButton: ImageView
    private lateinit var statusBadge: TextView
    private lateinit var appTitleText: TextView
    private lateinit var serverManager: CodexServerManager

    private var serverPort: Int = CodexServerManager.SERVER_PORT
    private var localIp: String? = null
    private var password: String? = null
    private var serverReady: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        statusText = findViewById(R.id.statusText)
        statusDetail = findViewById(R.id.statusDetail)
        serverInfoPanel = findViewById(R.id.serverInfoPanel)
        serverUrlText = findViewById(R.id.serverUrlText)
        networkUrlText = findViewById(R.id.networkUrlText)
        progressBar = findViewById(R.id.progressBar)
        settingsButton = findViewById(R.id.settingsButton)
        statusBadge = findViewById(R.id.statusBadge)
        appTitleText = findViewById(R.id.appTitleText)

        serverManager = CodexServerManager(this)

        // Settings gear button
        settingsButton.setOnClickListener { showSettingsDialog() }

        requestBatteryOptimizationExemption()
        startForegroundService()
        setupWebView()
        startSetupFlow()
    }

    override fun onDestroy() {
        super.onDestroy()
        serverManager.stopServer()
        stopService(Intent(this, CodexForegroundService::class.java))
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (pm.isIgnoringBatteryOptimizations(packageName)) return

        try {
            @Suppress("BatteryLife")
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not request battery optimization exemption: ${e.message}")
        }
    }

    private fun startForegroundService() {
        val intent = Intent(this, CodexForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    @Deprecated("Use onBackPressedDispatcher")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            setSupportZoom(false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                url: String,
            ): Boolean = false
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                Log.d(TAG, "[WebView] ${msg.sourceId()}:${msg.lineNumber()} ${msg.message()}")
                return true
            }
        }
    }

    private fun startSetupFlow() {
        showLoading(true)
        setStatus("Initializing...")
        updateBadge("Starting", "#facc15")

        Thread {
            try {
                runSetup()
            } catch (e: Exception) {
                Log.e(TAG, "Setup failed", e)
                runOnUiThread {
                    showError(e.message ?: "Unknown error")
                }
            }
        }.start()
    }

    private fun runSetup() {
        // Step 1: Extract bootstrap
        if (!BootstrapInstaller.isBootstrapInstalled(this)) {
            updateStatus("Extracting environment...")
            BootstrapInstaller.install(this) { msg -> updateStatus("Extracting: $msg") }
        }
        updateStatus("Environment ready")

        // Step 1b: Install proot
        if (!serverManager.isProotInstalled()) {
            updateStatus("Installing proot...", "Needed for package management")
            val prootOk = serverManager.installProot { msg -> updateDetail(msg) }
            if (!prootOk) {
                throw RuntimeException("Failed to install proot")
            }
        }
        updateStatus("proot ready")

        // Step 2: Install Node.js if missing
        if (!serverManager.isNodeInstalled()) {
            updateStatus("Installing Node.js...", "This may take a minute")
            val nodeOk = serverManager.installNode { msg -> updateDetail(msg) }
            if (!nodeOk) {
                throw RuntimeException("Failed to install Node.js")
            }
        }
        updateStatus("Node.js ready")

        // Step 3: Install Codex CLI if missing
        if (!serverManager.isCodexInstalled()) {
            updateStatus("Installing Codex CLI...")
            val codexOk = serverManager.installCodex { msg -> updateDetail(msg) }
            if (!codexOk) {
                throw RuntimeException("Failed to install Codex CLI")
            }
        }
        updateStatus("Codex CLI ready")

        // Step 4: Ensure platform binary
        if (!serverManager.isPlatformBinaryInstalled()) {
            updateStatus("Installing platform binary...")
            val binOk = serverManager.installPlatformBinary { msg -> updateDetail(msg) }
            if (!binOk) {
                throw RuntimeException("Failed to install platform binary")
            }
        }
        updateStatus("Platform binary ready")

        // Step 5: Ensure default workspace and config
        updateStatus("Configuring workspace...")
        serverManager.ensureDefaultWorkspace()
        serverManager.ensureFullAccessConfig()

        // Step 6: Ensure auth is set up
        updateStatus("Checking authentication...")
        if (!serverManager.isLoggedIn()) {
            updateStatus("Authentication required...")
            // Use local password instead of API key
            updateStatus("Generating local password...")
            val passFile = File(filesDir, "codexui-password")
            val localPass = generateLocalPassword()
            passFile.writeText(localPass)
            password = localPass

            // Set up codex login with local token
            updateStatus("Configuring local access...")
            serverManager.loginWithApiKey(localPass)
        }
        updateStatus("Authenticated")

        // Step 7: Health check (skip for local mode)
        updateStatus("Verifying server setup...")

        // Step 8: Configure and start OpenClaw
        if (serverManager.isOpenClawInstalled()) {
            updateStatus("Configuring OpenClaw...")
            serverManager.configureOpenClawAuth()
            serverManager.startOpenClawGateway()
            serverManager.startOpenClawControlUiServer()
        }

        // Step 9: Start web server
        updateStatus("Starting server...")
        val started = serverManager.startServer()
        if (!started) {
            throw RuntimeException("Failed to start server")
        }

        // Get local IP and show info
        localIp = serverManager.getLocalIpAddress()
        serverPort = CodexServerManager.SERVER_PORT
        updateBadge("Running", "#22c55e")

        // Show server URLs
        val localUrl = "http://127.0.0.1:$serverPort"
        val networkUrl = if (localIp != null) "http://$localIp:$serverPort/" else null

        runOnUiThread {
            serverUrlText.text = "Local: $localUrl"
            serverUrlText.visibility = View.VISIBLE
            if (networkUrl != null) {
                networkUrlText.text = "Network: $networkUrl"
                networkUrlText.visibility = View.VISIBLE
            }
            serverInfoPanel.visibility = View.VISIBLE
        }

        // Step 10: Wait for ready
        updateStatus("Waiting for server...")
        val ready = serverManager.waitForServer(timeoutMs = 90_000)
        if (!ready) {
            throw RuntimeException("Server did not start in time")
        }

        serverReady = true

        // Step 11: Update notification with server info
        updateForegroundService(localUrl, networkUrl)

        // Step 12: Show web UI
        runOnUiThread {
            showLoading(false)
            webView.visibility = View.VISIBLE
            webView.loadUrl("http://127.0.0.1:$serverPort/")
        }
    }

    private fun generateLocalPassword(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32).map { chars.random() }.joinToString("")
    }

    private fun updateForegroundService(localUrl: String, networkUrl: String?) {
        val intent = Intent(this, CodexForegroundService::class.java).apply {
            putExtra("local_url", localUrl)
            putExtra("network_url", networkUrl ?: "")
            putExtra("port", serverPort.toString())
            putExtra("password", password ?: "")
        }
        startService(intent)
    }

    /**
     * Show settings dialog with server info (gear icon).
     * Like InstantWeb plugin for Termux.
     */
    private fun showSettingsDialog() {
        val localIpStr = localIp ?: serverManager.getLocalIpAddress() ?: "127.0.0.1"
        val localUrl = "http://127.0.0.1:$serverPort"
        val networkUrl = "http://$localIpStr:$serverPort/"

        val info = buildString {
            appendLine("⚙️ Codex Web Local")
            appendLine()
            appendLine("Version:  1.0.90")
            appendLine("Bind:     http://0.0.0.0:$serverPort")
            appendLine("Status:   ${if (serverReady) "Running" else "Starting..."}")
            appendLine()
            appendLine("Local:    $localUrl")
            appendLine("Network:  $networkUrl")
            if (password != null) {
                appendLine()
                appendLine("Password: $password")
            }
            appendLine()
            appendLine("Sandbox:  danger-full-access")
            appendLine("Approval: never")
        }

        AlertDialog.Builder(this)
            .setTitle("Server Settings")
            .setMessage(info.toString())
            .setPositiveButton("Open Browser") { _, _ ->
                val url = if (serverReady) localUrl else "http://127.0.0.1:$serverPort"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }
            .setNeutralButton("Copy Password") { _, _ ->
                if (password != null) {
                    val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("Codex Password", password)
                    clipboard.setPrimaryClip(clip)
                    setStatus("Password copied to clipboard")
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showError(message: String) {
        updateBadge("Error", "#ef4444")
        AlertDialog.Builder(this)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("Retry") { _, _ ->
                startSetupFlow()
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setStatus(text: String, detail: String? = null) {
        statusText.text = text
        if (detail != null) {
            statusDetail.text = detail
            statusDetail.visibility = View.VISIBLE
        } else {
            statusDetail.visibility = View.GONE
        }
    }

    private fun updateStatus(text: String, detail: String? = null) {
        runOnUiThread { setStatus(text, detail) }
    }

    private fun updateDetail(text: String) {
        runOnUiThread {
            statusDetail.text = text
            statusDetail.visibility = View.VISIBLE
        }
    }

    private fun updateBadge(text: String, colorHex: String) {
        runOnUiThread {
            statusBadge.text = text
            statusBadge.visibility = View.VISIBLE
        }
    }
}
