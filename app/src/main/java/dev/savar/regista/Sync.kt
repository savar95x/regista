package dev.savar.regista

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Fetches the next two weeks of fixtures, then reschedules every reminder. Runs every 6 hours (JobScheduler,
 * when online), when the app opens if the data is older than an hour, and on pull-to-refresh.
 */
object Sync {
    private const val JOB_ID = 1001
    const val WINDOW_DAYS = 14L
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun schedulePeriodic(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        if (js.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, SyncJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(6 * 3_600_000L, 60 * 60_000L)
            .setPersisted(true)
            .build()
        runCatching { js.schedule(job) }
    }

    /** Fire-and-forget sync if the cache is older than [maxAgeMs]. */
    fun syncIfStale(ctx: Context, maxAgeMs: Long = 3_600_000L) {
        Store.load(ctx)
        if (System.currentTimeMillis() - Store.sync.at < maxAgeMs && Store.matches.isNotEmpty()) return
        scope.launch { run(ctx) }
    }

    fun syncNow(ctx: Context): Job = scope.launch { run(ctx) }

    /** Returns null on success, or a short error message. Never throws. */
    suspend fun run(ctx: Context): String? = lock.withLock {
        Store.load(ctx)
        setSyncing(true)
        try {
            val s = Store.settings
            val today = LocalDate.now()
            val from = today.minusDays(1)
            val to = today.plusDays(WINDOW_DAYS)

            var error: String? = null
            var result: Pair<List<Match>, String>? = null
            if (s.source != SourcePref.FOOTBALL_DATA) {
                result = runCatching { Espn.matches(from, to) to "ESPN" }.onFailure { error = it.message ?: "ESPN failed" }.getOrNull()
            }
            if (result == null && s.source != SourcePref.ESPN && s.fdToken.isNotBlank()) {
                result = runCatching { FootballData.matches(s.fdToken, from, to) to "football-data.org" }
                    .onFailure { e -> error = describe(e, "football-data.org") }.getOrNull()
            } else if (result == null && s.source == SourcePref.FOOTBALL_DATA) {
                error = "Add a football-data.org token in Settings"
            }

            // Team list for search and crests: weekly, always from ESPN (whatever the fixtures source), since
            // football-data.org crests are mostly SVG and it has no national-team list.
            if (Store.catalog.isEmpty() || System.currentTimeMillis() - Store.catalogAt > 7 * 86_400_000L) {
                runCatching { Espn.teams() }.getOrNull()?.takeIf { it.size > 50 }?.let { teams ->
                    withContext(Dispatchers.Main) { Store.saveCatalog(teams) }
                }
            }

            if (result == null) {
                val msg = error ?: "Couldn't update fixtures"
                withContext(Dispatchers.Main) { Store.recordSync(Store.sync.copy(error = msg)) }
                return msg
            }
            val (matches, source) = result
            withContext(Dispatchers.Main) { Store.saveMatches(matches, source) }

            val now = System.currentTimeMillis()
            // Club Elo: daily, only when the top-N rule is on.
            if (s.eloTopN > 0 && (Store.eloRanks.isEmpty() || now - Store.eloAt > 86_400_000L)) {
                runCatching { Elo.fetch() }.getOrNull()?.let { ranks -> withContext(Dispatchers.Main) { Store.saveElo(ranks) } }
            }
            // National teams: weekly is plenty (they play a few times a year). Keeps the snapshot on failure.
            if (s.nationTopN > 0 && now - Store.nationAt > 7 * 86_400_000L) {
                runCatching { Elo.fetchNations() }.getOrNull()?.let { ranks -> withContext(Dispatchers.Main) { Store.saveNationElo(ranks) } }
            }
            Reminders.reschedule(ctx) // already on a background thread
            null
        } finally {
            setSyncing(false)
        }
    }

    private suspend fun setSyncing(on: Boolean) = withContext(Dispatchers.Main) { Store.syncing = on }

    private fun describe(e: Throwable, who: String) = when ((e as? HttpStatusException)?.code) {
        400, 401, 403 -> "$who rejected the token"
        429 -> "$who rate limit, try again in a minute"
        else -> e.message ?: "$who failed"
    }
}

class SyncJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            val err = Sync.run(applicationContext)
            jobFinished(params, err != null && Store.matches.isEmpty())
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
