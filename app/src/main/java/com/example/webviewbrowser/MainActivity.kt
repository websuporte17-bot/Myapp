package com.example.webviewbrowser

import android.app.AlertDialog
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.Random
class MainActivity : AppCompatActivity() {
    private lateinit var urlInput: EditText
    private lateinit var goButton: Button
    private lateinit var tabsContainer: LinearLayout
    private lateinit var webViewContainer: FrameLayout
    private lateinit var identityButton: Button
    private lateinit var settingsButton: Button
    private lateinit var recordButton: Button
    private lateinit var playButton: Button

    private val tabs = mutableListOf<TabEntry>()
    private var activeTabIndex = 0
    private var ghostScriptEnabled = false
    private var ghostScriptText = ""
    private var isRecording = false
    private val macroSteps = mutableListOf<MacroStep>()
    private val macroBridge = MacroBridge(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        urlInput = findViewById(R.id.urlInput)
        goButton = findViewById(R.id.goButton)
        tabsContainer = findViewById(R.id.tabsContainer)
        webViewContainer = findViewById(R.id.webViewContainer)
        identityButton = findViewById(R.id.identityButton)
        settingsButton = findViewById(R.id.settingsButton)
        recordButton = findViewById(R.id.recordButton)
        playButton = findViewById(R.id.playButton)

        goButton.setOnClickListener { navigateActiveTab() }
        urlInput.setOnEditorActionListener { _, _, _ ->
            navigateActiveTab()
            true
        }
        identityButton.setOnClickListener { showIdentityDialog() }
        settingsButton.setOnClickListener { showSettingsDialog() }
        recordButton.setOnClickListener { toggleRecording() }
        playButton.setOnClickListener { replayAutomation() }

        createTab("Aba 1")
        renderTabs()
        showActiveTab()
    }

    private fun navigateActiveTab() {
        val raw = urlInput.text.toString().trim()
        if (raw.isEmpty()) return

        val normalized = normalizeUrl(raw)
        val active = tabs[activeTabIndex]
        active.currentUrl = normalized
        active.webView.loadUrl(normalized)
        active.displayTitle = normalized
        updateUrlField()
    }

    private fun normalizeUrl(raw: String): String {
        return when {
            raw.startsWith("javascript:") -> raw
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            else -> "https://$raw"
        }
    }

    private fun createTab(title: String) {
        val webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("mailto:") || url.startsWith("tel:")) return true
                    view?.loadUrl(url)
                    return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    val active = tabs.getOrNull(activeTabIndex)
                    if (active?.webView === view) {
                        injectIdentityScript(view)
                        injectGhostScript(view)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val active = tabs.getOrNull(activeTabIndex)
                    if (active?.webView === view) {
                        injectIdentityScript(view)
                        injectGhostScript(view)
                    }
                }
            }
            addJavascriptInterface(macroBridge, "android")
            loadUrl("about:blank")
        }

        tabs.add(TabEntry(tabName = title, webView = webView, currentUrl = "about:blank"))
        activeTabIndex = tabs.lastIndex
    }

    private fun renderTabs() {
        tabsContainer.removeAllViews()
        tabs.forEachIndexed { index, tab ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val tabButton = Button(this).apply {
                text = if (index == activeTabIndex) "${tab.tabName} ●" else tab.tabName
                setOnClickListener { switchTab(index) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            val closeButton = Button(this).apply {
                text = "×"
                setOnClickListener {
                    closeTab(index)
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            row.addView(tabButton)
            row.addView(closeButton)
            tabsContainer.addView(row)
        }

        val addButton = Button(this).apply {
            text = "+ Nova Aba"
            setOnClickListener { createTab("Aba ${tabs.size + 1}"); renderTabs(); showActiveTab() }
        }
        tabsContainer.addView(addButton)
    }

    private fun switchTab(index: Int) {
        if (index !in tabs.indices) return
        activeTabIndex = index
        renderTabs()
        showActiveTab()
    }

    private fun closeTab(index: Int) {
        if (tabs.size <= 1) return
        tabs.removeAt(index)
        if (activeTabIndex >= tabs.size) activeTabIndex = tabs.lastIndex
        renderTabs()
        showActiveTab()
    }

    private fun showActiveTab() {
        webViewContainer.removeAllViews()
        val active = tabs.getOrNull(activeTabIndex) ?: return
        active.webView.parent?.let { (it as ViewGroup).removeView(active.webView) }
        webViewContainer.addView(active.webView)
        updateUrlField()
    }

    private fun updateUrlField() {
        val active = tabs.getOrNull(activeTabIndex) ?: return
        urlInput.setText(active.currentUrl)
        urlInput.setSelection(urlInput.text?.length ?: 0)
    }

    private fun showIdentityDialog() {
        val active = tabs[activeTabIndex]
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val uaInput = EditText(this).apply {
            hint = "User-Agent"
            setText(generateFakeUserAgent())
        }
        val macInput = EditText(this).apply {
            hint = "MAC Address"
            setText(generateFakeMac())
        }
        val memoryInput = EditText(this).apply {
            hint = "Memória Java"
            setText((1024 + Random().nextInt(4096)).toString())
        }
        view.addView(uaInput)
        view.addView(macInput)
        view.addView(memoryInput)

        AlertDialog.Builder(this)
            .setTitle("Painel de Identidade")
            .setView(view)
            .setPositiveButton("Aplicar e Limpar") { _, _ ->
                val ua = uaInput.text.toString().trim()
                val mac = macInput.text.toString().trim()
                val memory = memoryInput.text.toString().trim()
                active.webView.settings.userAgentString = if (ua.isNotEmpty()) ua else generateFakeUserAgent()
                applyDeepClean(active.webView)
                injectIdentityScript(active.webView, ua, mac, memory)
                active.webView.reload()
                Toast.makeText(this, "Identidade aplicada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showSettingsDialog() {
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val scriptInput = EditText(this).apply {
            hint = "Cole aqui seu JS persistente"
            setText(ghostScriptText)
            minLines = 6
        }
        val toggle = CheckBox(this).apply {
            text = "Ativar Ghost Script"
            isChecked = ghostScriptEnabled
        }
        view.addView(scriptInput)
        view.addView(toggle)

        AlertDialog.Builder(this)
            .setTitle("Configurações")
            .setView(view)
            .setPositiveButton("Salvar") { _, _ ->
                ghostScriptEnabled = toggle.isChecked
                ghostScriptText = scriptInput.text.toString().trim()
                injectGhostScript(tabs[activeTabIndex].webView)
                Toast.makeText(this, "Ghost Script aplicado", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun toggleRecording() {
        isRecording = !isRecording
        recordButton.text = if (isRecording) "Parar" else "Gravar"
        macroSteps.clear()
        if (!isRecording) {
            saveAutomationFile()
            Toast.makeText(this, "Automação salva", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Gravação iniciada", Toast.LENGTH_SHORT).show()
            injectRecorderScript()
        }
    }

    private fun injectRecorderScript() {
        val script = """
            (function(){
                window.__macroRecorderEnabled = true;
                if (window.__macroRecorderBound) return;
                window.__macroRecorderBound = true;
                document.addEventListener('click', function(e){
                    if (!window.__macroRecorderEnabled) return;
                    const el = e.target;
                    const step = {
                        type: 'click',
                        selector: cssPath(el),
                        value: '',
                        html: el.outerHTML,
                        pre_action_script: ''
                    };
                    window.android.onMacroStep(JSON.stringify(step));
                });
                document.addEventListener('input', function(e){
                    if (!window.__macroRecorderEnabled) return;
                    const el = e.target;
                    const step = {
                        type: 'input',
                        selector: cssPath(el),
                        value: el.value,
                        html: el.outerHTML,
                        pre_action_script: ''
                    };
                    window.android.onMacroStep(JSON.stringify(step));
                });
                function cssPath(el){
                    if (!(el instanceof Element)) return '';
                    const path = [];
                    let node = el;
                    while (node && node.nodeType === Node.ELEMENT_NODE) {
                        let selector = node.nodeName.toLowerCase();
                        if (node.id) selector += '#' + node.id;
                        else {
                            const siblings = Array.from(node.parentNode.children).filter(c => c.nodeName === node.nodeName);
                            if (siblings.length > 1) {
                                selector += ':nth-of-type(' + (siblings.indexOf(node) + 1) + ')';
                            }
                        }
                        path.unshift(selector);
                        node = node.parentElement;
                    }
                    return path.join(' > ');
                }
            })();
        """.trimIndent()
        tabs[activeTabIndex].webView.evaluateJavascript(script, null)
    }

    private fun replayAutomation() {
        val automationFile = File(filesDir, "automation.json")
        if (!automationFile.exists()) {
            Toast.makeText(this, "Nenhuma automação salva", Toast.LENGTH_SHORT).show()
            return
        }
        val json = automationFile.readText(StandardCharsets.UTF_8)
        val steps = JSONArray(json)
        val active = tabs[activeTabIndex]
        for (i in 0 until steps.length()) {
            val step = steps.getJSONObject(i)
            val script = buildPlaybackScript(step)
            active.webView.evaluateJavascript(script, null)
        }
        Toast.makeText(this, "Automação iniciada", Toast.LENGTH_SHORT).show()
    }

    private fun buildPlaybackScript(step: JSONObject): String {
        val type = step.optString("type")
        val selector = step.optString("selector")
        val value = step.optString("value")
        val preAction = step.optString("pre_action_script")
        return """
            (function(){
                try {
                    ${if (preAction.isNotEmpty()) preAction else ""}
                    const el = document.querySelector('${selector.replace("'", "\\'")}');
                    if (!el) return;
                    if ('$type' === 'click') {
                        el.click();
                    } else if ('$type' === 'input') {
                        el.value = '$value'.replace(/\\n/g, '\\n');
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                    }
                } catch (e) { console.log(e); }
            })();
        """.trimIndent()
    }

    private fun saveAutomationFile() {
        val file = File(filesDir, "automation.json")
        val jsonArray = JSONArray(macroSteps.map { it.toJson() })
        FileOutputStream(file, false).use { it.write(jsonArray.toString().toByteArray(StandardCharsets.UTF_8)) }
    }

    private fun injectGhostScript(view: WebView?) {
        if (!ghostScriptEnabled || ghostScriptText.isBlank()) return
        view?.evaluateJavascript("""
            try {
                ${ghostScriptText}
            } catch (e) { console.error(e); }
        """.trimIndent(), null)
    }

    private fun injectIdentityScript(view: WebView?, ua: String = generateFakeUserAgent(), mac: String = generateFakeMac(), memory: String = "4096") {
        val script = """
            try {
                Object.defineProperty(navigator, 'userAgent', { value: '$ua', configurable: true });
                Object.defineProperty(navigator, 'platform', { value: 'Win32', configurable: true });
                Object.defineProperty(navigator, 'language', { value: 'en-US', configurable: true });
                Object.defineProperty(navigator, 'hardwareConcurrency', { value: 8, configurable: true });
                window.__fakeMac = '$mac';
                window.__fakeMemory = '$memory';
            } catch (e) { console.log(e); }
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    private fun applyDeepClean(view: WebView) {
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
        WebStorage.getInstance().deleteAllData()
        view.clearCache(true)
        view.clearHistory()
        view.clearFormData()
        view.clearMatches()
        view.reload()
    }

    private fun generateFakeUserAgent(): String {
        val browsers = listOf(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.5 Safari/605.1.15",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"
        )
        return browsers.random()
    }

    private fun generateFakeMac(): String {
        val random = Random()
        return (0 until 6).joinToString(":") { String.format("%02x", random.nextInt(256)) }
    }

    private class MacroBridge(private val activity: MainActivity) {
        @JavascriptInterface
        fun onMacroStep(step: String) {
            activity.macroSteps.add(MacroStep.fromJson(step))
        }
    }

    private data class MacroStep(
        val type: String,
        val selector: String,
        val value: String,
        val html: String,
        val preActionScript: String = ""
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("type", type)
            put("selector", selector)
            put("value", value)
            put("html", html)
            put("pre_action_script", preActionScript)
        }

        companion object {
            fun fromJson(json: String): MacroStep {
                val obj = JSONObject(json)
                return MacroStep(
                    type = obj.optString("type"),
                    selector = obj.optString("selector"),
                    value = obj.optString("value"),
                    html = obj.optString("html"),
                    preActionScript = obj.optString("pre_action_script")
                )
            }
        }
    }

    private data class TabEntry(
        val tabName: String,
        val webView: WebView,
        var currentUrl: String,
        var displayTitle: String = ""
    )
}
