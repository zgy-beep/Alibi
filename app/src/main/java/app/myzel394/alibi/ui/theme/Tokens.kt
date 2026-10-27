package app.myzel394.alibi.ui.theme

import android.graphics.Color
import androidx.compose.ui.unit.dp

object AlibiLayout {
    val pagePadding = 20.dp
    val widePagePadding = 24.dp
    val sectionGap = 24.dp
    val itemGap = 12.dp
    val actionCardMinHeight = 96.dp
    val minimumTouchTarget = 48.dp
}

object AlibiOverlayStyle {
    const val SHUTTER_SIZE_DP = 56
    const val ICON_TEXT_SIZE_SP = 24f
    const val PANEL_CORNER_DP = 24
    const val PANEL_WIDTH_DP = 176
    const val ACTION_CORNER_DP = 16
    const val STATE_ANIMATION_MILLIS = 180L
    const val ZOOM_STEP_FACTOR = 1.25f
    const val ZOOM_VALUE_TEXT_SIZE_SP = 16f
    const val ZOOM_SEEK_BAR_MAX = 1000

    const val STARTING_COLOR = 0xFF50575D.toInt()
    const val READY_COLOR = 0xFF176B5B.toInt()
    const val BUSY_COLOR = 0xFF315A73.toInt()
    const val ERROR_COLOR = 0xFFB3261E.toInt()
    const val PANEL_COLOR = 0xFF183A4D.toInt()
    const val ACTION_COLOR = 0xFF2C414C.toInt()
}
