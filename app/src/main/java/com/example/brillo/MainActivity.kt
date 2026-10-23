package com.example.brillo

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.*
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {
    override fun onResume() {
        super.onResume()
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        } else {
            startForegroundService(Intent(this, OverlayService::class.java))
            finish()
        }
    }
}

class OverlayService : Service() {
    private var wm: WindowManager? = null
    private var dim: View? = null
    private var panel: View? = null
    private var toggle: View? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "STOP") {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (dim != null) return START_STICKY

        val ch = NotificationChannel("ov", "Brillo", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Apagar", stopIntent).build()
        val n = Notification.Builder(this, "ov")
            .setContentTitle("Control de brillo activo")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .addAction(action)
            .build()
        startForeground(1, n)

        val w = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = w
        val dp = resources.displayMetrics.density

        val real = DisplayMetrics()
        @Suppress("DEPRECATION")
        w.defaultDisplay.getRealMetrics(real)

        // Capa oscura
        val d = View(this).apply { setBackgroundColor(Color.BLACK); alpha = 0.3f }
        dim = d
        val dimLp = WindowManager.LayoutParams(
            real.widthPixels + 400, real.heightPixels + 400,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.CENTER
                if (Build.VERSION.SDK_INT >= 28) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
        w.addView(d, dimLp)

        // Panel: slider + botón de apagar
        val seek = SeekBar(this).apply {
            max = 90; progress = 30
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                    d.alpha = p / 100f
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        val off = TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
            setOnClickListener { stopSelf() }
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 24 * dp
                setColor(Color.argb(110, 0, 0, 0))
            }
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
            addView(seek, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(off, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        panel = box
        w.addView(box, WindowManager.LayoutParams(
            real.widthPixels * 7 / 10, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                x = (16 * dp).toInt(); y = 250
            })

        // Botón pequeño para esconder/mostrar el panel
        val size = (40 * dp).toInt()
        val btn = TextView(this).apply {
            text = "☀"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            alpha = 0.5f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(120, 60, 60, 60))
            }
            setOnClickListener {
                box.visibility = if (box.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        toggle = btn
        w.addView(btn, WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                x = (16 * dp).toInt(); y = 250
            })

        return START_STICKY
    }

    override fun onDestroy() {
        dim?.let { wm?.removeView(it) }
        panel?.let { wm?.removeView(it) }
        toggle?.let { wm?.removeView(it) }
        dim = null; panel = null; toggle = null
        super.onDestroy()
    }
}
