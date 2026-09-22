package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.HttpAuthHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DirectIpWebDialog(
    host: String,
    username: String = "admin",
    passwordInput: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var cleanHost = host.trim().replace("http://", "").replace("https://", "").removeSuffix("/")
    if (cleanHost.isBlank()) cleanHost = "192.168.4.1"
    val fullUrl = "http://$cleanHost/"

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp, bottom = 12.dp, start = 8.dp, end = 8.dp)
                .testTag("direct_ip_web_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF090D14),
            border = BorderStroke(1.2.dp, GoldPrimary.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action & Status Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBackgroundElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DIRECT IP WEB UI",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = fullUrl,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = CyanAccent,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Open in Chrome / External Browser
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.size(36.dp).testTag("open_external_browser_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Open in Chrome",
                                tint = CyanAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Reload Button
                        IconButton(
                            onClick = {
                                errorMessage = null
                                webViewInstance?.reload()
                            },
                            modifier = Modifier.size(36.dp).testTag("reload_webview_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload",
                                tint = TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Close Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp).testTag("close_direct_ip_dialog_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = ErrorRed,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Progress Bar while loading
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { loadProgress / 100f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = GoldPrimary,
                        trackColor = Color(0xFF1B2433)
                    )
                } else {
                    HorizontalDivider(color = CardBorder, thickness = 1.dp)
                }

                // Info Banner
                Surface(
                    color = Color(0xFF0F1522),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ব্রাউজারে সরাসরি IP ($cleanHost) দিয়ে যেভাবে কন্ট্রোল করেন, এখানে হুবহু সেই মূল ওয়েব ইন্টারফেস লাইভ চলছে।",
                            fontSize = 11.sp,
                            color = TextSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }

                HorizontalDivider(color = CardBorder, thickness = 1.dp)

                // Error message overlay if clock unreachable
                if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = WarningOrange,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "ঘড়ির IP তে সংযোগ পাওয়া যায়নি",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "$fullUrl\nঅনুগ্রহ করে নিশ্চিত করুন আপনার ফোনটি ঘড়ির হটস্পট (Sultan Clock / ESP8266) অথবা একই রাউটারে যুক্ত আছে।",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        errorMessage = null
                                        webViewInstance?.loadUrl(fullUrl)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("আবার চেষ্টা করুন", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                                ) {
                                    Text("Chrome এ খুলুন")
                                }
                            }
                        }
                    }
                } else {
                    // Android WebView Interop
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewInstance = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                                var authAttempts = 0

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                        if (newProgress >= 100) {
                                            isLoading = false
                                        }
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        // Keep navigation inside this WebView
                                        return false
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                        errorMessage = null
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        isLoading = false
                                        authAttempts = 0
                                    }

                                    override fun onReceivedHttpAuthRequest(
                                        view: WebView?,
                                        handler: HttpAuthHandler?,
                                        authHost: String?,
                                        realm: String?
                                    ) {
                                        authAttempts++
                                        if (authAttempts <= 2) {
                                            handler?.proceed(username.ifBlank { "admin" }, passwordInput)
                                        } else {
                                            handler?.cancel()
                                            errorMessage = "লগইন পাসওয়ার্ড সঠিক নয়। Settings থেকে পাসওয়ার্ড চেক করুন।"
                                        }
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        error: WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            isLoading = false
                                            errorMessage = error?.description?.toString() ?: "Network error"
                                        }
                                    }
                                }

                                loadUrl(fullUrl)
                            }
                        },
                        update = { view ->
                            webViewInstance = view
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
