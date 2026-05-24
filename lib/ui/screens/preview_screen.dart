import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import '../../camera/camera_provider.dart';
import '../widgets/record_button.dart';
import '../widgets/filter_overlay.dart';

class PreviewScreen extends ConsumerStatefulWidget {
  const PreviewScreen({super.key});

  @override
  ConsumerState<PreviewScreen> createState() => _PreviewScreenState();
}

class _PreviewScreenState extends ConsumerState<PreviewScreen> {
  bool _controlsVisible = true;
  Timer? _hideControlsTimer;
  FilterSettings _filters = const FilterSettings();
  bool _showFilters = false;

  @override
  void initState() {
    super.initState();
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    _startPreview();
    _scheduleHideControls();
  }

  void _startPreview() {
    Future.microtask(() {
      ref.read(cameraProvider.notifier).startPreview();
    });
  }

  void _scheduleHideControls() {
    _hideControlsTimer?.cancel();
    _hideControlsTimer = Timer(const Duration(seconds: 4), () {
      if (mounted) setState(() => _controlsVisible = false);
    });
  }

  @override
  void dispose() {
    _hideControlsTimer?.cancel();
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    ref.read(cameraProvider.notifier).stopPreview();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final cameraState = ref.watch(cameraProvider);

    return Scaffold(
      backgroundColor: Colors.black,
      body: GestureDetector(
        onTap: () {
          setState(() => _controlsVisible = !_controlsVisible);
          if (_controlsVisible) _scheduleHideControls();
        },
        child: Stack(
          fit: StackFit.expand,
          children: [
            // Preview area - black placeholder (real preview is via SurfaceTexture)
            Container(
              color: Colors.black,
              child: Center(
                child: cameraState.isPreviewing
                    ? Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Icon(Icons.videocam, color: Colors.white24, size: 64),
                          const SizedBox(height: 16),
                          Text(
                            'Preview active\n${cameraState.settings.width}×${cameraState.settings.height} @ ${cameraState.settings.fps}fps',
                            style: const TextStyle(color: Colors.white38, fontSize: 14),
                            textAlign: TextAlign.center,
                          ),
                        ],
                      )
                    : Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const CircularProgressIndicator(color: Colors.white38),
                          const SizedBox(height: 16),
                          Text(
                            cameraState.activeDevice == null
                                ? 'No device connected'
                                : 'Starting preview...',
                            style: const TextStyle(color: Colors.white38),
                          ),
                        ],
                      ),
              ),
            ),

            // Filter overlay (GPU shader effects)
            if (cameraState.isPreviewing)
              FilterOverlay(settings: _filters),

            // Top controls
            AnimatedOpacity(
              opacity: _controlsVisible ? 1.0 : 0.0,
              duration: const Duration(milliseconds: 300),
              child: _TopBar(
                device: cameraState.activeDevice,
                isRecording: cameraState.isRecording,
                onBack: () {
                  ref.read(cameraProvider.notifier).stopPreview();
                  Navigator.pop(context);
                },
                onFilter: () => setState(() => _showFilters = !_showFilters),
              ),
            ),

            // Bottom controls
            AnimatedOpacity(
              opacity: _controlsVisible ? 1.0 : 0.0,
              duration: const Duration(milliseconds: 300),
              child: Align(
                alignment: Alignment.bottomCenter,
                child: _BottomBar(
                  cameraState: cameraState,
                  onRecordToggle: _toggleRecording,
                  onCapture: _capturePhoto,
                ),
              ),
            ),

            // Filter panel
            if (_showFilters)
              Align(
                alignment: Alignment.centerRight,
                child: _FilterPanel(
                  settings: _filters,
                  onChanged: (s) => setState(() => _filters = s),
                ),
              ),

            // Recording indicator
            if (cameraState.isRecording)
              Positioned(
                top: 60,
                right: 16,
                child: _RecordingIndicator(),
              ),
          ],
        ),
      ),
    );
  }

  Future<void> _toggleRecording() async {
    final notifier = ref.read(cameraProvider.notifier);
    final state = ref.read(cameraProvider);
    if (state.isRecording) {
      await notifier.stopRecording();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Recording saved')),
        );
      }
    } else {
      final path = await notifier.startRecording();
      if (path != null && mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Recording: ${path.split('/').last}')),
        );
      }
    }
  }

  Future<void> _capturePhoto() async {
    final path = await ref.read(cameraProvider.notifier).capturePhoto();
    if (path != null && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Photo saved: ${path.split('/').last}')),
      );
    }
  }
}

class _TopBar extends StatelessWidget {
  final dynamic device;
  final bool isRecording;
  final VoidCallback onBack;
  final VoidCallback onFilter;

  const _TopBar({
    required this.device,
    required this.isRecording,
    required this.onBack,
    required this.onFilter,
  });

  @override
  Widget build(BuildContext context) {
    return Positioned(
      top: 0, left: 0, right: 0,
      child: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [Color(0xCC000000), Colors.transparent],
          ),
        ),
        padding: const EdgeInsets.fromLTRB(8, 40, 8, 16),
        child: Row(
          children: [
            IconButton(
              icon: const Icon(Icons.arrow_back_ios, color: Colors.white),
              onPressed: onBack,
            ),
            Expanded(
              child: Text(
                device?.name ?? 'Camera',
                style: const TextStyle(color: Colors.white, fontSize: 16),
                overflow: TextOverflow.ellipsis,
              ),
            ),
            IconButton(
              icon: const Icon(Icons.tune, color: Colors.white),
              onPressed: onFilter,
            ),
          ],
        ),
      ),
    );
  }
}

class _BottomBar extends StatelessWidget {
  final CameraState cameraState;
  final VoidCallback onRecordToggle;
  final VoidCallback onCapture;

  const _BottomBar({
    required this.cameraState,
    required this.onRecordToggle,
    required this.onCapture,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: const BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.bottomCenter,
          end: Alignment.topCenter,
          colors: [Color(0xCC000000), Colors.transparent],
        ),
      ),
      padding: const EdgeInsets.fromLTRB(24, 16, 24, 40),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: [
          IconButton(
            icon: const Icon(Icons.photo_library, color: Colors.white, size: 30),
            onPressed: () => Navigator.pushNamed(context, '/gallery'),
          ),
          RecordButton(
            isRecording: cameraState.isRecording,
            onTap: onRecordToggle,
          ),
          IconButton(
            icon: const Icon(Icons.camera_alt, color: Colors.white, size: 30),
            onPressed: onCapture,
          ),
        ],
      ),
    );
  }
}

class _FilterPanel extends StatelessWidget {
  final FilterSettings settings;
  final ValueChanged<FilterSettings> onChanged;

  const _FilterPanel({required this.settings, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 200,
      margin: const EdgeInsets.symmetric(vertical: 80, horizontal: 8),
      decoration: BoxDecoration(
        color: Colors.black87,
        borderRadius: BorderRadius.circular(12),
      ),
      padding: const EdgeInsets.all(16),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text('Filters', style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold)),
          const SizedBox(height: 12),
          _slider('Brightness', settings.brightness, -1, 1, (v) => onChanged(settings.copyWith(brightness: v))),
          _slider('Contrast', settings.contrast, 0, 3, (v) => onChanged(settings.copyWith(contrast: v))),
          _slider('Saturation', settings.saturation, 0, 2, (v) => onChanged(settings.copyWith(saturation: v))),
          SwitchListTile(
            title: const Text('Grayscale', style: TextStyle(color: Colors.white, fontSize: 12)),
            value: settings.grayscale,
            onChanged: (v) => onChanged(settings.copyWith(grayscale: v)),
            activeColor: const Color(0xFF00D4FF),
            contentPadding: EdgeInsets.zero,
          ),
        ],
      ),
    );
  }

  Widget _slider(String label, double value, double min, double max, ValueChanged<double> onChanged) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: const TextStyle(color: Colors.white70, fontSize: 11)),
        Slider(
          value: value.clamp(min, max),
          min: min, max: max,
          onChanged: onChanged,
          activeColor: const Color(0xFF00D4FF),
        ),
      ],
    );
  }
}

class _RecordingIndicator extends StatefulWidget {
  @override
  State<_RecordingIndicator> createState() => _RecordingIndicatorState();
}

class _RecordingIndicatorState extends State<_RecordingIndicator>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: const Duration(milliseconds: 800))
      ..repeat(reverse: true);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, _) => Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Opacity(
            opacity: _controller.value,
            child: Container(
              width: 10, height: 10,
              decoration: const BoxDecoration(color: Colors.red, shape: BoxShape.circle),
            ),
          ),
          const SizedBox(width: 6),
          const Text('REC', style: TextStyle(color: Colors.red, fontWeight: FontWeight.bold, fontSize: 13)),
        ],
      ),
    );
  }
}
