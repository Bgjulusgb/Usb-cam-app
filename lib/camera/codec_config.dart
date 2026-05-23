class Resolution {
  final int width;
  final int height;
  final String label;

  const Resolution(this.width, this.height, this.label);

  @override
  String toString() => label;
}

class CodecConfig {
  static const List<Resolution> standardResolutions = [
    Resolution(320, 240, '320×240 (QVGA)'),
    Resolution(640, 480, '640×480 (VGA)'),
    Resolution(720, 576, '720×576 (PAL)'),
    Resolution(720, 480, '720×480 (NTSC)'),
    Resolution(800, 600, '800×600 (SVGA)'),
    Resolution(1024, 768, '1024×768 (XGA)'),
    Resolution(1280, 720, '1280×720 (HD)'),
    Resolution(1920, 1080, '1920×1080 (FHD)'),
    Resolution(2560, 1440, '2560×1440 (QHD)'),
    Resolution(3840, 2160, '3840×2160 (4K UHD)'),
  ];

  static const List<int> standardFps = [15, 24, 25, 30, 50, 60];

  static const List<String> uvcFormats = [
    'MJPG',
    'YUY2',
    'H264',
    'H265',
    'NV12',
    'P010',
  ];

  static const List<String> analogFormats = ['YUY2'];

  static String formatDescription(String format) {
    switch (format) {
      case 'MJPG': return 'MJPEG - Motion JPEG (most compatible)';
      case 'YUY2': return 'YUY2 - Raw YCbCr 4:2:2 (low CPU)';
      case 'H264': return 'H.264 - Hardware compressed (low bandwidth)';
      case 'H265': return 'H.265/HEVC - High efficiency (requires Android 5+)';
      case 'NV12': return 'NV12 - Raw planar YUV (GPU optimized)';
      case 'P010': return 'P010 - 10-bit HDR format';
      default: return format;
    }
  }
}
