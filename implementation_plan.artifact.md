# Implementation Plan - Fix and Finalize Universal Music Gestures

This plan addresses build failures and logic gaps to ensure the app is ready for installation and reliable use.

## User Review Required

> [!IMPORTANT]
> The app requires **Notification Access**, **Overlay Permission**, and **Battery Optimization exclusion** to function. These will be requested on the first launch via the "Setup" button.

## Proposed Changes

### Build Infrastructure & Resources

#### [NEW] [ic_launcher_background.xml](file:///C:/Users/HP/OneDrive/Desktop/Project/universal-music-gestures/app/src/main/res/drawable/ic_launcher_background.xml)
#### [NEW] [ic_launcher_foreground.xml](file:///C:/Users/HP/OneDrive/Desktop/Project/universal-music-gestures/app/src/main/res/drawable/ic_launcher_foreground.xml)
#### [NEW] [ic_launcher.xml](file:///C:/Users/HP/OneDrive/Desktop/Project/universal-music-gestures/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml)
#### [NEW] [ic_launcher_round.xml](file:///C:/Users/HP/OneDrive/Desktop/Project/universal-music-gestures/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml)
*   Provides the missing launcher icons required for a successful build.

---

### Media Control Logic

#### [MODIFY] [UniversalMediaService.kt](file:///C:/Users/HP/OneDrive/Desktop/Project/universal-music-gestures/app/src/main/java/com/example/universaloffscreenmusic/UniversalMediaService.kt)
*   Implement `OnActiveSessionsChangedListener` to automatically track which music app is currently playing (Spotify, YouTube Music, etc.).
*   Update `activeController` dynamically so gestures always target the "live" music session.

---

### Gesture & Overlay Logic

#### [MODIFY] [GestureOverlayService.kt](file:///C:/Users/HP/OneDrive/Desktop/Project/universal-music-gestures/app/src/main/java/com/example/universaloffscreenmusic/GestureOverlayService.kt)
*   Refine `wakeUpScreen` to use modern PowerManager flags where possible.
*   Optimize proximity sensor handling (ensure it doesn't prevent gestures when the screen is intentionally off).
*   Add a small "cooldown" or visual hint if needed (though keeping it black is the goal).

## Verification Plan

### Automated Tests
*   `./gradlew assembleDebug` to verify the build error is resolved.

### Manual Verification
1.  Install the APK.
2.  Grant all three required permissions.
3.  Open a music app and play a song.
4.  Press "Start" in the app (screen should go black).
5.  Perform gestures (Next, Previous, Pause) and verify music reacts.
6.  Double-tap to wake up the screen.
