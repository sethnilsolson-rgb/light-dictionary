package com.thelightphone.dictionary.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

fun Modifier.swipeToBack(
    sideOnly: Boolean = false,
    edgeThresholdPx: Float = 300f,
    swipeDistanceThresholdPx: Float = 70f,
    onBack: () -> Unit,
): Modifier = pointerInput(Unit) {
    var startX = 0f
    var horizontalDragAccumulator = 0f

    detectHorizontalDragGestures(
        onDragStart = { offset ->
            startX = offset.x
            horizontalDragAccumulator = 0f
        },
        onDragEnd = {
            val startedAtSide = !sideOnly || startX <= edgeThresholdPx
            if (startedAtSide && horizontalDragAccumulator >= swipeDistanceThresholdPx) {
                onBack()
            }
            horizontalDragAccumulator = 0f
        },
        onDragCancel = {
            horizontalDragAccumulator = 0f
        },
        onHorizontalDrag = { _, dragAmount ->
            horizontalDragAccumulator += dragAmount
        }
    )
}

fun Modifier.horizontalWordSwipe(
    edgeThresholdPx: Float = 250f,
    swipeThresholdPx: Float = 80f,
    onPrevWord: (() -> Unit)?,
    onNextWord: (() -> Unit)?,
    onBack: () -> Unit,
): Modifier = pointerInput(Unit) {
    var startX = 0f
    var horizontalDragAccumulator = 0f

    detectHorizontalDragGestures(
        onDragStart = { offset ->
            startX = offset.x
            horizontalDragAccumulator = 0f
        },
        onDragEnd = {
            if (horizontalDragAccumulator >= swipeThresholdPx) {
                // Swiped rightward
                if (startX <= edgeThresholdPx) {
                    // Swiped from the left edge: go back in navigation
                    onBack()
                } else {
                    // Swiped from middle: go to previous word alphabetically
                    if (onPrevWord != null) {
                        onPrevWord()
                    } else {
                        onBack()
                    }
                }
            } else if (horizontalDragAccumulator <= -swipeThresholdPx) {
                // Swiped leftward: go to next word alphabetically
                onNextWord?.invoke()
            }
            horizontalDragAccumulator = 0f
        },
        onDragCancel = {
            horizontalDragAccumulator = 0f
        },
        onHorizontalDrag = { _, dragAmount ->
            horizontalDragAccumulator += dragAmount
        }
    )
}
