import 'package:permission_handler/permission_handler.dart';
import 'package:flutter/foundation.dart';

class PermissionService {
  Future<bool> requestStoragePermission() async {
    if (defaultTargetPlatform != TargetPlatform.android) return true;

    final status = await Permission.storage.request();
    if (status.isGranted) return true;

    // Android 13+ uses granular permissions
    final photos = await Permission.photos.request();
    final videos = await Permission.videos.request();
    return photos.isGranted || videos.isGranted || status.isGranted;
  }

  Future<bool> requestMicrophonePermission() async {
    final status = await Permission.microphone.request();
    return status.isGranted;
  }

  Future<bool> checkAllPermissions() async {
    final storage = await requestStoragePermission();
    final mic = await requestMicrophonePermission();
    return storage && mic;
  }

  Future<void> openAppSettings() => openAppSettings();
}
