package com.example.chip8emulator

class Chip8Input {

    // 16 keys: 0x0 through 0xF
    // true = currently pressed
    private val keyState = BooleanArray(16)

    // Stores the last key that was pressed (-1 = none)
    var lastKeyPressed = -1
        private set

    // Press a key (0–15)
    fun pressKey(key: Int) {
        if (key in 0..15) {
            keyState[key] = true
            lastKeyPressed = key
        }
    }

    // Release a key (0–15)
    fun releaseKey(key: Int) {
        if (key in 0..15) {
            keyState[key] = false
        }
    }

    // Check if a specific key is currently held down
    fun isKeyPressed(key: Int): Boolean {
        return if (key in 0..15) keyState[key] else false
    }

    // Returns the last pressed key and resets it
    // Used by opcode FX0A — "wait for key press"
    fun consumeLastKeyPress(): Int {
        val key = lastKeyPressed
        lastKeyPressed = -1
        return key
    }

    // Release all keys (used on reset)
    fun reset() {
        keyState.fill(false)
        lastKeyPressed = -1
    }
}