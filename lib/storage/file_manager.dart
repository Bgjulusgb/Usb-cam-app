import 'dart:io';
import 'package:path_provider/path_provider.dart';

class FileManager {
  Future<Directory> get videosDir async {
    final base = await getExternalStorageDirectory() ?? await getApplicationDocumentsDirectory();
    final dir = Directory('${base.path}/Videos');
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
  }

  Future<Directory> get imagesDir async {
    final base = await getExternalStorageDirectory() ?? await getApplicationDocumentsDirectory();
    final dir = Directory('${base.path}/Images');
    if (!await dir.exists()) await dir.create(recursive: true);
    return dir;
  }

  Future<List<File>> getVideos() async {
    final dir = await videosDir;
    final files = await dir.list().toList();
    return files
        .whereType<File>()
        .where((f) => f.path.endsWith('.mp4') || f.path.endsWith('.mkv'))
        .toList()
      ..sort((a, b) => b.statSync().modified.compareTo(a.statSync().modified));
  }

  Future<List<File>> getImages() async {
    final dir = await imagesDir;
    final files = await dir.list().toList();
    return files
        .whereType<File>()
        .where((f) => f.path.endsWith('.jpg') || f.path.endsWith('.png'))
        .toList()
      ..sort((a, b) => b.statSync().modified.compareTo(a.statSync().modified));
  }

  Future<bool> deleteFile(String path) async {
    try {
      await File(path).delete();
      return true;
    } catch (_) {
      return false;
    }
  }

  String formatFileSize(int bytes) {
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }
}
