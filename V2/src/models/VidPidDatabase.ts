import { DeviceProfile, DeviceType } from './DeviceProfile';

export const DEVICE_DATABASE: DeviceProfile[] = [
  // ─── EasyCap UTVF007 / HTV600 / HTV800 ───────────────────────────
  {
    vendorId: 0x1B71, productId: 0x3002,
    deviceType: DeviceType.EASYCAP_UTVF007,
    name: 'EasyCap UTVF007/HTV600/HTV800',
    maxResolutionWidth: 720, maxResolutionHeight: 576,
    maxFps: 30, supportsAudio: false,
    audioSampleRates: [],
    supportedFormats: ['YUY2', 'MJPG'],
    isUvc: false,
    notes: 'VGA analog capture, PAL/NTSC. VID_1B71:PID_3002',
  },
  // ─── EasyCap STK1160 + SAA7113/GM7113 ────────────────────────────
  {
    vendorId: 0x05E1, productId: 0x0408,
    deviceType: DeviceType.EASYCAP_STK1160,
    name: 'EasyCap STK1160 + SAA7113',
    maxResolutionWidth: 720, maxResolutionHeight: 576,
    maxFps: 30, supportsAudio: true,
    audioSampleRates: [48000, 8000],
    supportedFormats: ['YUY2'],
    isUvc: false,
    notes: 'Composite/S-Video, 48kHz & 8kHz audio. VID_05E1:PID_0408',
  },
  // ─── EasyCap EM2860 + SAA7113/GM7113 ─────────────────────────────
  {
    vendorId: 0xEB1A, productId: 0x2861,
    deviceType: DeviceType.EASYCAP_EM2860,
    name: 'EasyCap EM2860 + SAA7113',
    maxResolutionWidth: 720, maxResolutionHeight: 576,
    maxFps: 30, supportsAudio: true,
    audioSampleRates: [48000],
    supportedFormats: ['YUY2', 'MJPG'],
    isUvc: false,
    notes: 'EM2860 bridge, composite input. VID_EB1A:PID_2861',
  },
  // ─── EasyCap SMI2021 variants ─────────────────────────────────────
  {
    vendorId: 0x1C88, productId: 0x0007,
    deviceType: DeviceType.EASYCAP_SMI2021,
    name: 'EasyCap SMI2021 (PID 0007)',
    maxResolutionWidth: 720, maxResolutionHeight: 576,
    maxFps: 30, supportsAudio: true,
    audioSampleRates: [48000],
    supportedFormats: ['YUY2'],
    isUvc: false,
    notes: 'VID_1C88:PID_0007',
  },
  {
    vendorId: 0x1C88, productId: 0x003C,
    deviceType: DeviceType.EASYCAP_SMI2021,
    name: 'EasyCap SMI2021 (PID 003C)',
    maxResolutionWidth: 720, maxResolutionHeight: 576,
    maxFps: 30, supportsAudio: true,
    audioSampleRates: [48000],
    supportedFormats: ['YUY2'],
    isUvc: false,
    notes: 'VID_1C88:PID_003C',
  },
  {
    vendorId: 0x1C88, productId: 0x003D,
    deviceType: DeviceType.EASYCAP_SMI2021,
    name: 'EasyCap SMI2021 (PID 003D)',
    maxResolutionWidth: 720, maxResolutionHeight: 576,
    maxFps: 30, supportsAudio: true,
    audioSampleRates: [48000],
    supportedFormats: ['YUY2'],
    isUvc: false,
    notes: 'VID_1C88:PID_003D',
  },
  {
    vendorId: 0x1C88, productId: 0x003E,
    deviceType: DeviceType.EASYCAP_SMI2021,
    name: 'EasyCap SMI2021 (PID 003E)',
    maxResolutionWidth: 720, maxResolutionHeight: 576,
    maxFps: 30, supportsAudio: true,
    audioSampleRates: [48000],
    supportedFormats: ['YUY2'],
    isUvc: false,
    notes: 'VID_1C88:PID_003E',
  },
  {
    vendorId: 0x1C88, productId: 0x003F,
    deviceType: DeviceType.EASYCAP_SMI2021,
    name: 'EasyCap SMI2021 (PID 003F)',
    maxResolutionWidth: 720, maxResolutionHeight: 576,
    maxFps: 30, supportsAudio: true,
    audioSampleRates: [48000],
    supportedFormats: ['YUY2'],
    isUvc: false,
    notes: 'VID_1C88:PID_003F',
  },
  {
    vendorId: 0x1C88, productId: 0x1001,
    deviceType: DeviceType.EASYCAP_SMI2021,
    name: 'EasyCap SMI2021 (PID 1001)',
    maxResolutionWidth: 720, maxResolutionHeight: 576,
    maxFps: 30, supportsAudio: true,
    audioSampleRates: [48000],
    supportedFormats: ['YUY2'],
    isUvc: false,
    notes: 'VID_1C88:PID_1001',
  },
  // ─── Supercamera Geek szitman ─────────────────────────────────────
  {
    vendorId: 0x2CE3, productId: 0x3828,
    deviceType: DeviceType.SUPERCAMERA_GEEK,
    name: 'Supercamera Geek szitman',
    maxResolutionWidth: 1920, maxResolutionHeight: 1080,
    maxFps: 60, supportsAudio: false,
    audioSampleRates: [],
    supportedFormats: ['MJPG', 'H264'],
    isUvc: true,
    notes: 'VID 2ce3:3828',
  },
];

export function findDeviceProfile(vendorId: number, productId: number): DeviceProfile | undefined {
  return DEVICE_DATABASE.find(p => p.vendorId === vendorId && p.productId === productId);
}

export function identifyDevice(vendorId: number, productId: number, productName?: string): string {
  const profile = findDeviceProfile(vendorId, productId);
  if (profile) return profile.name;

  const EASYCAP_VENDORS = new Set([0x1B71, 0x05E1, 0xEB1A, 0x1C88]);
  if (EASYCAP_VENDORS.has(vendorId)) {
    return `EasyCap (${hex4(vendorId)}:${hex4(productId)})`;
  }
  if (productName) {
    const captureHints = ['Elgato', 'ezcap', 'AVerMedia', 'Magewell'];
    for (const hint of captureHints) {
      if (productName.toLowerCase().includes(hint.toLowerCase())) {
        return `${productName} (Capture Card)`;
      }
    }
  }
  return `USB Camera (${hex4(vendorId)}:${hex4(productId)})`;
}

export function hex4(n: number): string {
  return n.toString(16).toUpperCase().padStart(4, '0');
}

export const SUPPORTED_RESOLUTIONS = [
  { width: 320, height: 240, label: '320×240 (QVGA)' },
  { width: 640, height: 480, label: '640×480 (VGA)' },
  { width: 720, height: 576, label: '720×576 (PAL)' },
  { width: 720, height: 480, label: '720×480 (NTSC)' },
  { width: 1280, height: 720, label: '1280×720 (HD)' },
  { width: 1920, height: 1080, label: '1920×1080 (FHD)' },
  { width: 2560, height: 1440, label: '2560×1440 (QHD)' },
  { width: 3840, height: 2160, label: '3840×2160 (4K UHD)' },
];

export const SUPPORTED_FPS = [15, 24, 25, 30, 50, 60];

export const SUPPORTED_FORMATS = ['MJPG', 'YUY2', 'H264', 'H265', 'NV12', 'P010'];
