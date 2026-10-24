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
import android.widget.TextView
import app.myzel394.alibi.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PhotoOverlayController(
    private val context: Context,
    private val onCapture: () -> Unit,
    private val onOpenApp: () -> Unit,
    private val onStop: () -> Unit,
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
    private val buttonSize = dp(56)
    private var root: FrameLayout? = null
    private var shutter: TextView? = null
    private var actionPanel: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var currentStatus: Status = Status.STARTING
    private var statusInitialized = false
    private var requestedOpacity = 0.9f
    private val statusBackgrounds = mutableMapOf<Status, GradientDrawable>()

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
            return true
        }

        val container = FrameLayout(context)
        val button = TextView(context).apply {
            text = "\u25CF"
            textSize = 24f
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

                        else -> super.performAccessibilityAction(host, action, arguments)
                    }
                }
            }
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = roundedBackground(Color.argb(235, 20, 20, 20), dp(16).toFloat())
        }
        panel.addView(actionButton(R.string.photo_overlay_open_app, onOpenApp))
        panel.addView(actionButton(R.string.photo_overlay_stop, onStop))
        panel.addView(actionButton(R.string.photo_overlay_close, ::hideActions))
        container.addView(
            button,
            FrameLayout.LayoutParams(buttonSize, buttonSize),
        )
        container.addView(
            panel,
            FrameLayout.LayoutParams(dp(176), FrameLayout.LayoutParams.WRAP_CONTENT).apply {
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
            layoutParams = null
        }.isSuccess

        updateStatus(status)
        return added
    }

    fun setOpacity(opacity: Float) {
        requestedOpacity = opacity.coerceIn(0.45f, 1f)
        root?.alpha = requestedOpacity
    }

    fun hide() {
        handler.removeCallbacks(longPress)
        val view = root ?: return
        runCatching { windowManager.removeView(view) }
        root = null
        shutter = null
        actionPanel = null
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
                .setDuration(140L)
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
        val params = layoutParams ?: return
        panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        params.width = if (panel.visibility == View.VISIBLE) dp(176) else buttonSize
        params.height = if (panel.visibility == View.VISIBLE) dp(168) else buttonSize
        clampToScreen(params, persist = false)
        root?.let { runCatching { windowManager.updateViewLayout(it, params) } }
        savePosition(params)
    }

    private fun hideActions() {
        val panel = actionPanel ?: return
        if (panel.visibility != View.VISIBLE) {
            return
        }

        panel.visibility = View.GONE
        layoutParams?.let { params ->
            params.width = buttonSize
            params.height = buttonSize
            root?.let { runCatching { windowManager.updateViewLayout(it, params) } }
            savePosition(params)
        }
    }

    private fun actionButton(labelRes: Int, action: () -> Unit): Button {
        return Button(context).apply {
            text = context.getString(labelRes)
            isAllCaps = false
            minHeight = dp(48)
            minimumHeight = dp(48)
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.rgb(52, 52, 52), dp(12).toFloat())
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

    private companion object {
        const val OVERLAY_PREFERENCES = "photo_overlay"
        const val PREF_X = "x"
        const val PREF_Y = "y"
        const val ACTION_OPEN_APP = 0x01020001
        const val ACTION_STOP_SESSION = 0x01020002

        val STATUS_COLORS = mapOf(
            Status.STARTING to Color.rgb(100, 100, 100),
            Status.READY to Color.rgb(0, 120, 105),
            Status.BUSY to Color.rgb(28, 120, 200),
            Status.ERROR to Color.rgb(180, 48, 48),
        )
    }
}
