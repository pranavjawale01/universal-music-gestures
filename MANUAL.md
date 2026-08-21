# 📖 Sense — Complete User Manual & Technical Documentation

---

## 📑 Table of Contents
1. [Overview & How It Works](#1-overview--how-it-works)
2. [Permissions Architecture](#2-permissions-architecture)
3. [Gesture Recognition Engine](#3-gesture-recognition-engine)
4. [Volume Math & Shoelace Algorithm](#4-volume-math--shoelace-algorithm)
5. [Ambient Edge Lighting System](#5-ambient-edge-lighting-system)
6. [Battery & Power Consumption Analysis](#6-battery--power-consumption-analysis)
7. [Edge Cases & System Safeguards](#7-edge-cases--system-safeguards)
8. [Troubleshooting FAQ](#8-troubleshooting-faq)

---

## 1. Overview & How It Works

### The Hardware Digitizer Challenge on Android
Standard Android OS shuts off power to the capacitive touchscreen digitizer when the device goes into deep sleep (`Display.STATE_OFF`). 

### The Sense Solution: Ambient Zero-Brightness Layer
Sense creates an intelligent, AMOLED-friendly lock surface:
- When you lock your phone while media is playing, `UniversalMediaService` detects `ACTION_SCREEN_OFF`.
- It silently presents `SenseLockActivity` over the keyguard with `setShowWhenLocked(true)` and `setTurnScreenOn(true)`.
- The screen renders **100% pure black pixels (`#000000`)**. On OLED/AMOLED displays, individual pixels are physically unpowered, giving the illusion of a sleeping screen while keeping the touch digitizer actively listening for your gestures.

---

## 2. Permissions Architecture

Sense requires 3 specific permissions to deliver a seamless experience:

### 1. Notification Listener Service (`BIND_NOTIFICATION_LISTENER_SERVICE`)
- **Why it's needed**: Intercepts active media sessions from players like Spotify, Apple Music, YouTube Music, VLC, and Podcast Addict via Android's `MediaSessionManager`.
- **How to grant**: Android Settings > Notification Access > Enable **Sense**.

### 2. Display Over Other Apps (`SYSTEM_ALERT_WINDOW`)
- **Why it's needed**: Enables the floating overlay mode for users who prefer using gestures within apps without locking their device.
- **How to grant**: Android Settings > Special App Access > Display Over Other Apps > Allow.

### 3. Ignore Battery Optimizations (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`)
- **Why it's needed**: Prevents aggressive Android OEM battery managers (e.g. MIUI, OneUI, ColorOS) from killing the media listener service during background playback.
- **How to grant**: Prompted automatically from the top-right permission indicator in the app.

---

## 3. Gesture Recognition Engine

Touch points are captured and normalized into geometric paths in real-time:

### Next Track (`>` or Swipe Right)
- Minimum horizontal bounding width $> 80\text{px}$.
- Detects either a right-pointing apex or a smooth horizontal swipe from left to right.

### Previous Track (`<` or Swipe Left)
- Minimum horizontal bounding width $> 80\text{px}$.
- Detects either a left-pointing apex or a smooth horizontal swipe from right to left.

### Play / Pause (2-Finger Tap)
- Detects simultaneous touch down of two distinct pointer IDs with minimal spatial displacement.

---

## 4. Volume Math & Shoelace Algorithm

To distinguish between **Clockwise (Volume +10%)** and **Anticlockwise (Volume -10%)** circles, Sense applies the **Shoelace Formula (Signed Polygon Area)** on screen coordinates:

$$\text{Signed Area} = \frac{1}{2} \sum_{i=0}^{n-1} (x_i y_{i+1} - x_{i+1} y_i) + (x_n y_0 - x_0 y_n)$$

- **$\text{Signed Area} > 0$**: Screen coordinate traversal is **Clockwise** $\rightarrow$ Increments media stream volume by 10% of maximum steps.
- **$\text{Signed Area} < 0$**: Screen coordinate traversal is **Anticlockwise** $\rightarrow$ Decrements media stream volume by 10% of maximum steps.
- Native Android volume HUD is shown using `AudioManager.FLAG_SHOW_UI` for clear visual feedback.

---

## 5. Ambient Edge Lighting System

- Custom canvas view (`EdgeLightingView`) calculating a dynamic gradient border shader with smooth phase progression.
- Toggled via the **Edge Lighting Effect** switch on the dashboard.
- When turned off, the border remains pitch black for maximum battery savings.

---

## 6. Battery & Power Consumption Analysis

- **AMOLED Pixel State**: Pure `#000000` pixels consume 0 mW on OLED panels.
- **CPU WakeLock Management**: Wakes CPU only during active music playback sessions.
- **Standby Draw**: When music stops, `SenseLockActivity` exits immediately, allowing the device to enter standard deep sleep (`Doze mode`).

---

## 7. Edge Cases & System Safeguards

| Event | System Action | Rationale |
| :--- | :--- | :--- |
| **Incoming Phone Call** | Instantly closes gesture canvas | Prevents interfering with incoming call UI |
| **Power Button Pressed** | Closes gesture canvas | Displays default system lockscreen |
| **Pocket / Proximity** | Ignores digitizer touches | Prevents accidental fabric touches |
| **Music Paused / Ended** | Closes gesture canvas | Eliminates unnecessary digitizer power draw |

---

## 8. Troubleshooting FAQ

**Q: Gestures are not working when screen is locked.**
- Ensure all 3 permission circles in the top-right header are green.
- Confirm music is actively playing in your music app before turning off the screen.

**Q: App keeps waking the screen.**
- Ensure battery optimization is disabled so Android does not continuously restart the service.

**Q: Can I adjust volume with one hand?**
- Yes, drawing a simple circular loop anywhere on screen will trigger volume adjustments instantly.
