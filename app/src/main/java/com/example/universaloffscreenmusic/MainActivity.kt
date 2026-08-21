package com.example.universaloffscreenmusic

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var switchMaster: SwitchMaterial
    private lateinit var switchAuto: SwitchMaterial
    private lateinit var switchEdge: SwitchMaterial
    private lateinit var toggleGroupMode: MaterialButtonToggleGroup
    private lateinit var toggleGroupTheme: MaterialButtonToggleGroup
    private lateinit var checkNext: MaterialCheckBox
    private lateinit var checkPrev: MaterialCheckBox
    private lateinit var checkPause: MaterialCheckBox
    private lateinit var btnSetup: Button
    private lateinit var btnStartSense: Button
    
    private lateinit var cardMode: MaterialCardView
    private lateinit var cardAuto: MaterialCardView
    private lateinit var cardEdge: MaterialCardView
    private lateinit var cardGestures: MaterialCardView
    private lateinit var txtGestureTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadPreferences()
        setupListeners()
        updatePermissionButton()
    }

    private fun initViews() {
        switchMaster = findViewById(R.id.switchMaster)
        switchAuto = findViewById(R.id.switchAuto)
        switchEdge = findViewById(R.id.switchEdge)
        toggleGroupMode = findViewById(R.id.toggleGroupMode)
        toggleGroupTheme = findViewById(R.id.toggleGroupTheme)
        checkNext = findViewById(R.id.checkNext)
        checkPrev = findViewById(R.id.checkPrev)
        checkPause = findViewById(R.id.checkPause)
        btnSetup = findViewById(R.id.btnSetup)
        btnStartSense = findViewById(R.id.btnStartSense)
        
        cardMode = findViewById(R.id.cardMode)
        cardAuto = findViewById(R.id.cardAuto)
        cardEdge = findViewById(R.id.cardEdge)
        cardGestures = findViewById(R.id.cardGestures)
        txtGestureTitle = findViewById(R.id.txtGestureTitle)
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("gestures_prefs", Context.MODE_PRIVATE)
        
        val isMasterOn = prefs.getBoolean("master_enabled", true)
        switchMaster.isChecked = isMasterOn
        switchAuto.isChecked = prefs.getBoolean("auto_activate", false)
        switchEdge.isChecked = prefs.getBoolean("edge_lighting_enabled", true)
        checkNext.isChecked = prefs.getBoolean("gesture_next", true)
        checkPrev.isChecked = prefs.getBoolean("gesture_prev", true)
        checkPause.isChecked = prefs.getBoolean("gesture_pause", true)
        
        val mode = prefs.getString("control_mode", "LOCK_SCREEN")
        toggleGroupMode.check(if (mode == "ANYWHERE") R.id.btnModeAnywhere else R.id.btnModeLock)

        val theme = prefs.getString("edge_lighting_theme", "RAINBOW")
        toggleGroupTheme.check(when (theme) {
            "AURORA" -> R.id.btnThemeAurora
            "FIRE" -> R.id.btnThemeFire
            else -> R.id.btnThemeRainbow
        })

        updateUIState(isMasterOn)
    }

    private fun setupListeners() {
        switchMaster.setOnCheckedChangeListener { _, isChecked ->
            updateUIState(isChecked)
            savePreferences()
        }

        switchAuto.setOnCheckedChangeListener { _, _ -> savePreferences() }
        switchEdge.setOnCheckedChangeListener { _, _ -> savePreferences() }
        checkNext.setOnCheckedChangeListener { _, _ -> savePreferences() }
        checkPrev.setOnCheckedChangeListener { _, _ -> savePreferences() }
        checkPause.setOnCheckedChangeListener { _, _ -> savePreferences() }

        toggleGroupMode.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) {
                savePreferences()
                updateUIState(switchMaster.isChecked)
            }
        }

        toggleGroupTheme.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) savePreferences()
        }

        btnSetup.setOnClickListener {
            if (areAllPermissionsGranted()) {
                Toast.makeText(this, "Sense is fully configured and active!", Toast.LENGTH_SHORT).show()
            } else {
                checkAndRequestPermissions()
            }
        }

        btnStartSense.setOnClickListener {
            if (areAllPermissionsGranted()) {
                val intent = Intent(this, GestureOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
            } else {
                Toast.makeText(this, "Please grant all permissions first", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUIState(isEnabled: Boolean) {
        cardAuto.alpha = if (isEnabled) 1.0f else 0.5f
        cardGestures.alpha = if (isEnabled) 1.0f else 0.5f
        txtGestureTitle.alpha = if (isEnabled) 1.0f else 0.5f
        
        switchAuto.isEnabled = isEnabled
        switchEdge.isEnabled = isEnabled
        toggleGroupMode.isEnabled = isEnabled
        toggleGroupTheme.isEnabled = isEnabled
        checkNext.isEnabled = isEnabled
        checkPrev.isEnabled = isEnabled
        checkPause.isEnabled = isEnabled
        
        findViewById<Button>(R.id.btnModeLock).isEnabled = isEnabled
        findViewById<Button>(R.id.btnModeAnywhere).isEnabled = isEnabled
        findViewById<Button>(R.id.btnThemeRainbow).isEnabled = isEnabled
        findViewById<Button>(R.id.btnThemeAurora).isEnabled = isEnabled
        findViewById<Button>(R.id.btnThemeFire).isEnabled = isEnabled

        if (isEnabled && toggleGroupMode.checkedButtonId == R.id.btnModeAnywhere) {
            btnStartSense.visibility = View.VISIBLE
        } else {
            btnStartSense.visibility = View.GONE
        }
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("gestures_prefs", Context.MODE_PRIVATE)
        val mode = if (toggleGroupMode.checkedButtonId == R.id.btnModeAnywhere) "ANYWHERE" else "LOCK_SCREEN"
        val theme = when (toggleGroupTheme.checkedButtonId) {
            R.id.btnThemeAurora -> "AURORA"
            R.id.btnThemeFire -> "FIRE"
            else -> "RAINBOW"
        }
        
        prefs.edit().apply {
            putBoolean("master_enabled", switchMaster.isChecked)
            putBoolean("auto_activate", switchAuto.isChecked)
            putBoolean("edge_lighting_enabled", switchEdge.isChecked)
            putBoolean("gesture_next", checkNext.isChecked)
            putBoolean("gesture_prev", checkPrev.isChecked)
            putBoolean("gesture_pause", checkPause.isChecked)
            putString("control_mode", mode)
            putString("edge_lighting_theme", theme)
        }.apply()
        
        val intent = Intent(this, UniversalMediaService::class.java).apply {
            action = "UPDATE_CONFIG"
        }
        startService(intent)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButton()
    }

    private fun areAllPermissionsGranted(): Boolean {
        val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val componentName = ComponentName(this, UniversalMediaService::class.java).flattenToString()
        val notification = enabledListeners?.contains(componentName) == true
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val battery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) powerManager.isIgnoringBatteryOptimizations(packageName) else true
        val audio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        return overlay && notification && battery && audio
    }

    private fun updatePermissionButton() {
        if (areAllPermissionsGranted()) {
            btnSetup.text = "System Ready \u2713"
            btnSetup.setBackgroundColor(android.graphics.Color.parseColor("#2E7D32")) // Green
            btnSetup.isEnabled = true
        } else {
            btnSetup.text = getString(R.string.permissions_btn)
            btnSetup.setBackgroundColor(getColor(R.color.primary))
            btnSetup.isEnabled = true
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant Overlay Permission", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1001)
            return
        }

        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val componentName = ComponentName(this, UniversalMediaService::class.java).flattenToString()
        if (enabledListeners == null || !enabledListeners.contains(componentName)) {
            Toast.makeText(this, "Enable Notification Access", Toast.LENGTH_SHORT).show()
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            return
        }
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            return
        }
        Toast.makeText(this, "All permissions ready!", Toast.LENGTH_SHORT).show()
        updatePermissionButton()
    }
}
