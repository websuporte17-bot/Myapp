package com.dg.browser

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var currentUA = "Mozilla/5.0 (Linux; Android 13; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // UI Minimalista e Preta
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(10, 10, 10, 10)
            setBackgroundColor(Color.parseColor("#111111"))
        }

        val urlInput = EditText(this).apply {
            hint = "https://seusite.com"
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val btnIr = Button(this).apply {
            text = "IR"
            setBackgroundColor(Color.parseColor("#333333"))
            setTextColor(Color.WHITE)
        }

        val btnFormatar = Button(this).apply {
            text = "FORMATAR"
            setBackgroundColor(Color.parseColor("#8B0000")) // Vermelho escuro para destacar
            setTextColor(Color.WHITE)
        }

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }

        topBar.addView(urlInput)
        topBar.addView(btnIr)
        topBar.addView(btnFormatar)
        layout.addView(topBar)
        layout.addView(webView)
        setContentView(layout)

        // Setup Inicial do WebView
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = currentUA

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Injetar Variaveis Falsas no Site
                val macFake = UUID.randomUUID().toString()
                val js = """
                    javascript:(function() {
                        window.dgVirtualMemory = {
                            mac_simulado: '${macFake}',
                            sessao_status: 'isolada',
                            ua_atual: navigator.userAgent
                        };
                        console.log('Dados mascarados injetados pelo app');
                    })();
                """.trimIndent()
                view?.evaluateJavascript(js, null)
            }
        }

        btnIr.setOnClickListener {
            val url = urlInput.text.toString()
            if (url.isNotEmpty()) {
                var finalUrl = url
                if (!url.startsWith("http")) {
                    finalUrl = "https://$url"
                }
                webView.loadUrl(finalUrl)
            }
        }

        btnFormatar.setOnClickListener {
            // Limpeza Profunda (Cookies, Cache, LocalStorage nativos do Android)
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            webView.clearCache(true)
            webView.clearHistory()
            
            // Mascarar com novo User-Agent aleatorio
            val randomBuild = (100..999).random()
            currentUA = "Mozilla/5.0 (Linux; Android 14; Build/DG$randomBuild) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            webView.settings.userAgentString = currentUA
            
            Toast.makeText(this, "Memória Limpa. Novo Perfil Gerado.", Toast.LENGTH_SHORT).show()
            webView.loadUrl("about:blank")
            urlInput.setText("")
        }
    }
}
