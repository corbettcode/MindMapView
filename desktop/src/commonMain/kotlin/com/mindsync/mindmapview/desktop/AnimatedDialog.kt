package com.mindsync.mindmapview.desktop

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

private const val DIALOG_OPEN_MILLIS = 160
private const val DIALOG_CLOSE_MILLIS = 120
// How large a dialog is drawn as it starts to open, and as it is gone, next to its own size.
private const val DIALOG_CLOSED_SCALE = 0.94f

// Whether the dialog is opening or open, as its target state, or closing. Null outside an [AnimatedDialog].
private val LocalDialogVisibility = staticCompositionLocalOf<MutableTransitionState<Boolean>?> { null }

// The last value a dialog was shown for, kept for it to be drawn with while it closes.
private class LastValue<T : Any> {
    var value: T? = null
}

/**
 * Shows the dialog [content] for [value] while it is not null, and for as long as the dialog takes to close once
 * it is, with the last value it had. The dialog is animated where it uses [dialogAnimation].
 */
@Composable
fun <T : Any> AnimatedDialog(
    value: T?
    , content: @Composable (T) -> Unit
) {
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = value != null
    val last = remember { LastValue<T>() }
    if (value != null) last.value = value
    val shown = last.value
    if (shown != null && (visibility.targetState || visibility.currentState)) {
        CompositionLocalProvider(LocalDialogVisibility provides visibility) {
            content(shown)
        }
    }
}

/** Shows the dialog [content] while [isOpen], and for as long as the dialog takes to close once it is not. */
@Composable
fun AnimatedDialog(
    isOpen: Boolean
    , content: @Composable () -> Unit
) = AnimatedDialog(value = if (isOpen) Unit else null) { content() }

/**
 * Fades and grows the dialog in as it opens, and fades and shrinks it out as it closes, in an [AnimatedDialog].
 * A closing dialog takes no more clicks, so a button is not pressed twice.
 */
@Composable
fun Modifier.dialogAnimation(): Modifier {
    val visibility = LocalDialogVisibility.current ?: return this
    val transition = rememberTransition(visibility, label = "dialog")
    val progress = transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                tween(DIALOG_OPEN_MILLIS, easing = FastOutSlowInEasing)
            } else {
                tween(DIALOG_CLOSE_MILLIS, easing = FastOutLinearInEasing)
            }
        }
        , label = "dialog progress"
    ) { isOpen -> if (isOpen) 1f else 0f }
    val isClosing = !transition.targetState
    return this
        .graphicsLayer {
            alpha = progress.value
            val scale = DIALOG_CLOSED_SCALE + (1f - DIALOG_CLOSED_SCALE) * progress.value
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (isClosing) {
                Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            } else {
                Modifier
            }
        )
}
