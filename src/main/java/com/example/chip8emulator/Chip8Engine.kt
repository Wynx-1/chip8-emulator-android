package com.example.chip8emulator

import android.util.Log

class Chip8Engine {

    val memory  = Chip8Memory()
    val display = Chip8Display()
    val input   = Chip8Input()
    val cpu     = Chip8CPU(memory, display, input)

    @Volatile var isRunning   = false
    @Volatile var isPaused    = false
    @Volatile var isRomLoaded = false

    // CHIP-8 should run at ~700-1000 cycles per second
    // At 60fps that's ~12-17 cycles per frame
    // Many modern ROMs need higher: 20-30 cycles per frame
    private var cyclesPerFrame = 10
    private val frameTimeMs    = 16L   // 60fps

    private var emulationThread: Thread? = null
    private var lastRomBytes: ByteArray? = null

    var onBeep: (() -> Unit)? = null

    fun loadROM(romBytes: ByteArray) {
        lastRomBytes = romBytes
        memory.loadROM(romBytes)
        isRomLoaded = true
        Log.d("Chip8Engine", "ROM loaded OK: ${romBytes.size} bytes")
    }

    fun start() {
        if (!isRomLoaded) { Log.e("Chip8Engine", "No ROM!"); return }
        if (isRunning)    { Log.w("Chip8Engine", "Already running"); return }

        isRunning = true
        isPaused  = false

        Log.d("Chip8Engine", "Starting. PC=${cpu.pc}")

        emulationThread = Thread {
            var frameCount = 0
            while (isRunning) {
                if (!isPaused) {
                    try {
                        val frameStart = System.currentTimeMillis()

                        repeat(cyclesPerFrame) {
                            if (isRunning && !isPaused) cpu.cycle()
                        }

                        cpu.updateTimers()
                        if (cpu.shouldBeep) onBeep?.invoke()

                        frameCount++
                        if (frameCount % 60 == 0) {
                            Log.d("Chip8Engine",
                                "Frame $frameCount | PC=${cpu.pc} | dirty=${display.dirty}")
                        }

                        val elapsed = System.currentTimeMillis() - frameStart
                        val sleep   = frameTimeMs - elapsed
                        if (sleep > 0) Thread.sleep(sleep)

                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt(); break
                    } catch (e: Exception) {
                        Log.e("Chip8Engine", "Error: ${e.message}")
                    }
                } else {
                    try { Thread.sleep(16) }
                    catch (e: InterruptedException) { Thread.currentThread().interrupt(); break }
                }
            }
            Log.d("Chip8Engine", "Thread ended")
        }

        emulationThread?.isDaemon = true
        emulationThread?.name = "Chip8-Emulation"
        emulationThread?.start()
    }

    fun pause()       { isPaused = true  }
    fun resume()      { isPaused = false }
    fun togglePause() { isPaused = !isPaused }

    fun stop() {
        isRunning = false
        emulationThread?.interrupt()
        try { emulationThread?.join(500) } catch (e: InterruptedException) { }
        emulationThread = null
        Log.d("Chip8Engine", "Stopped")
    }
    fun setCyclesPerFrame(cycles: Int) {
        cyclesPerFrame = cycles
    }
    fun fullReset() {
        stop()
        cpu.reset()
        memory.reset()
        display.reset()
        input.reset()
        isRomLoaded  = false
        isPaused     = false
        lastRomBytes = null
        Log.d("Chip8Engine", "Full reset done")
    }

    fun softReset() {
        val rom = lastRomBytes ?: run {
            Log.e("Chip8Engine", "No ROM for soft reset"); return
        }
        stop()
        cpu.reset()
        memory.reset()
        display.reset()
        input.reset()
        memory.loadROM(rom)
        isRomLoaded = true
        isPaused    = false
        Log.d("Chip8Engine", "Soft reset done")
    }
}