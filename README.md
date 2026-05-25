# USB Cam — Android USB-Kamera App

**100 % lokal. Kein Cloud, kein Konto, kein Server, kein Tracking.** Ein
npm/Node.js-Projekt (TypeScript + Vite), das über **Capacitor** zu einer
nativen Android-**APK** kompiliert wird. Der Live-Zugriff auf die USB-Kamera
läuft komplett nativ (Android USB Host API + MediaCodec), die Oberfläche ist
eine schlanke Web-UI in einem WebView.

---

## Unterstützte Geräte (USB-C OTG)

| Kategorie | VID:PID | Details |
|---|---|---|
| **UVC-Kameras & Webcams** | Klasse `0x0E` | H.264, H.265/HEVC, MJPG, YUY2, NV12, P010 |
| **Video Grabber / Capture Cards** | Auto-Erkennung (UVC) | HDMI bis 4K (z. B. Elgato Cam Link, ezcap) |
| **EasyCap UTVF007/HTV600/HTV800** | `1B71:3002` | VGA analog, PAL/NTSC |
| **EasyCap STK1160 + SAA7113/GM7113** | `05E1:0408` | Composite/S-Video, Audio 48 kHz & 8 kHz |
| **EasyCap EM2860 + SAA7113/GM7113** | `EB1A:2861` | Composite |
| **EasyCap SMI2021 + SAA7113/GM7113** | `1C88:0007 / 003C / 003D / 003E / 003F / 1001` | alle PIDs |
| **Supercamera (Geek szitman)** | `2CE3:3828` | UVC |
| **Non-UVC** | Auto-Fallback | USB-Endoskope, IR-Kameras |

> ⚠️ **Praxis-Hinweis:** Hochwertiges OTG-Kabel verwenden. Geräte wie Elgato
> Cam Link ggf. über einen aktiven USB-Hub betreiben (Signalverstärkung).
> HEVC braucht Android 5+, AV1 braucht Android 10+.

> 🔴 **Wichtig zu „EasyCap" / analogen Sticks (VHS via Cinch/SCART):**
> Es gibt zwei völlig verschiedene Kategorien:
> - **UVC-konforme Sticks** (alle HDMI-Grabber, manche neueren Analog-Sticks):
>   funktionieren über die native UVC-Bibliothek (libuvc) – Live-Bild, Aufnahme,
>   Foto.
> - **Rohe Analog-Bridges** (STK1160, SMI2021/Somagic, EM2860): das sind **keine
>   UVC-Geräte**. Sie brauchen einen kernelnahen Treiber (unter Linux `stk1160`,
>   `smi2021`, `em28xx`) und lassen sich auf einem **nicht gerooteten Android**
>   in der Praxis **nicht** ansteuern – auch nicht mit libuvc. Die App erkennt
>   und benennt den Chip per VID:PID, kann ihn aber nur „best effort" versuchen.
>   Für solche Sticks: einen **UVC-/HDMI-Grabber** verwenden oder am PC capturen.

---

## Tech Stack (alles Open Source, kostenlos)

```
TypeScript + Vite         → Web-UI (kein Framework-Ballast)
Capacitor 7               → npm-Projekt → native Android-APK (Android 16 / API 36)
UVCAndroid (libusb/libuvc)→ nativer UVC-Zugriff inkl. isochroner USB-Transfers
Android USB Host API      → Geräteerkennung + analoger EasyCap-Versuch (Kotlin)
MediaCodec + MediaMuxer   → H.264/MP4 Aufnahme (ersetzt das eingestellte FFmpegKit)
Lokaler MJPEG-Server      → Live-Preview im WebView + optionaler LAN-Stream
```

Es gibt **keine** Abhängigkeit zu FFmpegKit (wurde im April 2025 abgekündigt
und aus Maven Central entfernt) — die Aufnahme nutzt ausschließlich Androids
eingebaute `MediaCodec`/`MediaMuxer`.

---

## Projektstruktur

```
/
├── src/                      → TypeScript Web-App
│   ├── main.ts               → Einstieg
│   ├── App.ts                → Router + State
│   ├── screens/              → Home, Preview, Settings, Gallery
│   ├── services/             → UsbCamera-, Storage-, Settings-Service
│   ├── models/               → VID/PID-Datenbank, Typen
│   ├── plugins/              → Capacitor-Plugin-Interface (+ Web-Mock)
│   └── styles/app.css        → Dark/Light Theme
├── native-android/           → Native Kotlin-Quellen (Source of Truth)
│   ├── kotlin/com/usbcam/app/
│   │   ├── MainActivity.kt
│   │   ├── UsbCameraPlugin.kt         → @CapacitorPlugin Bridge
│   │   ├── usb/                        → UsbDeviceManager, UvcNativeCamera (libuvc),
│   │   │                                 EasyCapDevice (analog best-effort), Profile
│   │   └── camera/
│   │       ├── MjpegStreamServer.kt    → Live-Preview-/LAN-Server
│   │       ├── FrameConverter.kt       → YUY2/MJPEG → JPEG/Bitmap
│   │       └── VideoRecorder.kt        → MediaCodec H.264 → MP4
│   ├── AndroidManifest.xml
│   └── res/xml/               → device_filter.xml, file_paths.xml
├── scripts/setup-android.mjs → generiert android/ und patcht den nativen Code ein
├── capacitor.config.ts
├── package.json
└── vite.config.ts
```

`android/` wird **generiert** (steht in `.gitignore`). `native-android/`
enthält den nativen Code, der beim Setup hineinkopiert wird — so liefert
Capacitor die korrekten Launcher-Icons, das Splash-Theme und den
Gradle-Wrapper, während unser USB-Code eingespielt wird.

---

## Build: npm-Projekt → APK

### Voraussetzungen (alle kostenlos)
- **Node.js 20+** — https://nodejs.org
- **Android Studio (Ladybug 2024.2.1+) + JDK 21** — https://developer.android.com/studio
- Android SDK 36 (Android 16), Umgebungsvariable `ANDROID_HOME` gesetzt

### Schritte

```bash
# 1. Abhängigkeiten installieren
npm install

# 2. Android-Projekt erzeugen + nativen USB-Code einspielen (einmalig)
npm run setup:android

# 3a. Debug-APK bauen
npm run apk:debug
#    → android/app/build/outputs/apk/debug/app-debug.apk

# 3b. Release-APK bauen (unsigniert)
npm run apk:release
#    → android/app/build/outputs/apk/release/app-release-unsigned.apk

# 4. Auf dem Handy installieren
adb install android/app/build/outputs/apk/debug/app-debug.apk
# oder die APK per Dateimanager direkt öffnen
```

Nach Änderungen am Web-Code reicht `npm run sync` (Build + Copy in die APK).
Nach Änderungen am nativen Code erneut `npm run setup:android` ausführen.
Zum kompletten Neuaufsetzen: `npm run clean:android && npm run setup:android`.

### Im Browser entwickeln
```bash
npm run dev      # Vite Dev-Server; nutzt automatisch den Web-Mock des Plugins
```

---

## Wie die Integration funktioniert

### USB-Erkennung & Berechtigungen
`UsbDeviceManager` lauscht via `BroadcastReceiver` auf
`USB_DEVICE_ATTACHED/DETACHED`. Beim Einstecken wird die VID/PID gegen
`DeviceDatabase` (alle EasyCap-Varianten, Supercamera …) geprüft. Trifft kein
Profil zu, gilt das Gerät als UVC, sobald es ein Interface der Klasse `0x0E`
hat. `res/xml/device_filter.xml` sorgt dafür, dass Android die App beim
Einstecken automatisch anbietet und die USB-Berechtigung erfragt. Beim
App-Start fordert das Plugin zusätzlich die Laufzeit-Berechtigungen an
(Mikrofon, Benachrichtigungen & Medien auf Android 13+); die USB-Geräte-
Berechtigung wird beim Verbinden separat abgefragt.

### Live-Preview (der „Backend"-Kern)
**UVC-Geräte** werden über den **nativen UVC-Stack `UVCAndroid`
(libusb + libuvc)** geöffnet. Nur so lassen sich die **isochronen** USB-
Transfers lesen, die UVC-Webcams und Capture-Sticks fast immer nutzen – die
reine Java/Kotlin-USB-Host-API kann das nicht. Die Frames kommen als NV21 per
Callback (`UvcNativeCamera`), werden zu JPEG gewandelt und in den lokalen
MJPEG-Server gespeist.

Ein WebView kann keinen rohen USB-Stream zeigen. Deshalb läuft im
App-Prozess ein **lokaler MJPEG-HTTP-Server** (`MjpegStreamServer`,
`http://localhost:8080/stream`). Jeder JPEG-Frame wird an alle Clients
gepusht; die UI zeigt ihn einfach in einem `<img>`. Ist „LAN-Stream" in den
Einstellungen aktiv, bindet der Server auf `0.0.0.0` und ist als
`http://<handy-ip>:8080/stream` im lokalen Netz erreichbar (kein Internet).

**Analoge Non-UVC-Sticks** (`EasyCapDevice`) werden nur „best effort"
versucht – siehe den roten Hinweis oben. Liefert der Chip kein UVC-Interface,
bleibt das Bild in der Regel aus.

### Aufnahme (ohne FFmpeg)
`VideoRecorder` nutzt `MediaCodec` (H.264-Encoder) + `MediaMuxer` (MP4).
Frames werden zu NV12 konvertiert und in den Encoder gegeben — rein nativ,
keine externen Binaries, gültiges MP4.

### VID/PID-Matching für alle EasyCap-Varianten
Siehe `native-android/kotlin/com/usbcam/app/usb/DeviceProfiles.kt` und
`src/models/VidPidDatabase.ts`. Beide enthalten dieselbe Tabelle (nativ +
Web) inkl. STK1160 (Audio 48 kHz/8 kHz), EM2860, alle SMI2021-PIDs und
UTVF007.

---

## Features
- Auto-Erkennung beim Einstecken + USB-Permission-Dialog
- Live-Preview (MJPEG), Multi-Auflösung & FPS, Codec-Auswahl
- Video aufnehmen (MP4/H.264), Foto (JPEG)
- Reconnect-Handling bei Trennung
- Basic-Filter (SW/Kontrast/Helligkeit/Sättigung) via CSS/GPU
- Optionaler lokaler RTSP-/MJPEG-LAN-Stream (kein Internet)
- Dark/Light Mode, Galerie für `Videos/` und `Images/`
