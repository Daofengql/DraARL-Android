package cn.silverdragon.draarl.radio

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.edit
import cn.silverdragon.draarl.R
import cn.silverdragon.draarl.data.RadioStatus
import kotlin.math.hypot

internal class PttOverlayWindow(
    context: Context,
    private val onStartPtt: () -> Boolean,
    private val onStopPtt: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val windowManager = appContext.getSystemService(WindowManager::class.java)
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(appContext).scaledTouchSlop

    private var rootView: LinearLayout? = null
    private var groupLabel: TextView? = null
    private var pttButton: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var radioStatus = RadioStatus()
    private var groupName = appContext.getString(R.string.ptt_overlay_default_group)
    private var dragging = false
    private var localPttActive = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var startWindowX = 0
    private var startWindowY = 0

    val isShowing: Boolean
        get() = rootView != null

    private val delayedPttStart = Runnable {
        if (
            !dragging &&
            radioStatus.connected &&
            !radioStatus.transmitting &&
            radioStatus.speaker.isBlank()
        ) {
            localPttActive = onStartPtt()
            renderButton()
        }
    }

    fun show(name: String): Boolean {
        updateGroupName(name)
        if (isShowing) return true
        if (!Settings.canDrawOverlays(appContext)) return false

        val root = createView()
        val metrics = appContext.resources.displayMetrics
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = preferences.getInt(KEY_X, (metrics.widthPixels - dp(96)).coerceAtLeast(0))
            y = preferences.getInt(KEY_Y, metrics.heightPixels / 2)
        }

        return runCatching {
            windowManager.addView(root, params)
            rootView = root
            layoutParams = params
            root.post { updatePosition(params.x, params.y) }
            renderButton()
            true
        }.getOrElse {
            rootView = null
            groupLabel = null
            pttButton = null
            layoutParams = null
            false
        }
    }

    fun hide() {
        handler.removeCallbacks(delayedPttStart)
        stopLocalPtt()
        rootView?.let { view -> runCatching { windowManager.removeView(view) } }
        rootView = null
        groupLabel = null
        pttButton = null
        layoutParams = null
    }

    fun updateGroupName(name: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { updateGroupName(name) }
            return
        }
        groupName = name.ifBlank { appContext.getString(R.string.ptt_overlay_default_group) }
        groupLabel?.text = groupName
    }

    fun updateStatus(status: RadioStatus) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { updateStatus(status) }
            return
        }
        radioStatus = status
        if (!status.transmitting) localPttActive = false
        renderButton()
    }

    private fun createView(): LinearLayout {
        val label = TextView(appContext).apply {
            text = groupName
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(180)
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = roundedBackground(LABEL_COLOR, dp(7).toFloat())
        }
        val button = OverlayPttButton(appContext).apply {
            setText(R.string.ptt_overlay_button)
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            includeFontPadding = false
            elevation = dp(8).toFloat()
            contentDescription = appContext.getString(R.string.ptt_overlay_content_description)
            setOnTouchListener(::handleTouch)
        }
        groupLabel = label
        pttButton = button

        return LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            addView(
                label,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp(6) },
            )
            addView(button, LinearLayout.LayoutParams(dp(76), dp(76)))
        }
    }

    private fun handleTouch(view: View, event: MotionEvent): Boolean {
        val params = layoutParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = false
                downRawX = event.rawX
                downRawY = event.rawY
                startWindowX = params.x
                startWindowY = params.y
                handler.postDelayed(delayedPttStart, ViewConfiguration.getTapTimeout().toLong())
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - downRawX
                val deltaY = event.rawY - downRawY
                if (!dragging && hypot(deltaX.toDouble(), deltaY.toDouble()) > touchSlop) {
                    dragging = true
                    handler.removeCallbacks(delayedPttStart)
                    stopLocalPtt()
                }
                if (dragging) updatePosition(startWindowX + deltaX.toInt(), startWindowY + deltaY.toInt())
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(delayedPttStart)
                stopLocalPtt()
                if (dragging) savePosition() else view.performClick()
                dragging = false
            }
        }
        return true
    }

    private fun stopLocalPtt() {
        if (!localPttActive) return
        localPttActive = false
        onStopPtt()
        renderButton()
    }

    private fun updatePosition(requestedX: Int, requestedY: Int) {
        val root = rootView ?: return
        val params = layoutParams ?: return
        val metrics = appContext.resources.displayMetrics
        val maxX = (metrics.widthPixels - root.width.coerceAtLeast(dp(76))).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - root.height.coerceAtLeast(dp(104))).coerceAtLeast(0)
        params.x = requestedX.coerceIn(0, maxX)
        params.y = requestedY.coerceIn(0, maxY)
        runCatching { windowManager.updateViewLayout(root, params) }
    }

    private fun savePosition() {
        val params = layoutParams ?: return
        preferences.edit {
            putInt(KEY_X, params.x)
            putInt(KEY_Y, params.y)
        }
    }

    private fun renderButton() {
        val button = pttButton ?: return
        val transmitting = radioStatus.transmitting || localPttActive
        val available = radioStatus.connected && radioStatus.speaker.isBlank()
        button.setText(if (transmitting) R.string.ptt_overlay_transmitting else R.string.ptt_overlay_button)
        button.background = roundedBackground(
            color = when {
                transmitting -> TRANSMITTING_COLOR
                available -> PTT_COLOR
                else -> DISABLED_COLOR
            },
            radius = dp(38).toFloat(),
        )
        button.alpha = if (available || transmitting) 1f else 0.72f
    }

    private fun roundedBackground(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }

    private fun dp(value: Int): Int = (value * appContext.resources.displayMetrics.density).toInt()

    private companion object {
        const val PREFERENCES_NAME = "draarl_ptt_overlay"
        const val KEY_X = "x"
        const val KEY_Y = "y"
        val PTT_COLOR: Int = Color.rgb(25, 118, 210)
        val TRANSMITTING_COLOR: Int = Color.rgb(211, 47, 47)
        val DISABLED_COLOR: Int = Color.rgb(117, 117, 117)
        val LABEL_COLOR: Int = Color.argb(220, 38, 50, 64)
    }
}

private class OverlayPttButton(context: Context) : TextView(context) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
