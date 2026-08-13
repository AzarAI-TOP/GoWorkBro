import 'dart:io';
import 'package:tray_manager/tray_manager.dart';
import 'package:window_manager/window_manager.dart';

import 'app_locale.dart';
import 'database_service.dart';

/// Manages the system tray icon and window visibility for background mode.
///
/// On Windows desktop, closing the window hides it to the tray instead of
/// quitting. The user can show the window again from the tray menu or
/// truly quit via "退出".
class TrayService with TrayListener {
  static final TrayService _instance = TrayService._();
  factory TrayService() => _instance;
  TrayService._();

  bool _initialized = false;

  Future<void> init() async {
    if (_initialized) return;
    if (!Platform.isWindows && !Platform.isLinux && !Platform.isMacOS) return;

    final savedLocale = await DatabaseService.getSetting('locale');
    final s = S.of(savedLocale == 'en' ? AppLocale.en : AppLocale.zh);
    await trayManager.setIcon(
      Platform.isWindows
          ? 'assets/icons/app_icon.ico'
          : 'assets/icons/app_icon.png',
    );
    await trayManager.setToolTip(s.trayRunning);
    await trayManager.setContextMenu(
      Menu(
        items: [
          MenuItem(key: 'show', label: s.showMainWindow),
          MenuItem.separator(),
          MenuItem(key: 'quit', label: s.exitGoWorkBro),
        ],
      ),
    );

    trayManager.addListener(this);
    _initialized = true;
  }

  @override
  void onTrayMenuItemClick(MenuItem menuItem) {
    switch (menuItem.key) {
      case 'show':
        _showWindow();
        break;
      case 'quit':
        _quitApp();
        break;
    }
  }

  @override
  void onTrayIconMouseDown() {
    _showWindow();
  }

  @override
  void onTrayIconRightMouseDown() {
    trayManager.popUpContextMenu();
  }

  void _showWindow() async {
    await windowManager.show();
    await windowManager.focus();
    if (await windowManager.isMinimized()) {
      await windowManager.restore();
    }
  }

  void _quitApp() async {
    await trayManager.destroy();
    await windowManager.setPreventClose(false);
    await windowManager.destroy();
  }

  Future<void> dispose() async {
    if (!_initialized) return;
    trayManager.removeListener(this);
    await trayManager.destroy();
    _initialized = false;
  }
}
