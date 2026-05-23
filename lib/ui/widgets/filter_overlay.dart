import 'package:flutter/material.dart';

class FilterSettings {
  final double brightness;
  final double contrast;
  final double saturation;
  final bool grayscale;

  const FilterSettings({
    this.brightness = 0.0,
    this.contrast = 1.0,
    this.saturation = 1.0,
    this.grayscale = false,
  });

  FilterSettings copyWith({
    double? brightness, double? contrast, double? saturation, bool? grayscale,
  }) {
    return FilterSettings(
      brightness: brightness ?? this.brightness,
      contrast: contrast ?? this.contrast,
      saturation: saturation ?? this.saturation,
      grayscale: grayscale ?? this.grayscale,
    );
  }

  bool get isDefault =>
      brightness == 0.0 && contrast == 1.0 && saturation == 1.0 && !grayscale;
}

class FilterOverlay extends StatelessWidget {
  final FilterSettings settings;

  const FilterOverlay({super.key, required this.settings});

  @override
  Widget build(BuildContext context) {
    if (settings.isDefault) return const SizedBox.shrink();
    return ColorFiltered(
      colorFilter: _buildColorFilter(),
      child: const SizedBox.expand(),
    );
  }

  ColorFilter _buildColorFilter() {
    if (settings.grayscale) {
      return const ColorFilter.matrix([
        0.2126, 0.7152, 0.0722, 0, 0,
        0.2126, 0.7152, 0.0722, 0, 0,
        0.2126, 0.7152, 0.0722, 0, 0,
        0,      0,      0,      1, 0,
      ]);
    }

    final c = settings.contrast;
    final b = settings.brightness * 255;
    final s = settings.saturation;
    final sr = (1 - s) * 0.2126;
    final sg = (1 - s) * 0.7152;
    final sb = (1 - s) * 0.0722;

    return ColorFilter.matrix([
      c * (sr + s), c * sg,       c * sb,       0, b + (1 - c) * 128,
      c * sr,       c * (sg + s), c * sb,       0, b + (1 - c) * 128,
      c * sr,       c * sg,       c * (sb + s), 0, b + (1 - c) * 128,
      0,            0,            0,            1, 0,
    ]);
  }
}
