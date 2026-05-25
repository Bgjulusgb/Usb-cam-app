import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.usbcam.app',
  appName: 'USB Cam',
  webDir: 'dist',
  // Allow the WebView to load the local MJPEG preview server (http://localhost:PORT).
  server: {
    androidScheme: 'http',
    cleartext: true,
  },
  android: {
    allowMixedContent: true,
    buildOptions: {
      releaseType: 'APK',
    },
  },
  plugins: {
    StatusBar: {
      style: 'DARK',
      backgroundColor: '#0D0D1A',
      overlaysWebView: false,
    },
  },
};

export default config;
