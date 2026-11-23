package app.simklcheckin

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Creates a standard WebView bound to one account's isolated profile.
 *
 * Nothing about the browser identity is altered: the user agent, headers and
 * device characteristics are whatever this phone's WebView reports by default.
 */
object Browser {
    const val URL = "https://simkl.com/"

    /** Separate cookie jars per account need WebView's multi-profile support. */
    fun supported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    @SuppressLint("SetJavaScriptEnabled")
    fun create(ctx: Context, account: Int): WebView {
        val wv = WebView(ctx)
        val name = "account${account + 1}"
        // The profile must be chosen before the WebView is used for anything else.
        ProfileStore.getInstance().getOrCreateProfile(name)
        WebViewCompat.setProfile(wv, name)

        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        val cookies = cookies(wv)
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(wv, true)

        // Keep navigation inside this WebView instead of handing links to another browser.
        wv.webViewClient = WebViewClient()
        wv.webChromeClient = WebChromeClient()
        return wv
    }

    private fun cookies(wv: WebView): CookieManager = WebViewCompat.getProfile(wv).cookieManager

    /** Writes cookies to disk so the session survives the app being closed. */
    fun flush(wv: WebView) {
        cookies(wv).flush()
    }

    fun normalize(url: String?): String =
        (url ?: "").lowercase()
            .substringBefore('#')
            .substringBefore('?')
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')

    /** True when the WebView is on the logged-in page learned during setup. */
    fun isHome(ctx: Context, account: Int, url: String?): Boolean {
        val home = Store.home(ctx, account) ?: return false
        return normalize(url).startsWith(home)
    }
}
