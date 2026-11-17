package com.thelightphone.dictionary.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

fun createBookmarkOutlineVector(tintColor: Color): ImageVector {
    return ImageVector.Builder(
        name = "BookmarkOutline",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(tintColor),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6f, 3f)
            horizontalLineTo(18f)
            verticalLineTo(21f)
            lineTo(12f, 16.5f)
            lineTo(6f, 21f)
            close()
        }
    }.build()
}

fun createBookmarkFilledVector(tintColor: Color): ImageVector {
    return ImageVector.Builder(
        name = "BookmarkFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(tintColor),
        ) {
            moveTo(6f, 3f)
            horizontalLineTo(18f)
            verticalLineTo(21f)
            lineTo(12f, 16.5f)
            lineTo(6f, 21f)
            close()
        }
    }.build()
}
