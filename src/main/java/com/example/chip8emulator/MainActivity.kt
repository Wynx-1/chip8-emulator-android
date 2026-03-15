package com.example.chip8emulator

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private val engine = Chip8Engine()

    private lateinit var chip8Screen:  Chip8SurfaceView
    private lateinit var debugOverlay: DebugOverlayView
    private lateinit var btnLoadRom:   Button
    private lateinit var btnStart:     Button
    private lateinit var btnPause:     Button
    private lateinit var btnStep:      Button
    private lateinit var btnReset:     Button
    private lateinit var btnBenchmark: Button

    private var audioTrack:    AudioTrack? = null
    private var currentSpeed   = 10
    private var currentTheme   = Chip8Theme.GREEN
    private var debugEnabled   = false
    private var currentRomName = ""

    private val debugHandler  = Handler(Looper.getMainLooper())
    private val debugRunnable = object : Runnable {
        override fun run() {
            if (debugEnabled) {
                debugOverlay.invalidate()
                debugHandler.postDelayed(this, 100)
            }
        }
    }

    // ── ROM Library launcher ──
    private val libraryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uriStr  = result.data?.getStringExtra("ROM_URI")  ?: return@registerForActivityResult
            val romName = result.data?.getStringExtra("ROM_NAME") ?: "ROM"
            handleRomUri(Uri.parse(uriStr), romName)
        }
    }

    // ── Direct file picker ──
    private val romPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) handleRomUri(uri)
        else toast("No file selected")
    }

    // ── Keypad mapping ──
    private val keyMap: Map<Int, Int> by lazy {
        mapOf(
            R.id.keyBtn0 to 0x0, R.id.keyBtn1 to 0x1,
            R.id.keyBtn2 to 0x2, R.id.keyBtn3 to 0x3,
            R.id.keyBtn4 to 0x4, R.id.keyBtn5 to 0x5,
            R.id.keyBtn6 to 0x6, R.id.keyBtn7 to 0x7,
            R.id.keyBtn8 to 0x8, R.id.keyBtn9 to 0x9,
            R.id.keyBtnA to 0xA, R.id.keyBtnB to 0xB,
            R.id.keyBtnC to 0xC, R.id.keyBtnD to 0xD,
            R.id.keyBtnE to 0xE, R.id.keyBtnF to 0xF
        )
    }

    // ─────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        enableFullscreen()

        // ── findViewById FIRST — always before using any button ──
        chip8Screen  = findViewById(R.id.chip8Screen)
        debugOverlay = findViewById(R.id.debugOverlay)
        btnLoadRom   = findViewById(R.id.btnLoadRom)
        btnStart     = findViewById(R.id.btnStart)
        btnPause     = findViewById(R.id.btnPause)
        btnStep      = findViewById(R.id.btnStep)
        btnReset     = findViewById(R.id.btnReset)
        btnBenchmark = findViewById(R.id.btnBenchmark)

        chip8Screen.chip8Display = engine.display
        chip8Screen.startRendering()
        debugOverlay.cpu = engine.cpu

        loadGlobalPreferences()
        setupBeepAudio()
        engine.onBeep = { playBeep() }

        // ── LOAD ──
        btnLoadRom.setOnClickListener {
            if (engine.isRunning) engine.stop()
            val intent = android.content.Intent(this, RomLibraryActivity::class.java)
            libraryLauncher.launch(intent)
        }
        btnLoadRom.setOnLongClickListener {
            romPickerLauncher.launch(arrayOf("*/*"))
            true
        }

        // ── START ──
        btnStart.setOnClickListener {
            if (!engine.isRomLoaded) { toast("Load a ROM first!"); return@setOnClickListener }
            if (engine.isRunning) engine.stop()
            engine.start()
            btnPause.text      = "PAUSE"
            btnStart.isEnabled = false;  btnStart.alpha = 0.4f
            btnPause.isEnabled = true;   btnPause.alpha = 1.0f
            btnStep.isEnabled  = false;  btnStep.alpha  = 0.4f
            btnReset.isEnabled = true;   btnReset.alpha = 1.0f
            toast("Running!")
        }
        btnStart.setOnLongClickListener {
            if (currentRomName.isNotEmpty()) showQuirksDialog()
            else toast("Load a ROM first!")
            true
        }

        // ── PAUSE ──
        btnPause.setOnClickListener {
            if (!engine.isRunning) { toast("Not running!"); return@setOnClickListener }
            engine.togglePause()
            val paused = engine.isPaused
            btnPause.text     = if (paused) "RESUME" else "PAUSE"
            btnStep.isEnabled = paused
            btnStep.alpha     = if (paused) 1.0f else 0.4f
        }

        // ── STEP — advance one opcode ──
        btnStep.setOnClickListener {
            if (!engine.isRomLoaded) { toast("Load a ROM first!"); return@setOnClickListener }
            if (!engine.isPaused) {
                engine.pause()
                btnPause.text     = "RESUME"
                btnStep.isEnabled = true
                btnStep.alpha     = 1.0f
            }
            engine.cpu.cycle()
            chip8Screen.invalidate()
            debugOverlay.invalidate()
        }
        // Long-press STEP = toggle debug overlay
        btnStep.setOnLongClickListener {
            debugEnabled = !debugEnabled
            debugOverlay.debugEnabled = debugEnabled
            debugOverlay.visibility   = if (debugEnabled) View.VISIBLE else View.GONE
            if (debugEnabled) debugHandler.post(debugRunnable)
            else debugHandler.removeCallbacks(debugRunnable)
            toast(if (debugEnabled) "Debug ON" else "Debug OFF")
            true
        }

        // ── RESET ──
        btnReset.setOnClickListener {
            if (!engine.isRomLoaded) {
                engine.fullReset()
                setInitialButtonStates()
                return@setOnClickListener
            }
            engine.stop()
            engine.softReset()
            engine.start()
            btnPause.text      = "PAUSE"
            btnStart.isEnabled = false;  btnStart.alpha = 0.4f
            btnPause.isEnabled = true;   btnPause.alpha = 1.0f
            btnStep.isEnabled  = false;  btnStep.alpha  = 0.4f
            btnReset.isEnabled = true;   btnReset.alpha = 1.0f
            toast("Reset!")
        }
        // Long-press RESET = Settings
        btnReset.setOnLongClickListener {
            showSettingsDialog()
            true
        }

        // ── BENCHMARK ──
        btnBenchmark.setOnClickListener {
            val intent = android.content.Intent(this, BenchmarkActivity::class.java)
            startActivity(intent)
        }

        // ── Keypad ──
        for ((btnId, chip8Key) in keyMap) {
            val button = findViewById<Button>(btnId)
            button.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN   -> { engine.input.pressKey(chip8Key);   button.alpha = 0.5f }
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> { engine.input.releaseKey(chip8Key); button.alpha = 1.0f }
                }
                false
            }
        }

        setInitialButtonStates()
    }

    // ─────────────────────────────────────────
    // Settings dialog — long-press RESET
    // ─────────────────────────────────────────
    @SuppressLint("InflateParams")
    private fun showSettingsDialog() {
        val view       = layoutInflater.inflate(R.layout.dialog_settings, null)
        val speedBar   = view.findViewById<SeekBar>(R.id.speedSeekBar)
        val speedLabel = view.findViewById<TextView>(R.id.tvSpeedLabel)
        val ghostBar   = view.findViewById<SeekBar>(R.id.ghostSeekBar)
        val ghostLabel = view.findViewById<TextView>(R.id.tvGhostLabel)
        val switchGlow = view.findViewById<SwitchCompat>(R.id.switchGlow)
        val switchScan = view.findViewById<SwitchCompat>(R.id.switchScanlines)

        speedBar.progress    = currentSpeed - 1
        speedLabel.text      = "$currentSpeed cycles/frame"
        ghostBar.progress    = chip8Screen.ghostFrames
        ghostLabel.text      = "Ghost frames: ${chip8Screen.ghostFrames}"
        switchGlow.isChecked = chip8Screen.glowEnabled
        switchScan.isChecked = chip8Screen.scanlinesEnabled

        speedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, f: Boolean) {
                speedLabel.text = "${p + 1} cycles/frame"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
        ghostBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, f: Boolean) {
                ghostLabel.text = "Ghost frames: $p"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        val themeButtons = mapOf(
            view.findViewById<Button>(R.id.themeGreen)  to Chip8Theme.GREEN,
            view.findViewById<Button>(R.id.themeAmber)  to Chip8Theme.AMBER,
            view.findViewById<Button>(R.id.themeBlue)   to Chip8Theme.BLUE,
            view.findViewById<Button>(R.id.themeWhite)  to Chip8Theme.WHITE,
            view.findViewById<Button>(R.id.themeRed)    to Chip8Theme.RED,
            view.findViewById<Button>(R.id.themeMatrix) to Chip8Theme.MATRIX
        )
        for ((btn, theme) in themeButtons) {
            btn.alpha = if (theme == currentTheme) 1.0f else 0.5f
            btn.setOnClickListener {
                currentTheme = theme
                chip8Screen.currentTheme = theme
                for ((b, _) in themeButtons) b.alpha = 0.5f
                btn.alpha = 1.0f
            }
        }

        AlertDialog.Builder(this)
            .setTitle(
                if (currentRomName.isNotEmpty())
                    "Settings — $currentRomName"
                else "Settings"
            )
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                currentSpeed = speedBar.progress + 1
                engine.setCyclesPerFrame(currentSpeed)
                chip8Screen.ghostFrames      = ghostBar.progress
                chip8Screen.glowEnabled      = switchGlow.isChecked
                chip8Screen.scanlinesEnabled = switchScan.isChecked
                saveGlobalPreferences()
                if (currentRomName.isNotEmpty()) saveRomSettings()
                toast("Settings saved!")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─────────────────────────────────────────
    // Quirks dialog — long-press START
    // ─────────────────────────────────────────
    private fun showQuirksDialog() {
        val cpu   = engine.cpu
        val items = arrayOf(
            "VF reset on logic ops (8xy1/2/3)",
            "I increments on FX55/65",
            "Shifting uses Vy (CHIP-48)",
            "Jump uses VX (CHIP-48)",
            "SCHIP hi-res mode"
        )
        val checked = booleanArrayOf(
            cpu.quirk_vfReset,
            cpu.quirk_memoryInc,
            cpu.quirk_shifting,
            cpu.quirk_jumping,
            cpu.schipMode
        )

        AlertDialog.Builder(this)
            .setTitle("ROM Quirks — $currentRomName")
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                when (which) {
                    0 -> cpu.quirk_vfReset    = isChecked
                    1 -> cpu.quirk_memoryInc  = isChecked
                    2 -> cpu.quirk_shifting   = isChecked
                    3 -> cpu.quirk_jumping    = isChecked
                    4 -> {
                        cpu.schipMode        = isChecked
                        engine.display.hiRes = isChecked
                    }
                }
            }
            .setPositiveButton("Save & Apply") { _, _ ->
                saveRomSettings()
                toast("Quirks saved for $currentRomName")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─────────────────────────────────────────
    private fun handleRomUri(uri: Uri, displayName: String? = null) {
        try {
            val romBytes = contentResolver
                .openInputStream(uri)?.use { it.readBytes() }
                ?: run { toast("Could not open file"); return }

            if (romBytes.isEmpty()) { toast("ROM is empty!");  return }
            if (romBytes.size > 3584) { toast("ROM too large!"); return }

            engine.fullReset()
            engine.loadROM(romBytes)

            currentRomName = displayName
                ?: uri.lastPathSegment?.substringAfterLast('/') ?: "ROM"

            val saved = RomSettingsStore.load(this, currentRomName)
            if (saved != null) {
                applyRomSettings(saved)
                toast("Loaded: $currentRomName (saved settings applied)")
            } else {
                toast("Loaded: $currentRomName — tap START")
            }

            runOnUiThread { setRomLoadedButtonStates() }

        } catch (e: Exception) {
            toast("Error: ${e.message}")
        }
    }

    // ─────────────────────────────────────────
    private fun applyRomSettings(s: RomSettings) {
        currentSpeed = s.speed
        engine.setCyclesPerFrame(s.speed)
        currentTheme = try {
            Chip8Theme.valueOf(s.theme)
        } catch (e: Exception) { Chip8Theme.GREEN }
        chip8Screen.currentTheme   = currentTheme
        chip8Screen.ghostFrames    = s.ghostFrames
        chip8Screen.glowEnabled    = s.glowEnabled
        engine.cpu.quirk_vfReset   = s.quirk_vfReset
        engine.cpu.quirk_memoryInc = s.quirk_memoryInc
        engine.cpu.quirk_shifting  = s.quirk_shifting
        engine.cpu.quirk_jumping   = s.quirk_jumping
        engine.cpu.schipMode       = s.schipMode
        engine.display.hiRes       = s.schipMode
    }

    private fun saveRomSettings() {
        val cpu = engine.cpu
        RomSettingsStore.save(this, currentRomName, RomSettings(
            romName         = currentRomName,
            speed           = currentSpeed,
            theme           = currentTheme.name,
            ghostFrames     = chip8Screen.ghostFrames,
            glowEnabled     = chip8Screen.glowEnabled,
            quirk_vfReset   = cpu.quirk_vfReset,
            quirk_memoryInc = cpu.quirk_memoryInc,
            quirk_shifting  = cpu.quirk_shifting,
            quirk_jumping   = cpu.quirk_jumping,
            schipMode       = cpu.schipMode
        ))
    }

    // ─────────────────────────────────────────
    private fun loadGlobalPreferences() {
        val prefs = getSharedPreferences("chip8_prefs", Context.MODE_PRIVATE)
        currentSpeed = prefs.getInt("speed", 10)
        engine.setCyclesPerFrame(currentSpeed)
        currentTheme = try {
            Chip8Theme.valueOf(prefs.getString("theme", "GREEN") ?: "GREEN")
        } catch (e: Exception) { Chip8Theme.GREEN }
        chip8Screen.currentTheme     = currentTheme
        chip8Screen.ghostFrames      = prefs.getInt("ghost", 3)
        chip8Screen.glowEnabled      = prefs.getBoolean("glow", true)
        chip8Screen.scanlinesEnabled = prefs.getBoolean("scanlines", false)
    }

    private fun saveGlobalPreferences() {
        getSharedPreferences("chip8_prefs", Context.MODE_PRIVATE).edit().apply {
            putInt("speed",           currentSpeed)
            putString("theme",        currentTheme.name)
            putInt("ghost",           chip8Screen.ghostFrames)
            putBoolean("glow",        chip8Screen.glowEnabled)
            putBoolean("scanlines",   chip8Screen.scanlinesEnabled)
            apply()
        }
    }

    // ─────────────────────────────────────────
    private fun enableFullscreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(
                    WindowInsets.Type.statusBars() or
                            WindowInsets.Type.navigationBars()
                )
                it.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                            android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableFullscreen()
    }

    // ─────────────────────────────────────────
    private fun setInitialButtonStates() {
        btnStart.isEnabled = false;  btnStart.alpha = 0.4f
        btnPause.isEnabled = false;  btnPause.alpha = 0.4f
        btnStep.isEnabled  = false;  btnStep.alpha  = 0.4f
        btnReset.isEnabled = false;  btnReset.alpha = 0.4f
        btnPause.text = "PAUSE"
    }

    private fun setRomLoadedButtonStates() {
        btnStart.isEnabled = true;   btnStart.alpha = 1.0f
        btnPause.isEnabled = false;  btnPause.alpha = 0.4f
        btnStep.isEnabled  = true;   btnStep.alpha  = 1.0f
        btnReset.isEnabled = true;   btnReset.alpha = 1.0f
    }

    // ─────────────────────────────────────────
    private fun setupBeepAudio() {
        try {
            val sampleRate = 44100
            val numSamples = sampleRate / 10
            val samples = ShortArray(numSamples) { i ->
                val a = 2.0 * Math.PI * 440.0 * i / sampleRate
                if (sin(a) >= 0) Short.MAX_VALUE else Short.MIN_VALUE
            }
            val bufSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            audioTrack?.write(samples, 0, samples.size)
        } catch (e: Exception) { /* audio optional */ }
    }

    private fun playBeep() {
        try {
            audioTrack?.let {
                if (it.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    it.reloadStaticData()
                    it.play()
                }
            }
        } catch (e: Exception) { /* ignore */ }
    }

    // ─────────────────────────────────────────
    override fun onPause()  { super.onPause() }
    override fun onResume() { super.onResume() }

    override fun onStop() {
        super.onStop()
        if (engine.isRunning && !engine.isPaused) engine.pause()
    }

    override fun onStart() {
        super.onStart()
        if (engine.isRunning && engine.isPaused) engine.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        debugHandler.removeCallbacks(debugRunnable)
        engine.stop()
        chip8Screen.stopRendering()
        audioTrack?.release()
        audioTrack = null
    }

    private fun toast(msg: String) {
        runOnUiThread {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}