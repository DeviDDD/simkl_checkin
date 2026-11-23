package app.simklcheckin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Small persistent state: per-account progress, learned home pages and the log. */
object Store {
    const val COUNT = 3
    private const val LOG_LINES = 150

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("simkl", Context.MODE_PRIVATE)

    /** Simkl counts days in UTC, so "today" is always the UTC date. */
    fun today(): String {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date())
    }

    fun isDone(ctx: Context, i: Int): Boolean = prefs(ctx).getString("done$i", "") == today()

    fun markDone(ctx: Context, i: Int) {
        prefs(ctx).edit().putString("done$i", today()).apply()
    }

    /** The logged-in page Simkl redirected to when the account was set up. */
    fun home(ctx: Context, i: Int): String? = prefs(ctx).getString("home$i", null)

    fun setHome(ctx: Context, i: Int, url: String) {
        prefs(ctx).edit().putString("home$i", url).apply()
    }

    /** True when every configured account has checked in for the current UTC day. */
    fun allDone(ctx: Context): Boolean =
        (0 until COUNT).all { home(ctx, it) == null || isDone(ctx, it) }

    /** Returns true at most once per UTC day. */
    fun shouldCheckUpdate(ctx: Context): Boolean {
        val p = prefs(ctx)
        if (p.getString("updateDay", "") == today()) return false
        p.edit().putString("updateDay", today()).apply()
        return true
    }

    fun log(ctx: Context, msg: String) {
        val f = File(ctx.filesDir, "log.txt")
        val stamp = SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date())
        val lines = (if (f.exists()) f.readLines() else emptyList()) + "$stamp  $msg"
        f.writeText(lines.takeLast(LOG_LINES).joinToString("\n"))
    }

    fun readLog(ctx: Context): String {
        val f = File(ctx.filesDir, "log.txt")
        return if (f.exists()) f.readLines().reversed().joinToString("\n") else "(empty)"
    }

    /** Tells the user an account needs a manual look (logged out, or a challenge to tap). */
    fun notifyAttention(ctx: Context, account: Int, msg: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel("attention", "Needs attention", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            ctx,
            100 + account,
            Intent(ctx, LoginActivity::class.java).putExtra("account", account),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, "attention")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Simkl account ${account + 1} needs attention")
            .setContentText(msg)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(account, n)
        } catch (e: SecurityException) {
            // Notification permission not granted; the log still records the failure.
        }
    }
}
