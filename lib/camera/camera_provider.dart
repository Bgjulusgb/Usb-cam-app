import 'dart:async';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import '../core/di/injection.dart';
import 'camera_service.dart';
import 'codec_config.dart';

// --- State classes ---

class CameraState {
  final List<UsbDeviceInfo> devices;
  final UsbDeviceInfo? activeDevice;
  final bool isPreviewing;
  final bool isRecording;
  final String? recordingPath;
  final CameraSettings settings;
  final String? errorMessage;
  final ConnectionStatus connectionStatus;

  const CameraState({
    this.devices = const [],
    this.activeDevice,
    this.isPreviewing = false,
    this.isRecording = false,
    this.recordingPath,
    this.settings = const CameraSettings(),
    this.errorMessage,
    this.connectionStatus = ConnectionStatus.disconnected,
  });

  CameraState copyWith({
    List<UsbDeviceInfo>? devices,
    UsbDeviceInfo? activeDevice,
    bool? isPreviewing,
    bool? isRecording,
    String? recordingPath,
    CameraSettings? settings,
    String? errorMessage,
    ConnectionStatus? connectionStatus,
    bool clearError = false,
    bool clearDevice = false,
  }) {
    return CameraState(
      devices: devices ?? this.devices,
      activeDevice: clearDevice ? null : (activeDevice ?? this.activeDevice),
      isPreviewing: isPreviewing ?? this.isPreviewing,
      isRecording: isRecording ?? this.isRecording,
      recordingPath: recordingPath ?? this.recordingPath,
      settings: settings ?? this.settings,
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
      connectionStatus: connectionStatus ?? this.connectionStatus,
    );
  }
}

enum ConnectionStatus { disconnected, connecting, connected, error }

class CameraSettings {
  final int width;
  final int height;
  final int fps;
  final String format;
  final double brightness;
  final double contrast;
  final double saturation;
  final bool lowLatencyMode;
  final bool enableRtsp;
  final int rtspPort;

  const CameraSettings({
    this.width = 640,
    this.height = 480,
    this.fps = 30,
    this.format = 'MJPG',
    this.brightness = 0.0,
    this.contrast = 1.0,
    this.saturation = 1.0,
    this.lowLatencyMode = true,
    this.enableRtsp = false,
    this.rtspPort = 8554,
  });

  CameraSettings copyWith({
    int? width, int? height, int? fps, String? format,
    double? brightness, double? contrast, double? saturation,
    bool? lowLatencyMode, bool? enableRtsp, int? rtspPort,
  }) {
    return CameraSettings(
      width: width ?? this.width,
      height: height ?? this.height,
      fps: fps ?? this.fps,
      format: format ?? this.format,
      brightness: brightness ?? this.brightness,
      contrast: contrast ?? this.contrast,
      saturation: saturation ?? this.saturation,
      lowLatencyMode: lowLatencyMode ?? this.lowLatencyMode,
      enableRtsp: enableRtsp ?? this.enableRtsp,
      rtspPort: rtspPort ?? this.rtspPort,
    );
  }
}

// --- Notifier ---

class CameraNotifier extends StateNotifier<CameraState> {
  final CameraService _service;
  StreamSubscription? _connectedSub;
  StreamSubscription? _disconnectedSub;
  StreamSubscription? _recordingSub;
  StreamSubscription? _errorSub;

  CameraNotifier(this._service) : super(const CameraState()) {
    _setupListeners();
    refreshDevices();
  }

  void _setupListeners() {
    _service.startListening();
    _connectedSub = _service.onDeviceConnected.listen((device) {
      state = state.copyWith(
        devices: [...state.devices.where((d) => d.deviceKey != device.deviceKey), device],
        activeDevice: device,
        connectionStatus: ConnectionStatus.connected,
      );
    });
    _disconnectedSub = _service.onDeviceDisconnected.listen((key) {
      final updatedDevices = state.devices.where((d) => d.deviceKey != key).toList();
      state = state.copyWith(
        devices: updatedDevices,
        clearDevice: state.activeDevice?.deviceKey == key,
        isPreviewing: state.activeDevice?.deviceKey == key ? false : state.isPreviewing,
        connectionStatus: ConnectionStatus.disconnected,
      );
    });
    _recordingSub = _service.onRecordingState.listen((rs) {
      state = state.copyWith(
        isRecording: rs.isRecording,
        recordingPath: rs.outputPath,
      );
    });
    _errorSub = _service.onError.listen((msg) {
      state = state.copyWith(
        errorMessage: msg,
        connectionStatus: ConnectionStatus.error,
      );
    });
  }

  Future<void> refreshDevices() async {
    final devices = await _service.getConnectedDevices();
    state = state.copyWith(devices: devices);
  }

  Future<bool> connectDevice(UsbDeviceInfo device) async {
    state = state.copyWith(connectionStatus: ConnectionStatus.connecting);
    final success = await _service.openDevice(device.deviceKey);
    if (success) {
      state = state.copyWith(
        activeDevice: device,
        connectionStatus: ConnectionStatus.connected,
      );
    } else {
      state = state.copyWith(
        errorMessage: 'Failed to open device: ${device.name}',
        connectionStatus: ConnectionStatus.error,
      );
    }
    return success;
  }

  Future<void> startPreview() async {
    final s = state.settings;
    await _service.startPreview(
      width: s.width, height: s.height, fps: s.fps, format: s.format,
    );
    state = state.copyWith(isPreviewing: true);
  }

  Future<void> stopPreview() async {
    await _service.stopPreview();
    state = state.copyWith(isPreviewing: false);
  }

  Future<String?> startRecording() async {
    final path = await _service.startRecording();
    if (path != null) {
      state = state.copyWith(isRecording: true, recordingPath: path);
    }
    return path;
  }

  Future<void> stopRecording() async {
    await _service.stopRecording();
    state = state.copyWith(isRecording: false);
  }

  Future<String?> capturePhoto() => _service.capturePhoto();

  Future<void> disconnect() async {
    await _service.disconnect();
    state = state.copyWith(
      clearDevice: true,
      isPreviewing: false,
      isRecording: false,
      connectionStatus: ConnectionStatus.disconnected,
    );
  }

  void updateSettings(CameraSettings settings) {
    state = state.copyWith(settings: settings);
  }

  void clearError() {
    state = state.copyWith(clearError: true);
  }

  @override
  void dispose() {
    _connectedSub?.cancel();
    _disconnectedSub?.cancel();
    _recordingSub?.cancel();
    _errorSub?.cancel();
    _service.stopListening();
    super.dispose();
  }
}

// --- Providers ---

final cameraServiceProvider = Provider<CameraService>((ref) {
  final service = getIt<CameraService>();
  ref.onDispose(service.dispose);
  return service;
});

final cameraProvider = StateNotifierProvider<CameraNotifier, CameraState>((ref) {
  return CameraNotifier(ref.watch(cameraServiceProvider));
});
