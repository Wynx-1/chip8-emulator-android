package com.example.chip8emulator

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.NumberFormat
import java.util.Locale

class BenchmarkActivity : AppCompatActivity() {

    // ── UI ──
    private lateinit var tvLiveCounter:  TextView
    private lateinit var tvStatus:       TextView
    private lateinit var progressBar:    ProgressBar
    private lateinit var resultsCard:    View
    private lateinit var tvCyclesPerSec: TextView
    private lateinit var tvTotalCycles:  TextView
    private lateinit var tvEstFps:       TextView
    private lateinit var tvRatingBadge:  TextView
    private lateinit var tvRatingMessage:TextView
    private lateinit var btnRun:         Button
    private lateinit var btnBack:        Button

    // ── Benchmark state ──
    private val mainHandler   = Handler(Looper.getMainLooper())
    private var benchThread:    HandlerThread? = null
    private var benchHandler:   Handler? = null
    private var isRunning       = false

    private val BENCHMARK_DURATION_MS = 10_000L  // 10 seconds
    private val UPDATE_INTERVAL_MS    = 100L      // UI update every 100ms

    // ── Number formatter ──
    private val numFmt = NumberFormat.getNumberInstance(Locale.US)

    // ── A minimal CHIP-8 test ROM baked in ──
    // This is a tight loop that exercises all common opcodes:
    // Clear screen, set registers, draw sprite, jump back
    // 512 bytes of repeating opcodes — pure CPU stress test
    private val testRom: ByteArray by lazy {
        buildTestRom()
    }

    // ─────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_benchmark)
        enableFullscreen()

        tvLiveCounter   = findViewById(R.id.tvLiveCounter)
        tvStatus        = findViewById(R.id.tvStatus)
        progressBar     = findViewById(R.id.progressBar)
        resultsCard     = findViewById(R.id.resultsCard)
        tvCyclesPerSec  = findViewById(R.id.tvCyclesPerSec)
        tvTotalCycles   = findViewById(R.id.tvTotalCycles)
        tvEstFps        = findViewById(R.id.tvEstFps)
        tvRatingBadge   = findViewById(R.id.tvRatingBadge)
        tvRatingMessage = findViewById(R.id.tvRatingMessage)
        btnRun          = findViewById(R.id.btnRunBenchmark)
        btnBack         = findViewById(R.id.btnBack)

        btnRun.setOnClickListener  { startBenchmark() }
        btnBack.setOnClickListener { finish() }
    }

    // ─────────────────────────────────────────
    // Build a minimal stress-test ROM in memory
    // This ROM runs a tight loop of common opcodes
    // so we stress all CPU paths evenly
    // ─────────────────────────────────────────
    private fun buildTestRom(): ByteArray {
        // Opcodes (each is 2 bytes):
        // 6000 = LD V0, 0x00
        // 6101 = LD V1, 0x01
        // 6210 = LD V2, 0x10
        // 8012 = AND V0, V1
        // 8013 = XOR V0, V1
        // 7001 = ADD V0, 0x01
        // 3F00 = SE VF, 0x00  (skip next if VF==0)
        // 1200 = JP 0x200      (jump back to start)
        // Repeat this pattern to fill 512 bytes
        val opcodes = byteArrayOf(
            0x60.toByte(), 0x00.toByte(), // LD V0, 0
            0x61.toByte(), 0x01.toByte(), // LD V1, 1
            0x62.toByte(), 0x10.toByte(), // LD V2, 16
            0x80.toByte(), 0x12.toByte(), // AND V0, V1
            0x80.toByte(), 0x13.toByte(), // XOR V0, V1
            0x70.toByte(), 0x01.toByte(), // ADD V0, 1
            0x3F.toByte(), 0x00.toByte(), // SE VF, 0
            0x12.toByte(), 0x00.toByte()  // JP 0x200
        )
        // Fill 512 bytes by repeating the pattern
        val rom = ByteArray(512)
        for (i in rom.indices) {
            rom[i] = opcodes[i % opcodes.size]
        }
        return rom
    }

    // ─────────────────────────────────────────
    // Start the benchmark on a background thread
    // ─────────────────────────────────────────
    private fun startBenchmark() {
        if (isRunning) return

        // Reset UI
        isRunning = true
        resultsCard.visibility = View.GONE
        progressBar.progress   = 0
        tvLiveCounter.text     = "0"
        tvStatus.text          = "Warming up..."
        btnRun.isEnabled       = false
        btnRun.alpha           = 0.4f

        // Build isolated CPU — no display, no input needed
        val memory  = Chip8Memory()
        val display = Chip8Display()
        val input   = Chip8Input()
        val cpu     = Chip8CPU(memory, display, input)

        memory.loadROM(testRom)

        // Start background thread
        benchThread = HandlerThread("BenchmarkThread").also { it.start() }
        benchHandler = Handler(benchThread!!.looper)

        benchHandler?.post {
            runBenchmark(cpu)
        }
    }

    // ─────────────────────────────────────────
    // The actual benchmark — runs on background thread
    // ─────────────────────────────────────────
    private fun runBenchmark(cpu: Chip8CPU) {
        val startTime   = System.currentTimeMillis()
        val endTime     = startTime + BENCHMARK_DURATION_MS
        var totalCycles = 0L
        var lastUpdate  = startTime

        // Warmup — 500ms before counting
        val warmupEnd = startTime + 500L
        while (System.currentTimeMillis() < warmupEnd) {
            repeat(1000) { cpu.cycle() }
        }

        // Reset after warmup
        val memory = Chip8Memory()
        val display = Chip8Display()
        val input   = Chip8Input()
        val cpu2    = Chip8CPU(memory, display, input)
        memory.loadROM(testRom)

        val countStart = System.currentTimeMillis()
        val countEnd   = countStart + BENCHMARK_DURATION_MS

        mainHandler.post {
            tvStatus.text = "Benchmarking... 10s"
        }

        // Main benchmark loop — run as fast as possible
        while (System.currentTimeMillis() < countEnd) {
            // Execute 10,000 cycles per inner loop
            // to minimize System.currentTimeMillis() overhead
            repeat(10_000) { cpu2.cycle() }
            totalCycles += 10_000

            // Update UI every 100ms
            val now = System.currentTimeMillis()
            if (now - lastUpdate >= UPDATE_INTERVAL_MS) {
                lastUpdate = now
                val elapsed  = now - countStart
                val progress = ((elapsed.toFloat() / BENCHMARK_DURATION_MS) * 100).toInt()
                    .coerceIn(0, 100)
                val remaining = ((BENCHMARK_DURATION_MS - elapsed) / 1000f)
                    .coerceAtLeast(0f)
                val cyclesCopy = totalCycles

                mainHandler.post {
                    tvLiveCounter.text = numFmt.format(cyclesCopy)
                    progressBar.progress = progress
                    tvStatus.text = "Benchmarking... ${"%.1f".format(remaining)}s remaining"
                }
            }
        }

        // Benchmark done — calculate results
        val actualDurationMs = System.currentTimeMillis() - countStart
        val cyclesPerSecond  = (totalCycles * 1000L) / actualDurationMs
        val estimatedFps     = cyclesPerSecond / 600L  // CHIP-8 needs ~600 cycles/sec for 60fps

        mainHandler.post {
            showResults(totalCycles, cyclesPerSecond, estimatedFps)
        }
    }

    // ─────────────────────────────────────────
    // Show final results on the UI thread
    // ─────────────────────────────────────────
    private fun showResults(
        totalCycles:    Long,
        cyclesPerSecond: Long,
        estimatedFps:   Long
    ) {
        isRunning = false
        progressBar.progress = 100
        tvStatus.text        = "Benchmark complete!"
        tvLiveCounter.text   = numFmt.format(totalCycles)

        tvTotalCycles.text   = numFmt.format(totalCycles)
        tvCyclesPerSec.text  = numFmt.format(cyclesPerSecond)
        tvEstFps.text        = "${numFmt.format(estimatedFps)} FPS"

        // Rating
        val (badge, message, color) = getRating(cyclesPerSecond)
        tvRatingBadge.text    = badge
        tvRatingMessage.text  = message
        tvRatingBadge.setTextColor(color)

        resultsCard.visibility = View.VISIBLE

        btnRun.isEnabled = true
        btnRun.alpha     = 1.0f
        btnRun.text      = "RUN AGAIN"

        // Clean up background thread
        benchThread?.quitSafely()
        benchThread  = null
        benchHandler = null
    }

    // ─────────────────────────────────────────
    // Rating logic
    // ─────────────────────────────────────────
    private fun getRating(cyclesPerSecond: Long): Triple<String, String, Int> {
        return when {
            cyclesPerSecond < 200_000L -> Triple(
                "WEAK DEVICE",
                "This device may struggle with demanding CHIP-8 ROMs.\nTry lowering speed in settings.",
                Color.parseColor("#FF4444")
            )
            cyclesPerSecond < 800_000L -> Triple(
                "GOOD",
                "This device handles CHIP-8 well.\nAll standard ROMs will run smoothly.",
                Color.parseColor("#FFB000")
            )
            cyclesPerSecond < 2_000_000L -> Triple(
                "EXCELLENT",
                "Great performance!\nSCHIP and XO-CHIP ROMs will run perfectly.",
                Color.parseColor("#00FF41")
            )
            else -> Triple(
                "OVERKILL",
                "This device is way more powerful than CHIP-8 needs.\nPerfect for any ROM at any speed.",
                Color.parseColor("#00CFFF")
            )
        }
    }

    // ─────────────────────────────────────────
    private fun enableFullscreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.hide(
                android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        benchThread?.quitSafely()
        mainHandler.removeCallbacksAndMessages(null)
    }
}