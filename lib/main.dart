import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';
import 'core/di/injection.dart';
import 'ui/theme/app_theme.dart';
import 'ui/screens/home_screen.dart';
import 'ui/screens/preview_screen.dart';
import 'ui/screens/settings_screen.dart';
import 'ui/screens/gallery_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.landscapeLeft,
    DeviceOrientation.landscapeRight,
  ]);
  SystemChrome.setSystemUIOverlayStyle(const SystemUiOverlayStyle(
    statusBarColor: Colors.transparent,
    statusBarIconBrightness: Brightness.light,
  ));
  setupInjection();
  runApp(const ProviderScope(child: UsbCamApp()));
}

class UsbCamApp extends ConsumerWidget {
  const UsbCamApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final themeMode = ref.watch(themeModeProvider);
    return MaterialApp(
      title: 'USB Cam',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: themeMode,
      initialRoute: '/',
      routes: {
        '/': (_) => const HomeScreen(),
        '/preview': (_) => const PreviewScreen(),
        '/settings': (_) => const SettingsScreen(),
        '/gallery': (_) => const GalleryScreen(),
      },
    );
  }
}

final themeModeProvider = StateProvider<ThemeMode>((ref) => ThemeMode.dark);
