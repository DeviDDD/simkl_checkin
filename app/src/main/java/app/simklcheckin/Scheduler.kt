package app.simklcheckin

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import java.util.Calendar
import java.util.TimeZone

/**
 * Attempts run at minute 05 and 35 of every UTC hour (00:05 UTC = 5:35 AM IST).
 * Once every account is done for the UTC day, the next attempt is 00:05 UTC tomorrow.
 */
object Scheduler {
    fun nextSlot(ctx: Context): Long {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        if (Store.allDone(ctx)) {
            c.add(Calendar.DAY_OF_YEAR, 1)
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 5)
        } else {
            val m = c.get(Calendar.MINUTE)
            when {
                m < 5 -> c.set(Calendar.MINUTE, 5)
                m < 35 -> c.set(Calendar.MINUTE, 35)
                else -> {
                    c.add(Calendar.HOUR_OF_DAY, 1)
                    c.set(Calendar.MINUTE, 5)
                }
            }
        }
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx,
            0,
            Intent(ctx, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val at = nextSlot(ctx)
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        val pending = !Store.allDone(ctx)
        // Queue the next slot first so a crash mid-run can never stop the schedule.
        Scheduler.schedule(ctx)
        if (!pending) return

        // Keep the CPU awake long enough for the check-in screen to start.
        ctx.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "simkl:run")
            .acquire(3 * 60 * 1000L)
        try {
            ctx.startActivity(
                Intent(ctx, CheckInActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Store.log(ctx, "Could not open the check-in screen: ${e.message}")
        }
    }
}

/** Alarms are lost on reboot and after an app update, so set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        Scheduler.schedule(ctx)
    }
}
