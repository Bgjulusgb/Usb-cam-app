import 'dart:io';
import 'file_manager.dart';

class MediaItem {
  final File file;
  final bool isVideo;
  final DateTime modified;
  final int sizeBytes;

  MediaItem({
    required this.file,
    required this.isVideo,
    required this.modified,
    required this.sizeBytes,
  });

  String get name => file.uri.pathSegments.last;
  String get path => file.path;
}

class MediaRepository {
  final FileManager _fileManager;
  MediaRepository(this._fileManager);

  Future<List<MediaItem>> getAllMedia() async {
    final videos = await _fileManager.getVideos();
    final images = await _fileManager.getImages();
    final items = [
      ...videos.map((f) {
        final stat = f.statSync();
        return MediaItem(file: f, isVideo: true, modified: stat.modified, sizeBytes: stat.size);
      }),
      ...images.map((f) {
        final stat = f.statSync();
        return MediaItem(file: f, isVideo: false, modified: stat.modified, sizeBytes: stat.size);
      }),
    ];
    items.sort((a, b) => b.modified.compareTo(a.modified));
    return items;
  }

  Future<bool> delete(MediaItem item) => _fileManager.deleteFile(item.path);

  String formatSize(int bytes) => _fileManager.formatFileSize(bytes);
}
