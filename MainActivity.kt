package com.thek.cliente

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var rootLayout: FrameLayout
    private lateinit var navBar: LinearLayout
    private var activePage: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Th.load(this)
        
        rootLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(rootLayout)

        val bgView = Backdrop(this)
        rootLayout.addView(bgView)

        val mainContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                bottomMargin = 150
            }
        }
        rootLayout.addView(mainContainer)

        val headerText = TextView(this).apply {
            text = "Thek Cliente"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(40, 60, 40, 40)
        }
        mainContainer.addView(headerText)

        val hudToggle = TextView(this).apply {
            text = "LIGAR / DESLIGAR HUD"
            textSize = 18f
            setTextColor(Color.BLACK)
            setBackgroundColor(Th.accent)
            setPadding(40, 30, 40, 30)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(40, 20, 40, 20)
            }
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    startActivityForResult(intent, 100)
                } else {
                    toggleHudService()
                }
            }
        }
        mainContainer.addView(hudToggle)

        val contentScroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        mainContainer.addView(contentScroll)

        val pageContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        contentScroll.addView(pageContainer)

        navBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                150,
                Gravity.BOTTOM
            )
        }
        rootLayout.addView(navBar)

        setupNavTabs(pageContainer)
        switchTab(0)
    }

    private fun setupNavTabs(container: FrameLayout) {
        val tabs = listOf("Turbo", "Música", "Consumo", "Ajustes", "IA")
        val widthPerTab = resources.displayMetrics.widthPixels / tabs.size

        tabs.forEachIndexed { index, name ->
            val tab = object : android.view.View(context) {
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    val size = 40f
                    val x = (width - size) / 2f
                    val y = 15f
                    val col = if (activePage == index) Th.accent else Color.GRAY
                    when(index) {
                        0 -> Ico2.BOLT(canvas, x, y, size, col)
                        1 -> Ico2.CHART(canvas, x, y, size, col)
                        2 -> Ico2.DISC(canvas, x, y, size, col)
                        3 -> Ico2.DOWN(canvas, x, y, size, col)
                        4 -> Ico2.SPARK(canvas, x, y, size, col)
                    }
                }
            }.apply {
                layoutParams = LinearLayout.LayoutParams(widthPerTab, LinearLayout.LayoutParams.MATCH_PARENT)
                setOnClickListener { switchTab(index) }
            }
            navBar.addView(tab)
        }
    }

    private fun switchTab(index: Int) {
        activePage = index
        for (i in 0 until navBar.childCount) {
            navBar.getChildAt(i).invalidate()
        }
    }

    private fun toggleHudService() {
        val intent = Intent(this, OverlayService::class.java)
        startService(intent)
    }
}
