package com.example.poolfloatingoverlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.*

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: View? = null
    private var panel: View? = null
    private var aimCanvasView: AimCanvasView? = null
    private var isAimEnabled = true

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showAimCanvas()
        showBubble()
    }

    private fun params(width: Int, height: Int, touchable: Boolean = true): WindowManager.LayoutParams {
        val flags = if (touchable) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }

        return WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 220
        }
    }

    private fun showAimCanvas() {
        if (aimCanvasView != null) return
        aimCanvasView = AimCanvasView(this)
        val p = params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, touchable = false)
        p.x = 0; p.y = 0
        wm.addView(aimCanvasView, p)
    }

    private fun showBubble() {
        if (bubble != null) return
        val button = TextView(this).apply {
            text = "POOL"
            textSize = 13f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(25, 110, 190))
            setPadding(18, 14, 18, 14)
            elevation = 12f
        }
        val lp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        button.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX; downY = event.rawY
                        startX = lp.x; startY = lp.y; moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - downX).toInt()
                        val dy = (event.rawY - downY).toInt()
                        if (abs(dx) > 5 || abs(dy) > 5) moved = true
                        lp.x = startX + dx; lp.y = startY + dy
                        wm.updateViewLayout(button, lp)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) togglePanel()
                        return true
                    }
                }
                return false
            }
        })
        bubble = button
        wm.addView(button, lp)
    }

    private fun togglePanel() {
        if (panel != null) {
            wm.removeView(panel)
            panel = null
            return
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 16)
            setBackgroundColor(Color.rgb(28, 32, 42))
            elevation = 16f
        }
        val heading = TextView(this).apply {
            text = "Pool Practice Tools"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(4, 4, 4, 12)
        }
        box.addView(heading)
        addAction(box, if (isAimEnabled) "Auto Aim Guide: ON" else "Auto Aim Guide: OFF") {
            isAimEnabled = !isAimEnabled
            aimCanvasView?.visibility = if (isAimEnabled) View.VISIBLE else View.GONE
            Toast.makeText(this, "Aim Guide: ${if (isAimEnabled) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
            togglePanel()
        }
        addAction(box, "Close Menu") { togglePanel() }
        val p = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        p.x = 24; p.y = 290
        panel = box
        wm.addView(box, p)
    }

    private fun addAction(parent: LinearLayout, label: String, action: () -> Unit) {
        val item = TextView(this).apply {
            text = label
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(12, 12, 12, 12)
            setOnClickListener { action() }
        }
        parent.addView(item)
    }

    override fun onDestroy() {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        bubble?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        aimCanvasView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null; bubble = null; aimCanvasView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class AimCanvasView(context: Context) : View(context) {
    private val cueLinePaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val targetLinePaint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 5f
        isAntiAlias = true
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }

    private val ghostBallPaint = Paint().apply {
        color = Color.YELLOW
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val cuePointPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        // កូអរដោនេគណនាបន្ទាត់តម្រង់ចំតុប៊ីយ៉ា
        val cueX = width * 0.36f
        val cueY = height * 0.53f
        val targetX = width * 0.69f
        val targetY = height * 0.53f

        val dx = targetX - cueX
        val dy = targetY - cueY
        val angle = atan2(dy, dx)
        val radius = 24f

        val contactX = targetX - (2 * radius * cos(angle))
        val contactY = targetY - (2 * radius * sin(angle))

        // គូសបន្ទាត់តម្រង់ (Aim Line)
        canvas.drawCircle(cueX, cueY, 8f, cuePointPaint)
        canvas.drawLine(cueX, cueY, contactX, contactY, cueLinePaint)

        // គូស Ghost Ball ត្រង់ចំណុចបុក
        canvas.drawCircle(contactX, contactY, radius, ghostBallPaint)

        // គូសបន្ទាត់ដាច់ៗទិសដៅគ្រាប់បាល់រត់ទៅរន្ធ
        val lineLen = 550f
        val endX = targetX + lineLen * cos(angle)
        val endY = targetY + lineLen * sin(angle)
        canvas.drawLine(targetX, targetY, endX, endY, targetLinePaint)

        invalidate()
    }
}