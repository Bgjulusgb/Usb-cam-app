import 'package:get_it/get_it.dart';
import '../../camera/camera_service.dart';
import '../../storage/file_manager.dart';
import '../../storage/media_repository.dart';
import '../../usb/device_database.dart';

final getIt = GetIt.instance;

void setupInjection() {
  getIt.registerLazySingleton<CameraService>(() => CameraService());
  getIt.registerLazySingleton<FileManager>(() => FileManager());
  getIt.registerLazySingleton<MediaRepository>(() => MediaRepository(getIt()));
  getIt.registerLazySingleton<DeviceDatabase>(() => DeviceDatabase());
}
