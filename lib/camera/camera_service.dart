import 'dart:async';
import 'package:flutter/services.dart';

class UsbDeviceInfo {
  final String deviceKey;
  final String name;
  final bool isUvc;
  final String vendorId;
  final String productId;
  final int maxWidth;
  final int maxHeight;
  final int maxFps;
  final List<String> formats;
  final bool hasAudio;

  const UsbDeviceInfo({
    required this.deviceKey,
    required this.name,
    required this.isUvc,
    required this.vendorId,
    required this.productId,
    required this.maxWidth,
    required this.maxHeight,
    required this.maxFps,
    required this.formats,
    required this.hasAudio,
  });

  factory UsbDeviceInfo.fromMap(Map<dynamic, dynamic> map) {
    return UsbDeviceInfo(
      deviceKey: map['deviceKey'] as String? ?? '',
      name: map['name'] as String? ?? 'Unknown Device',
      isUvc: map['isUvc'] as bool? ?? false,
      vendorId: map['vendorId'] as String? ?? '0000',
      productId: map['productId'] as String? ?? '0000',
      maxWidth: map['maxWidth'] as int? ?? 1920,
      maxHeight: map['maxHeight'] as int? ?? 1080,
      maxFps: map['maxFps'] as int? ?? 30,
      formats: (map['formats'] as List?)?.map((e) => e.toString()).toList() ?? ['MJPG'],
      hasAudio: map['hasAudio'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toMap() => {
    'deviceKey': deviceKey,
    'name': name,
    'isUvc': isUvc,
    'vendorId': vendorId,
    'productId': productId,
    'maxWidth': maxWidth,
    'maxHeight': maxHeight,
    'maxFps': maxFps,
    'formats': formats,
    'hasAudio': hasAudio,
  };
}

class CameraService {
  static const _channel = MethodChannel('com.usbcam.app/camera');
  static const _eventChannel = EventChannel('com.usbcam.app/camera/events');

  StreamSubscription? _eventSubscription;
  final _deviceConnectedController = StreamController<UsbDeviceInfo>.broadcast();
  final _deviceDisconnectedController = StreamController<String>.broadcast();
  final _recordingStateController = StreamController<RecordingState>.broadcast();
  final _errorController = StreamController<String>.broadcast();

  Stream<UsbDeviceInfo> get onDeviceConnected => _deviceConnectedController.stream;
  Stream<String> get onDeviceDisconnected => _deviceDisconnectedController.stream;
  Stream<RecordingState> get onRecordingState => _recordingStateController.stream;
  Stream<String> get onError => _errorController.stream;

  void startListening() {
    _eventSubscription = _eventChannel.receiveBroadcastStream().listen(
      (event) {
        if (event is! Map) return;
        final type = event['type'] as String?;
        switch (type) {
          case 'deviceConnected':
            final data = event['data'] as Map?;
            if (data != null) {
              _deviceConnectedController.add(UsbDeviceInfo.fromMap(data));
            }
          case 'deviceDisconnected':
            _deviceDisconnectedController.add(event['deviceKey'] as String? ?? '');
          case 'recordingState':
            _recordingStateController.add(RecordingState(
              isRecording: event['isRecording'] as bool? ?? false,
              outputPath: event['path'] as String?,
            ));
          case 'error':
            _errorController.add(event['message'] as String? ?? 'Unknown error');
        }
      },
      onError: (e) => _errorController.add('Event channel error: $e'),
    );
  }

  void stopListening() {
    _eventSubscription?.cancel();
    _eventSubscription = null;
  }

  Future<List<UsbDeviceInfo>> getConnectedDevices() async {
    final result = await _channel.invokeMethod<List>('getConnectedDevices');
    return result?.map((e) => UsbDeviceInfo.fromMap(e as Map)).toList() ?? [];
  }

  Future<bool> openDevice(String deviceKey) async {
    return await _channel.invokeMethod<bool>('openDevice', {'deviceKey': deviceKey}) ?? false;
  }

  Future<bool> startPreview({
    int width = 640,
    int height = 480,
    int fps = 30,
    String format = 'MJPG',
  }) async {
    return await _channel.invokeMethod<bool>('startPreview', {
      'width': width,
      'height': height,
      'fps': fps,
      'format': format,
    }) ?? false;
  }

  Future<bool> stopPreview() async {
    return await _channel.invokeMethod<bool>('stopPreview') ?? false;
  }

  Future<String?> startRecording() async {
    return await _channel.invokeMethod<String?>('startRecording');
  }

  Future<bool> stopRecording() async {
    return await _channel.invokeMethod<bool>('stopRecording') ?? false;
  }

  Future<String?> capturePhoto() async {
    return await _channel.invokeMethod<String?>('capturePhoto');
  }

  Future<void> disconnect() async {
    await _channel.invokeMethod('disconnect');
  }

  Future<String?> getStoragePath() async {
    return await _channel.invokeMethod<String?>('getStoragePath');
  }

  void dispose() {
    stopListening();
    _deviceConnectedController.close();
    _deviceDisconnectedController.close();
    _recordingStateController.close();
    _errorController.close();
  }
}

class RecordingState {
  final bool isRecording;
  final String? outputPath;
  const RecordingState({required this.isRecording, this.outputPath});
}
