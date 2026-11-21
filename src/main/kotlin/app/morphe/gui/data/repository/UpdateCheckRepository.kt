/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.engine.AppUpdateCheck
import app.morphe.engine.ReleaseChannel
import app.morphe.engine.UpdateChecker
import app.morphe.engine.UpdateInfo
import app.morphe.engine.util.Logger
import app.morphe.gui.data.model.UpdateChannelPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Singleton wrapper around [UpdateChecker] so the network probe runs at most
 * once per (app session, channel) combination. Both home view models share the
 * successful result. Network/metadata failures are intentionally retryable.
 * Settings should call [clearCache] after a channel preference change.
 */
class UpdateCheckRepository(
    private val configRepository: ConfigRepository,
) {

    private val mutex = Mutex()
    private val checked = SuccessfulCustomUpdateCache()

    /**
     * Failed checks are not cached: a transient offline/HTTP/invalid-payload
     * failure must not permanently suppress real published Custom updates.
     * OFF never causes network activity or an "up to date" assertion.
     */
    suspend fun checkUpdate(): AppUpdateCheck? = withContext(Dispatchers.IO) {
        mutex.withLock {
            checked.get()?.let { return@withContext it }

            val currentVersion = UpdateChecker.currentVersion()
            val pref = if (currentVersion != null) {
                configRepository.getOrInitUpdateChannelPreference(currentVersion)
            } else {
                UpdateChannelPreference.STABLE
            }
            if (pref == UpdateChannelPreference.OFF) return@withContext null

            val channel = when (pref) {
                UpdateChannelPreference.STABLE -> ReleaseChannel.STABLE
                UpdateChannelPreference.DEV -> ReleaseChannel.DEV
                UpdateChannelPreference.OFF -> error("unreachable, short-circuited above")
            }
            val outcome = try {
                UpdateChecker.checkStatus(channel)
            } catch (e: Exception) {
                Logger.error("Custom release update check failed", e)
                AppUpdateCheck.Failed
            }
            checked.remember(outcome)
            outcome
        }
    }

    /** Compatibility API for home banners and CLI callers. */
    suspend fun getUpdateInfo(): UpdateInfo? =
        (checkUpdate() as? AppUpdateCheck.Available)?.info

    suspend fun clearCache() {
        mutex.withLock { checked.clear() }
    }
}


/** Pure cache contract: only successfully checked Custom release evidence is durable. */
internal class SuccessfulCustomUpdateCache {
    private var result: AppUpdateCheck? = null

    fun get(): AppUpdateCheck? = result

    fun remember(outcome: AppUpdateCheck) {
        if (outcome != AppUpdateCheck.Failed) result = outcome
    }

    fun clear() {
        result = null
    }
}
