package com.digitalminds.artstar

import android.Manifest
import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast

class MainActivity : Activity(), Host {
    override val synth = Synth()
    override lateinit var prefs: SharedPreferences

    private lateinit var root: FrameLayout
    private var home: HomeScreen? = null
    private var player: PlayerScreen? = null
    private var micCb: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Fonts.init(this)
        prefs = getSharedPreferences("artstar", MODE_PRIVATE)
        if (prefs.getInt("schema", 0) < 2) {
            val e = prefs.edit()
            for (k in prefs.all.keys) if (k.startsWith("bpm_")) e.remove(k)
            e.putInt("speed", 3).putInt("schema", 2).apply()
        }
        Thread { Bank.load(applicationContext) }.start()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        root = FrameLayout(this)
        root.setBackgroundColor(C.BG)
        setContentView(root)
        showSplash()
    }

    /** Pantalla de entrada: icono de la app y "Powered by" DigitalMinds. */
    private fun showSplash() {
        val sp = FrameLayout(this)
        sp.setBackgroundColor(C.BG)
        val icon = android.widget.ImageView(this)
        icon.setImageBitmap(assets.open("splash_icon.png").use { android.graphics.BitmapFactory.decodeStream(it) })
        sp.addView(icon, FrameLayout.LayoutParams(dpi(116f), dpi(116f), android.view.Gravity.CENTER))
        val box = android.widget.LinearLayout(this)
        box.orientation = android.widget.LinearLayout.VERTICAL
        box.gravity = android.view.Gravity.CENTER_HORIZONTAL
        val pb = label(this, "POWERED BY", 12f, C.MUTED, Fonts.medium)
        pb.letterSpacing = 0.35f
        box.addView(pb)
        val lg = logoView(this, 18f)
        (lg.layoutParams as android.widget.LinearLayout.LayoutParams).topMargin = dpi(12f)
        box.addView(lg)
        sp.addView(box, FrameLayout.LayoutParams(WRAP, WRAP, android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL).also { it.bottomMargin = dpi(96f) })
        root.addView(sp, FrameLayout.LayoutParams(MATCH, MATCH))
        root.postDelayed({
            if (player == null && home == null) {
                sp.animate().alpha(0f).setDuration(260).withEndAction { showHome() }.start()
            }
        }, 1300)
    }

    override fun onStart() {
        super.onStart()
        synth.start()
    }

    override fun onStop() {
        player?.pause()
        home?.onLeave()
        synth.allOff()
        synth.metroOn = false
        synth.stop()
        super.onStop()
    }

    private fun showHome() {
        root.removeAllViews()
        val h = HomeScreen(this) { info, partId, mode -> openPlayer(info, partId, mode) }
        home = h
        root.addView(h, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun openPlayer(info: SongInfo, partId: String, mode: Int) {
        home?.onLeave()
        val song = Library.load(this, info.id)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        immersive(true)
        val p = PlayerScreen(this, song, partId, mode)
        player = p
        root.removeAllViews()
        root.addView(p, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    override fun closePlayer() {
        val p = player ?: return
        p.release()
        player = null
        immersive(false)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        showHome()
    }

    @Suppress("DEPRECATION")
    private fun immersive(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 30) {
            val c = window.insetsController ?: return
            if (on) {
                c.hide(WindowInsets.Type.systemBars())
                c.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else c.show(WindowInsets.Type.systemBars())
        } else {
            val d = window.decorView
            d.systemUiVisibility = if (on) (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE) else View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && player != null) immersive(true)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (player != null) { closePlayer(); return }
        if (home?.handleBack() == true) return
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    // ---- Host ----
    override fun ensureMic(cb: (Boolean) -> Unit) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            cb(true)
        } else {
            micCb = cb
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 77)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 77) {
            val ok = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            val cb = micCb
            micCb = null
            cb?.invoke(ok)
        }
    }

    override fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
