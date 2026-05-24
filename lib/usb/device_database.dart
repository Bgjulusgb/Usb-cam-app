enum DeviceType {
  uvcStandard,
  easyCapUtvf007,
  easyCapStk1160,
  easyCapEm2860,
  easyCapSmi2021,
  supercameraGeek,
  nonUvcEndoscope,
  captureCard,
  unknown,
}

class DeviceProfile {
  final int vendorId;
  final int productId;
  final DeviceType deviceType;
  final String name;
  final int maxResolutionWidth;
  final int maxResolutionHeight;
  final int maxFps;
  final bool supportsAudio;
  final List<int> audioSampleRates;
  final List<String> supportedFormats;
  final bool isUvc;
  final String notes;

  const DeviceProfile({
    required this.vendorId,
    required this.productId,
    required this.deviceType,
    required this.name,
    this.maxResolutionWidth = 1920,
    this.maxResolutionHeight = 1080,
    this.maxFps = 60,
    this.supportsAudio = false,
    this.audioSampleRates = const [],
    this.supportedFormats = const ['MJPG', 'YUY2'],
    this.isUvc = true,
    this.notes = '',
  });
}

class DeviceDatabase {
  static final List<DeviceProfile> profiles = [
    DeviceProfile(
      vendorId: 0x1B71, productId: 0x3002,
      deviceType: DeviceType.easyCapUtvf007,
      name: 'EasyCap UTVF007/HTV600/HTV800',
      maxResolutionWidth: 720, maxResolutionHeight: 576,
      maxFps: 30, supportsAudio: false,
      supportedFormats: ['YUY2', 'MJPG'],
      isUvc: false,
      notes: 'VGA analog capture, PAL/NTSC',
    ),
    DeviceProfile(
      vendorId: 0x05E1, productId: 0x0408,
      deviceType: DeviceType.easyCapStk1160,
      name: 'EasyCap STK1160 + SAA7113',
      maxResolutionWidth: 720, maxResolutionHeight: 576,
      maxFps: 30, supportsAudio: true,
      audioSampleRates: [48000, 8000],
      supportedFormats: ['YUY2'],
      isUvc: false,
      notes: 'Composite/S-Video, 48kHz & 8kHz audio',
    ),
    DeviceProfile(
      vendorId: 0xEB1A, productId: 0x2861,
      deviceType: DeviceType.easyCapEm2860,
      name: 'EasyCap EM2860 + SAA7113',
      maxResolutionWidth: 720, maxResolutionHeight: 576,
      maxFps: 30, supportsAudio: true,
      audioSampleRates: [48000],
      supportedFormats: ['YUY2', 'MJPG'],
      isUvc: false,
      notes: 'EM2860 bridge, composite input',
    ),
    DeviceProfile(
      vendorId: 0x1C88, productId: 0x0007,
      deviceType: DeviceType.easyCapSmi2021,
      name: 'EasyCap SMI2021 (PID 0007)',
      maxResolutionWidth: 720, maxResolutionHeight: 576,
      maxFps: 30, supportsAudio: true,
      audioSampleRates: [48000],
      supportedFormats: ['YUY2'],
      isUvc: false,
    ),
    DeviceProfile(
      vendorId: 0x1C88, productId: 0x003C,
      deviceType: DeviceType.easyCapSmi2021,
      name: 'EasyCap SMI2021 (PID 003C)',
      maxResolutionWidth: 720, maxResolutionHeight: 576,
      maxFps: 30, supportsAudio: true,
      audioSampleRates: [48000],
      supportedFormats: ['YUY2'],
      isUvc: false,
    ),
    DeviceProfile(
      vendorId: 0x1C88, productId: 0x003D,
      deviceType: DeviceType.easyCapSmi2021,
      name: 'EasyCap SMI2021 (PID 003D)',
      maxResolutionWidth: 720, maxResolutionHeight: 576,
      maxFps: 30, supportsAudio: true,
      audioSampleRates: [48000],
      supportedFormats: ['YUY2'],
      isUvc: false,
    ),
    DeviceProfile(
      vendorId: 0x1C88, productId: 0x003E,
      deviceType: DeviceType.easyCapSmi2021,
      name: 'EasyCap SMI2021 (PID 003E)',
      maxResolutionWidth: 720, maxResolutionHeight: 576,
      maxFps: 30, supportsAudio: true,
      audioSampleRates: [48000],
      supportedFormats: ['YUY2'],
      isUvc: false,
    ),
    DeviceProfile(
      vendorId: 0x1C88, productId: 0x003F,
      deviceType: DeviceType.easyCapSmi2021,
      name: 'EasyCap SMI2021 (PID 003F)',
      maxResolutionWidth: 720, maxResolutionHeight: 576,
      maxFps: 30, supportsAudio: true,
      audioSampleRates: [48000],
      supportedFormats: ['YUY2'],
      isUvc: false,
    ),
    DeviceProfile(
      vendorId: 0x1C88, productId: 0x1001,
      deviceType: DeviceType.easyCapSmi2021,
      name: 'EasyCap SMI2021 (PID 1001)',
      maxResolutionWidth: 720, maxResolutionHeight: 576,
      maxFps: 30, supportsAudio: true,
      audioSampleRates: [48000],
      supportedFormats: ['YUY2'],
      isUvc: false,
    ),
    DeviceProfile(
      vendorId: 0x2CE3, productId: 0x3828,
      deviceType: DeviceType.supercameraGeek,
      name: 'Supercamera Geek szitman',
      maxResolutionWidth: 1920, maxResolutionHeight: 1080,
      maxFps: 60, supportsAudio: false,
      supportedFormats: ['MJPG', 'H264'],
      isUvc: true,
      notes: 'VID 2ce3:3828',
    ),
  ];

  DeviceProfile? findByVidPid(int vendorId, int productId) {
    try {
      return profiles.firstWhere(
        (p) => p.vendorId == vendorId && p.productId == productId,
      );
    } catch (_) {
      return null;
    }
  }

  bool isKnownDevice(int vendorId, int productId) {
    return findByVidPid(vendorId, productId) != null;
  }

  List<DeviceProfile> getEasyCapDevices() {
    return profiles.where((p) => p.deviceType.name.startsWith('easyCap')).toList();
  }
}
