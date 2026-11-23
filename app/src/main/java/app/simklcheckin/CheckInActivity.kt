package app.simklcheckin

import android.app.Activity
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Opens simkl.com for each pending account, one after another, in a visible
 * WebView shown over the lock screen. An account is marked done only when the
 * page reaches the logged-in home learned during setup.
 */
class CheckInActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var container: FrameLayout
    private var current: WebView? = null
    private var force = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        force = intent.getBooleanExtra("force", false)

        val pad = (16 * resources.displayMetrics.density).toInt()
        status = TextView(this).apply { setPadding(pad, pad, pad, pad) }
        container = FrameLayout(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(status)
            addView(container, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        setContentView(root)

        if (!Browser.supported()) {
            Store.log(this, "WebView on this phone is too old for separate profiles")
            finish()
            return
        }
        runAccount(0)
    }

    private fun runAccount(i: Int) {
        if (i >= Store.COUNT) {
            finishRun()
            return
        }
        val skip = Store.home(this, i) == null || (!force && Store.isDone(this, i))
        if (skip) {
            runAccount(i + 1)
            return
        }
        if (!online()) {
            Store.log(this, "No internet, will retry at the next slot")
            finishRun()
            return
        }
        status.text = "Checking in account ${i + 1}…"
        val wv = Browser.create(this, i)
        current = wv
        container.addView(wv, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        wv.loadUrl(Browser.URL)
        watch(i, wv, 0)
    }

    /** Polls once a second until the logged-in page appears or 45 seconds pass. */
    private fun watch(i: Int, wv: WebView, seconds: Int) {
        handler.postDelayed({
            if (isDestroyed) return@postDelayed
            when {
                wv.progress == 100 && Browser.isHome(this, i, wv.url) -> {
                    // Stay on the loaded page briefly before closing it.
                    handler.postDelayed({
                        if (isDestroyed) return@postDelayed
                        Store.markDone(this, i)
                        Store.log(this, "Account ${i + 1} checked in")
                        close(wv)
                        runAccount(i + 1)
                    }, 1500)
                }
                seconds >= 45 -> {
                    val where = "${wv.title ?: "no title"} (${Browser.normalize(wv.url)})"
                    Store.log(this, "Account ${i + 1} FAILED, stuck at: $where")
                    Store.notifyAttention(this, i, "Open to log in again or complete a check.")
                    close(wv)
                    runAccount(i + 1)
                }
                else -> watch(i, wv, seconds + 1)
            }
        }, 1000)
    }

    private fun close(wv: WebView) {
        Browser.flush(wv)
        container.removeView(wv)
        wv.destroy()
        current = null
    }

    private fun finishRun() {
        status.text = "Finished"
        if (online() && Store.shouldCheckUpdate(this)) {
            status.text = "Checking for app update…"
            val app = applicationContext
            Updater.check(app) { msg ->
                if (msg != Updater.UP_TO_DATE) Store.log(app, msg)
                finish()
            }
        } else {
            finish()
        }
    }

    private fun online(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        current?.destroy()
        current = null
        super.onDestroy()
    }
}
