import 'dart:io';
import 'package:tray_manager/tray_manager.dart';
import 'package:window_manager/window_manager.dart';

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

    await trayManager.setIcon(
      Platform.isWindows
          ? 'assets/icons/app_icon.ico'
          : 'assets/icons/app_icon.png',
    );
    await trayManager.setToolTip('GoWorkBro — 正在后台运行');
    await trayManager.setContextMenu(Menu(items: [
      MenuItem(
        key: 'show',
        label: '显示主窗口',
      ),
      MenuItem.separator(),
      MenuItem(
        key: 'quit',
        label: '退出 GoWorkBro',
      ),
    ]));

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
