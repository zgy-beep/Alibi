package app.myzel394.alibi.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.staticCompositionLocalOf

object AlibiMotion {
    const val pressDurationMillis = 120
    const val stateDurationMillis = 180
    const val pageDurationMillis = 220
    const val sheetDurationMillis = 260

    fun duration(durationMillis: Int, reduceMotion: Boolean): Int =
        if (reduceMotion) 1 else durationMillis

    fun <T> page(reduceMotion: Boolean) = tween<T>(
        durationMillis = duration(pageDurationMillis, reduceMotion),
        easing = FastOutSlowInEasing,
    )

    fun <T> state(reduceMotion: Boolean) = tween<T>(
        durationMillis = duration(stateDurationMillis, reduceMotion),
        easing = FastOutSlowInEasing,
    )

    fun <T> press(reduceMotion: Boolean) = tween<T>(
        durationMillis = duration(pressDurationMillis, reduceMotion),
        easing = FastOutSlowInEasing,
    )

    fun <T> sheet(reduceMotion: Boolean) = tween<T>(
        durationMillis = duration(sheetDurationMillis, reduceMotion),
        easing = FastOutSlowInEasing,
    )
}

val LocalReduceMotion = staticCompositionLocalOf { false }
