package com.example.brillo

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.*
import android.widget.SeekBar

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
    private var panel: SeekBar? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (dim != null) return START_STICKY
        val ch = NotificationChannel("ov", "Brillo", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val n = Notification.Builder(this, "ov")
            .setContentTitle("Control de brillo activo")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        startForeground(1, n)

        val w = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = w

        val real = DisplayMetrics()
        @Suppress("DEPRECATION")
        w.defaultDisplay.getRealMetrics(real)

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

        val s = SeekBar(this).apply {
            max = 90; progress = 30
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, u: Boolean) {
                    d.alpha = p / 100f
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        panel = s
        w.addView(s, WindowManager.LayoutParams(
            real.widthPixels * 8 / 10, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 250 })
        return START_STICKY
    }

    override fun onDestroy() {
        dim?.let { wm?.removeView(it) }
        panel?.let { wm?.removeView(it) }
        super.onDestroy()
    }
}
