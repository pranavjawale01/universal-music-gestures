package com.example.universaloffscreenmusic

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var switchMaster: SwitchMaterial
    private lateinit var switchAuto: SwitchMaterial
    private lateinit var switchEdge: SwitchMaterial
    private lateinit var toggleGroupMode: MaterialButtonToggleGroup
    private lateinit var checkNext: MaterialCheckBox
    private lateinit var checkPrev: MaterialCheckBox
    private lateinit var checkPause: MaterialCheckBox
    private lateinit var checkVolUp: MaterialCheckBox
    private lateinit var checkVolDown: MaterialCheckBox
    private lateinit var btnStartSense: Button
    
    private lateinit var cardMaster: MaterialCardView
    private lateinit var cardMode: MaterialCardView
    private lateinit var cardAuto: View
    private lateinit var cardEdge: MaterialCardView
    private lateinit var txtGestureTitle: TextView

    private lateinit var layoutPermissionsHeader: LinearLayout
    private lateinit var badgePermMedia: MaterialCardView
    private lateinit var badgePermOverlay: MaterialCardView
    private lateinit var badgePermBattery: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        loadPreferences()
        setupListeners()
        updatePermissionBadges()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                android.service.notification.NotificationListenerService.requestRebind(
                    ComponentName(this, UniversalMediaService::class.java)
                )
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    private fun initViews() {
        switchMaster = findViewById(R.id.switchMaster)
        switchAuto = findViewById(R.id.switchAuto)
        switchEdge = findViewById(R.id.switchEdge)
        toggleGroupMode = findViewById(R.id.toggleGroupMode)
        checkNext = findViewById(R.id.checkNext)
        checkPrev = findViewById(R.id.checkPrev)
        checkPause = findViewById(R.id.checkPause)
        checkVolUp = findViewById(R.id.checkVolUp)
        checkVolDown = findViewById(R.id.checkVolDown)
        btnStartSense = findViewById(R.id.btnStartSense)
        
        cardMaster = findViewById(R.id.cardMaster)
        cardMode = findViewById(R.id.cardMode)
        cardAuto = findViewById(R.id.cardAuto)
        cardEdge = findViewById(R.id.cardEdge)
        txtGestureTitle = findViewById(R.id.txtGestureTitle)

        layoutPermissionsHeader = findViewById(R.id.layoutPermissionsHeader)
        badgePermMedia = findViewById(R.id.badgePermMedia)
        badgePermOverlay = findViewById(R.id.badgePermOverlay)
        badgePermBattery = findViewById(R.id.badgePermBattery)
    }

    private fun loadPreferences() {
        val prefs = getSharedPreferences("gestures_prefs", Context.MODE_PRIVATE)
        
        val isMasterOn = prefs.getBoolean("master_enabled", true)
        switchMaster.isChecked = isMasterOn
        switchAuto.isChecked = prefs.getBoolean("auto_activate", true)
        switchEdge.isChecked = prefs.getBoolean("edge_lighting_enabled", true)
        checkNext.isChecked = prefs.getBoolean("gesture_next", true)
        checkPrev.isChecked = prefs.getBoolean("gesture_prev", true)
        checkPause.isChecked = prefs.getBoolean("gesture_pause", true)
        checkVolUp.isChecked = prefs.getBoolean("gesture_vol_up", true)
        checkVolDown.isChecked = prefs.getBoolean("gesture_vol_down", true)
        
        val mode = prefs.getString("control_mode", "LOCK_SCREEN")
        toggleGroupMode.check(if (mode == "ANYWHERE") R.id.btnModeAnywhere else R.id.btnModeLock)

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
        checkVolUp.setOnCheckedChangeListener { _, _ -> savePreferences() }
        checkVolDown.setOnCheckedChangeListener { _, _ -> savePreferences() }

        toggleGroupMode.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) {
                savePreferences()
                updateUIState(switchMaster.isChecked)
            }
        }

        layoutPermissionsHeader.setOnClickListener {
            checkAndRequestPermissions()
        }
        badgePermMedia.setOnClickListener {
            checkAndRequestPermissions()
        }
        badgePermOverlay.setOnClickListener {
            checkAndRequestPermissions()
        }
        badgePermBattery.setOnClickListener {
            checkAndRequestPermissions()
        }

        btnStartSense.setOnClickListener {
            if (areAllPermissionsGranted()) {
                val intent = Intent(this, SenseLockActivity::class.java).apply {
                    putExtra("is_test_mode", true)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please grant permissions (indicated by red dots at top right)", Toast.LENGTH_LONG).show()
                checkAndRequestPermissions()
            }
        }
    }

    private fun updateUIState(isEnabled: Boolean) {
        cardAuto.alpha = if (isEnabled) 1.0f else 0.5f
        cardMode.alpha = if (isEnabled) 1.0f else 0.5f
        cardEdge.alpha = if (isEnabled) 1.0f else 0.5f
        txtGestureTitle.alpha = if (isEnabled) 1.0f else 0.5f
        
        switchAuto.isEnabled = isEnabled
        switchEdge.isEnabled = isEnabled
        toggleGroupMode.isEnabled = isEnabled
        checkNext.isEnabled = isEnabled
        checkPrev.isEnabled = isEnabled
        checkPause.isEnabled = isEnabled
        checkVolUp.isEnabled = isEnabled
        checkVolDown.isEnabled = isEnabled
        
        findViewById<Button>(R.id.btnModeLock).isEnabled = isEnabled
        findViewById<Button>(R.id.btnModeAnywhere).isEnabled = isEnabled

        btnStartSense.visibility = if (isEnabled) View.VISIBLE else View.GONE
    }

    private fun savePreferences() {
        val prefs = getSharedPreferences("gestures_prefs", Context.MODE_PRIVATE)
        val mode = if (toggleGroupMode.checkedButtonId == R.id.btnModeAnywhere) "ANYWHERE" else "LOCK_SCREEN"
        
        prefs.edit().apply {
            putBoolean("master_enabled", switchMaster.isChecked)
            putBoolean("auto_activate", switchAuto.isChecked)
            putBoolean("edge_lighting_enabled", switchEdge.isChecked)
            putBoolean("gesture_next", checkNext.isChecked)
            putBoolean("gesture_prev", checkPrev.isChecked)
            putBoolean("gesture_pause", checkPause.isChecked)
            putBoolean("gesture_vol_up", checkVolUp.isChecked)
            putBoolean("gesture_vol_down", checkVolDown.isChecked)
            putString("control_mode", mode)
            putString("edge_lighting_theme", "RAINBOW")
        }.apply()
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                android.service.notification.NotificationListenerService.requestRebind(
                    ComponentName(this, UniversalMediaService::class.java)
                )
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionBadges()
    }

    private fun isMediaPermissionGranted(): Boolean {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val componentName = ComponentName(this, UniversalMediaService::class.java).flattenToString()
        return enabledListeners?.contains(componentName) == true
    }

    private fun isOverlayPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
    }

    private fun isBatteryPermissionGranted(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) powerManager.isIgnoringBatteryOptimizations(packageName) else true
    }

    private fun areAllPermissionsGranted(): Boolean {
        return isMediaPermissionGranted() && isOverlayPermissionGranted() && isBatteryPermissionGranted()
    }

    private fun updatePermissionBadges() {
        val green = Color.parseColor("#10B981")
        val red = Color.parseColor("#EF4444")

        badgePermMedia.setCardBackgroundColor(if (isMediaPermissionGranted()) green else red)
        badgePermOverlay.setCardBackgroundColor(if (isOverlayPermissionGranted()) green else red)
        badgePermBattery.setCardBackgroundColor(if (isBatteryPermissionGranted()) green else red)
    }

    private fun checkAndRequestPermissions() {
        if (!isMediaPermissionGranted()) {
            Toast.makeText(this, "Enable Notification & Media Access", Toast.LENGTH_SHORT).show()
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            return
        }

        if (!isOverlayPermissionGranted()) {
            Toast.makeText(this, "Grant Display Over Other Apps Permission", Toast.LENGTH_SHORT).show()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
            return
        }

        if (!isBatteryPermissionGranted()) {
            Toast.makeText(this, "Grant Background Battery Access", Toast.LENGTH_SHORT).show()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
            return
        }

        Toast.makeText(this, "All permissions granted and active!", Toast.LENGTH_SHORT).show()
        updatePermissionBadges()
    }
}
