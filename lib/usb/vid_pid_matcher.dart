import 'device_database.dart';

class VidPidMatcher {
  static const _easyCapVendors = {0x1B71, 0x05E1, 0xEB1A, 0x1C88};
  static const _captureCardHints = {'Elgato', 'ezcap', 'AVerMedia', 'Magewell'};

  static String identifyDevice(int vid, int pid, String? productName) {
    final db = DeviceDatabase();
    final known = db.findByVidPid(vid, pid);
    if (known != null) return known.name;
    if (_easyCapVendors.contains(vid)) return 'EasyCap (VID ${_hex4(vid)}:${_hex4(pid)})';
    if (productName != null) {
      for (final hint in _captureCardHints) {
        if (productName.toLowerCase().contains(hint.toLowerCase())) {
          return '$productName (Capture Card)';
        }
      }
    }
    return 'USB Device (${_hex4(vid)}:${_hex4(pid)})';
  }

  static String deviceTypeLabel(int vid, int pid) {
    final db = DeviceDatabase();
    final profile = db.findByVidPid(vid, pid);
    if (profile == null) return 'UVC Camera';
    return switch (profile.deviceType) {
      DeviceType.easyCapUtvf007 => 'EasyCap Analog',
      DeviceType.easyCapStk1160 => 'EasyCap STK1160',
      DeviceType.easyCapEm2860 => 'EasyCap EM2860',
      DeviceType.easyCapSmi2021 => 'EasyCap SMI2021',
      DeviceType.supercameraGeek => 'Supercamera',
      DeviceType.captureCard => 'Capture Card',
      DeviceType.nonUvcEndoscope => 'Endoscope',
      _ => 'UVC Camera',
    };
  }

  static String _hex4(int v) => v.toRadixString(16).toUpperCase().padLeft(4, '0');
}
