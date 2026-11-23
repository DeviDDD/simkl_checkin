package app.simklcheckin

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

/**
 * One-time setup for an account: log in by hand, then tap Save. The app reloads
 * simkl.com and remembers the page Simkl redirects to, which becomes the proof
 * of a successful check-in on later runs.
 */
class LoginActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wv: WebView
    private var account = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        account = intent.getIntExtra("account", 0)
        title = "Simkl account ${account + 1}"

        if (!Browser.supported()) {
            Toast.makeText(this, "Update Android System WebView first", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val save = Button(this)
        save.text = "I'm logged in: save"
        save.setOnClickListener { verify(save) }

        wv = Browser.create(this, account)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
            addView(save)
            addView(wv, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        wv.loadUrl(Browser.URL)
    }

    private fun verify(save: Button) {
        save.isEnabled = false
        save.text = "Checking…"
        val root = Browser.normalize(Browser.URL)
        var last = ""
        var stable = 0
        var ticks = 0
        wv.loadUrl(Browser.URL)

        val tick = object : Runnable {
            override fun run() {
                if (isDestroyed) return
                val url = Browser.normalize(wv.url)
                stable = if (wv.progress == 100 && url == last) stable + 1 else 0
                last = url
                ticks++
                when {
                    stable >= 3 && url != root -> {
                        Store.setHome(this@LoginActivity, account, url)
                        Browser.flush(wv)
                        Store.log(this@LoginActivity, "Account ${account + 1} set up ($url)")
                        Toast.makeText(this@LoginActivity, "Saved", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    ticks > 30 -> {
                        Toast.makeText(
                            this@LoginActivity,
                            "Simkl did not open your account page. Log in and try again.",
                            Toast.LENGTH_LONG
                        ).show()
                        save.isEnabled = true
                        save.text = "I'm logged in: save"
                    }
                    else -> handler.postDelayed(this, 1000)
                }
            }
        }
        handler.postDelayed(tick, 1000)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::wv.isInitialized && wv.canGoBack()) {
            wv.goBack()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::wv.isInitialized) {
            Browser.flush(wv)
            wv.destroy()
        }
        super.onDestroy()
    }
}
