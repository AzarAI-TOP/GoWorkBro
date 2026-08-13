// GoWorkBro Theme — 番茄TODO-inspired geometric design
// Clean, minimal, with geometric accents and a warm-cool palette
library;

import 'package:flutter/material.dart';

class AppTheme {
  /// Default UI font. Has no CJK glyphs — Chinese text falls back to
  /// [cjkFontFallback].
  static const String defaultFontFamily = '0xProto';

  /// Cross-platform CJK fallback chain. NotoSansSC is bundled; the rest are
  /// system fonts (Microsoft YaHei on Windows, PingFang SC on Apple,
  /// sans-serif on Android). Keeping every CJK glyph on the same chain makes
  /// Chinese rendering weight-consistent instead of mixing system fonts.
  static const List<String> cjkFontFallback = [
    'NotoSansSC',
    'Microsoft YaHei',
    'PingFang SC',
    'sans-serif',
  ];

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

  static ThemeData light({String fontFamily = defaultFontFamily}) =>
      _buildTheme(
        fontFamily: fontFamily,
        brightness: Brightness.light,
        primary: _primaryLight,
        surface: Color(0xFFF8F8FA),
        surfaceContainerHighest: Color(0xFFEEEEF0),
        scaffoldBackground: Color(0xFFF8F8FA),
        appBarBackground: Color(0xFFF8F8FA),
        appBarForeground: Color(0xFF2D2D2D),
        cardColor: Colors.white,
        cardBorder: Color(0xFFE8E8EC),
        inputFill: Color(0xFFF0F0F2),
        navBackground: Color(0xFFF8F8FA),
        navUnselected: Color(0xFF999999),
        dividerColor: Color(0xFFE8E8EC),
        textPrimary: Color(0xFF1A1A1A),
        textSecondary: Color(0xFF2D2D2D),
        textBody: Color(0xFF3D3D3D),
        textMuted: Color(0xFF5D5D5D),
        textFaint: Color(0xFF999999),
      );

  static ThemeData dark({String fontFamily = defaultFontFamily}) =>
      _buildTheme(
        fontFamily: fontFamily,
        brightness: Brightness.dark,
        primary: _primaryDark,
        surface: Color(0xFF1A1A1E),
        surfaceContainerHighest: Color(0xFF2A2A2E),
        scaffoldBackground: Color(0xFF121214),
        appBarBackground: Color(0xFF121214),
        appBarForeground: Color(0xFFE8E8EA),
        cardColor: Color(0xFF1E1E22),
        cardBorder: Color(0xFF2A2A2E),
        inputFill: Color(0xFF2A2A2E),
        navBackground: Color(0xFF121214),
        navUnselected: Color(0xFF666666),
        dividerColor: Color(0xFF2A2A2E),
        textPrimary: Color(0xFFF0F0F2),
        textSecondary: Color(0xFFE0E0E2),
        textBody: Color(0xFFB0B0B2),
        textMuted: Color(0xFF909092),
        textFaint: Color(0xFF666666),
      );

  static ThemeData _buildTheme({
    required String fontFamily,
    required Brightness brightness,
    required Color primary,
    required Color surface,
    required Color surfaceContainerHighest,
    required Color scaffoldBackground,
    required Color appBarBackground,
    required Color appBarForeground,
    required Color cardColor,
    required Color cardBorder,
    required Color inputFill,
    required Color navBackground,
    required Color navUnselected,
    required Color dividerColor,
    required Color textPrimary,
    required Color textSecondary,
    required Color textBody,
    required Color textMuted,
    required Color textFaint,
  }) {
    // Every text style carries the same CJK fallback chain so Chinese glyphs
    // render from one consistent source instead of mixing system fonts.
    TextStyle fallback(TextStyle style) =>
        style.copyWith(fontFamilyFallback: cjkFontFallback);

    final isDark = brightness == Brightness.dark;
    final secondary = isDark ? const Color(0xFF5BA0E9) : const Color(0xFF4A90D9);

    return ThemeData(
      fontFamily: fontFamily,
      useMaterial3: true,
      brightness: brightness,
      colorScheme: ColorScheme.fromSeed(
        seedColor: primary,
        brightness: brightness,
        primary: primary,
        secondary: secondary,
        surface: surface,
        surfaceContainerHighest: surfaceContainerHighest,
      ),
      scaffoldBackgroundColor: scaffoldBackground,
      appBarTheme: AppBarTheme(
        backgroundColor: appBarBackground,
        foregroundColor: appBarForeground,
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardThemeData(
        elevation: 0,
        color: cardColor,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: BorderSide(color: cardBorder, width: 1),
        ),
        margin: EdgeInsets.zero,
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: inputFill,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide.none,
        ),
        contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: primary,
          foregroundColor: Colors.white,
          elevation: 0,
          padding: EdgeInsets.symmetric(horizontal: 24, vertical: 14),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(foregroundColor: primary),
      ),
      floatingActionButtonTheme: FloatingActionButtonThemeData(
        backgroundColor: primary,
        foregroundColor: Colors.white,
        elevation: 2,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      ),
      bottomNavigationBarTheme: BottomNavigationBarThemeData(
        backgroundColor: cardColor,
        selectedItemColor: primary,
        unselectedItemColor: navUnselected,
        type: BottomNavigationBarType.fixed,
        elevation: 8,
        selectedLabelStyle: TextStyle(fontSize: 11, fontWeight: FontWeight.w600),
        unselectedLabelStyle: TextStyle(fontSize: 11),
      ),
      navigationRailTheme: NavigationRailThemeData(
        backgroundColor: navBackground,
        selectedIconTheme: IconThemeData(color: primary, size: 28),
        unselectedIconTheme: IconThemeData(color: navUnselected, size: 24),
        selectedLabelTextStyle: TextStyle(
          color: primary,
          fontSize: 13,
          fontWeight: FontWeight.w600,
        ),
        unselectedLabelTextStyle: TextStyle(
          color: navUnselected,
          fontSize: 13,
        ),
        indicatorColor: primary.withValues(alpha: isDark ? 0.15 : 0.12),
        minWidth: 80,
      ),
      dividerTheme: DividerThemeData(
        color: dividerColor,
        thickness: 1,
        space: 1,
      ),
      textTheme: TextTheme(
        headlineLarge: fallback(TextStyle(
          fontSize: 28,
          fontWeight: FontWeight.w700,
          color: textPrimary,
          letterSpacing: -0.5,
        )),
        headlineMedium: fallback(TextStyle(
          fontSize: 22,
          fontWeight: FontWeight.w600,
          color: textPrimary,
        )),
        titleLarge: fallback(TextStyle(
          fontSize: 18,
          fontWeight: FontWeight.w600,
          color: textSecondary,
        )),
        titleMedium: fallback(TextStyle(
          fontSize: 16,
          fontWeight: FontWeight.w500,
          color: textSecondary,
        )),
        bodyLarge: fallback(TextStyle(fontSize: 15, color: textBody)),
        bodyMedium: fallback(TextStyle(fontSize: 14, color: textMuted)),
        bodySmall: fallback(TextStyle(fontSize: 12, color: textFaint)),
        labelLarge: fallback(TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w600,
          color: textSecondary,
        )),
      ),
    );
  }
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
    return CustomPaint(painter: _GeometricPainter(), child: child);
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
    canvas.drawCircle(Offset(size.width + 20, -20), 80, paint);
    // Bottom-left circle
    canvas.drawCircle(Offset(-30, size.height + 30), 100, paint);
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
