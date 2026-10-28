package com.poolaim.promax.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.poolaim.promax.MainActivity
import com.poolaim.promax.capture.ScreenCaptureService

class FloatingAimService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null
    private var status: TextView? = null

    override fun onCreate() { super.onCreate(); wm = getSystemService(WINDOW_SERVICE) as WindowManager; showBubble() }

    private fun type() = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun showBubble() {
        val view = TextView(this).apply {
            text = "🎱"; textSize = 24f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(35,145,78)); setPadding(16,10,16,10)
        }
        val p = WindowManager.LayoutParams(-2,-2,type(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START; x=12; y=220 }
        bubble=view; wm.addView(view,p)
        view.setOnTouchListener(object : android.view.View.OnTouchListener {
            var sx=0f; var sy=0f; var ix=0; var iy=0; var moved=false
            override fun onTouch(v: android.view.View, e: MotionEvent): Boolean {
                when(e.action) {
                    MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; ix=p.x; iy=p.y; moved=false; return true }
                    MotionEvent.ACTION_MOVE -> {
                        val dx=(e.rawX-sx).toInt(); val dy=(e.rawY-sy).toInt()
                        if(kotlin.math.abs(dx)>8 || kotlin.math.abs(dy)>8) moved=true
                        p.x=ix+dx; p.y=iy+dy; runCatching { wm.updateViewLayout(view,p) }; return true
                    }
                    MotionEvent.ACTION_UP -> { if(!moved) togglePanel(); return true }
                }; return false
            }
        })
    }

    private fun togglePanel() {
        panel?.let { runCatching { wm.removeView(it) }; panel=null; status=null; return }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(20,16,20,16); setBackgroundColor(Color.rgb(28,31,34)) }
        box.addView(label("PoolAim Pro Max",18f))
        status=label("Live analysis is off. Start to allow Android screen capture.",14f); box.addView(status)
        box.addView(label("Start live analysis",15f).apply {
            setTextColor(Color.rgb(100,220,130))
            setOnClickListener {
                val i=Intent(this@FloatingAimService,MainActivity::class.java).apply {
                    action=MainActivity.ACTION_REQUEST_LIVE_ANALYSIS
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(i)
            }
        })
        box.addView(label("Stop analysis",15f).apply {
            setOnClickListener {
                startService(Intent(this@FloatingAimService,ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
                status?.text="Analysis stopped."
            }
        })
        box.addView(label("Hide controls",15f).apply { setOnClickListener { stopSelf() } })
        val p=WindowManager.LayoutParams(-2,-2,type(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.START; x=24; y=280 }
        panel=box; wm.addView(box,p)
    }

    private fun label(value:String,size:Float)=TextView(this).apply {
        text=value; textSize=size; setTextColor(Color.WHITE); setPadding(0,10,0,10)
    }

    override fun onDestroy() {
        panel?.let { runCatching { wm.removeView(it) } }; bubble?.let { runCatching { wm.removeView(it) } }
        panel=null; bubble=null; status=null; super.onDestroy()
    }
    override fun onBind(intent:Intent?):IBinder?=null
}
