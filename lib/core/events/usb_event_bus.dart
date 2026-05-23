import 'dart:async';

enum UsbEventType { deviceAttached, deviceDetached, permissionGranted, permissionDenied }

class UsbEvent {
  final UsbEventType type;
  final String deviceKey;
  final Map<String, dynamic>? deviceInfo;

  const UsbEvent({required this.type, required this.deviceKey, this.deviceInfo});
}

class UsbEventBus {
  static final UsbEventBus _instance = UsbEventBus._internal();
  factory UsbEventBus() => _instance;
  UsbEventBus._internal();

  final _controller = StreamController<UsbEvent>.broadcast();
  Stream<UsbEvent> get stream => _controller.stream;

  void emit(UsbEvent event) => _controller.add(event);

  void dispose() => _controller.close();
}
