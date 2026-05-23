import { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.usbcam.v2',
  appName: 'USB Cam V2',
  webDir: 'dist',
  android: {
    buildOptions: {
      keystorePath: undefined,
      releaseType: 'APK',
    },
  },
  plugins: {
    StatusBar: {
      style: 'Dark',
      backgroundColor: '#0D0D1A',
    },
  },
};

export default config;
