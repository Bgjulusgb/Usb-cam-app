import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.usbcam.app',
  appName: 'USB Cam',
  webDir: 'dist',
  // Keep http scheme so the WebView can reach http://localhost:8080/stream (MJPEG).
  // Android 15 still allows cleartext to 127.0.0.1 by default; we declare it
  // explicitly here and in the network_security_config.xml for clarity.
  server: {
    androidScheme: 'http',
    cleartext: true,
  },
  android: {
    // Allow loading the MJPEG stream (http://) while the app itself runs on http://.
    allowMixedContent: true,
    buildOptions: {
      releaseType: 'APK',
    },
  },
  plugins: {
    // overlaysWebView=true is required for Android 15's enforced edge-to-edge mode.
    // The web layer fills the entire screen; safe-area insets (status bar height,
    // navigation bar height) are injected as CSS env() variables via Capacitor's
    // window-insets bridge and consumed in app.css.
    StatusBar: {
      style: 'DARK',
      backgroundColor: '#00000000',
      overlaysWebView: true,
    },
  },
};

export default config;
