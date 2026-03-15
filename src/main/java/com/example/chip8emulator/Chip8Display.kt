package com.example.chip8emulator

class Chip8Display {

    companion object {
        const val WIDTH_LO  = 64
        const val HEIGHT_LO = 32
        const val WIDTH_HI  = 128
        const val HEIGHT_HI = 64

        // XO-CHIP supports 4 drawing planes (bit flags 1,2,3)
        const val MAX_PLANES = 4
    }

    // Current resolution mode
    var hiRes = false
    val width  get() = if (hiRes) WIDTH_HI  else WIDTH_LO
    val height get() = if (hiRes) HEIGHT_HI else HEIGHT_LO

    // XO-CHIP: up to 2 independent pixel planes
    // plane[0] and plane[1] — combined they give 4 colors
    private val planeData = Array(2) { BooleanArray(WIDTH_HI * HEIGHT_HI) }

    // Active draw planes bitmask (default = 1 = plane 0 only)
    var activePlanes = 1

    // Public pixel array for renderer — combined color index 0-3
    // 0=off, 1=plane0 only, 2=plane1 only, 3=both planes
    val pixels = Array(HEIGHT_HI) { IntArray(WIDTH_HI) }

    var dirty = false

    // ─────────────────────────────────────────
    fun clear() {
        synchronized(this) {
            // Clear active planes only
            for (p in 0..1) {
                if (activePlanes and (1 shl p) != 0) {
                    planeData[p].fill(false)
                }
            }
            rebuildPixels()
            dirty = true
        }
    }

    fun clearAll() {
        synchronized(this) {
            planeData[0].fill(false)
            planeData[1].fill(false)
            rebuildPixels()
            dirty = true
        }
    }

    // ─────────────────────────────────────────
    fun drawSprite(
        x: Int, y: Int,
        spriteData: IntArray,
        numRows: Int,
        plane: Int = 0
    ): Boolean {
        var collision = false
        synchronized(this) {
            val w = width
            val h = height

            for (row in 0 until numRows) {
                val spriteByte = spriteData[row]
                for (col in 0 until 8) {
                    if ((spriteByte shr (7 - col)) and 0x1 == 1) {
                        val sx  = (x + col) % w
                        val sy  = (y + row) % h
                        val idx = sy * WIDTH_HI + sx

                        if (planeData[plane][idx]) collision = true
                        planeData[plane][idx] = planeData[plane][idx] xor true
                    }
                }
            }
            rebuildPixels()
            dirty = true
        }
        return collision
    }

    // Draw 16-wide sprite (SCHIP)
    fun drawSprite16(x: Int, y: Int, spriteData: IntArray, numRows: Int, plane: Int = 0): Boolean {
        var collision = false
        synchronized(this) {
            val w = width
            val h = height

            for (row in 0 until numRows) {
                val high = spriteData[row * 2]
                val low  = spriteData[row * 2 + 1]
                val word = (high shl 8) or low

                for (col in 0 until 16) {
                    if ((word shr (15 - col)) and 0x1 == 1) {
                        val sx  = (x + col) % w
                        val sy  = (y + row) % h
                        val idx = sy * WIDTH_HI + sx

                        if (planeData[plane][idx]) collision = true
                        planeData[plane][idx] = planeData[plane][idx] xor true
                    }
                }
            }
            rebuildPixels()
            dirty = true
        }
        return collision
    }

    // Scroll operations (SCHIP)
    fun scrollDown(n: Int) {
        synchronized(this) {
            val w = width; val h = height
            for (p in 0..1) {
                if (activePlanes and (1 shl p) == 0) continue
                val tmp = planeData[p].copyOf()
                planeData[p].fill(false)
                for (row in 0 until h - n) {
                    for (col in 0 until w) {
                        planeData[p][(row + n) * WIDTH_HI + col] = tmp[row * WIDTH_HI + col]
                    }
                }
            }
            rebuildPixels(); dirty = true
        }
    }

    fun scrollUp(n: Int) {
        synchronized(this) {
            val w = width; val h = height
            for (p in 0..1) {
                if (activePlanes and (1 shl p) == 0) continue
                val tmp = planeData[p].copyOf()
                planeData[p].fill(false)
                for (row in n until h) {
                    for (col in 0 until w) {
                        planeData[p][(row - n) * WIDTH_HI + col] = tmp[row * WIDTH_HI + col]
                    }
                }
            }
            rebuildPixels(); dirty = true
        }
    }

    fun scrollLeft() {
        synchronized(this) {
            val w = width; val h = height
            for (p in 0..1) {
                if (activePlanes and (1 shl p) == 0) continue
                val tmp = planeData[p].copyOf()
                planeData[p].fill(false)
                for (row in 0 until h) {
                    for (col in 4 until w) {
                        planeData[p][row * WIDTH_HI + (col - 4)] = tmp[row * WIDTH_HI + col]
                    }
                }
            }
            rebuildPixels(); dirty = true
        }
    }

    fun scrollRight() {
        synchronized(this) {
            val w = width; val h = height
            for (p in 0..1) {
                if (activePlanes and (1 shl p) == 0) continue
                val tmp = planeData[p].copyOf()
                planeData[p].fill(false)
                for (row in 0 until h) {
                    for (col in 0 until w - 4) {
                        planeData[p][row * WIDTH_HI + (col + 4)] = tmp[row * WIDTH_HI + col]
                    }
                }
            }
            rebuildPixels(); dirty = true
        }
    }

    // Rebuild the combined pixels array from both planes
    private fun rebuildPixels() {
        val w = width; val h = height
        for (row in 0 until h) {
            for (col in 0 until w) {
                val idx = row * WIDTH_HI + col
                val p0  = if (planeData[0][idx]) 1 else 0
                val p1  = if (planeData[1][idx]) 2 else 0
                pixels[row][col] = p0 or p1
            }
        }
    }

    fun reset() {
        synchronized(this) {
            hiRes = false
            activePlanes = 1
            planeData[0].fill(false)
            planeData[1].fill(false)
            rebuildPixels()
            dirty = false
        }
    }
}