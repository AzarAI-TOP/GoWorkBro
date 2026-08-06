// GoWorkBro Theme — 番茄TODO-inspired geometric design
// Clean, minimal, with geometric accents and a warm-cool palette
library;

import 'package:flutter/material.dart';

class AppTheme {
  // Primary palette — warm tomato red (番茄TODO signature)
  static const Color _primaryLight = Color(0xFFE85D3C);
  static const Color _primaryDark = Color(0xFFFF7A5C);

  // Accent colors for countdowns, charts
  static const List<Color> chartColors = [
    Color(0xFFE85D3C), // tomato
    Color(0xFF4A90D9), // blue
    Color(0xFF50C878), // green
    Color(0xFFFFB347), // orange
    Color(0xFF9B59B6), // purple
    Color(0xFFE74C3C), // red
    Color(0xFF1ABC9C), // teal
  ];

  static ThemeData get light => ThemeData(
    useMaterial3: true,
    brightness: Brightness.light,
    colorScheme: ColorScheme.fromSeed(
      seedColor: _primaryLight,
      brightness: Brightness.light,
      primary: _primaryLight,
      secondary: Color(0xFF4A90D9),
      surface: Color(0xFFF8F8FA),
      surfaceContainerHighest: Color(0xFFEEEEF0),
    ),
    scaffoldBackgroundColor: Color(0xFFF8F8FA),
    appBarTheme: const AppBarTheme(
      backgroundColor: Color(0xFFF8F8FA),
      foregroundColor: Color(0xFF2D2D2D),
      elevation: 0,
      centerTitle: false,
    ),
    cardTheme: CardThemeData(
      elevation: 0,
      color: Colors.white,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: Color(0xFFE8E8EC), width: 1),
      ),
      margin: EdgeInsets.zero,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Color(0xFFF0F0F2),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: BorderSide.none,
      ),
      contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 14),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: _primaryLight,
        foregroundColor: Colors.white,
        elevation: 0,
        padding: EdgeInsets.symmetric(horizontal: 24, vertical: 14),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: _primaryLight,
      ),
    ),
    floatingActionButtonTheme: FloatingActionButtonThemeData(
      backgroundColor: _primaryLight,
      foregroundColor: Colors.white,
      elevation: 2,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
      ),
    ),
    bottomNavigationBarTheme: BottomNavigationBarThemeData(
      backgroundColor: Colors.white,
      selectedItemColor: _primaryLight,
      unselectedItemColor: Color(0xFF999999),
      type: BottomNavigationBarType.fixed,
      elevation: 8,
      selectedLabelStyle: TextStyle(fontSize: 11, fontWeight: FontWeight.w600),
      unselectedLabelStyle: TextStyle(fontSize: 11),
    ),
    navigationRailTheme: NavigationRailThemeData(
      backgroundColor: Color(0xFFF8F8FA),
      selectedIconTheme: IconThemeData(color: _primaryLight, size: 28),
      unselectedIconTheme: IconThemeData(color: Color(0xFF999999), size: 24),
      selectedLabelTextStyle: TextStyle(color: _primaryLight, fontSize: 13, fontWeight: FontWeight.w600),
      unselectedLabelTextStyle: TextStyle(color: Color(0xFF999999), fontSize: 13),
      indicatorColor: _primaryLight.withValues(alpha: 0.12),
      minWidth: 80,
    ),
    dividerTheme: DividerThemeData(
      color: Color(0xFFE8E8EC),
      thickness: 1,
      space: 1,
    ),
    textTheme: const TextTheme(
      headlineLarge: TextStyle(fontSize: 28, fontWeight: FontWeight.w700, color: Color(0xFF1A1A1A), letterSpacing: -0.5),
      headlineMedium: TextStyle(fontSize: 22, fontWeight: FontWeight.w600, color: Color(0xFF1A1A1A)),
      titleLarge: TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: Color(0xFF2D2D2D)),
      titleMedium: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: Color(0xFF2D2D2D)),
      bodyLarge: TextStyle(fontSize: 15, color: Color(0xFF3D3D3D)),
      bodyMedium: TextStyle(fontSize: 14, color: Color(0xFF5D5D5D)),
      bodySmall: TextStyle(fontSize: 12, color: Color(0xFF999999)),
      labelLarge: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Color(0xFF2D2D2D)),
    ),
  );

  static ThemeData get dark => ThemeData(
    useMaterial3: true,
    brightness: Brightness.dark,
    colorScheme: ColorScheme.fromSeed(
      seedColor: _primaryDark,
      brightness: Brightness.dark,
      primary: _primaryDark,
      secondary: Color(0xFF5BA0E9),
      surface: Color(0xFF1A1A1E),
      surfaceContainerHighest: Color(0xFF2A2A2E),
    ),
    scaffoldBackgroundColor: Color(0xFF121214),
    appBarTheme: const AppBarTheme(
      backgroundColor: Color(0xFF121214),
      foregroundColor: Color(0xFFE8E8EA),
      elevation: 0,
      centerTitle: false,
    ),
    cardTheme: CardThemeData(
      elevation: 0,
      color: Color(0xFF1E1E22),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: Color(0xFF2A2A2E), width: 1),
      ),
      margin: EdgeInsets.zero,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Color(0xFF2A2A2E),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: BorderSide.none,
      ),
      contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 14),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: _primaryDark,
        foregroundColor: Colors.white,
        elevation: 0,
        padding: EdgeInsets.symmetric(horizontal: 24, vertical: 14),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
      ),
    ),
    bottomNavigationBarTheme: BottomNavigationBarThemeData(
      backgroundColor: Color(0xFF1A1A1E),
      selectedItemColor: _primaryDark,
      unselectedItemColor: Color(0xFF666666),
      type: BottomNavigationBarType.fixed,
      elevation: 8,
    ),
    navigationRailTheme: NavigationRailThemeData(
      backgroundColor: Color(0xFF121214),
      selectedIconTheme: IconThemeData(color: _primaryDark, size: 28),
      unselectedIconTheme: IconThemeData(color: Color(0xFF666666), size: 24),
      selectedLabelTextStyle: TextStyle(color: _primaryDark, fontSize: 13, fontWeight: FontWeight.w600),
      unselectedLabelTextStyle: TextStyle(color: Color(0xFF666666), fontSize: 13),
      indicatorColor: _primaryDark.withValues(alpha: 0.15),
      minWidth: 80,
    ),
    dividerTheme: DividerThemeData(
      color: Color(0xFF2A2A2E),
      thickness: 1,
      space: 1,
    ),
    textTheme: const TextTheme(
      headlineLarge: TextStyle(fontSize: 28, fontWeight: FontWeight.w700, color: Color(0xFFF0F0F2), letterSpacing: -0.5),
      headlineMedium: TextStyle(fontSize: 22, fontWeight: FontWeight.w600, color: Color(0xFFF0F0F2)),
      titleLarge: TextStyle(fontSize: 18, fontWeight: FontWeight.w600, color: Color(0xFFE0E0E2)),
      titleMedium: TextStyle(fontSize: 16, fontWeight: FontWeight.w500, color: Color(0xFFE0E0E2)),
      bodyLarge: TextStyle(fontSize: 15, color: Color(0xFFB0B0B2)),
      bodyMedium: TextStyle(fontSize: 14, color: Color(0xFF909092)),
      bodySmall: TextStyle(fontSize: 12, color: Color(0xFF666666)),
      labelLarge: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Color(0xFFE0E0E2)),
    ),
  );
}

/// Geometric background pattern widget — subtle triangles/circles
class GeometricBackground extends StatelessWidget {
  final Widget child;
  final bool showPattern;

  const GeometricBackground({
    super.key,
    required this.child,
    this.showPattern = true,
  });

  @override
  Widget build(BuildContext context) {
    if (!showPattern) return child;
    return CustomPaint(
      painter: _GeometricPainter(),
      child: child,
    );
  }
}

class _GeometricPainter extends CustomPainter {
  @override
  void paint(Canvas canvas, Size size) {
    // Subtle geometric circles in corners
    final paint = Paint()
      ..color = const Color(0xFFE85D3C).withValues(alpha: 0.03)
      ..style = PaintingStyle.fill;

    // Top-right circle
    canvas.drawCircle(
      Offset(size.width + 20, -20),
      80,
      paint,
    );
    // Bottom-left circle
    canvas.drawCircle(
      Offset(-30, size.height + 30),
      100,
      paint,
    );
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
