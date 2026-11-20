package dev.savar.regista

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager

/** The three reminder times. Each has its own notification channel so sound/vibration can differ. */
enum class Lead(val ms: Long, val key: String, val channel: String, val channelName: String) {
    DAY(24 * 3_600_000L, "1d", "day", "A day before"),
    HOUR(3_600_000L, "1h", "hour", "An hour before"),
    FIVE(5 * 60_000L, "5m", "five", "5 minutes before");

    fun enabled(s: Settings) = when (this) {
        DAY -> s.leadDay
        HOUR -> s.leadHour
        FIVE -> s.leadFive
    }
}

/**
 * Schedules one alarm per (match, lead) for everything in Up next. Exact alarms when the user allows them, so the
 * 5-minute reminder is on time even in Doze; otherwise inexact (Android may deliver them a few minutes late).
 */
object Reminders {
    const val ACTION_FIRE = "dev.savar.regista.FIRE"
    const val ACTION_MUTE = "dev.savar.regista.MUTE"
    const val EXTRA_MATCH = "match"
    const val EXTRA_LEAD = "lead"
    const val EXTRA_KICKOFF = "kickoff"
    private const val MAX_ALARMS = 300 // Android caps an app at 500 pending alarms.

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    fun notificationsAllowed(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return ctx.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() != false
    }

    fun ignoringBatteryOptimizations(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannelGroup(NotificationChannelGroup("reminders", "Match reminders"))
        Lead.entries.forEach { lead ->
            val importance = if (lead == Lead.DAY) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_HIGH
            nm.createNotificationChannel(
                NotificationChannel(lead.channel, lead.channelName, importance).apply {
                    group = "reminders"
                    description = when (lead) {
                        Lead.DAY -> "The day before kick-off"
                        Lead.HOUR -> "An hour before kick-off"
                        Lead.FIVE -> "Just before kick-off"
                    }
                },
            )
        }
    }

    private fun key(matchId: String, lead: Lead) = "$matchId|${lead.key}"
    private fun notifId(matchId: String, lead: Lead) = key(matchId, lead).hashCode()

    private fun alarmIntent(ctx: Context, matchId: String, lead: Lead, kickoff: Long): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .setData(Uri.parse("regista://reminder/${Uri.encode(matchId)}/${lead.key}"))
            .putExtra(EXTRA_MATCH, matchId)
            .putExtra(EXTRA_LEAD, lead.name)
            .putExtra(EXTRA_KICKOFF, kickoff)
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** What would be scheduled right now: (match, lead, trigger time). Public for the Up next detail sheet. */
    fun plan(now: Long = System.currentTimeMillis()): List<Triple<Match, Lead, Long>> {
        val s = Store.settings
        return Store.upNext(now)
            .filter { (m, _) -> m.status == Status.SCHEDULED && !m.timeTbd && m.id !in Store.muted }
            .flatMap { (m, _) -> Lead.entries.filter { it.enabled(s) }.map { Triple(m, it, m.kickoff - it.ms) } }
            .filter { it.third > now + 5_000 }
            .sortedBy { it.third }
            .take(MAX_ALARMS)
    }

    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "reminders").apply { isDaemon = true } }
    private val pending = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Coalesced background reschedule: a burst of setting changes costs one pass. */
    fun rescheduleAsync(ctx: Context) {
        val app = ctx.applicationContext
        if (!pending.compareAndSet(false, true)) return
        worker.execute {
            pending.set(false)
            runCatching { reschedule(app) }
        }
    }

    /**
     * Brings alarms in line with the plan. Each alarm is an IPC to system_server, so only alarms that are new or
     * whose time changed are (re)set, and only dropped ones are cancelled. Stored as "match|lead@time".
     */
    @Synchronized
    fun reschedule(ctx: Context) {
        Store.load(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val prefs = ctx.getSharedPreferences("alarms", Context.MODE_PRIVATE)
        val exact = canExact(ctx)
        // A change in exact-alarm permission means every alarm must be set again with the other API.
        val sameMode = prefs.getBoolean("exact", exact) == exact
        val previous: Map<String, Long> = prefs.getStringSet("set", emptySet())!!.mapNotNull { s ->
            val at = s.substringAfterLast('@').toLongOrNull() ?: return@mapNotNull null
            s.substringBeforeLast('@') to at
        }.toMap()
        val wanted = LinkedHashMap<String, Triple<Match, Lead, Long>>()
        plan().forEach { wanted[key(it.first.id, it.second)] = it }

        for ((k, item) in wanted) {
            val (m, lead, at) = item
            if (sameMode && previous[k] == at) continue
            val pi = alarmIntent(ctx, m.id, lead, m.kickoff)
            try {
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
        for (k in previous.keys - wanted.keys) {
            val id = k.substringBeforeLast('|')
            val lead = Lead.entries.firstOrNull { it.key == k.substringAfterLast('|') } ?: continue
            am.cancel(alarmIntent(ctx, id, lead, 0))
        }
        prefs.edit()
            .putStringSet("set", wanted.mapTo(HashSet()) { (k, v) -> "$k@${v.third}" })
            .putBoolean("exact", exact)
            .remove("keys")
            .apply()
    }

    /** Called by the alarm. Re-checks the match is still on, still relevant and still at that time. */
    fun fire(ctx: Context, matchId: String, lead: Lead, kickoff: Long) {
        Store.load(ctx)
        val entry = Store.upNext().firstOrNull { it.first.id == matchId } ?: return
        val m = entry.first
        if (m.status != Status.SCHEDULED || m.kickoff != kickoff || !lead.enabled(Store.settings) || m.id in Store.muted) return
        if (!notificationsAllowed(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(notifId(m.id, lead), build(ctx, m, entry.second.firstOrNull(), lead))
        if (lead == Lead.DAY) summarize(ctx, nm)
    }

    fun mute(ctx: Context, matchId: String) {
        Store.load(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        Lead.entries.forEach { nm?.cancel(notifId(matchId, it)) }
        Store.setMuted(ctx, matchId, true)
    }

    private fun build(ctx: Context, m: Match, reason: Reason?, lead: Lead): Notification {
        val title = "${m.home.short} vs ${m.away.short}"
        val text = when (lead) {
            Lead.DAY -> "${Fmt.dayWord(ctx, m.kickoff).replaceFirstChar { it.uppercase() }} at ${Fmt.time(ctx, m.kickoff)} · ${m.compName}"
            Lead.HOUR -> "Kicks off at ${Fmt.time(ctx, m.kickoff)} · ${m.compName}"
            Lead.FIVE -> "Kicks off in 5 minutes · ${m.compName}"
        }
        val open = PendingIntent.getActivity(
            ctx, m.id.hashCode(),
            Intent(ctx, MainActivity::class.java).putExtra(EXTRA_MATCH, m.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val mute = PendingIntent.getBroadcast(
            ctx, m.id.hashCode(),
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_MUTE)
                .setData(Uri.parse("regista://mute/${Uri.encode(m.id)}")).putExtra(EXTRA_MATCH, m.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val sub = listOfNotNull(reason?.label, m.stage.takeIf { it.isNotBlank() && reason !is Reason.Knockout }).joinToString(" · ")
        return Notification.Builder(ctx, lead.channel)
            .setSmallIcon(R.drawable.ic_stat_regista)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(sub.ifBlank { null })
            .setStyle(Notification.BigTextStyle().bigText(listOfNotNull(text, m.venue.takeIf { it.isNotBlank() }).joinToString("\n")))
            .setWhen(m.kickoff)
            .setShowWhen(true)
            .apply { if (lead != Lead.DAY) setUsesChronometer(true).setChronometerCountDown(true) }
            .setCategory(Notification.CATEGORY_EVENT)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter((m.kickoff + 2 * 3_600_000L - System.currentTimeMillis()).coerceAtLeast(60_000L))
            .setGroup("lead_${lead.key}")
            .addAction(Notification.Action.Builder(null, "Mute this match", mute).build())
            .build()
    }

    /** Several day-before reminders at once (a busy Saturday) collapse under one summary. */
    private fun summarize(ctx: Context, nm: NotificationManager) {
        val active = nm.activeNotifications.filter {
            it.notification.group == "lead_${Lead.DAY.key}" && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
        }
        if (active.size < 2) return
        val style = Notification.InboxStyle()
        active.forEach { style.addLine(it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)) }
        val summary = Notification.Builder(ctx, Lead.DAY.channel)
            .setSmallIcon(R.drawable.ic_stat_regista)
            .setContentTitle("${active.size} matches tomorrow")
            .setStyle(style.setSummaryText("Tomorrow"))
            .setGroup("lead_${Lead.DAY.key}")
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        nm.notify("summary_day".hashCode(), summary)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Reminders.EXTRA_MATCH) ?: return
        when (intent.action) {
            Reminders.ACTION_FIRE -> {
                val lead = Lead.entries.firstOrNull { it.name == intent.getStringExtra(Reminders.EXTRA_LEAD) } ?: return
                Reminders.fire(context, id, lead, intent.getLongExtra(Reminders.EXTRA_KICKOFF, 0))
            }
            Reminders.ACTION_MUTE -> Reminders.mute(context, id)
        }
    }
}

/** Alarms don't survive reboots or app updates; time zone changes shift them. Rebuild on all of these. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                Reminders.createChannels(app)
                Reminders.reschedule(app)
                Sync.schedulePeriodic(app)
            } finally {
                result.finish()
            }
        }.start()
    }
}
