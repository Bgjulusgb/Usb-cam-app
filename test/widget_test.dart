import 'package:flutter_test/flutter_test.dart';
import '../lib/usb/device_database.dart';
import '../lib/usb/vid_pid_matcher.dart';

void main() {
  group('DeviceDatabase', () {
    final db = DeviceDatabase();

    test('finds EasyCap UTVF007', () {
      final profile = db.findByVidPid(0x1B71, 0x3002);
      expect(profile, isNotNull);
      expect(profile!.name, contains('UTVF007'));
      expect(profile.isUvc, false);
    });

    test('finds EasyCap STK1160', () {
      final profile = db.findByVidPid(0x05E1, 0x0408);
      expect(profile, isNotNull);
      expect(profile!.supportsAudio, true);
      expect(profile.audioSampleRates, contains(48000));
      expect(profile.audioSampleRates, contains(8000));
    });

    test('finds all SMI2021 PIDs', () {
      for (final pid in [0x0007, 0x003C, 0x003D, 0x003E, 0x003F, 0x1001]) {
        final profile = db.findByVidPid(0x1C88, pid);
        expect(profile, isNotNull, reason: 'SMI2021 PID 0x${pid.toRadixString(16)} not found');
      }
    });

    test('finds Supercamera Geek', () {
      final profile = db.findByVidPid(0x2CE3, 0x3828);
      expect(profile, isNotNull);
      expect(profile!.deviceType, DeviceType.supercameraGeek);
    });

    test('returns null for unknown device', () {
      final profile = db.findByVidPid(0x1234, 0x5678);
      expect(profile, isNull);
    });
  });

  group('VidPidMatcher', () {
    test('identifies EasyCap by vendor', () {
      final name = VidPidMatcher.identifyDevice(0x1B71, 0x3002, null);
      expect(name, contains('EasyCap'));
    });
  });
}
