package app.simklcheckin

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import org.json.JSONObject

/**
 * Self-update from this repository's GitHub Releases. Release tags look like
 * "v123", where 123 is the versionCode the workflow built the APK with.
 */
object Updater {
    const val UP_TO_DATE = "App is up to date"

    fun check(ctx: Context, onDone: (String) -> Unit) {
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isEmpty()) {
            onDone("Updates are off: this build did not come from GitHub Actions")
            return
        }
        val app = ctx.applicationContext
        thread {
            val msg = try {
                update(app, repo)
            } catch (e: Exception) {
                "Update check failed: ${e.message}"
            }
            Handler(Looper.getMainLooper()).post { onDone(msg) }
        }
    }

    private fun update(ctx: Context, repo: String): String {
        val api = URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection
        api.connectTimeout = 15000
        api.readTimeout = 15000
        api.setRequestProperty("Accept", "application/vnd.github+json")
        val json = JSONObject(api.inputStream.bufferedReader().use { it.readText() })

        val latest = json.getString("tag_name").removePrefix("v").toIntOrNull()
            ?: return "Update check failed: unexpected release tag"
        if (latest <= BuildConfig.VERSION_CODE) return UP_TO_DATE

        var apkUrl: String? = null
        val assets = json.getJSONArray("assets")
        for (k in 0 until assets.length()) {
            val asset = assets.getJSONObject(k)
            if (asset.getString("name").endsWith(".apk")) {
                apkUrl = asset.getString("browser_download_url")
            }
        }
        if (apkUrl == null) return "Update check failed: release has no APK"

        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        if (Build.VERSION.SDK_INT >= 31) {
            // Lets Android install silently when it allows it; otherwise it asks once.
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            val download = URL(apkUrl).openConnection() as HttpURLConnection
            download.connectTimeout = 15000
            download.readTimeout = 30000
            download.inputStream.use { input ->
                session.openWrite("app.apk", 0, -1).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val result = PendingIntent.getBroadcast(
                ctx,
                1,
                Intent(ctx, InstallReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            session.commit(result.intentSender)
        }
        return "Installing update v$latest"
    }
}

/** Receives the installer's verdict; shows Android's confirm screen if it wants one. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    try {
                        ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        Store.log(ctx, "Update waiting: open the app and tap Check for app update")
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Store.log(ctx, "App updated")
            else -> {
                val why = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Store.log(ctx, "Update failed ($status): $why")
            }
        }
    }
}
