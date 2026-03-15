package com.example.chip8emulator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View

class Chip8SurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var chip8Display: Chip8Display? = null

    // ── Visual settings ──
    var currentTheme: Chip8Theme = Chip8Theme.GREEN
        set(value) { field = value; applyTheme() }

    var glowEnabled      = true
    var scanlinesEnabled = false
    var ghostFrames      = 3

    @Volatile private var rendering = false
    private var renderHandler: Handler? = null
    private var renderRunnable: Runnable? = null

    // ── Snapshot buffers ──
    // Bool snapshot for legacy ghost buffer
    private val snapshotBool = Array(Chip8Display.HEIGHT_HI) {
        BooleanArray(Chip8Display.WIDTH_HI)
    }
    // Color index snapshot for XO-CHIP 4-color support
    private val snapshotColor = Array(Chip8Display.HEIGHT_HI) {
        IntArray(Chip8Display.WIDTH_HI)
    }
    // Ghost buffer — counts down per pixel
    private val ghostBuffer = Array(Chip8Display.HEIGHT_HI) {
        IntArray(Chip8Display.WIDTH_HI)
    }

    // ── Paints — declared as class fields ──
    private val paintOn    = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val paintOff   = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val paintGhost = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val paintGlow  = Paint().apply { style = Paint.Style.FILL; isAntiAlias = true  }

    // XO-CHIP 4-color planes
    private val paintPlane0 = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val paintPlane1 = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }
    private val paintPlane2 = Paint().apply { style = Paint.Style.FILL; isAntiAlias = false }

    private val paintScanline = Paint().apply {
        color = Color.argb(30, 0, 0, 0)
        style = Paint.Style.FILL
    }
    private val paintText = Paint().apply {
        color = Color.parseColor("#00FF41")
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
    }

    init {
        applyTheme()
        Log.d("Chip8Surface", "View init")
    }

    // ─────────────────────────────────────────
    // Apply theme colors to all paints
    // ─────────────────────────────────────────
    private fun applyTheme() {
        paintOn.color    = currentTheme.onColor
        paintOff.color   = currentTheme.offColor
        paintGhost.color = currentTheme.ghostColor

        // Glow color = ON color but very transparent
        val r = Color.red(currentTheme.onColor)
        val g = Color.green(currentTheme.onColor)
        val b = Color.blue(currentTheme.onColor)
        paintGlow.color = Color.argb(60, r, g, b)

        // XO-CHIP plane colors derived from theme
        paintPlane0.color = currentTheme.onColor
        paintPlane1.color = Color.argb(
            255,
            (r * 0.6f).toInt(),
            (g * 0.6f + 80).toInt().coerceAtMost(255),
            (b * 0.6f + 120).toInt().coerceAtMost(255)
        )
        paintPlane2.color = Color.argb(
            255,
            (r * 0.8f + 60).toInt().coerceAtMost(255),
            (g * 0.8f).toInt(),
            (b * 0.3f).toInt()
        )

        setBackgroundColor(currentTheme.backgroundColor)
        paintText.color = currentTheme.onColor
    }

    // ─────────────────────────────────────────
    fun startRendering() {
        if (rendering) return
        rendering = true
        renderHandler = Handler(Looper.getMainLooper())
        renderRunnable = object : Runnable {
            override fun run() {
                if (rendering) {
                    takeSnapshot()
                    invalidate()
                    renderHandler?.postDelayed(this, 16)
                }
            }
        }
        renderHandler?.post(renderRunnable!!)
        Log.d("Chip8Surface", "Render started")
    }

    fun stopRendering() {
        rendering = false
        renderRunnable?.let { renderHandler?.removeCallbacks(it) }
        renderHandler  = null
        renderRunnable = null
    }

    // ─────────────────────────────────────────
    // Thread-safe snapshot of display state
    // ─────────────────────────────────────────
    private fun takeSnapshot() {
        val disp = chip8Display ?: return
        val h = disp.height
        val w = disp.width

        synchronized(disp) {
            for (row in 0 until h) {
                for (col in 0 until w) {
                    val c = disp.pixels[row][col]
                    snapshotColor[row][col] = c
                    snapshotBool[row][col]  = c > 0
                }
            }
        }

        // Update ghost buffer
        if (ghostFrames > 0) {
            for (row in 0 until h) {
                for (col in 0 until w) {
                    if (snapshotBool[row][col]) {
                        ghostBuffer[row][col] = ghostFrames + 1
                    } else if (ghostBuffer[row][col] > 0) {
                        ghostBuffer[row][col]--
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────
    // Draw one frame
    // ─────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val vw = width.toFloat()
        val vh = height.toFloat()

        canvas.drawColor(currentTheme.backgroundColor)

        val disp = chip8Display
        if (disp == null || vw <= 0f || vh <= 0f) {
            canvas.drawText("Load a ROM to start", vw / 2f, vh / 2f, paintText)
            return
        }

        val dispW = disp.width
        val dispH = disp.height

        // Scale to fill view keeping correct aspect ratio
        val pixelSize = minOf(vw / dispW, vh / dispH)

        val totalW  = pixelSize * dispW
        val totalH  = pixelSize * dispH
        val offsetX = (vw - totalW) / 2f
        val offsetY = (vh - totalH) / 2f

        // ── Glow layer (drawn behind pixels) ──
        if (glowEnabled) {
            val glowPad = pixelSize * 0.8f
            for (row in 0 until dispH) {
                for (col in 0 until dispW) {
                    if (snapshotBool[row][col]) {
                        canvas.drawRect(
                            offsetX + col * pixelSize - glowPad,
                            offsetY + row * pixelSize - glowPad,
                            offsetX + col * pixelSize + pixelSize + glowPad,
                            offsetY + row * pixelSize + pixelSize + glowPad,
                            paintGlow
                        )
                    }
                }
            }
        }

        // ── Pixel layer ──
        for (row in 0 until dispH) {
            for (col in 0 until dispW) {
                val left   = offsetX + col * pixelSize
                val top    = offsetY + row * pixelSize
                val right  = left + pixelSize - 0.5f
                val bottom = top  + pixelSize - 0.5f

                val colorIndex = snapshotColor[row][col]
                val paint = when {
                    // Ghost pixel — recently turned off
                    colorIndex == 0 &&
                            ghostFrames > 0 &&
                            ghostBuffer[row][col] > 0 -> paintGhost
                    // Off pixel
                    colorIndex == 0           -> paintOff
                    // XO-CHIP plane colors
                    colorIndex == 1           -> paintPlane0
                    colorIndex == 2           -> paintPlane1
                    colorIndex == 3           -> paintPlane2
                    else                      -> paintOff
                }
                canvas.drawRect(left, top, right, bottom, paint)
            }
        }

        // ── CRT Scanlines overlay ──
        if (scanlinesEnabled) {
            var scanY = offsetY
            while (scanY < offsetY + totalH) {
                canvas.drawRect(
                    offsetX, scanY,
                    offsetX + totalW, scanY + 1f,
                    paintScanline
                )
                scanY += pixelSize * 0.5f
            }
        }
    }
}