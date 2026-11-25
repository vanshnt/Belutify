package com.music.spotui.ui.screens

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppMuted
import com.music.spotui.ui.theme.AppSans
import com.music.spotui.ui.theme.AppText
import com.music.spotui.ui.theme.SpotuiTheme
import com.music.spotui.ui.theme.tone
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import java.net.URI

/**
 * Lightweight in-app login browser backed by Mozilla GeckoView running in an isolated `:gecko`
 * process. Lets the user sign in to Spotify normally without Android WebView's forced
 * `X-Requested-With` header, extracts the `sp_dc` cookie via a built-in WebExtension, returns
 * it to Belutify, and shuts down the `:gecko` process immediately afterward.
 */
class GeckoLoginActivity : ComponentActivity() {
    private var runtime: GeckoRuntime? = null
    private var session: GeckoSession? = null
    private var geckoView: GeckoView? = null
    private var canGoBack = false
    private var delivered = false

    private var currentHost by mutableStateOf("accounts.spotify.com")
    private var loadProgress by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val rt = GeckoRuntime.getDefault(applicationContext)
        runtime = rt

        val s = GeckoSession()
        session = s
        setupSession(s)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (canGoBack) {
                    session?.goBack()
                } else {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        })

        setContent {
            SpotuiTheme {
                var manualOpen by remember { mutableStateOf(false) }
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(tone(0xFF121212))
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = AppText)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            Text(
                                "Sign in to Spotify",
                                color = AppText,
                                fontFamily = AppSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = AppMuted,
                                    modifier = Modifier.size(12.dp).padding(end = 3.dp),
                                )
                                Text(
                                    currentHost.ifBlank { "accounts.spotify.com" },
                                    color = AppMuted,
                                    fontFamily = AppSans,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(onClick = { manualOpen = true }) {
                            Icon(Icons.Rounded.Key, contentDescription = "Paste sp_dc", tint = AppMuted)
                        }
                        IconButton(onClick = { session?.reload() }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Reload", tint = AppText)
                        }
                    }

                    if (loadProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { loadProgress / 100f },
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = AppAccent,
                            trackColor = tone(0xFF121212),
                        )
                    }

                    AndroidView(
                        factory = { ctx ->
                            GeckoView(ctx).apply {
                                geckoView = this
                                setSession(s)
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }

                if (manualOpen) {
                    ManualSpDcDialog(
                        onDismiss = { manualOpen = false },
                        onSubmit = { spDc ->
                            manualOpen = false
                            deliverToken(spDc)
                        },
                    )
                }
            }
        }

        // Clear any leftover browser session before starting so Add Account / re-login starts fresh.
        rt.storageController.clearData(StorageController.ClearFlags.ALL).accept(
            { startExtensionAndLoad(rt, s) },
            { startExtensionAndLoad(rt, s) },
        )
    }

    private fun setupSession(s: GeckoSession) {
        s.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                if (!url.isNullOrBlank()) {
                    currentHost = runCatching { URI(url).host.orEmpty() }.getOrDefault("")
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                this@GeckoLoginActivity.canGoBack = canGoBack
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest,
            ): GeckoResult<AllowOrDeny> {
                val uri = request.uri
                val parsed = runCatching { URI(uri) }.getOrNull()
                val scheme = parsed?.scheme?.lowercase().orEmpty()
                if (scheme != "https" && scheme != "http" && scheme != "about") {
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
                // Keep OAuth popups (Google / Facebook / Apple) inside the same login session.
                session.loadUri(uri)
                return GeckoResult.fromValue(null)
            }
        }

        s.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onProgressChange(session: GeckoSession, progress: Int) {
                this@GeckoLoginActivity.loadProgress = progress
            }
        }
    }

    private fun startExtensionAndLoad(rt: GeckoRuntime, s: GeckoSession) {
        rt.webExtensionController
            .ensureBuiltIn("resource://android/assets/gecko_login/", EXTENSION_ID)
            .accept(
                { ext ->
                    if (ext != null) {
                        val delegate = object : WebExtension.MessageDelegate {
                            override fun onMessage(
                                nativeApp: String,
                                message: Any,
                                sender: WebExtension.MessageSender,
                            ): GeckoResult<Any>? {
                                val raw = when (message) {
                                    is JSONObject -> message.optString("sp_dc")
                                    is String -> message
                                    else -> runCatching { JSONObject(message.toString()).optString("sp_dc") }.getOrDefault("")
                                }
                                if (isValidSpDc(raw)) {
                                    runOnUiThread { deliverToken(raw) }
                                }
                                return null
                            }
                        }
                        ext.setMessageDelegate(delegate, NATIVE_APP)
                    }
                    runOnUiThread {
                        s.open(rt)
                        s.loadUri(LOGIN_URL)
                    }
                },
                {
                    runOnUiThread {
                        s.open(rt)
                        s.loadUri(LOGIN_URL)
                    }
                },
            )
    }

    private fun deliverToken(raw: String) {
        val cleaned = cleanSpDc(raw) ?: return
        if (delivered) return
        delivered = true
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_SP_DC, cleaned))
        finish()
    }

    override fun onDestroy() {
        runCatching { geckoView?.releaseSession() }
        runCatching { session?.close() }
        super.onDestroy()
        // Terminate the isolated :gecko process when login ends so zero Gecko RAM stays allocated.
        if (isFinishing) {
            Process.killProcess(Process.myPid())
        }
    }

    companion object {
        const val EXTRA_SP_DC = "sp_dc"
        private const val EXTENSION_ID = "login@belutify.app"
        private const val NATIVE_APP = "belutify"
        private const val LOGIN_URL =
            "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"

        private fun cleanSpDc(raw: String): String? {
            val v = raw.trim().removePrefix("sp_dc=").substringBefore(';').trim().trim('"')
            if (v.length < 20 || v.any { it.isWhitespace() || it == ',' || it == '"' }) return null
            return v
        }

        private fun isValidSpDc(raw: String): Boolean = cleanSpDc(raw) != null
    }
}

@androidx.compose.runtime.Composable
private fun ManualSpDcDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val ctx = LocalContext.current
    var value by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tone(0xFF242424),
        title = { Text("Paste sp_dc cookie", color = AppText) },
        text = {
            Column {
                Text(
                    "Optional: paste an sp_dc cookie directly if you signed in on another browser.",
                    color = AppText.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; bad = false },
                    singleLine = true,
                    isError = bad,
                    label = { Text("sp_dc") },
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
                )
                TextButton(onClick = {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                    cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.let {
                        value = it; bad = false
                    }
                }) { Text("Paste from clipboard", color = AppText) }
                if (bad) Text("That does not look like an sp_dc cookie.", color = androidx.compose.ui.graphics.Color(0xFFFF6B6B))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = value.trim().removePrefix("sp_dc=").substringBefore(';').trim().trim('"')
                if (trimmed.length >= 20 && trimmed.none { it.isWhitespace() || it == ',' || it == '"' }) {
                    onSubmit(trimmed)
                } else {
                    bad = true
                }
            }) { Text("Sign in", color = AppAccent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = AppText) } },
    )
}
