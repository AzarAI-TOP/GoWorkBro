import 'package:flutter/material.dart';

import 'database_service.dart';

/// Single source of truth for locale and theme mode.
/// Persists to SQLite via DatabaseService (no AppProvider dependency).
enum AppLocale { zh, en }

class AppLocaleProvider extends ChangeNotifier {
  AppLocale _locale = AppLocale.zh;
  ThemeMode _themeMode = ThemeMode.system;
  bool _loaded = false;

  AppLocale get locale => _locale;
  ThemeMode get themeMode => _themeMode;
  bool get loaded => _loaded;
  Locale get flutterLocale => _locale == AppLocale.zh
      ? const Locale('zh')
      : const Locale('en');

  /// Load persisted values from DB. Call once at startup.
  Future<void> init() async {
    final savedLocale = await DatabaseService.getSetting('locale');
    _locale = savedLocale == 'en' ? AppLocale.en : AppLocale.zh;

    final savedTheme = await DatabaseService.getSetting('theme_mode');
    switch (savedTheme) {
      case 'light':
        _themeMode = ThemeMode.light;
        break;
      case 'dark':
        _themeMode = ThemeMode.dark;
        break;
      default:
        _themeMode = ThemeMode.system;
    }
    _loaded = true;
    notifyListeners();
  }

  void setLocale(AppLocale locale) {
    _locale = locale;
    DatabaseService.setSetting('locale', locale == AppLocale.en ? 'en' : 'zh');
    notifyListeners();
  }

  void setThemeMode(ThemeMode mode) {
    _themeMode = mode;
    String value;
    switch (mode) {
      case ThemeMode.light:
        value = 'light';
        break;
      case ThemeMode.dark:
        value = 'dark';
        break;
      case ThemeMode.system:
        value = 'system';
        break;
    }
    DatabaseService.setSetting('theme_mode', value);
    notifyListeners();
  }

  void toggle() {
    setLocale(_locale == AppLocale.zh ? AppLocale.en : AppLocale.zh);
  }
}

/// Centralized string translations.
class S {
  final AppLocale locale;
  S(this.locale);

  static S of(AppLocale locale) => S(locale);

  // ---- Nav ----
  String get todo => locale == AppLocale.zh ? '待办' : 'Todo';
  String get countdown => locale == AppLocale.zh ? '倒计时' : 'Countdown';
  String get today => locale == AppLocale.zh ? '今天' : 'Today';
  String get me => locale == AppLocale.zh ? '我的' : 'Me';

  // ---- Todo ----
  String get addTodo => locale == AppLocale.zh ? '新建待办' : 'New Todo';
  String get addHabit => locale == AppLocale.zh ? '新建习惯' : 'New Habit';
  String get newItems => locale == AppLocale.zh ? '新建' : 'New';
  String get todoTitleHint => locale == AppLocale.zh ? '输入待办标题' : 'Enter todo title';
  String get timingMethod => locale == AppLocale.zh ? '计时方式' : 'Timing';
  String get forwardTimer => locale == AppLocale.zh ? '正向计时' : 'Count Up';
  String get backwardTimer => locale == AppLocale.zh ? '倒向计时' : 'Count Down';
  String get noTimer => locale == AppLocale.zh ? '不记时' : 'No Timer';
  String get duration => locale == AppLocale.zh ? '时长' : 'Duration';
  String get customMin => locale == AppLocale.zh ? '自定义' : 'Custom';
  String get customMinHint => locale == AppLocale.zh ? '自定义分钟数' : 'Custom minutes';
  String get keepTomorrow => locale == AppLocale.zh ? '明天继续' : 'Repeat Tomorrow';
  String get keepTomorrowDesc => locale == AppLocale.zh ? '完成后明天自动重建' : 'Auto-recreate tomorrow when done';
  String get completed => locale == AppLocale.zh ? '已完成' : 'Completed';
  String get habits => locale == AppLocale.zh ? '习惯' : 'Habits';
  String get noTodos => locale == AppLocale.zh ? '还没有待办事项' : 'No todos yet';
  String get noTodosHint => locale == AppLocale.zh ? '点击右下角 + 添加待办或习惯' : 'Tap + to add a todo or habit';
  String get oneTimeTask => locale == AppLocale.zh ? '一次性任务 · 支持计时' : 'One-time · With timer';
  String get dailyHabit => locale == AppLocale.zh ? '每日打卡 · 计量目标' : 'Daily · Track count';
  String get delete => locale == AppLocale.zh ? '删除' : 'Delete';
  String get edit => locale == AppLocale.zh ? '编辑' : 'Edit';
  String confirmDelete(String name) => locale == AppLocale.zh
      ? '确定删除「$name」吗？此操作不可撤销。'
      : 'Delete "$name"? This cannot be undone.';
  String get cancel => locale == AppLocale.zh ? '取消' : 'Cancel';
  String get save => locale == AppLocale.zh ? '保存' : 'Save';
  String get enterTitle => locale == AppLocale.zh ? '请输入标题' : 'Please enter a title';

  // ---- Habit ----
  String get habitName => locale == AppLocale.zh ? '习惯名称' : 'Habit name';
  String get targetCount => locale == AppLocale.zh ? '每日目标数量' : 'Daily target count';
  String get unit => locale == AppLocale.zh ? '量词' : 'Unit';
  String get daily => locale == AppLocale.zh ? '每日' : 'Daily';
  String get customUnit => locale == AppLocale.zh ? '自定义量词' : 'Custom unit';
  String get unitHint => locale == AppLocale.zh ? '输入自定义量词' : 'Enter custom unit';

  // ---- Timer ----
  String get start => locale == AppLocale.zh ? '开始' : 'Start';
  String get pause => locale == AppLocale.zh ? '暂停' : 'Pause';
  String get stop => locale == AppLocale.zh ? '完成' : 'Done';
  String get record => locale == AppLocale.zh ? '记录' : 'Record';
  String get exitTimer => locale == AppLocale.zh ? '退出计时' : 'Exit Timer';
  String recordPrompt(String time) => locale == AppLocale.zh
      ? '已计时 $time，是否记录本次专注？'
      : 'Tracked $time, record this session?';
  String get keepTiming => locale == AppLocale.zh ? '继续计时' : 'Keep Timing';
  String get recordAndExit => locale == AppLocale.zh ? '记录并退出' : 'Record & Exit';
  String get abandon => locale == AppLocale.zh ? '放弃' : 'Discard';
  String get back => locale == AppLocale.zh ? '返回' : 'Back';
  String focusTime(String time) => locale == AppLocale.zh ? '专注 $time' : 'Focused $time';
  String get remaining => locale == AppLocale.zh ? '剩余时间' : 'Remaining';
  String get done => locale == AppLocale.zh ? '已完成' : 'Done';

  // ---- Today ----
  String get todayFocus => locale == AppLocale.zh ? '今日专注' : "Today's Focus";
  String get pomodoroCount => locale == AppLocale.zh ? '今日番茄数' : 'Pomodoros';
  String get timeAllocation => locale == AppLocale.zh ? '时间分配' : 'Time Allocation';
  String get last7days => locale == AppLocale.zh ? '最近 7 天' : 'Last 7 Days';
  String get noFocusData => locale == AppLocale.zh ? '今天还没有专注数据' : 'No focus data today';
  String get ustcNews => locale == AppLocale.zh ? 'USTC 要闻' : 'USTC News';
  String get noNews => locale == AppLocale.zh ? '暂无 USTC 要闻' : 'No USTC news available';
  String get retry => locale == AppLocale.zh ? '重试' : 'Retry';
  String get viewNews => locale == AppLocale.zh ? '查看今日 USTC 要闻' : "View Today's USTC News";
  String get loadFailed => locale == AppLocale.zh ? '加载失败' : 'Failed to load';

  // ---- Me ----
  String get checkIn => locale == AppLocale.zh ? '打卡' : 'Check-in';
  String get stats => locale == AppLocale.zh ? '统计' : 'Stats';
  String get settings => locale == AppLocale.zh ? '设置' : 'Settings';
  String get todayCheckIn => locale == AppLocale.zh ? '今日打卡' : "Today's Check-in";
  String get wakeUp => locale == AppLocale.zh ? '起床' : 'Wake Up';
  String get sleep => locale == AppLocale.zh ? '睡觉' : 'Sleep';
  String get workout => locale == AppLocale.zh ? '健身' : 'Workout';
  String get notCheckedIn => locale == AppLocale.zh ? '未打卡' : 'Not checked in';
  String get checkInHistory => locale == AppLocale.zh ? '打卡记录' : 'History';
  String get noCheckInRecords => locale == AppLocale.zh ? '暂无打卡记录' : 'No records yet';
  String get todoCompleted => locale == AppLocale.zh ? '待办完成' : 'Todos Done';
  String get habitCompleted => locale == AppLocale.zh ? '习惯完成' : 'Habits Done';
  String get activeCountdowns => locale == AppLocale.zh ? '活跃倒计时' : 'Active Countdowns';

  // ---- Settings ----
  String get cloudSync => locale == AppLocale.zh ? '云端同步' : 'Cloud Sync';
  String get connected => locale == AppLocale.zh ? '已连接' : 'Connected';
  String get notConnected => locale == AppLocale.zh ? '未连接' : 'Not Connected';
  String get connectedToSupabase => locale == AppLocale.zh ? '已连接 Supabase' : 'Connected to Supabase';
  String get user => locale == AppLocale.zh ? '用户' : 'User';
  String get unknown => locale == AppLocale.zh ? '未知' : 'Unknown';
  String get logout => locale == AppLocale.zh ? '退出登录' : 'Sign Out';
  String get logoutConfirm => locale == AppLocale.zh ? '确定退出登录？本地数据会保留，重新登录后将从云端同步。' : 'Sign out? Local data is kept; re-login will sync from cloud.';
  String get signOut => locale == AppLocale.zh ? '退出' : 'Sign Out';
  String get checkUpdate => locale == AppLocale.zh ? '检查更新' : 'Check for Updates';
  String get checkingUpdate => locale == AppLocale.zh ? '正在检查更新...' : 'Checking for updates...';
  String get latestVersion => locale == AppLocale.zh ? '当前已是最新版本' : 'Already up to date';
  String get aboutGoWorkBro => locale == AppLocale.zh ? '关于 GoWorkBro' : 'About GoWorkBro';
  String get version => locale == AppLocale.zh ? '版本' : 'Version';
  String get openSourceLicenses => locale == AppLocale.zh ? '开源许可' : 'Open Source Licenses';
  String get language => locale == AppLocale.zh ? '语言' : 'Language';
  String get chinese => locale == AppLocale.zh ? '中文' : 'Chinese';
  String get english => locale == AppLocale.zh ? '英文' : 'English';
  String get theme => locale == AppLocale.zh ? '主题' : 'Theme';
  String get lightMode => locale == AppLocale.zh ? '浅色' : 'Light';
  String get darkMode => locale == AppLocale.zh ? '深色' : 'Dark';
  String get systemMode => locale == AppLocale.zh ? '跟随系统' : 'System';
  String get deleteData => locale == AppLocale.zh ? '删除所有数据' : 'Delete All Data';
  String get deleteDataConfirm => locale == AppLocale.zh ? '确定删除所有本地数据？此操作不可撤销，包括待办、习惯、打卡记录等。' : 'Delete all local data? This is irreversible and includes todos, habits, check-in records, etc.';
  String get deleteDataSuccess => locale == AppLocale.zh ? '所有数据已删除' : 'All data deleted';
  String get avatarUpload => locale == AppLocale.zh ? '更换头像' : 'Change Avatar';
  String get avatarHint => locale == AppLocale.zh ? '头像上传功能开发中，敬请期待 🚀' : 'Avatar upload coming soon 🚀';
  String get editName => locale == AppLocale.zh ? '编辑昵称' : 'Edit Name';
  String get nameHint => locale == AppLocale.zh ? '输入昵称' : 'Enter name';

  // ---- Countdown ----
  String get noCountdowns => locale == AppLocale.zh ? '还没有倒计时' : 'No countdowns';
  String get addCountdownHint => locale == AppLocale.zh ? '点击右下角 + 添加' : 'Tap + to add';
  String get countdownTitle => locale == AppLocale.zh ? '倒计时标题' : 'Countdown title';
  String get targetDate => locale == AppLocale.zh ? '目标日期' : 'Target date';
  String get targetTime => locale == AppLocale.zh ? '目标时间' : 'Target time';
  String get days => locale == AppLocale.zh ? '天' : 'd';
  String get hours => locale == AppLocale.zh ? '时' : 'h';
  String get mins => locale == AppLocale.zh ? '分' : 'm';
  String get secs => locale == AppLocale.zh ? '秒' : 's';
  String get expired => locale == AppLocale.zh ? '已过期' : 'Expired';
  String get countdownFinished => locale == AppLocale.zh ? '已结束' : 'Finished';
  String get deleteCountdownTitle => locale == AppLocale.zh ? '删除倒计时' : 'Delete Countdown';
  String get targetMustBeFuture => locale == AppLocale.zh ? '目标时间必须在未来' : 'Target must be in the future';
  String get create => locale == AppLocale.zh ? '创建' : 'Create';
}
