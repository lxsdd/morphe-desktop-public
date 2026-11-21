/*
 * Morphe Desktop Custom — production-wired source resolution progress.
 * Built on Morphe Desktop 2.3.0 source-progress concepts; keeps Custom guards.
 */
package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.util.EnabledSourcesLoader
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

enum class SourceUpdateStatus { Updating, Success }

/**
 * A progress count reports SOURCE RESOLUTION, never a confirmed app update.
 * Failure/partial success are reported by the existing source failure UI.
 */
data class SourceUpdateBannerState(
    val visible: Boolean = false,
    val status: SourceUpdateStatus = SourceUpdateStatus.Updating,
    val completed: Int = 0,
    val total: Int = 0,
    val activeSourceName: String? = null,
    val downloadFraction: Float? = null,
) {
    fun progress(event: EnabledSourcesLoader.SourceLoadProgress): SourceUpdateBannerState {
        if (!visible || status != SourceUpdateStatus.Updating) return this
        val capped = maxOf(completed, event.completed.coerceIn(0, total))
        return copy(
            completed = capped,
            activeSourceName = event.activeSource?.name ?: if (capped < total) activeSourceName else null,
            downloadFraction = event.downloadFraction?.takeIf { it.isFinite() && it > 0f && it < 1f },
        )
    }

    /** Never report full success for partial, failed, or empty source loads. */
    fun finished(usableSources: Int, failedSources: Int): SourceUpdateBannerState =
        if (visible && status == SourceUpdateStatus.Updating &&
            usableSources == total && failedSources == 0 && total > 0
        ) {
            copy(status = SourceUpdateStatus.Success, completed = total,
                activeSourceName = null, downloadFraction = null)
        } else {
            SourceUpdateBannerState()
        }
}

@Composable
fun SourceUpdateBanner(
    state: SourceUpdateBannerState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return
    MorpheBanner(
        modifier = modifier,
        tone = MorpheBannerTone.Info,
        icon = if (state.status == SourceUpdateStatus.Success) MorpheIcons.Check else null,
    ) {
        if (state.status == SourceUpdateStatus.Updating) {
            val resolved = state.downloadFraction
                ?: state.total.takeIf { it > 0 }?.let { state.completed.toFloat() / it }
            if (resolved != null) {
                CircularProgressIndicator(
                    progress = { resolved.coerceIn(0f, 1f) },
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        val message = when (state.status) {
            SourceUpdateStatus.Updating -> {
                val counter = if (state.total > 0) {
                    stringResource(Res.string.custom_source_progress_count, state.completed, state.total)
                } else ""
                val source = state.activeSourceName?.takeIf { it.isNotBlank() }
                listOfNotNull(
                    stringResource(Res.string.custom_source_loading_title),
                    counter.takeIf { it.isNotBlank() },
                    source,
                ).joinToString(" · ")
            }
            SourceUpdateStatus.Success -> stringResource(Res.string.custom_source_loaded_title)
        }
        MorpheBannerText(text = message, modifier = Modifier.weight(1f), emphasis = true)
        if (state.status == SourceUpdateStatus.Success) {
            MorpheBannerDismiss(onClick = onDismiss)
        }
    }
}
