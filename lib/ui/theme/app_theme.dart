import 'package:flutter/material.dart';

class AppTheme {
  static final dark = ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    colorScheme: ColorScheme.dark(
      primary: const Color(0xFF00D4FF),
      secondary: const Color(0xFF00FF88),
      surface: const Color(0xFF1A1A2E),
      background: const Color(0xFF0D0D1A),
      error: Colors.redAccent,
    ),
    scaffoldBackgroundColor: const Color(0xFF0D0D1A),
    appBarTheme: const AppBarTheme(
      backgroundColor: Color(0xFF1A1A2E),
      foregroundColor: Colors.white,
      elevation: 0,
    ),
    cardTheme: CardTheme(
      color: const Color(0xFF1E1E3A),
      elevation: 4,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
    ),
    iconTheme: const IconThemeData(color: Color(0xFF00D4FF)),
    floatingActionButtonTheme: const FloatingActionButtonThemeData(
      backgroundColor: Color(0xFF00D4FF),
      foregroundColor: Colors.black,
    ),
  );

  static final light = ThemeData(
    useMaterial3: true,
    brightness: Brightness.light,
    colorScheme: ColorScheme.light(
      primary: const Color(0xFF0066CC),
      secondary: const Color(0xFF00AA55),
      surface: Colors.white,
      background: const Color(0xFFF5F5F5),
    ),
    scaffoldBackgroundColor: const Color(0xFFF5F5F5),
    appBarTheme: const AppBarTheme(
      backgroundColor: Color(0xFF0066CC),
      foregroundColor: Colors.white,
      elevation: 2,
    ),
    cardTheme: CardTheme(
      elevation: 2,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
    ),
  );
}
