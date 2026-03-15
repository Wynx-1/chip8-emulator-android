package com.example.chip8emulator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

class DebugOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var cpu: Chip8CPU? = null
    var debugEnabled = false

    private val bgPaint = Paint().apply {
        color = Color.argb(200, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#00FF41")
        textSize = 22f
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }

    private val labelPaint = Paint().apply {
        color = Color.parseColor("#888888")
        textSize = 20f
        typeface = Typeface.MONOSPACE
        isAntiAlias = true
    }

    // ── FIXED: use Typeface.create() for bold, not textStyle ──
    private val titlePaint = Paint().apply {
        color = Color.parseColor("#00FF41")
        textSize = 24f
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!debugEnabled) return

        val cpu = cpu ?: return

        val pw = 260f
        val ph = 420f
        val px = 8f
        val py = 8f

        // Background panel
        canvas.drawRoundRect(px, py, px + pw, py + ph, 8f, 8f, bgPaint)

        var ty = py + 28f
        val tx = px + 10f
        val col2 = px + 140f

        // Title
        canvas.drawText("DEBUG", tx, ty, titlePaint)
        ty += 28f

        // PC / Opcode / I / SP / DT / ST
        fun row(label: String, value: String) {
            canvas.drawText(label, tx, ty, labelPaint)
            canvas.drawText(value, col2, ty, textPaint)
            ty += 22f
        }

        row("PC", "0x${cpu.pc.toString(16).uppercase().padStart(4, '0')}")
        row("OP", "0x${cpu.lastOpcode.toString(16).uppercase().padStart(4, '0')}")
        row("I",  "0x${cpu.i.toString(16).uppercase().padStart(4, '0')}")
        row("SP", "${cpu.sp}")
        row("DT", "${cpu.delayTimer}")
        row("ST", "${cpu.soundTimer}")

        ty += 6f

        // Registers V0-VF in two columns
        canvas.drawText("REGISTERS", tx, ty, titlePaint)
        ty += 24f

        for (reg in 0..15) {
            val cx = if (reg % 2 == 0) tx else col2
            if (reg % 2 == 0 && reg > 0) ty += 20f
            val label = "V${reg.toString(16).uppercase()}"
            val value = cpu.v[reg].toString(16).uppercase().padStart(2, '0')
            canvas.drawText(label, cx, ty, labelPaint)
            canvas.drawText("0x$value", cx + 36f, ty, textPaint)
        }

        ty += 28f

        // Stack
        canvas.drawText("STACK", tx, ty, titlePaint)
        ty += 22f
        val stackStr = (0 until minOf(cpu.sp, 4))
            .joinToString("  ") {
                "0x${cpu.stack[it].toString(16).uppercase()}"
            }
        canvas.drawText(
            if (stackStr.isEmpty()) "empty" else stackStr,
            tx, ty, textPaint
        )
    }
}