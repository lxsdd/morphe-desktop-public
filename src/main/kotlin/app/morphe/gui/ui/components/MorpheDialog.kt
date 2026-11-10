/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

internal data class HostedMorpheDialog(
    val id: Any,
    val onDismiss: () -> Unit,
    val content: @Composable () -> Unit,
    val dismissOnOutside: Boolean = false,
)

@Stable
class MorpheDialogHostState {
    private val dialogs = mutableStateListOf<HostedMorpheDialog>()
    internal val current: HostedMorpheDialog? get() = dialogs.lastOrNull()
    internal fun show(dialog: HostedMorpheDialog) {
        dialogs.removeAll { it.id === dialog.id }
        dialogs += dialog
    }
    internal fun hide(id: Any) {
        dialogs.removeAll { it.id === id }
    }
}

val LocalMorpheDialogHostState = staticCompositionLocalOf<MorpheDialogHostState?> { null }

/** Renders Morphe dialogs inside the existing window, avoiding a second native Skia surface. */
@Composable
fun MorpheDialogHost(state: MorpheDialogHostState) {
    val dialog = state.current ?: return
    val focusRequester = remember(dialog.id) { FocusRequester() }
    val dismissInteraction = remember(dialog.id) { MutableInteractionSource() }
    val consumeInteraction = remember(dialog.id) { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) {
                    dialog.onDismiss()
                    true
                } else false
            }
            .clickable(interactionSource = dismissInteraction, indication = null,
                onClick = { if (dialog.dismissOnOutside) dialog.onDismiss() }),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.clickable(
                interactionSource = consumeInteraction,
                indication = null,
                onClick = {},
            ),
        ) { dialog.content() }
    }
    LaunchedEffect(dialog.id) { focusRequester.requestFocus() }
}

@Composable
private fun MorpheDialogContainer(
    onDismiss: () -> Unit,
    dismissOnOutside: Boolean = false,
    content: @Composable () -> Unit,
) {
    val hostState = LocalMorpheDialogHostState.current
    if (hostState == null) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = dismissOnOutside),
            content = content,
        )
        return
    }
    val id = remember { Any() }
    val latestDismiss = rememberUpdatedState(onDismiss)
    val latestContent = rememberUpdatedState(content)
    val hosted = remember(id, hostState) {
        HostedMorpheDialog(
            id = id,
            onDismiss = { latestDismiss.value() },
            content = { latestContent.value() },
            dismissOnOutside = dismissOnOutside,
        )
    }
    DisposableEffect(hostState, hosted) {
        hostState.show(hosted)
        onDispose { hostState.hide(id) }
    }
}

/** Common visible close control for Morphe dialogs; Escape uses the same dismiss callback. */
@Composable
fun MorpheDialogTitleBar(
    onDismiss: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) { title() }
        actions()
        MorpheIconButton(
            icon = MorpheIcons.Close,
            contentDescription = stringResource(Res.string.close),
            enabled = dismissEnabled,
            onClick = onDismiss,
            variant = MorpheIconButtonVariant.GHOST,
            size = MorpheIconButtonSize.DEFAULT,
        )
    }
}

/** Base dialog surface container providing shape, elevation, and content padding. */
@Composable
fun MorpheDialogSurface(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    contentPadding: PaddingValues = MorpheTheme.dimens.dialogPadding,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MorpheTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Column(
            modifier = contentModifier.padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

val MorpheDialogMinWidth = 280.dp
val MorpheDialogMaxWidth = 560.dp

@Composable
fun MorpheAlertDialog(
    onDismiss: () -> Unit,
    confirmButton: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable RowScope.() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    minWidth: Dp = MorpheDialogMinWidth,
    maxWidth: Dp = MorpheDialogMaxWidth,
    dismissOnOutside: Boolean = false,
) {
    MorpheDialogContainer(onDismiss = onDismiss, dismissOnOutside = dismissOnOutside) {
        MorpheDialogSurface(
            modifier = modifier.widthIn(min = minWidth, max = maxWidth),
            horizontalAlignment = horizontalAlignment,
        ) {
            MorpheDialogTitleBar(onDismiss = onDismiss, title = { title?.invoke() })
            icon?.invoke()
            text?.invoke()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dismissButton?.invoke(this)
                confirmButton(this)
            }
        }
    }
}

/** Modal dialog card with standard title and surface styling. */
@Composable
fun MorpheDialogCard(
    onDismiss: () -> Unit,
    title: String,
    maxWidth: Dp = 440.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    MorpheDialogContainer(onDismiss = onDismiss) {
        MorpheDialogSurface(modifier = Modifier.widthIn(max = maxWidth)) {
            MorpheDialogTitleBar(onDismiss = onDismiss, title = {
                Text(text = title, style = MorpheTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            })
            content()
        }
    }
}

/** Body paragraph for a [MorpheDialogCard]. */
@Composable
fun MorpheDialogText(text: String) {
    Text(
        text = text,
        style = MorpheTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}
