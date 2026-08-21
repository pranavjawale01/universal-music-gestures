<div align="center">

# 🎵 Sense
### Intelligent Off-Screen Music Gestures for Android

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-10B981?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Root-Not%20Required-6366F1?style=for-the-badge" />
  <img src="https://img.shields.io/badge/License-MIT-3B82F6?style=for-the-badge" />
  <img src="https://img.shields.io/badge/Status-Active%20Build-emerald?style=for-the-badge" />
</p>

<br/>

<a href="https://github.com/pranavjawale01/universal-music-gestures/raw/refs/heads/main/output/Sense.apk" download="Sense.apk">
  <img src="https://img.shields.io/badge/⚡%20DOWNLOAD%20SENSE%20APK%20(v1.0)-10B981?style=for-the-badge&logo=android&logoColor=white&labelColor=059669" alt="Direct Download APK" height="50"/>
</a>

<br/><br/>

> 📲 **[Direct Download APK Link](https://github.com/pranavjawale01/universal-music-gestures/raw/refs/heads/main/output/Sense.apk)** *(Tap to start direct download on your device)*

---

</div>

## 🌟 Highlights

<table>
<tr>
<td width="33%" align="center">
<h3>📱 Screen-Off Gestures</h3>
<p>Control media playback on a dark AMOLED canvas without unlocking your device.</p>
</td>
<td width="33%" align="center">
<h3>🔊 Dial Volume Loops</h3>
<p>Draw clockwise ↻ for Volume Up (+10%) and anticlockwise ↺ for Volume Down (-10%).</p>
</td>
<td width="33%" align="center">
<h3>🌈 Ambient Neon Glow</h3>
<p>Flowing multi-color border lighting while music is playing. Consumes 0 mW when off.</p>
</td>
</tr>
</table>

---

## ⚡ Quick Gesture Guide

| Gesture | Action | Description |
| :---: | :--- | :--- |
| **`>`** | **Next Track** | Draw right arrow or swipe right |
| **`<`** | **Previous Track** | Draw left arrow or swipe left |
| **`✌️`** | **Play / Pause** | Two-finger tap anywhere on screen |
| **`↻`** | **Volume Up (+10%)** | Draw a clockwise circular loop |
| **`↺`** | **Volume Down (-10%)** | Draw an anticlockwise circular loop |
| **`👆`** | **Exit / Unlock** | Single tap or press power key to reveal lockscreen |

---

## 🚀 Quick Start & Installation Guide

### 1. Download & Install
Grab the latest **[Sense.apk](https://github.com/pranavjawale01/universal-music-gestures/raw/refs/heads/main/output/Sense.apk)** directly on your Android phone.

> [!TIP]
> ### 🛡️ If you see "App blocked by Google Play Protect"
> Android 13/14+ includes a strict sideloading protection shield that blocks manual APK installs for apps requesting notification access (`BIND_NOTIFICATION_LISTENER_SERVICE`, which Sense uses solely for media controls like Spotify / Apple Music / YouTube Music).
>
> **To install smoothly:**
> 1. Open the **Google Play Store** app on your phone.
> 2. Tap your **Profile icon** (top right) ➔ **Play Protect**.
> 3. Tap the **Settings (⚙️ gear)** icon in the top right corner.
> 4. Turn **OFF** **"Scan apps with Play Protect"** (tap *Turn off* when prompted).
> 5. Return to your **Downloads / File Manager** and tap **Sense.apk** to install.
> 6. *(Optional)* You can turn Play Protect back ON after installation completes.

### 2. Grant Permissions
Open Sense and tap the 3 status badges in the top-right corner until all turn 🟢:
- **Media Access**: Connects to active media playback sessions.
- **Overlay Access**: Enables gesture capture surface over lockscreen.
- **Battery Optimization**: Keeps background service alive when phone sleeps.

### 3. Play Music & Lock
Start playing music on Spotify, YouTube Music, Apple Music, or VLC, then lock your device to use gestures!

---

## 📚 Complete User Manual & Architecture

For in-depth explanations on the **Shoelace gesture engine**, **battery consumption**, **permission internals**, and **edge cases**, check out the dedicated technical guide:

👉 **[Read the Full Sense User Manual (MANUAL.md)](MANUAL.md)**

---

## 💻 Build from Source

```bash
git clone https://github.com/pranavjawale01/universal-music-gestures.git
cd universal-music-gestures
./gradlew assembleDebug
```

---

<div align="center">
  <sub>Built with ❤️ for Android • Open-source under the MIT License</sub>
</div>