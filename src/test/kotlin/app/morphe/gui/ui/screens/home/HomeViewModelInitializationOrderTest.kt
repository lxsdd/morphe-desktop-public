package app.morphe.gui.ui.screens.home

import java.io.File
import app.morphe.engine.PatchedAppStore
import app.morphe.engine.network.createHttpClient
import app.morphe.gui.data.model.AppConfig
import app.morphe.gui.data.repository.ChangelogRepository
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.data.repository.UpdateCheckRepository
import androidx.lifecycle.viewModelScope
import java.awt.EventQueue
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression for the Windows 2.1.1-beta.3 startup crash:
 * StateFlow.collect can emit synchronously from viewModelScope.launch on Main.immediate.
 * Kotlin field initializers after an init block have not run at that point.
 */
class HomeViewModelInitializationOrderTest {
    private val sourcePath = "src/main/kotlin/app/morphe/gui/ui/screens/home/HomeViewModel.kt"

    private fun productionSource(): String =
        File(System.getProperty("user.dir"), sourcePath).readText().normalizeLineEndings()

    private fun String.normalizeLineEndings(): String =
        replace("\r\n", "\n").replace('\r', '\n')

    private val startupBackingFields = listOf(
        "private val updateMetadataGeneration = UpdateMetadataGenerationGuard()",
        "private var patchedRecordsByPackage:",
        "private var relevantSourceUpdates:",
        "private var incompleteSourceChecks:",
        "private var deviceRefreshGeneration:",
        "private val deviceLabelDiscovery = DeviceAppDiscoveryService()",
        "private var cachedSourcesResult:",
    )

    private fun observersStartAfterBackingFields(source: String): Boolean {
        val normalized = source.normalizeLineEndings()
        val initAt = normalized.indexOf("\n    init {\n")
        if (initAt < 0) return false
        return startupBackingFields.all { field ->
            val at = normalized.indexOf(field)
            at >= 0 && at < initAt
        }
    }

    @Test
    fun `production eager flow subscriptions start after all required fields`() {
        val source = productionSource()
        assertTrue(observersStartAfterBackingFields(source))
        // A refactor must not "fix" this check by removing the production subscribers.
        assertTrue(source.contains("patchSourceManager.activeMode.collect"))
        assertTrue(source.contains("DeviceMonitor.state"))
        assertTrue(source.contains("patchSourceManager.sourceVersion.drop(1).collect"))
        assertTrue(source.contains("loadPatchesAndSupportedApps()"))
    }

    @Test
    fun `Windows CRLF checkout preserves production field ordering check`() {
        val windowsSource = productionSource().replace("\n", "\r\n")
        assertTrue(observersStartAfterBackingFields(windowsSource))
    }

    @Test
    fun `regression rejects metadata generation guard declared after eager subscription`() {
        val source = productionSource()
        val field = startupBackingFields.first()
        val broken = source.replace(field, "").replaceFirst("\n    init {\n", "\n    init {\n        $field\n")
        assertFalse(observersStartAfterBackingFields(broken))
    }

    @Test
    fun `regression rejects device generation declared after eager subscription`() {
        val source = productionSource()
        val field = startupBackingFields.first { it.contains("deviceRefreshGeneration") }
        val broken = source.replace(field, "").replaceFirst("\n    init {\n", "\n    init {\n        $field\n")
        assertFalse(observersStartAfterBackingFields(broken))
    }

    @Test
    fun `real home view model starts eager collector on AWT without indefinite skeleton`() {
        // This exercises the production constructor and StateFlow subscription,
        // not just the source-order check. No network sources, APKs or ADB targets.
        val config = object : ConfigRepository() {
            override suspend fun loadConfig(): AppConfig = AppConfig(
                patchSource = emptyList(),
                updateChannelPreference = "OFF",
                userDidChooseUpdateChannel = true,
            )
        }
        val http = createHttpClient()
        val model = AtomicReference<HomeViewModel>()
        try {
            EventQueue.invokeAndWait {
                model.set(HomeViewModel(
                    patchSourceManager = PatchSourceManager(http, config),
                    configRepository = config,
                    updateCheckRepository = UpdateCheckRepository(config),
                    patchedAppStore = PatchedAppStore(File.createTempFile("morphe-home-init", ".json").apply { delete() }),
                    changelogRepository = ChangelogRepository(http),
                ))
            }
            runBlocking {
                withTimeout(8_000) {
                    // With the old constructor this stayed true after the
                    // NullPointerException cancelled the Main.immediate collector.
                    model.get().uiState.filter { !it.isLoadingPatches }.first()
                }
            }
        } finally {
            model.get()?.viewModelScope?.cancel()
            http.close()
        }
    }
}
