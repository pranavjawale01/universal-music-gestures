# Universal Music Gestures

An Android application that brings built-in, screen-off music gestures to **any** Android device. Control your media playback without turning on your screen or unlocking your phone.

## ✨ Features
* **Universal Media Support:** Works seamlessly with Spotify, YouTube Music, Apple Music, VLC, and most standard media players via Android's `MediaSessionManager`.
* **Screen-Off Gestures:**
    * Draw `>` to play the **Next** track.
    * Draw `<` to play the **Previous** track.
    * Swipe with two fingers `||` to **Play / Pause**.
* **Double-Tap to Wake:** Easily wake your screen with a quick double-tap.
* **AMOLED Friendly:** Uses a zero-brightness black overlay to keep the touch digitizer active while drawing virtually zero power on OLED/AMOLED screens.
* **Pocket Protection:** Integrates with the device's proximity sensor to ignore accidental touches when your phone is in your pocket or bag.
* **No Root Required:** Uses standard Android permissions (System Alert Window & Notification Listener) to function on stock devices.

## 🛠️ How It Works
Because standard Android cuts power to the touch digitizer when the screen is physically off, this app creates a simulated "Zero-Brightness Screen Off" state. It draws a pitch-black, full-screen overlay over your device, keeping the digitizer awake to register your finger paths while saving battery on OLED displays.

## 📱 Permissions Required
To function properly, the app will request the following permissions upon first launch:
1. **Display over other apps (System Alert Window):** Required to draw the black overlay.
2. **Notification Access:** Required to securely intercept and control active media sessions.
3. **Ignore Battery Optimizations:** Prevents the Android system from killing the background service while you are listening to music.

## 💻 Building the Project
1. Clone this repository:
   ```bash
   git clone [https://github.com/YourUsername/universal-music-gestures.git](https://github.com/YourUsername/universal-music-gestures.git)