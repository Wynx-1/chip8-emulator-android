package com.example.chip8emulator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject

data class RomEntry(
    val name: String,
    val uriString: String,
    val sizeBytes: Int
)

class RomLibraryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty:      TextView
    private lateinit var tvRomCount:   TextView
    private lateinit var btnAdd:       Button

    private val romList = mutableListOf<RomEntry>()
    private lateinit var adapter: RomAdapter

    private val romPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) addRomFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rom_library)
        enableFullscreen()

        // ── Bind views ──
        recyclerView = findViewById(R.id.romRecyclerView)
        tvEmpty      = findViewById(R.id.tvEmptyLibrary)
        tvRomCount   = findViewById(R.id.tvRomCount)
        btnAdd       = findViewById(R.id.btnAddRom)

        // ── Back button ──
        findViewById<Button>(R.id.btnBackLibrary).setOnClickListener {
            finish()
        }

        // ── Setup RecyclerView ──
        adapter = RomAdapter(
            roms     = romList,
            onPlay   = { rom -> launchRom(rom) },
            onDelete = { rom -> confirmDelete(rom) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // ── Add ROM button ──
        btnAdd.setOnClickListener {
            romPickerLauncher.launch(arrayOf("*/*"))
        }

        loadLibrary()
        updateEmptyState()
    }

    // ─────────────────────────────────────────
    // Launch ROM — send result back to MainActivity
    // ─────────────────────────────────────────
    private fun launchRom(rom: RomEntry) {
        val result = Intent().apply {
            putExtra("ROM_URI",  rom.uriString)
            putExtra("ROM_NAME", rom.name)
        }
        setResult(android.app.Activity.RESULT_OK, result)
        finish()
    }

    // ─────────────────────────────────────────
    // Add ROM from URI
    // ─────────────────────────────────────────
    private fun addRomFromUri(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            val bytes = contentResolver.openInputStream(uri)
                ?.use { it.readBytes() } ?: return

            if (bytes.size > 3584) { toast("ROM too large!"); return }

            val name  = getProperFileName(uri)
            val entry = RomEntry(name, uri.toString(), bytes.size)

            if (romList.none { it.uriString == entry.uriString }) {
                romList.add(entry)
                adapter.notifyItemInserted(romList.size - 1)
                saveLibrary()
                updateEmptyState()
                toast("Added: $name")
            } else {
                toast("ROM already in library")
            }

        } catch (e: Exception) {
            toast("Error: ${e.message}")
        }
    }

    // ─────────────────────────────────────────
    // Get real filename from URI
    // ─────────────────────────────────────────
    private fun getProperFileName(uri: Uri): String {
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(
                        android.provider.OpenableColumns.DISPLAY_NAME
                    )
                    if (nameIndex >= 0) {
                        val fullName = it.getString(nameIndex) ?: ""
                        if (fullName.isNotEmpty()) {
                            return fullName
                                .removeSuffix(".ch8")
                                .removeSuffix(".CH8")
                                .removeSuffix(".c8")
                                .trim()
                        }
                    }
                }
            }
        }
        val path = uri.lastPathSegment ?: uri.toString()
        return path
            .substringAfterLast('/')
            .substringAfterLast(':')
            .removeSuffix(".ch8")
            .removeSuffix(".CH8")
            .removeSuffix(".c8")
            .trim()
            .ifEmpty { "ROM_${System.currentTimeMillis()}" }
    }

    // ─────────────────────────────────────────
    // Confirm delete dialog
    // ─────────────────────────────────────────
    private fun confirmDelete(rom: RomEntry) {
        AlertDialog.Builder(this)
            .setTitle("Remove ROM")
            .setMessage("Remove \"${rom.name}\" from library?")
            .setPositiveButton("Remove") { _, _ ->
                val idx = romList.indexOf(rom)
                romList.remove(rom)
                adapter.notifyItemRemoved(idx)
                saveLibrary()
                updateEmptyState()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─────────────────────────────────────────
    // Update empty state — ONE function only
    // ─────────────────────────────────────────
    private fun updateEmptyState() {
        if (romList.isEmpty()) {
            recyclerView.visibility = View.GONE
            tvEmpty.visibility      = View.VISIBLE
            tvRomCount.text         = ""
        } else {
            recyclerView.visibility = View.VISIBLE
            tvEmpty.visibility      = View.GONE
            tvRomCount.text         = "${romList.size} ROM${if (romList.size == 1) "" else "s"}"
        }
    }

    // ─────────────────────────────────────────
    // Save library to SharedPreferences
    // ─────────────────────────────────────────
    private fun saveLibrary() {
        val arr = JSONArray()
        for (rom in romList) {
            val obj = JSONObject()
            obj.put("name", rom.name)
            obj.put("uri",  rom.uriString)
            obj.put("size", rom.sizeBytes)
            arr.put(obj)
        }
        getSharedPreferences("chip8_library", Context.MODE_PRIVATE)
            .edit()
            .putString("roms", arr.toString())
            .apply()
    }

    // ─────────────────────────────────────────
    // Load library from SharedPreferences
    // ─────────────────────────────────────────
    private fun loadLibrary() {
        val json = getSharedPreferences("chip8_library", Context.MODE_PRIVATE)
            .getString("roms", "[]") ?: "[]"
        try {
            val arr = JSONArray(json)
            romList.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                romList.add(RomEntry(
                    obj.getString("name"),
                    obj.getString("uri"),
                    obj.getInt("size")
                ))
            }
            adapter.notifyDataSetChanged()
        } catch (e: Exception) {
            // Corrupt data — start fresh
        }
    }

    // ─────────────────────────────────────────
    private fun enableFullscreen() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.hide(
                android.view.WindowInsets.Type.statusBars() or
                        android.view.WindowInsets.Type.navigationBars()
            )
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

// ─────────────────────────────────────────
// RecyclerView Adapter
// ─────────────────────────────────────────
class RomAdapter(
    private val roms:     List<RomEntry>,
    private val onPlay:   (RomEntry) -> Unit,
    private val onDelete: (RomEntry) -> Unit
) : RecyclerView.Adapter<RomAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name:   TextView    = view.findViewById(R.id.tvRomName)
        val size:   TextView    = view.findViewById(R.id.tvRomSize)
        val delete: ImageButton = view.findViewById(R.id.btnDeleteRom)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rom, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rom = roms[position]
        holder.name.text = rom.name
        holder.size.text = "${rom.sizeBytes} bytes"
        holder.itemView.setOnClickListener { onPlay(rom) }
        holder.delete.setOnClickListener   { onDelete(rom) }
    }

    override fun getItemCount() = roms.size
}