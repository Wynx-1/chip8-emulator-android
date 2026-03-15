package com.example.chip8emulator

import kotlin.random.Random

class Chip8CPU(
    private val memory: Chip8Memory,
    val display: Chip8Display,
    private val input: Chip8Input
) {
    val v = IntArray(16)
    var i  = 0
    var pc = 0x200

    val stack = IntArray(16)
    var sp    = 0

    var delayTimer = 0
    var soundTimer = 0

    var waitingForKey      = false
    var waitingKeyRegister = 0
    var shouldBeep         = false

    // SCHIP flag registers (HP48)
    val flagRegisters = IntArray(8)

    // Quirks — toggled per ROM
    var quirk_vfReset    = true   // 8xy1/2/3 reset VF
    var quirk_memoryInc  = true   // FX55/65 increment I
    var quirk_shifting   = false  // 8xy6/E use Vy
    var quirk_jumping    = false  // BNNN uses VX not V0

    // SCHIP mode flag
    var schipMode = false

    // Last executed opcode (for debug/step mode)
    var lastOpcode = 0
        private set

    // ─────────────────────────────────────────
    fun cycle() {
        if (waitingForKey) {
            val key = input.consumeLastKeyPress()
            if (key != -1) { v[waitingKeyRegister] = key; waitingForKey = false }
            return
        }

        if (pc < 0x200 || pc >= 4094) { pc = 0x200; return }

        val opcode = (memory.read(pc) shl 8) or memory.read(pc + 1)
        lastOpcode = opcode
        pc += 2
        executeOpcode(opcode)
    }

    fun updateTimers() {
        if (delayTimer > 0) delayTimer--
        if (soundTimer > 0) {
            soundTimer--
            shouldBeep = soundTimer > 0
        } else {
            shouldBeep = false
        }
    }

    // ─────────────────────────────────────────
    private fun executeOpcode(opcode: Int) {
        val nnn = opcode and 0x0FFF
        val n   = opcode and 0x000F
        val x   = (opcode shr 8) and 0xF
        val y   = (opcode shr 4) and 0xF
        val kk  = opcode and 0x00FF

        when (opcode and 0xF000) {

            0x0000 -> when (opcode and 0x00FF) {
                0xE0 -> display.clear()
                0xEE -> { sp--; pc = stack[sp] }
                0xFB -> display.scrollRight()
                0xFC -> display.scrollLeft()
                0xFD -> { /* EXIT — stop execution */ pc = 0x200 }
                0xFE -> { display.hiRes = false; schipMode = false }
                0xFF -> { display.hiRes = true;  schipMode = true  }
                else -> {
                    // 00CN — scroll down N pixels (SCHIP)
                    if (opcode and 0xFFF0 == 0x00C0) {
                        display.scrollDown(opcode and 0x000F)
                    } else if (opcode and 0xFFF0 == 0x00D0) {
                        display.scrollUp(opcode and 0x000F)
                    }
                    // else SYS — ignore
                }
            }

            0x1000 -> pc = nnn
            0x2000 -> { stack[sp++] = pc; pc = nnn }
            0x3000 -> { if (v[x] == kk) pc += 2 }
            0x4000 -> { if (v[x] != kk) pc += 2 }
            0x5000 -> when (n) {
                0x0 -> { if (v[x] == v[y]) pc += 2 }
                0x2 -> {
                    // 5XY2 — XO-CHIP: store Vx..Vy to [I]
                    val step = if (x <= y) 1 else -1
                    var addr = i; var reg = x
                    while (reg != y + step) { memory.write(addr++, v[reg]); reg += step }
                }
                0x3 -> {
                    // 5XY3 — XO-CHIP: load [I] into Vx..Vy
                    val step = if (x <= y) 1 else -1
                    var addr = i; var reg = x
                    while (reg != y + step) { v[reg] = memory.read(addr++); reg += step }
                }
                else -> skip(opcode)
            }
            0x6000 -> v[x] = kk
            0x7000 -> v[x] = (v[x] + kk) and 0xFF

            0x8000 -> when (n) {
                0x0 -> v[x] = v[y]
                0x1 -> {
                    v[x] = v[x] or v[y]
                    if (quirk_vfReset) v[0xF] = 0
                }
                0x2 -> {
                    v[x] = v[x] and v[y]
                    if (quirk_vfReset) v[0xF] = 0
                }
                0x3 -> {
                    v[x] = v[x] xor v[y]
                    if (quirk_vfReset) v[0xF] = 0
                }
                0x4 -> {
                    val sum = v[x] + v[y]
                    v[x] = sum and 0xFF
                    v[0xF] = if (sum > 0xFF) 1 else 0
                }
                0x5 -> {
                    val nb = if (v[x] >= v[y]) 1 else 0
                    v[x] = (v[x] - v[y]) and 0xFF
                    v[0xF] = nb
                }
                0x6 -> {
                    val src = if (quirk_shifting) v[y] else v[x]
                    val lsb = src and 0x1
                    v[x] = src shr 1
                    v[0xF] = lsb
                }
                0x7 -> {
                    val nb = if (v[y] >= v[x]) 1 else 0
                    v[x] = (v[y] - v[x]) and 0xFF
                    v[0xF] = nb
                }
                0xE -> {
                    val src = if (quirk_shifting) v[y] else v[x]
                    val msb = (src shr 7) and 0x1
                    v[x] = (src shl 1) and 0xFF
                    v[0xF] = msb
                }
                else -> skip(opcode)
            }

            0x9000 -> { if (v[x] != v[y]) pc += 2 }
            0xA000 -> i = nnn
            0xB000 -> {
                pc = if (quirk_jumping) {
                    (nnn + v[x]) and 0xFFF
                } else {
                    (nnn + v[0]) and 0xFFF
                }
            }
            0xC000 -> v[x] = Random.nextInt(256) and kk

            0xD000 -> {
                if (n == 0 && schipMode) {
                    // DXYN with N=0 in SCHIP = 16x16 sprite
                    val sprite = IntArray(32) { memory.read(i + it) }
                    var collision = false
                    for (plane in 0..1) {
                        if (display.activePlanes and (1 shl plane) != 0) {
                            if (display.drawSprite16(v[x], v[y], sprite, 16, plane))
                                collision = true
                        }
                    }
                    v[0xF] = if (collision) 1 else 0
                } else {
                    val sprite = IntArray(n) { memory.read(i + it) }
                    var collision = false
                    for (plane in 0..1) {
                        if (display.activePlanes and (1 shl plane) != 0) {
                            if (display.drawSprite(v[x], v[y], sprite, n, plane))
                                collision = true
                        }
                    }
                    v[0xF] = if (collision) 1 else 0
                }
            }

            0xE000 -> when (kk) {
                0x9E -> { if ( input.isKeyPressed(v[x] and 0xF)) pc += 2 }
                0xA1 -> { if (!input.isKeyPressed(v[x] and 0xF)) pc += 2 }
                else -> skip(opcode)
            }

            0xF000 -> when (opcode and 0xFF) {
                0x00 -> {
                    // F000 — XO-CHIP: load long I
                    i = (memory.read(pc) shl 8) or memory.read(pc + 1)
                    pc += 2
                }
                0x01 -> {
                    // FX01 — XO-CHIP: set draw plane to X
                    display.activePlanes = x and 0x3
                }
                0x02 -> { /* XO-CHIP audio buffer — ignore */ }
                0x07 -> v[x] = delayTimer
                0x0A -> { waitingForKey = true; waitingKeyRegister = x }
                0x15 -> delayTimer = v[x]
                0x18 -> soundTimer  = v[x]
                0x1E -> i = (i + v[x]) and 0xFFFF
                0x29 -> i = (v[x] and 0xF) * 5
                0x30 -> i = 80 + (v[x] and 0xF) * 10 // SCHIP large font at 0x50
                0x33 -> {
                    memory.write(i,     v[x] / 100)
                    memory.write(i + 1, (v[x] / 10) % 10)
                    memory.write(i + 2, v[x] % 10)
                }
                0x55 -> {
                    for (r in 0..x) memory.write(i + r, v[r])
                    if (quirk_memoryInc) i = (i + x + 1) and 0xFFFF
                }
                0x65 -> {
                    for (r in 0..x) v[r] = memory.read(i + r)
                    if (quirk_memoryInc) i = (i + x + 1) and 0xFFFF
                }
                0x75 -> { for (r in 0..minOf(x, 7)) flagRegisters[r] = v[r] }
                0x85 -> { for (r in 0..minOf(x, 7)) v[r] = flagRegisters[r] }
                else -> skip(opcode)
            }

            else -> skip(opcode)
        }
    }

    private fun skip(opcode: Int) {
        android.util.Log.w("Chip8CPU",
            "Unknown: 0x${opcode.toString(16).uppercase().padStart(4,'0')} PC=${pc-2}")
    }

    fun reset() {
        v.fill(0); i = 0; pc = 0x200
        stack.fill(0); sp = 0
        delayTimer = 0; soundTimer = 0
        waitingForKey = false; waitingKeyRegister = 0
        shouldBeep = false; flagRegisters.fill(0)
        lastOpcode = 0
    }
}