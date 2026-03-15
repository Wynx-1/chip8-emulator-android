package com.example.chip8emulator

import android.content.Context
import org.json.JSONObject

data class RomSettings(
    val romName:      String  = "",
    val speed:        Int     = 10,
    val theme:        String  = "GREEN",
    val ghostFrames:  Int     = 3,
    val glowEnabled:  Boolean = true,
    val quirk_vfReset:   Boolean = true,
    val quirk_memoryInc: Boolean = true,
    val quirk_shifting:  Boolean = false,
    val quirk_jumping:   Boolean = false,
    val schipMode:    Boolean = false,
    // Keypad remap: chip8Key → display label
    val keyLabels: Map<Int, String> = emptyMap()
)

object RomSettingsStore {

    fun save(context: Context, romName: String, settings: RomSettings) {
        val obj = JSONObject().apply {
            put("speed",           settings.speed)
            put("theme",           settings.theme)
            put("ghostFrames",     settings.ghostFrames)
            put("glowEnabled",     settings.glowEnabled)
            put("quirk_vfReset",   settings.quirk_vfReset)
            put("quirk_memoryInc", settings.quirk_memoryInc)
            put("quirk_shifting",  settings.quirk_shifting)
            put("quirk_jumping",   settings.quirk_jumping)
            put("schipMode",       settings.schipMode)
            val keysObj = JSONObject()
            for ((k, v) in settings.keyLabels) keysObj.put(k.toString(), v)
            put("keyLabels", keysObj)
        }
        context.getSharedPreferences("rom_settings", Context.MODE_PRIVATE)
            .edit().putString(sanitize(romName), obj.toString()).apply()
    }

    fun load(context: Context, romName: String): RomSettings? {
        val json = context.getSharedPreferences("rom_settings", Context.MODE_PRIVATE)
            .getString(sanitize(romName), null) ?: return null
        return try {
            val obj = JSONObject(json)
            val keysObj = obj.optJSONObject("keyLabels") ?: JSONObject()
            val keyLabels = mutableMapOf<Int, String>()
            keysObj.keys().forEach { k -> keyLabels[k.toInt()] = keysObj.getString(k) }
            RomSettings(
                romName      = romName,
                speed        = obj.getInt("speed"),
                theme        = obj.getString("theme"),
                ghostFrames  = obj.getInt("ghostFrames"),
                glowEnabled  = obj.getBoolean("glowEnabled"),
                quirk_vfReset   = obj.getBoolean("quirk_vfReset"),
                quirk_memoryInc = obj.getBoolean("quirk_memoryInc"),
                quirk_shifting  = obj.getBoolean("quirk_shifting"),
                quirk_jumping   = obj.getBoolean("quirk_jumping"),
                schipMode    = obj.getBoolean("schipMode"),
                keyLabels    = keyLabels
            )
        } catch (e: Exception) { null }
    }

    private fun sanitize(name: String) =
        name.replace(Regex("[^a-zA-Z0-9_]"), "_").take(64)
}