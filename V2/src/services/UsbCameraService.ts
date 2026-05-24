import UsbCameraPlugin from '../plugins/usb-camera.plugin';
import type { UsbDeviceInfo, PreviewConfig } from '../models/DeviceProfile';

type DeviceConnectedHandler = (device: UsbDeviceInfo) => void;
type DeviceDisconnectedHandler = (deviceKey: string) => void;
type RecordingStateHandler = (isRecording: boolean, outputPath?: string) => void;
type ErrorHandler = (message: string) => void;

export class UsbCameraService {
  private listeners: Array<{ remove: () => void }> = [];
  private _onDeviceConnected?: DeviceConnectedHandler;
  private _onDeviceDisconnected?: DeviceDisconnectedHandler;
  private _onRecordingState?: RecordingStateHandler;
  private _onError?: ErrorHandler;

  set onDeviceConnected(h: DeviceConnectedHandler) { this._onDeviceConnected = h; }
  set onDeviceDisconnected(h: DeviceDisconnectedHandler) { this._onDeviceDisconnected = h; }
  set onRecordingState(h: RecordingStateHandler) { this._onRecordingState = h; }
  set onError(h: ErrorHandler) { this._onError = h; }

  async startListening(): Promise<void> {
    const [l1, l2, l3, l4] = await Promise.all([
      UsbCameraPlugin.addListener('deviceConnected', (d) => this._onDeviceConnected?.(d)),
      UsbCameraPlugin.addListener('deviceDisconnected', (d) => this._onDeviceDisconnected?.(d.deviceKey)),
      UsbCameraPlugin.addListener('recordingState', (d) => this._onRecordingState?.(d.isRecording, d.outputPath)),
      UsbCameraPlugin.addListener('error', (d) => this._onError?.(d.message)),
    ]);
    this.listeners = [l1, l2, l3, l4];
  }

  stopListening(): void {
    this.listeners.forEach(l => l.remove());
    this.listeners = [];
  }

  getConnectedDevices(): Promise<UsbDeviceInfo[]> {
    return UsbCameraPlugin.getConnectedDevices().then(r => r.devices);
  }

  async openDevice(deviceKey: string): Promise<boolean> {
    const { success } = await UsbCameraPlugin.openDevice({ deviceKey });
    return success;
  }

  async startPreview(config: PreviewConfig): Promise<boolean> {
    const { success } = await UsbCameraPlugin.startPreview(config);
    return success;
  }

  async stopPreview(): Promise<void> {
    await UsbCameraPlugin.stopPreview();
  }

  async startRecording(): Promise<string | undefined> {
    const { success, outputPath } = await UsbCameraPlugin.startRecording();
    return success ? outputPath : undefined;
  }

  async stopRecording(): Promise<string | undefined> {
    const { outputPath } = await UsbCameraPlugin.stopRecording();
    return outputPath;
  }

  async capturePhoto(): Promise<string | undefined> {
    const { success, outputPath } = await UsbCameraPlugin.capturePhoto();
    return success ? outputPath : undefined;
  }

  async disconnect(): Promise<void> {
    await UsbCameraPlugin.disconnect();
  }

  async requestPermission(deviceKey: string): Promise<boolean> {
    const { granted } = await UsbCameraPlugin.requestPermission({ deviceKey });
    return granted;
  }
}
