import { WebPlugin } from '@capacitor/core';
import type { UsbCameraPluginInterface } from './usb-camera.plugin';
import type { UsbDeviceInfo, PreviewConfig, PreviewResult, StreamInfo } from '../models/DeviceProfile';

// Browser/web fallback – returns mock data so the UI can be developed in browser
export class UsbCameraWeb extends WebPlugin implements UsbCameraPluginInterface {
  async getConnectedDevices(): Promise<{ devices: UsbDeviceInfo[] }> {
    return {
      devices: [
        {
          deviceKey: 'mock-uvc-001',
          name: 'Demo UVC Camera',
          isUvc: true,
          vendorId: '046D',
          productId: '0825',
          maxWidth: 1920,
          maxHeight: 1080,
          maxFps: 60,
          formats: ['MJPG', 'YUY2', 'H264'],
          hasAudio: false,
        },
        {
          deviceKey: 'mock-easycap-002',
          name: 'EasyCap STK1160 + SAA7113 [Demo]',
          isUvc: false,
          vendorId: '05E1',
          productId: '0408',
          maxWidth: 720,
          maxHeight: 576,
          maxFps: 30,
          formats: ['YUY2'],
          hasAudio: true,
        },
      ],
    };
  }

  async openDevice(options: { deviceKey: string }): Promise<{ success: boolean }> {
    console.log('[Web] openDevice', options.deviceKey);
    return { success: true };
  }

  async startPreview(options: PreviewConfig): Promise<PreviewResult> {
    console.log('[Web] startPreview', options);
    // A public sample MJPEG stream so the browser preview shows something.
    return {
      success: true,
      streamUrl: 'https://upload.wikimedia.org/wikipedia/commons/2/2d/Snake_River_%285mb%29.gif',
      port: 8080,
    };
  }

  async stopPreview(): Promise<{ success: boolean }> {
    return { success: true };
  }

  async getStreamInfo(): Promise<StreamInfo> {
    return { running: false, streamUrl: '' };
  }

  async startRecording(): Promise<{ success: boolean; outputPath?: string }> {
    return { success: true, outputPath: '/storage/emulated/0/Videos/VID_demo.mp4' };
  }

  async stopRecording(): Promise<{ success: boolean; outputPath?: string }> {
    return { success: true, outputPath: '/storage/emulated/0/Videos/VID_demo.mp4' };
  }

  async capturePhoto(): Promise<{ success: boolean; outputPath?: string }> {
    return { success: true, outputPath: '/storage/emulated/0/Images/IMG_demo.jpg' };
  }

  async disconnect(): Promise<void> {
    console.log('[Web] disconnect');
  }

  async getStoragePath(): Promise<{ path: string }> {
    return { path: '/storage/emulated/0' };
  }

  async requestPermission(options: { deviceKey: string }): Promise<{ granted: boolean }> {
    console.log('[Web] requestPermission', options.deviceKey);
    return { granted: true };
  }

  async requestAppPermissions(): Promise<{ granted: boolean }> {
    return { granted: true };
  }

  async removeAllListeners(): Promise<void> {}
}
