package com.shisan.campuspro.feature.profile

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.shisan.campuspro.core.common.CAMPUS_DESKTOP_USER_AGENT
import com.shisan.campuspro.core.designsystem.CampusIcons
import com.shisan.campuspro.core.model.PortalWebSessionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val PORTAL_AUTH_URL = "https://authserver.jsu.edu.cn/authserver/login"

private sealed interface PortalPageState {
    data object LoadingSession : PortalPageState
    class Ready(val castgc: String) : PortalPageState
    data class Error(val message: String, val requiresLogin: Boolean) : PortalPageState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecondClassroomRoute(
    prepareSession: suspend () -> PortalWebSessionResult,
    onBack: () -> Unit,
    onRequirePortalLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var retryKey by remember { mutableIntStateOf(0) }
    var pageState: PortalPageState by remember { mutableStateOf(PortalPageState.LoadingSession) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showSlowLoadingNotice by remember { mutableStateOf(true) }

    LaunchedEffect(retryKey) {
        pageState = PortalPageState.LoadingSession
        pageState = when (val result = prepareSession()) {
            is PortalWebSessionResult.Ready -> PortalPageState.Ready(result.castgc)
            PortalWebSessionResult.PortalLoginRequired -> PortalPageState.Error(
                message = "门户登录状态不可用，请重新登录",
                requiresLogin = true,
            )
            is PortalWebSessionResult.Failure -> PortalPageState.Error(
                message = result.message,
                requiresLogin = false,
            )
        }
    }

    BackHandler {
        val currentWebView = webView
        if (currentWebView?.canGoBack() == true) currentWebView.goBack() else onBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("第二课堂") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(CampusIcons.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        when (val state = pageState) {
            PortalPageState.LoadingSession -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is PortalPageState.Ready -> SecondClassroomWebView(
                castgc = state.castgc,
                onWebViewReady = { webView = it },
                onError = { message, requiresLogin ->
                    webView = null
                    pageState = PortalPageState.Error(message, requiresLogin)
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            is PortalPageState.Error -> PortalErrorPage(
                message = state.message,
                buttonText = if (state.requiresLogin) "重新登录" else "重试",
                onAction = if (state.requiresLogin) onRequirePortalLogin else {
                    { retryKey += 1 }
                },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }

        if (showSlowLoadingNotice) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text("加载提示") },
                text = { Text("二课系统加载较慢，请耐心等待。") },
                confirmButton = {
                    TextButton(onClick = { showSlowLoadingNotice = false }) {
                        Text("知道了", color = MaterialTheme.colorScheme.primary)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SecondClassroomWebView(
    castgc: String,
    onWebViewReady: (WebView) -> Unit,
    onError: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val released = remember { AtomicBoolean(false) }
    var loading by remember { mutableStateOf(true) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        userAgentString = CAMPUS_DESKTOP_USER_AGENT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        allowFileAccess = false
                        allowContentAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        setSupportMultipleWindows(false)
                    }

                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            val uri = request.url
                            // 只在安全协议下放行，让 WebView 自行处理所有导航
                            return uri.scheme !in listOf("http", "https")
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            loading = true
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            val uri = Uri.parse(url)
                            if (uri.host == "authserver.jsu.edu.cn" &&
                                uri.path?.startsWith("/authserver/login") == true
                            ) {
                                view.stopLoading()
                                onError("门户登录状态已失效，请重新登录", true)
                            } else {
                                loading = false
                            }
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) {
                                onError("页面加载失败，请检查网络后重试", false)
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: WebResourceResponse,
                        ) {
                            if (request.isForMainFrame) {
                                onError("页面加载失败（${errorResponse.statusCode}）", false)
                            }
                        }
                    }

                    onWebViewReady(this)
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.removeAllCookies {
                        if (released.get()) return@removeAllCookies
                        cookieManager.setCookie(
                            PORTAL_AUTH_URL,
                            buildCastgcCookieHeader(castgc),
                        ) { cookieSet ->
                            if (released.get()) return@setCookie
                            if (!cookieSet) {
                                post { onError("门户登录状态写入失败，请重试", false) }
                                return@setCookie
                            }
                            scope.launch(Dispatchers.IO) {
                                cookieManager.flush()
                                withContext(Dispatchers.Main) {
                                    if (!released.get()) loadUrl(SECOND_CLASSROOM_TARGET_URL)
                                }
                            }
                        }
                    }
                }
            },
            onRelease = { releasedWebView ->
                released.set(true)
                val cookieManager = CookieManager.getInstance()
                cookieManager.setCookie(
                    PORTAL_AUTH_URL,
                    buildExpiredCastgcCookieHeader(),
                ) {
                    cookieManager.flush()
                }
                releasedWebView.stopLoading()
                releasedWebView.loadUrl("about:blank")
                releasedWebView.clearHistory()
                releasedWebView.removeAllViews()
                releasedWebView.destroy()
            },
        )
        if (loading) {
            LinearProgressIndicator(modifier = Modifier.align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun PortalErrorPage(
    message: String,
    buttonText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onAction) {
                Text(buttonText)
            }
        }
    }
}
