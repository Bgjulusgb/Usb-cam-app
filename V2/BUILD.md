# USB Cam V2 – Build & Setup Guide

## Tech Stack
- **Node.js / npm** + TypeScript + Vite (Frontend)
- **Capacitor 6** (npm → Android APK Bridge)
- **Kotlin** (Native Android USB plugin)
- **FFmpegKit** (Video encoding)

## Prerequisites (all free)

```bash
# Node.js 18+
https://nodejs.org

# Android Studio + JDK 17
https://developer.android.com/studio

# Android SDK 34, NDK 25
# Set ANDROID_HOME env variable

export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

## Build Steps

### 1. Install npm dependencies
```bash
cd V2
npm install
```

### 2. Add Android platform (first time only)
```bash
npx cap add android
```

### 3. Build web assets + sync to Android
```bash
npm run build       # TypeScript → JS bundle in dist/
npx cap sync android  # Copy dist/ to android/app/src/main/assets/public
```

### 4a. Build Debug APK
```bash
cd android
./gradlew assembleDebug
# Output: android/app/build/outputs/apk/debug/app-debug.apk
```

### 4b. Build Release APK (unsigned)
```bash
cd android
./gradlew assembleRelease
# Output: android/app/build/outputs/apk/release/app-release-unsigned.apk
```

### 4c. One-liner (build + APK)
```bash
npm run build:apk
```

### 5. Install on Android device
```bash
adb install android/app/build/outputs/apk/debug/app-debug.apk
# or copy APK directly via USB file manager
```

## Supported Devices

| Device | VID:PID | Notes |
|--------|---------|-------|
| All UVC cameras | Class 0x0E | H264/H265/MJPG/YUY2/NV12/P010 |
| EasyCap UTVF007 | 1B71:3002 | PAL/NTSC analog |
| EasyCap STK1160 | 05E1:0408 | + 48kHz/8kHz audio |
| EasyCap EM2860  | EB1A:2861 | Composite/S-Video |
| EasyCap SMI2021 | 1C88:0007/003C/3D/3E/3F/1001 | All PIDs |
| Supercamera Geek | 2CE3:3828 | VID 2ce3 |
| Capture Cards | Auto-detect | Elgato/ezcap/AVerMedia |
| Endoscopes | Auto-detect | Non-UVC fallback |

## Architecture

```
V2/
├── src/                    ← TypeScript (npm) frontend
│   ├── main.ts             ← Entry point
│   ├── App.ts              ← App router + state
│   ├── screens/            ← Home, Preview, Settings, Gallery
│   ├── services/           ← USB, Storage, Settings services
│   ├── models/             ← DeviceProfile, VID/PID DB
│   ├── plugins/            ← Capacitor plugin interface
│   └── styles/app.css      ← Dark/Light theme CSS
├── android/                ← Android project (Capacitor)
│   └── app/src/main/kotlin/com/usbcam/v2/
│       ├── MainActivity.kt         ← Capacitor BridgeActivity
│       ├── UsbCameraPlugin.kt      ← @CapacitorPlugin bridge
│       └── usb/
│           ├── DeviceProfiles.kt   ← VID/PID database
│           ├── UsbDeviceManager.kt ← Attach/detach/permission
│           ├── UvcCameraDevice.kt  ← UVC protocol (USB Host API)
│           └── EasyCapDevice.kt    ← EasyCap analog capture
├── capacitor.config.ts     ← Capacitor config
├── package.json            ← npm project
└── vite.config.ts          ← Vite bundler

Data flow:
  USB Device → Android USB Host API → Kotlin (UvcCameraDevice/EasyCapDevice)
  → UsbCameraPlugin (@CapacitorPlugin) → Capacitor Bridge
  → TypeScript (UsbCameraService) → App.ts UI
```

## How Capacitor Converts npm → APK

1. `npm run build` → Vite bundles TypeScript to `dist/`
2. `npx cap sync` → copies `dist/` into Android's `assets/public/`
3. Capacitor's `BridgeActivity` wraps a WebView loading `assets/public/index.html`
4. Plugin methods are called from JS via `@capacitor/core`'s bridge
5. `./gradlew assembleRelease` → compiles Kotlin + bundles → `.apk`

## No internet, no cloud, no tracking
- 100% offline APK
- All data stored in /sdcard/Android/data/com.usbcam.v2/files/
- Videos/ and Images/ subfolders
- No analytics, no permissions except USB + Storage + Microphone
