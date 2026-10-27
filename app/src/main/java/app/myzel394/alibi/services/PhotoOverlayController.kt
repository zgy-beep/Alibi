package app.myzel394.alibi.services

import android.content.Context
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import app.myzel394.alibi.R
import app.myzel394.alibi.ui.theme.AlibiOverlayStyle
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

class PhotoOverlayController(
    private val context: Context,
    private val onCapture: () -> Unit,
    private val onOpenApp: () -> Unit,
    private val onStop: () -> Unit,
    private val onZoomBy: (Float) -> Unit,
    private val onZoomTo: (Float) -> Unit,
) {
    enum class Status {
        STARTING,
        READY,
        BUSY,
        ERROR,
    }

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val preferences =
        context.getSharedPreferences(OVERLAY_PREFERENCES, Context.MODE_PRIVATE)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val buttonSize = dp(AlibiOverlayStyle.SHUTTER_SIZE_DP)
    private var root: FrameLayout? = null
    private var shutter: TextView? = null
    private var actionPanel: LinearLayout? = null
    private var zoomSection: LinearLayout? = null
    private var zoomUnavailable: TextView? = null
    private var zoomValue: TextView? = null
    private var zoomSeekBar: SeekBar? = null
    private var zoomOutButton: Button? = null
    private var zoomInButton: Button? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var currentStatus: Status = Status.STARTING
    private var statusInitialized = false
    private var requestedOpacity = 0.9f
    private val statusBackgrounds = mutableMapOf<Status, GradientDrawable>()

    // Cached camera zoom state, kept even while the overlay is not attached so
    // that updateZoom() can arrive before show() and render once the views exist.
    private var zoomRatio = 1f
    private var zoomMinRatio = 1f
    private var zoomMaxRatio = 1f
    private var zoomAvailable = false
    private var zoomDragging = false
    private var renderedZoomAvailable: Boolean? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var downWindowX = 0
    private var downWindowY = 0
    private var moved = false
    private var longPressed = false
    private val longPress = Runnable {
        if (!moved) {
            longPressed = true
            toggleActions()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(status: Status = currentStatus): Boolean {
        if (!Settings.canDrawOverlays(context)) {
            return false
        }

        if (root != null) {
            if (currentStatus != status) {
                updateStatus(status)
            }
            renderZoomControls()
            return true
        }

        val container = FrameLayout(context)
        val button = TextView(context).apply {
            text = "\u25CF"
            textSize = AlibiOverlayStyle.ICON_TEXT_SIZE_SP
            setTextColor(Color.WHITE)
            contentDescription = context.getString(R.string.photo_overlay_take_photo)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            background = statusBackground(status)
            setOnClickListener { onCapture() }
            setOnTouchListener { _, event -> handleShutterTouch(event) }
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfo,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = android.widget.Button::class.java.name
                    info.isClickable = true
                    info.addAction(
                        AccessibilityNodeInfo.AccessibilityAction(
                            ACTION_OPEN_APP,
                            context.getString(R.string.photo_overlay_open_app),
                        ),
                    )
                    info.addAction(
                        AccessibilityNodeInfo.AccessibilityAction(
                            ACTION_STOP_SESSION,
                            context.getString(R.string.photo_overlay_stop),
                        ),
                    )
                    if (zoomAvailable) {
                        info.addAction(
                            AccessibilityNodeInfo.AccessibilityAction(
                                ACTION_ZOOM_IN,
                                context.getString(R.string.photo_overlay_zoom_in),
                            ),
                        )
                        info.addAction(
                            AccessibilityNodeInfo.AccessibilityAction(
                                ACTION_ZOOM_OUT,
                                context.getString(R.string.photo_overlay_zoom_out),
                            ),
                        )
                        info.addAction(
                            AccessibilityNodeInfo.AccessibilityAction(
                                ACTION_ZOOM_RESET,
                                context.getString(R.string.photo_overlay_zoom_reset),
                            ),
                        )
                    }
                }

                override fun performAccessibilityAction(
                    host: View,
                    action: Int,
                    arguments: android.os.Bundle?,
                ): Boolean {
                    return when (action) {
                        ACTION_OPEN_APP -> {
                            onOpenApp()
                            true
                        }

                        ACTION_STOP_SESSION -> {
                            onStop()
                            true
                        }

                        ACTION_ZOOM_IN -> {
                            if (!zoomAvailable) {
                                return false
                            }
                            onZoomBy(AlibiOverlayStyle.ZOOM_STEP_FACTOR)
                            true
                        }

                        ACTION_ZOOM_OUT -> {
                            if (!zoomAvailable) {
                                return false
                            }
                            onZoomBy(1f / AlibiOverlayStyle.ZOOM_STEP_FACTOR)
                            true
                        }

                        ACTION_ZOOM_RESET -> {
                            if (!zoomAvailable) {
                                return false
                            }
                            onZoomTo(zoomMinRatio)
                            true
                        }

                        else -> super.performAccessibilityAction(host, action, arguments)
                    }
                }
            }
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = roundedBackground(
                AlibiOverlayStyle.PANEL_COLOR,
                dp(AlibiOverlayStyle.PANEL_CORNER_DP).toFloat(),
            )
        }
        panel.addView(buildZoomControls())
        panel.addView(buildZoomUnavailableHint())
        panel.addView(actionButton(R.string.photo_overlay_open_app, onOpenApp))
        panel.addView(actionButton(R.string.photo_overlay_stop, onStop))
        panel.addView(actionButton(R.string.photo_overlay_close, ::hideActions))
        container.addView(
            button,
            FrameLayout.LayoutParams(buttonSize, buttonSize),
        )
        container.addView(
            panel,
            FrameLayout.LayoutParams(
                dp(AlibiOverlayStyle.PANEL_WIDTH_DP),
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.TOP
            },
        )

        val display = context.resources.displayMetrics
        val params = WindowManager.LayoutParams(
            buttonSize,
            buttonSize,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.getInt(PREF_X, display.widthPixels - buttonSize - dp(16))
            y = preferences.getInt(PREF_Y, display.heightPixels / 3)
            clampToScreen(this, persist = false)
        }

        root = container
        shutter = button
        actionPanel = panel
        layoutParams = params
        container.alpha = requestedOpacity

        val added = runCatching {
            windowManager.addView(container, params)
        }.onFailure {
            root = null
            shutter = null
            actionPanel = null
            zoomSection = null
            zoomUnavailable = null
            zoomValue = null
            zoomSeekBar = null
            zoomOutButton = null
            zoomInButton = null
            renderedZoomAvailable = null
            layoutParams = null
        }.isSuccess

        updateStatus(status)
        renderZoomControls()
        return added
    }

    fun setOpacity(opacity: Float) {
        requestedOpacity = opacity.coerceIn(0.45f, 1f)
        root?.alpha = requestedOpacity
    }

    /** Keeps the floating zoom controls in sync with the camera zoom state. */
    fun updateZoom(ratio: Float, minRatio: Float, maxRatio: Float) {
        val safeMin = if (minRatio.isFinite() && minRatio > 0f) {
            minRatio
        } else {
            DEFAULT_ZOOM_RATIO
        }
        val safeMax = if (maxRatio.isFinite() && maxRatio > 0f) {
            maxRatio
        } else {
            safeMin
        }
        val low = min(safeMin, safeMax)
        val high = max(safeMin, safeMax)

        zoomMinRatio = low
        zoomMaxRatio = high
        zoomAvailable = high - low >= ZOOM_AVAILABLE_MIN_RANGE
        zoomRatio = if (ratio.isFinite()) ratio.coerceIn(low, high) else low

        // The views may not be attached yet (updateZoom() can be called before
        // show()); the cached values above are rendered once they exist.
        renderZoomControls()
    }

    fun hide() {
        handler.removeCallbacks(longPress)
        val view = root ?: return
        runCatching { windowManager.removeView(view) }
        root = null
        shutter = null
        actionPanel = null
        zoomSection = null
        zoomUnavailable = null
        zoomValue = null
        zoomSeekBar = null
        zoomOutButton = null
        zoomInButton = null
        zoomDragging = false
        renderedZoomAvailable = null
        layoutParams = null
        currentStatus = Status.STARTING
        statusInitialized = false
    }

    fun updateStatus(status: Status) {
        if (currentStatus == status && statusInitialized) {
            return
        }
        currentStatus = status
        val button = shutter ?: return
        button.background = statusBackground(status)
        button.alpha = if (status == Status.BUSY || status == Status.STARTING) 0.65f else 1f
        val targetScale = if (status == Status.BUSY) 0.92f else 1f
        if (statusInitialized) {
            button.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(animationDuration(AlibiOverlayStyle.STATE_ANIMATION_MILLIS))
                .start()
        } else {
            button.scaleX = targetScale
            button.scaleY = targetScale
        }
        statusInitialized = true
        button.contentDescription = context.getString(
            when (status) {
                Status.STARTING -> R.string.photo_overlay_starting
                Status.READY -> R.string.photo_overlay_take_photo
                Status.BUSY -> R.string.photo_overlay_busy
                Status.ERROR -> R.string.photo_overlay_error
            }
        )
    }

    private fun buildZoomControls(): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val outButton = zoomStepButton(
            label = "\u2212",
            descriptionRes = R.string.photo_overlay_zoom_out,
        ) {
            onZoomBy(1f / AlibiOverlayStyle.ZOOM_STEP_FACTOR)
        }
        val inButton = zoomStepButton(
            label = "+",
            descriptionRes = R.string.photo_overlay_zoom_in,
        ) {
            onZoomBy(AlibiOverlayStyle.ZOOM_STEP_FACTOR)
        }
        val valueView = TextView(context).apply {
            textSize = AlibiOverlayStyle.ZOOM_VALUE_TEXT_SIZE_SP
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            minHeight = dp(48)
            minimumHeight = dp(48)
            isClickable = true
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener { onZoomTo(zoomMinRatio) }
        }
        row.addView(outButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        row.addView(
            valueView,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(inButton, LinearLayout.LayoutParams(dp(48), dp(48)))

        val seekBar = SeekBar(context).apply {
            max = AlibiOverlayStyle.ZOOM_SEEK_BAR_MAX
            contentDescription = context.getString(R.string.photo_overlay_zoom_title)
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean,
                    ) {
                        // Progress updates coming from updateZoom() must never
                        // feed back into the camera.
                        if (!fromUser || !zoomAvailable) {
                            return
                        }
                        val ratio = zoomRatioForProgress(progress)
                        zoomRatio = ratio
                        updateZoomValueLabel()
                        updateZoomStepButtons()
                        onZoomTo(ratio)
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        zoomDragging = true
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        zoomDragging = false
                        renderZoomControls()
                    }
                },
            )
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (zoomAvailable) View.VISIBLE else View.GONE
            addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                seekBar,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            zoomSection = this
            zoomValue = valueView
            zoomSeekBar = seekBar
            zoomOutButton = outButton
            zoomInButton = inButton
        }
    }

    private fun buildZoomUnavailableHint(): TextView {
        return TextView(context).apply {
            text = context.getString(R.string.photo_overlay_zoom_unavailable)
            textSize = ZOOM_UNAVAILABLE_TEXT_SIZE_SP
            setTextColor(Color.WHITE)
            alpha = 0.8f
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(8), dp(4), dp(8))
            visibility = if (zoomAvailable) View.GONE else View.VISIBLE
        }.also {
            zoomUnavailable = it
        }
    }

    private fun zoomStepButton(
        label: String,
        descriptionRes: Int,
        action: () -> Unit,
    ): Button {
        return Button(context).apply {
            text = label
            isAllCaps = false
            textSize = ZOOM_STEP_TEXT_SIZE_SP
            setTextColor(Color.WHITE)
            contentDescription = context.getString(descriptionRes)
            minWidth = 0
            minimumWidth = 0
            minHeight = dp(48)
            minimumHeight = dp(48)
            setPadding(0, 0, 0, 0)
            background = roundedBackground(
                AlibiOverlayStyle.ACTION_COLOR,
                dp(AlibiOverlayStyle.ACTION_CORNER_DP).toFloat(),
            )
            setOnClickListener { action() }
        }
    }

    /** Applies the cached zoom state to the panel views. Safe to call before [show]. */
    private fun renderZoomControls() {
        val section = zoomSection
        val valueView = zoomValue
        val seekBar = zoomSeekBar
        val hint = zoomUnavailable
        if (section == null || valueView == null || seekBar == null || hint == null) {
            return
        }

        section.visibility = if (zoomAvailable) View.VISIBLE else View.GONE
        hint.visibility = if (zoomAvailable) View.GONE else View.VISIBLE

        val availabilityChanged = renderedZoomAvailable != zoomAvailable
        renderedZoomAvailable = zoomAvailable

        if (zoomAvailable) {
            updateZoomValueLabel()
            updateZoomStepButtons()
            if (!zoomDragging) {
                seekBar.progress = zoomProgressForRatio(zoomRatio)
            }
        }

        if (availabilityChanged) {
            // The zoom section appeared/disappeared, so the panel needs a new
            // measured height. Never touch the window position here otherwise.
            refreshWindowHeightIfExpanded()
        }
    }

    private fun updateZoomValueLabel() {
        val valueView = zoomValue ?: return
        valueView.text = context.getString(R.string.photo_overlay_zoom_value, zoomRatio)
        valueView.contentDescription = context.getString(
            R.string.photo_overlay_zoom_description,
            zoomRatio,
        )
    }

    private fun updateZoomStepButtons() {
        val canZoomOut = zoomRatio > zoomMinRatio + ZOOM_BOUNDARY_EPSILON
        val canZoomIn = zoomRatio < zoomMaxRatio - ZOOM_BOUNDARY_EPSILON
        zoomOutButton?.apply {
            isEnabled = canZoomOut
            alpha = if (canZoomOut) 1f else 0.45f
        }
        zoomInButton?.apply {
            isEnabled = canZoomIn
            alpha = if (canZoomIn) 1f else 0.45f
        }
    }

    /** Maps min..max to 0..[AlibiOverlayStyle.ZOOM_SEEK_BAR_MAX] on a log scale. */
    private fun zoomProgressForRatio(ratio: Float): Int {
        if (!zoomAvailable || zoomMaxRatio <= zoomMinRatio || ratio <= 0f) {
            return 0
        }
        val range = ln(zoomMaxRatio.toDouble() / zoomMinRatio.toDouble())
        if (range <= 0.0) {
            return 0
        }
        val linear = ln(ratio.toDouble() / zoomMinRatio.toDouble()) / range
        return (linear * AlibiOverlayStyle.ZOOM_SEEK_BAR_MAX)
            .roundToInt()
            .coerceIn(0, AlibiOverlayStyle.ZOOM_SEEK_BAR_MAX)
    }

    /** Inverse of [zoomProgressForRatio]; ratio = min * (max / min) ^ linear. */
    private fun zoomRatioForProgress(progress: Int): Float {
        if (progress <= 0) {
            return zoomMinRatio
        }
        if (progress >= AlibiOverlayStyle.ZOOM_SEEK_BAR_MAX) {
            return zoomMaxRatio
        }
        val linear = progress.toDouble() /
            AlibiOverlayStyle.ZOOM_SEEK_BAR_MAX.toDouble()
        val ratio = zoomMinRatio.toDouble() *
            (zoomMaxRatio.toDouble() / zoomMinRatio.toDouble()).pow(linear)
        return ratio.toFloat().coerceIn(zoomMinRatio, zoomMaxRatio)
    }

    private fun measuredPanelHeight(): Int {
        val panel = actionPanel ?: return buttonSize
        val width = dp(AlibiOverlayStyle.PANEL_WIDTH_DP)
        panel.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val maxHeight =
            (context.resources.displayMetrics.heightPixels * PANEL_MAX_HEIGHT_FRACTION).toInt()
        return min(panel.measuredHeight, maxHeight).coerceAtLeast(buttonSize)
    }

    private fun refreshWindowHeightIfExpanded() {
        val panel = actionPanel ?: return
        if (panel.visibility != View.VISIBLE) {
            return
        }
        val params = layoutParams ?: return
        val width = dp(AlibiOverlayStyle.PANEL_WIDTH_DP)
        val height = measuredPanelHeight()
        if (params.width == width && params.height == height) {
            return
        }
        params.width = width
        params.height = height
        clampToScreen(params, persist = false)
        root?.let { runCatching { windowManager.updateViewLayout(it, params) } }
    }

    private fun expandActions() {
        val panel = actionPanel ?: return
        val params = layoutParams ?: return
        renderZoomControls()
        panel.visibility = View.VISIBLE
        params.width = dp(AlibiOverlayStyle.PANEL_WIDTH_DP)
        params.height = measuredPanelHeight()
        clampToScreen(params, persist = false)
        root?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        savePosition(params)
    }

    private fun collapseActions() {
        val panel = actionPanel ?: return
        val params = layoutParams ?: return
        panel.visibility = View.GONE
        params.width = buttonSize
        params.height = buttonSize
        clampToScreen(params, persist = false)
        root?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        savePosition(params)
    }

    private fun handleShutterTouch(event: MotionEvent): Boolean {
        val params = layoutParams ?: return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downWindowX = params.x
                downWindowY = params.y
                moved = false
                longPressed = false
                shutter?.alpha = 0.75f
                handler.postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - downRawX
                val deltaY = event.rawY - downRawY

                if (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop) {
                    moved = true
                    handler.removeCallbacks(longPress)
                    hideActions()
                    params.x = downWindowX + deltaX.toInt()
                    params.y = downWindowY + deltaY.toInt()
                    clampToScreen(params, persist = false)
                    root?.let {
                        runCatching { windowManager.updateViewLayout(it, params) }
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                shutter?.alpha =
                    if (currentStatus == Status.BUSY || currentStatus == Status.STARTING) 0.65f else 1f

                if (!moved &&
                    !longPressed &&
                    (currentStatus == Status.READY || currentStatus == Status.BUSY)
                ) {
                    shutter?.performClick()
                }
                if (moved) {
                    savePosition(params)
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                shutter?.alpha =
                    if (currentStatus == Status.BUSY || currentStatus == Status.STARTING) 0.65f else 1f
            }
        }

        return true
    }

    private fun toggleActions() {
        val panel = actionPanel ?: return
        if (panel.visibility == View.VISIBLE) {
            collapseActions()
        } else {
            expandActions()
        }
    }

    private fun hideActions() {
        if (actionPanel?.visibility != View.VISIBLE) {
            return
        }
        collapseActions()
    }

    private fun actionButton(labelRes: Int, action: () -> Unit): Button {
        return Button(context).apply {
            text = context.getString(labelRes)
            isAllCaps = false
            minHeight = dp(48)
            minimumHeight = dp(48)
            setTextColor(Color.WHITE)
            background = roundedBackground(
                AlibiOverlayStyle.ACTION_COLOR,
                dp(AlibiOverlayStyle.ACTION_CORNER_DP).toFloat(),
            )
            setOnClickListener { action() }
        }
    }

    private fun clampToScreen(
        params: WindowManager.LayoutParams,
        persist: Boolean,
    ) {
        val display = context.resources.displayMetrics
        val frameHeight = if (params.height > 0) params.height else buttonSize
        params.x = max(0, min(params.x, display.widthPixels - params.width))
        params.y = max(0, min(params.y, display.heightPixels - frameHeight))

        if (persist) {
            savePosition(params)
        }
    }

    private fun savePosition(params: WindowManager.LayoutParams) {
        preferences.edit()
            .putInt(PREF_X, params.x)
            .putInt(PREF_Y, params.y)
            .apply()
    }

    private fun overlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun circleBackground(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(2), Color.WHITE)
    }

    private fun statusBackground(status: Status): GradientDrawable {
        return statusBackgrounds.getOrPut(status) {
            circleBackground(STATUS_COLORS.getValue(status))
        }
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = radius
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun animationDuration(duration: Long): Long {
        val scale = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        return if (scale == 0f) 0L else duration
    }

    private companion object {
        const val OVERLAY_PREFERENCES = "photo_overlay"
        const val PREF_X = "x"
        const val PREF_Y = "y"
        const val ACTION_OPEN_APP = 0x01020001
        const val ACTION_STOP_SESSION = 0x01020002
        const val ACTION_ZOOM_IN = 0x01020003
        const val ACTION_ZOOM_OUT = 0x01020004
        const val ACTION_ZOOM_RESET = 0x01020005

        const val DEFAULT_ZOOM_RATIO = 1f
        const val ZOOM_AVAILABLE_MIN_RANGE = 0.05f
        const val ZOOM_BOUNDARY_EPSILON = 0.005f
        const val PANEL_MAX_HEIGHT_FRACTION = 0.7f
        const val ZOOM_STEP_TEXT_SIZE_SP = 20f
        const val ZOOM_UNAVAILABLE_TEXT_SIZE_SP = 12f

        val STATUS_COLORS = mapOf(
            Status.STARTING to AlibiOverlayStyle.STARTING_COLOR,
            Status.READY to AlibiOverlayStyle.READY_COLOR,
            Status.BUSY to AlibiOverlayStyle.BUSY_COLOR,
            Status.ERROR to AlibiOverlayStyle.ERROR_COLOR,
        )
    }
}
