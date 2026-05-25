import { registerPlugin } from '@capacitor/core';
import type { UsbDeviceInfo, PreviewConfig, PreviewResult, StreamInfo } from '../models/DeviceProfile';

// ─── Plugin Interface ───────────────────────────────────────────────────────
export interface UsbCameraPluginInterface {
  /** List all currently connected USB devices */
  getConnectedDevices(): Promise<{ devices: UsbDeviceInfo[] }>;

  /** Open a specific USB device and prepare it for streaming */
  openDevice(options: { deviceKey: string }): Promise<{ success: boolean; error?: string }>;

  /** Start video preview; returns the local MJPEG stream URL for an <img> tag */
  startPreview(options: PreviewConfig): Promise<PreviewResult>;

  /** Stop video preview and shut down the MJPEG server */
  stopPreview(): Promise<{ success: boolean }>;

  /** Query the current preview stream state */
  getStreamInfo(): Promise<StreamInfo>;

  /** Start MP4 recording */
  startRecording(): Promise<{ success: boolean; outputPath?: string }>;

  /** Stop recording and finalize the file */
  stopRecording(): Promise<{ success: boolean; outputPath?: string }>;

  /** Capture a JPEG photo */
  capturePhoto(): Promise<{ success: boolean; outputPath?: string }>;

  /** Disconnect the current device */
  disconnect(): Promise<void>;

  /** Get external storage path for media files */
  getStoragePath(): Promise<{ path: string }>;

  /** Request USB permission for a device */
  requestPermission(options: { deviceKey: string }): Promise<{ granted: boolean }>;

  /** Request runtime app permissions (microphone, notifications & media on Android 13+) */
  requestAppPermissions(): Promise<{ granted: boolean }>;

  /** Add event listener */
  addListener(
    eventName: 'deviceConnected',
    handler: (data: UsbDeviceInfo) => void
  ): Promise<{ remove: () => void }>;
  addListener(
    eventName: 'deviceDisconnected',
    handler: (data: { deviceKey: string }) => void
  ): Promise<{ remove: () => void }>;
  addListener(
    eventName: 'recordingState',
    handler: (data: { isRecording: boolean; outputPath?: string }) => void
  ): Promise<{ remove: () => void }>;
  addListener(
    eventName: 'error',
    handler: (data: { message: string }) => void
  ): Promise<{ remove: () => void }>;

  removeAllListeners(): Promise<void>;
}

// ─── Register the plugin (bridged to Kotlin UsbCameraPlugin) ───────────────
const UsbCameraPlugin = registerPlugin<UsbCameraPluginInterface>('UsbCamera', {
  // Web implementation for browser testing (returns mock data)
  web: () => import('./usb-camera.web').then(m => new m.UsbCameraWeb()),
});

export default UsbCameraPlugin;
