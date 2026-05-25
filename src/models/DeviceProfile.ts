export enum DeviceType {
  UVC_STANDARD = 'uvc_standard',
  EASYCAP_UTVF007 = 'easycap_utvf007',
  EASYCAP_STK1160 = 'easycap_stk1160',
  EASYCAP_EM2860 = 'easycap_em2860',
  EASYCAP_SMI2021 = 'easycap_smi2021',
  SUPERCAMERA_GEEK = 'supercamera_geek',
  NON_UVC_ENDOSCOPE = 'non_uvc_endoscope',
  CAPTURE_CARD = 'capture_card',
  UNKNOWN = 'unknown',
}

export interface DeviceProfile {
  vendorId: number;
  productId: number;
  deviceType: DeviceType;
  name: string;
  maxResolutionWidth: number;
  maxResolutionHeight: number;
  maxFps: number;
  supportsAudio: boolean;
  audioSampleRates: number[];
  supportedFormats: string[];
  isUvc: boolean;
  notes: string;
}

export interface UsbDeviceInfo {
  deviceKey: string;
  name: string;
  isUvc: boolean;
  vendorId: string;
  productId: string;
  maxWidth: number;
  maxHeight: number;
  maxFps: number;
  formats: string[];
  hasAudio: boolean;
  profile?: DeviceProfile;
}

export interface PreviewConfig {
  width: number;
  height: number;
  fps: number;
  format: string;
  lanAccessible?: boolean;
}

export interface PreviewResult {
  success: boolean;
  streamUrl: string;
  port: number;
  lanUrl?: string;
}

export interface StreamInfo {
  running: boolean;
  streamUrl: string;
}

export interface RecordingState {
  isRecording: boolean;
  outputPath?: string;
  durationSeconds?: number;
}
