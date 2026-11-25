package com.music.spotui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import app.myspoti.engine.R
import app.myspoti.host.android.Logbook
import app.myspoti.host.android.MyspotiRuntime
import com.music.spotui.ui.screens.shareDiagnosticReport
import com.music.spotui.ui.theme.AppAccent
import com.music.spotui.ui.theme.AppSans
import com.music.spotui.ui.theme.AppText

/**
 * A failed sign-in had no way to a diagnostic report: the export lives in the settings, which
 * only a signed-in app reaches (the reported "Oops! Something went wrong" and e-mail loops came
 * with no log at all). What the page shows is noted first, then the usual share sheet.
 */
fun sendLoginReport(context: android.content.Context) {
    MyspotiRuntime.host.noteLoginPage {
        val file = runCatching { Logbook.export(context) }.getOrNull()
        if (file != null) shareDiagnosticReport(context, file)
        else android.widget.Toast.makeText(context, context.getString(R.string.diagnostic_export_failed), android.widget.Toast.LENGTH_SHORT).show()
    }
}

/**
 * "Help" over Spotify's sign-in page. The page is a WebView above the app's screens, so this is a
 * popup (a window of its own, above it), small, in the top corner Spotify's page leaves empty, and
 * not focusable: the keyboard and the form keep working under it.
 */
@Composable
fun LoginHelpButton() {
    var open by remember { mutableStateOf(false) }
    val top = WindowInsets.statusBars.getTop(LocalDensity.current)
    val gap = with(LocalDensity.current) { 12.dp.roundToPx() }
    Popup(alignment = Alignment.TopEnd, offset = IntOffset(-gap, top + gap)) {
        Text(
            stringResource(R.string.login_help_button),
            color = AppText, fontFamily = AppSans, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xCC2A2A2A))
                .clickable { open = true }.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
    if (open) LoginHelpDialog(onClose = { open = false })
}

@Composable
fun LoginHelpDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var browser by remember { mutableStateOf(false) }
    if (browser) { BrowserSignInDialog(onClose = { browser = false; onClose() }); return }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xFF242424),
        title = { Text(stringResource(R.string.login_help_title), color = AppText) },
        text = {
            Column {
                Text(stringResource(R.string.login_help_detail), color = AppText.copy(alpha = 0.7f))
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    val clip = (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
                        ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
                    val link = Regex("""https://(accounts|challenge)\.spotify\.com/\S+""").find(clip)?.value ?: clip
                    if (MyspotiRuntime.host.openLoginLink(link)) onClose()
                    else android.widget.Toast.makeText(ctx, ctx.getString(R.string.login_paste_none), android.widget.Toast.LENGTH_LONG).show()
                }) {
                    Text(stringResource(R.string.login_paste_link), color = AppAccent)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = {
                    val act = ctx as? app.myspoti.MainActivity
                    if (act != null) {
                        onClose()
                        act.launchGeckoLogin(fromHelp = true)
                    } else {
                        browser = true
                    }
                }) { Text("Sign in with built-in Firefox browser", color = AppAccent) }
                TextButton(onClick = { browser = true }) { Text("Paste sp_dc cookie manually", color = AppText) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onClose(); sendLoginReport(ctx) }) {
                Text(stringResource(R.string.login_help_send), color = AppAccent)
            }
        },
        dismissButton = {
            // A half-finished flow is what Spotify answers "Oops! Something went wrong" to: from nothing.
            TextButton(onClick = { onClose(); MyspotiRuntime.host.retryLogin() }) {
                Text(stringResource(R.string.login_help_restart), color = AppText)
            }
        },
    )
}

/** Under a wait that lasts (the loading screen after the sign-in), the same way to a report. */
@Composable
fun LoginReportLink(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Text(
        stringResource(R.string.login_help_slow),
        color = AppText.copy(alpha = 0.6f), fontFamily = AppSans, fontSize = 14.sp,
        modifier = modifier.clip(RoundedCornerShape(50)).clickable { sendLoginReport(ctx) }.padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/** Sign in in Chrome, then paste the `sp_dc` cookie here. Added by vanshnt (Belutify). */
@Composable
fun BrowserSignInDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var value by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Color(0xFF242424),
        title = { Text("Sign in with browser", color = AppText) },
        text = {
            Column {
                Text(
                    "Android Chrome has no DevTools to view HttpOnly cookies.\n\n" +
                        "• On a PC/Mac: open Chrome/Edge, sign in at open.spotify.com, open DevTools (F12) → Application → Cookies → https://open.spotify.com, and copy the sp_dc value.\n" +
                        "• On Android: use a browser with Cookie-Editor (e.g. Firefox + Cookie-Editor or Edge/Kiwi), sign in at open.spotify.com, and copy sp_dc.",
                    color = AppText.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    val uri = android.net.Uri.parse("https://accounts.spotify.com/login")
                    val chrome = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).setPackage("com.android.chrome")
                    runCatching { ctx.startActivity(chrome.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                }) { Text("Open Chrome", color = AppAccent) }
                OutlinedTextField(value = value, onValueChange = { value = it; bad = false }, singleLine = true, isError = bad, label = { Text("sp_dc") })
                TextButton(onClick = {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.primaryClip?.getItemAt(0)?.text?.toString()?.let { value = it; bad = false }
                }) { Text("Paste from clipboard", color = AppText) }
                if (bad) Text("That does not look like an sp_dc cookie.", color = Color(0xFFFF6B6B))
            }
        },
        confirmButton = { TextButton(onClick = { if (MyspotiRuntime.host.importSession(value)) onClose() else bad = true }) { Text("Sign in", color = AppAccent) } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel", color = AppText) } },
    )
}
