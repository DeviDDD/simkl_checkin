package app.simklcheckin

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Setup and status screen. Rebuilt every time it comes to the front. */
class MainActivity : Activity() {
    private lateinit var box: LinearLayout

    override fun onResume() {
        super.onResume()
        Scheduler.schedule(this)
        render()
    }

    private fun render() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        val scroll = ScrollView(this)
        scroll.fitsSystemWindows = true
        scroll.addView(box)
        setContentView(scroll)

        if (!Browser.supported()) {
            text("Android System WebView on this phone is too old to keep three accounts apart. Update it in the Play Store, then reopen this app.")
            return
        }

        for (i in 0 until Store.COUNT) {
            val state = when {
                Store.home(this, i) == null -> "tap to set up"
                Store.isDone(this, i) -> "checked in today"
                else -> "pending today"
            }
            button("Account ${i + 1}: $state") {
                startActivity(Intent(this, LoginActivity::class.java).putExtra("account", i))
            }
        }

        button("Run check-in now (test)") {
            startActivity(Intent(this, CheckInActivity::class.java).putExtra("force", true))
        }

        val missing = missingPermission()
        if (missing != null) {
            button("Required: ${missing.first}") { missing.second() }
        } else {
            text("All permissions granted.")
        }

        button("Check for app update") {
            Toast.makeText(this, "Checking…", Toast.LENGTH_SHORT).show()
            val app = applicationContext
            Updater.check(app) { msg -> Toast.makeText(app, msg, Toast.LENGTH_LONG).show() }
        }

        val logging = Store.loggingOn(this)
        button(if (logging) "Debug log: ON (tap to turn off and erase)" else "Debug log: OFF (tap to turn on)") {
            Store.setLogging(this, !logging)
            render()
        }
        if (logging) {
            button("Share log") {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, Store.readLog(this))
                startActivity(Intent.createChooser(send, "Share log"))
            }
        }
        val logText = if (logging) "\n\nLast 10 log lines, newest first:\n${Store.readLog(this)}" else ""
        text("Version ${BuildConfig.VERSION_NAME}$logText")
    }

    /** The first permission still missing, with the action that opens its setting. */
    private fun missingPermission(): Pair<String, () -> Unit>? {
        val pkg = Uri.parse("package:$packageName")
        if (!Settings.canDrawOverlays(this)) {
            return "allow display over other apps" to {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg))
            }
        }
        if (!getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)) {
            return "turn off battery optimisation" to {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg))
            }
        }
        if (Build.VERSION.SDK_INT >= 31 &&
            !getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        ) {
            return "allow exact alarms" to {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg))
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return "allow notifications" to {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        if (!packageManager.canRequestPackageInstalls()) {
            return "allow installing updates" to {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkg))
            }
        }
        return null
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        render()
    }

    private fun button(label: String, onClick: () -> Unit) {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        box.addView(b)
    }

    private fun text(value: String) {
        val t = TextView(this)
        t.text = value
        t.setTextIsSelectable(true)
        t.setPadding(0, 24, 0, 24)
        box.addView(t)
    }
}
