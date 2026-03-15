package com.example.chip8emulator

import android.graphics.Color

enum class Chip8Theme(
    val displayName: String,
    val onColor: Int,
    val offColor: Int,
    val ghostColor: Int,
    val backgroundColor: Int
) {
    GREEN(
        displayName    = "Classic Green",
        onColor        = Color.parseColor("#00FF41"),
        offColor       = Color.parseColor("#0D1117"),
        ghostColor     = Color.argb(140, 0, 255, 65),
        backgroundColor = Color.parseColor("#0D1117")
    ),
    AMBER(
        displayName    = "Amber",
        onColor        = Color.parseColor("#FFB000"),
        offColor       = Color.parseColor("#1A1000"),
        ghostColor     = Color.argb(140, 255, 176, 0),
        backgroundColor = Color.parseColor("#1A1000")
    ),
    BLUE(
        displayName    = "Cyan Blue",
        onColor        = Color.parseColor("#00CFFF"),
        offColor       = Color.parseColor("#000D1A"),
        ghostColor     = Color.argb(140, 0, 207, 255),
        backgroundColor = Color.parseColor("#000D1A")
    ),
    WHITE(
        displayName    = "White",
        onColor        = Color.parseColor("#FFFFFF"),
        offColor       = Color.parseColor("#000000"),
        ghostColor     = Color.argb(140, 255, 255, 255),
        backgroundColor = Color.parseColor("#000000")
    ),
    RED(
        displayName    = "Red Alert",
        onColor        = Color.parseColor("#FF3B3B"),
        offColor       = Color.parseColor("#1A0000"),
        ghostColor     = Color.argb(140, 255, 59, 59),
        backgroundColor = Color.parseColor("#1A0000")
    ),
    MATRIX(
        displayName    = "Matrix",
        onColor        = Color.parseColor("#AAFFAA"),
        offColor       = Color.parseColor("#001100"),
        ghostColor     = Color.argb(140, 100, 220, 100),
        backgroundColor = Color.parseColor("#001100")
    )
}