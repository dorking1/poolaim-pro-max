package com.poolaim.promax.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * User-controlled floating control bubble.
 *
 * This service is only the overlay foundation. It intentionally does not claim
 * to capture or analyze game frames; that pipeline is a separate milestone.
 */
class FloatingAimService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showBubble()
    }

    private fun windowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun showBubble() {
        val view = TextView(this).apply {
            text = "🎱"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(35, 145, 78))
            setPadding(16, 10, 16, 10)
            contentDescription = "PoolAim floating controls"
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 12
            y = 220
        }
        bubble = view
        windowManager.addView(view, params)

        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var downX = 0f
            private var downY = 0f
            private var dragged = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        downX = event.rawX
                        downY = event.rawY
                        dragged = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) dragged = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        runCatching { windowManager.updateViewLayout(view, params) }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!dragged) togglePanel()
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun togglePanel() {
        panel?.let {
            runCatching { windowManager.removeView(it) }
            panel = null
            return
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 16, 20, 16)
            setBackgroundColor(Color.rgb(28, 31, 34))
        }
        layout.addView(TextView(this).apply {
            text = "PoolAim Pro Max"
            textSize = 18f
            setTextColor(Color.WHITE)
        })
        layout.addView(TextView(this).apply {
            text = "Floating controls are active. Live screen analysis is not implemented yet."
            textSize = 14f
            setTextColor(Color.LTGRAY)
            setPadding(0, 10, 0, 10)
        })
        layout.addView(TextView(this).apply {
            text = "Hide bubble"
            textSize = 16f
            setTextColor(Color.rgb(100, 220, 130))
            setPadding(0, 12, 0, 8)
            setOnClickListener { stopSelf() }
        })
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 280
        }
        panel = layout
        windowManager.addView(layout, params)
    }

    override fun onDestroy() {
        panel?.let { runCatching { windowManager.removeView(it) } }
        bubble?.let { runCatching { windowManager.removeView(it) } }
        panel = null
        bubble = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
