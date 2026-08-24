import 'package:flutter/material.dart';

import 'package:goworkbro/core/theme/app_theme.dart';
import 'package:goworkbro/core/database/repositories/settings_repository.dart';
import 'package:goworkbro/core/domain/check_in_type.dart';

/// Single source of truth for locale and theme mode.
/// Persists to SQLite via DatabaseService (no AppProvider dependency).
enum AppLocale { zh, en }

class AppLocaleProvider extends ChangeNotifier {
  AppLocaleProvider();

  @visibleForTesting
  AppLocaleProvider.forTesting({
    AppLocale locale = AppLocale.zh,
    ThemeMode themeMode = ThemeMode.system,
    String fontFamily = AppTheme.defaultFontFamily,
  }) {
    _locale = locale;
    _themeMode = themeMode;
    _fontFamily = fontFamily;
    _loaded = true;
  }

  AppLocale _locale = AppLocale.zh;
  ThemeMode _themeMode = ThemeMode.system;
  String _fontFamily = AppTheme.defaultFontFamily;
  bool _loaded = false;

  AppLocale get locale => _locale;
  ThemeMode get themeMode => _themeMode;
  String get fontFamily => _fontFamily;
  bool get loaded => _loaded;
  Locale get flutterLocale =>
      _locale == AppLocale.zh ? const Locale('zh') : const Locale('en');

  /// Load persisted values from DB. Call once at startup.
  Future<void> init() async {
    final savedLocale = await SettingsRepository.get('locale');
    _locale = savedLocale == 'en' ? AppLocale.en : AppLocale.zh;

    final savedTheme = await SettingsRepository.get('theme_mode');
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

    final savedFont = await SettingsRepository.get('font_family');
    if (savedFont != null && savedFont.isNotEmpty) {
      _fontFamily = savedFont;
    }
    _loaded = true;
    notifyListeners();
  }

  Future<void> setLocale(AppLocale locale) async {
    _locale = locale;
    notifyListeners();
    await SettingsRepository.set(
      'locale',
      locale == AppLocale.en ? 'en' : 'zh',
    );
  }

  Future<void> setThemeMode(ThemeMode mode) async {
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
    notifyListeners();
    await SettingsRepository.set('theme_mode', value);
  }

  Future<void> setFontFamily(String family) async {
    if (family == _fontFamily) return;
    _fontFamily = family;
    notifyListeners();
    await SettingsRepository.set('font_family', family);
  }

  Future<void> toggle() =>
      setLocale(_locale == AppLocale.zh ? AppLocale.en : AppLocale.zh);
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

  // ---- Authentication ----
  String get login => locale == AppLocale.zh ? '登录' : 'Sign In';
  String get register => locale == AppLocale.zh ? '注册' : 'Register';
  String get loginSubtitle => locale == AppLocale.zh
      ? '登录你的账号，同步跨设备数据'
      : 'Sign in to sync data across devices';
  String get registerSubtitle => locale == AppLocale.zh
      ? '创建新账号，开始记录你的专注时光'
      : 'Create an account to start tracking focus time';
  String get email => locale == AppLocale.zh ? '邮箱' : 'Email';
  String get password => locale == AppLocale.zh ? '密码' : 'Password';
  String get passwordHint =>
      locale == AppLocale.zh ? '至少 6 位' : 'At least 6 characters';
  String loginFailed(Object error) =>
      locale == AppLocale.zh ? '登录失败: $error' : 'Sign-in failed: $error';
  String get enterEmailPassword =>
      locale == AppLocale.zh ? '请输入邮箱和密码' : 'Enter your email and password';
  String get passwordTooShort => locale == AppLocale.zh
      ? '密码至少 10 位'
      : 'Password must be at least 10 characters';
  String get registrationSuccess => locale == AppLocale.zh
      ? '注册成功！请检查邮箱完成验证后登录。'
      : 'Registration successful. Verify your email, then sign in.';
  String get networkError => locale == AppLocale.zh
      ? '网络错误，请检查连接后重试'
      : 'Network error. Check your connection and retry.';
  String get invalidCredentials =>
      locale == AppLocale.zh ? '邮箱或密码错误' : 'Incorrect email or password';
  String get emailNotConfirmed => locale == AppLocale.zh
      ? '邮箱未验证，请检查邮箱'
      : 'Email not verified. Check your inbox.';
  String get emailAlreadyRegistered =>
      locale == AppLocale.zh ? '该邮箱已注册' : 'This email is already registered';
  String get rateLimited => locale == AppLocale.zh
      ? '操作过于频繁，请稍后再试'
      : 'Too many attempts. Try again later.';
  String get enterEmailFirst =>
      locale == AppLocale.zh ? '请先输入邮箱地址' : 'Enter your email address first';
  String get resetEmailSent => locale == AppLocale.zh
      ? '密码重置邮件已发送，请检查邮箱'
      : 'Password reset email sent. Check your inbox.';
  String get sendFailed => locale == AppLocale.zh
      ? '发送失败，请检查网络连接'
      : 'Failed to send. Check your connection.';
  String get forgotPassword =>
      locale == AppLocale.zh ? '忘记密码？' : 'Forgot password?';
  String get noAccount =>
      locale == AppLocale.zh ? '没有账号？点击注册' : 'No account? Register';
  String get haveAccount =>
      locale == AppLocale.zh ? '已有账号？点击登录' : 'Already registered? Sign in';
  String get useOffline =>
      locale == AppLocale.zh ? '离线使用（不同步数据）' : 'Use offline (no sync)';
  String get quickLogin =>
      locale == AppLocale.zh ? '🧪 快速登录 (测试)' : '🧪 Quick sign-in (test)';

  // ---- Todo ----
  String get addTodo => locale == AppLocale.zh ? '新建待办' : 'New Todo';
  String get addHabit => locale == AppLocale.zh ? '新建习惯' : 'New Habit';
  String get newItems => locale == AppLocale.zh ? '新建' : 'New';
  String get todoTitleHint =>
      locale == AppLocale.zh ? '输入待办标题' : 'Enter todo title';
  String get timingMethod => locale == AppLocale.zh ? '计时方式' : 'Timing';
  String get forwardTimer => locale == AppLocale.zh ? '正向计时' : 'Count Up';
  String get backwardTimer => locale == AppLocale.zh ? '倒向计时' : 'Count Down';
  String get noTimer => locale == AppLocale.zh ? '不记时' : 'No Timer';
  String get duration => locale == AppLocale.zh ? '时长' : 'Duration';
  String get customMin => locale == AppLocale.zh ? '自定义' : 'Custom';
  String get customMinHint =>
      locale == AppLocale.zh ? '自定义分钟数' : 'Custom minutes';
  String get keepTomorrow =>
      locale == AppLocale.zh ? '明天继续' : 'Repeat Tomorrow';
  String get keepTomorrowDesc =>
      locale == AppLocale.zh ? '完成后明天自动重建' : 'Auto-recreate tomorrow when done';
  String get completed => locale == AppLocale.zh ? '已完成' : 'Completed';
  String get habits => locale == AppLocale.zh ? '习惯' : 'Habits';
  String get noTodos => locale == AppLocale.zh ? '还没有待办事项' : 'No todos yet';
  String get noTodosHint => locale == AppLocale.zh
      ? '点击右下角 + 添加待办或习惯'
      : 'Tap + to add a todo or habit';
  String get oneTimeTask =>
      locale == AppLocale.zh ? '一次性任务 · 支持计时' : 'One-time · With timer';
  String get dailyHabit =>
      locale == AppLocale.zh ? '每日打卡 · 计量目标' : 'Daily · Track count';
  String get delete => locale == AppLocale.zh ? '删除' : 'Delete';
  String get edit => locale == AppLocale.zh ? '编辑' : 'Edit';
  String confirmDelete(String name) => locale == AppLocale.zh
      ? '确定删除「$name」吗？此操作不可撤销。'
      : 'Delete "$name"? This cannot be undone.';
  String get cancel => locale == AppLocale.zh ? '取消' : 'Cancel';
  String get save => locale == AppLocale.zh ? '保存' : 'Save';
  String get enterTitle =>
      locale == AppLocale.zh ? '请输入标题' : 'Please enter a title';
  String get editTodo => locale == AppLocale.zh ? '编辑待办' : 'Edit Todo';
  String get editHabit => locale == AppLocale.zh ? '编辑习惯' : 'Edit Habit';
  String get genericItem => locale == AppLocale.zh ? '此项' : 'this item';
  String get add => locale == AppLocale.zh ? '添加' : 'Add';
  String timingLabel(String value) => switch (value) {
    'forward' => locale == AppLocale.zh ? '正向计时' : 'Count Up',
    'backward' => locale == AppLocale.zh ? '倒向计时' : 'Count Down',
    _ => locale == AppLocale.zh ? '不记时' : 'No Timer',
  };

  // ---- Habit ----
  String get habitName => locale == AppLocale.zh ? '习惯名称' : 'Habit name';
  String get targetCount =>
      locale == AppLocale.zh ? '每日目标数量' : 'Daily target count';
  String get unit => locale == AppLocale.zh ? '量词' : 'Unit';
  String get daily => locale == AppLocale.zh ? '每日' : 'Daily';
  String get customUnit => locale == AppLocale.zh ? '自定义量词' : 'Custom unit';
  String get unitHint =>
      locale == AppLocale.zh ? '输入自定义量词' : 'Enter custom unit';
  String habitUnitLabel(String value) {
    if (locale == AppLocale.zh) return value;
    return const {
          '次': 'times',
          '分钟': 'minutes',
          '小时': 'hours',
          '个': 'items',
          '页': 'pages',
          '道': 'problems',
        }[value] ??
        value;
  }

  String habitProgress(int current, int target, String unit) =>
      locale == AppLocale.zh
      ? '每日 $current/$target ${habitUnitLabel(unit)}'
      : 'Daily $current/$target ${habitUnitLabel(unit)}';

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
  String get recordAndExit =>
      locale == AppLocale.zh ? '记录并退出' : 'Record & Exit';
  String get abandon => locale == AppLocale.zh ? '放弃' : 'Discard';
  String get back => locale == AppLocale.zh ? '返回' : 'Back';
  String focusTime(String time) =>
      locale == AppLocale.zh ? '专注 $time' : 'Focused $time';
  String get remaining => locale == AppLocale.zh ? '剩余时间' : 'Remaining';
  String get done => locale == AppLocale.zh ? '已完成' : 'Done';
  String get timerNotStarted =>
      locale == AppLocale.zh ? '尚未开始计时' : 'Timer has not started';
  String get confirmExitTimer =>
      locale == AppLocale.zh ? '确定退出计时？' : 'Exit the timer?';
  String get todoDone =>
      locale == AppLocale.zh ? '待办已完成 ✓' : 'Todo completed ✓';

  // ---- Today ----
  String get todayFocus => locale == AppLocale.zh ? '今日专注' : "Today's Focus";
  String get pomodoroCount => locale == AppLocale.zh ? '今日番茄数' : 'Pomodoros';
  String get timeAllocation =>
      locale == AppLocale.zh ? '时间分配' : 'Time Allocation';
  String get last7days => locale == AppLocale.zh ? '最近 7 天' : 'Last 7 Days';
  String get noFocusData =>
      locale == AppLocale.zh ? '今天还没有专注数据' : 'No focus data today';
  String get ustcNews => locale == AppLocale.zh ? 'USTC 要闻' : 'USTC News';
  String get noNews =>
      locale == AppLocale.zh ? '暂无 USTC 要闻' : 'No USTC news available';
  String get retry => locale == AppLocale.zh ? '重试' : 'Retry';
  String get viewNews =>
      locale == AppLocale.zh ? '查看今日 USTC 要闻' : "View Today's USTC News";
  String get loadFailed => locale == AppLocale.zh ? '加载失败' : 'Failed to load';

  // ---- Me ----
  String get checkIn => locale == AppLocale.zh ? '打卡' : 'Check-in';
  String get stats => locale == AppLocale.zh ? '统计' : 'Stats';
  String get settings => locale == AppLocale.zh ? '设置' : 'Settings';
  String get todayCheckIn =>
      locale == AppLocale.zh ? '今日打卡' : "Today's Check-in";
  String get wakeUp => locale == AppLocale.zh ? '起床' : 'Wake Up';
  String get sleep => locale == AppLocale.zh ? '睡觉' : 'Sleep';
  String get workout => locale == AppLocale.zh ? '健身' : 'Workout';
  String get workoutCheckIn => locale == AppLocale.zh ? '记录健身' : 'Log Workout';
  String get workoutDuration => locale == AppLocale.zh ? '持续时长' : 'Duration';
  String minutes(int value) =>
      locale == AppLocale.zh ? '$value 分钟' : '$value min';
  String get customDuration =>
      locale == AppLocale.zh ? '自定义时长' : 'Custom duration';
  String get minuteUnit => locale == AppLocale.zh ? '分钟' : 'min';
  String get workoutDurationError =>
      locale == AppLocale.zh ? '请输入 1–1440 分钟' : 'Enter 1–1440 minutes';
  String get workoutDescription =>
      locale == AppLocale.zh ? '内容描述（可选）' : 'Description (optional)';
  String get workoutDescriptionHint => locale == AppLocale.zh
      ? '如：爬楼梯、力量训练'
      : 'e.g. Stair climbing or strength training';
  String get saveWorkoutCheckIn =>
      locale == AppLocale.zh ? '保存健身记录' : 'Save Workout';
  String get legacyWorkoutRecord =>
      locale == AppLocale.zh ? '旧记录' : 'Legacy record';
  String workoutRecordSummary(int durationMinutes, String? description) {
    final durationText = minutes(durationMinutes);
    final detail = description?.trim() ?? '';
    return detail.isEmpty ? durationText : '$durationText · $detail';
  }

  String legacyWorkoutAt(String time) =>
      locale == AppLocale.zh ? '旧记录 $time' : 'Legacy record $time';
  String get notCheckedIn => locale == AppLocale.zh ? '未打卡' : 'Not checked in';
  String get checkInHistory => locale == AppLocale.zh ? '打卡记录' : 'History';
  String get noCheckInRecords =>
      locale == AppLocale.zh ? '暂无打卡记录' : 'No records yet';
  String get todoCompleted => locale == AppLocale.zh ? '待办完成' : 'Todos Done';
  String get habitCompleted => locale == AppLocale.zh ? '习惯完成' : 'Habits Done';
  String get activeCountdowns =>
      locale == AppLocale.zh ? '活跃倒计时' : 'Active Countdowns';
  String get lifetimeStats =>
      locale == AppLocale.zh ? '累计统计' : 'Lifetime Stats';
  String get totalFocus => locale == AppLocale.zh ? '累计专注' : 'Total Focus';
  String get totalSessions =>
      locale == AppLocale.zh ? '专注次数' : 'Focus Sessions';
  String get totalTodos => locale == AppLocale.zh ? '完成待办' : 'Todos Completed';
  String get totalHabits =>
      locale == AppLocale.zh ? '完成习惯' : 'Habits Completed';
  String get usingSince => locale == AppLocale.zh ? '开始使用' : 'Using Since';
  String get sleepTrends =>
      locale == AppLocale.zh ? '近七天睡眠趋势' : '7-Day Sleep Trends';
  String get wakeTime => locale == AppLocale.zh ? '起床时间' : 'Wake Time';
  String get bedtime => locale == AppLocale.zh ? '睡觉时间' : 'Bedtime';
  String get sleepDuration =>
      locale == AppLocale.zh ? '睡眠时长' : 'Sleep Duration';
  String get average => locale == AppLocale.zh ? '平均' : 'Average';
  String get changeAvatar => locale == AppLocale.zh ? '更改' : 'Change';
  String sleepRecordSummary(String wake, String workout, String sleep) =>
      locale == AppLocale.zh
      ? '起床 $wake  ·  健身 $workout  ·  睡觉 $sleep'
      : 'Wake $wake  ·  Workout $workout  ·  Sleep $sleep';
  String selectCheckInTime(CheckInType type) => switch (type) {
        CheckInType.sleep =>
          locale == AppLocale.zh ? '选择睡觉时间' : 'Select bedtime',
        CheckInType.wake =>
          locale == AppLocale.zh ? '选择起床时间' : 'Select wake time',
        CheckInType.workout =>
          locale == AppLocale.zh ? '选择健身时间' : 'Select workout time',
      };
  String count(int value) => locale == AppLocale.zh ? '$value 个' : '$value';

  // ---- Settings ----
  String get cloudSync => locale == AppLocale.zh ? '云端同步' : 'Cloud Sync';
  String get lateNightMode =>
      locale == AppLocale.zh ? '熬夜模式' : 'Late-night mode';
  String get lateNightModeDescription => locale == AppLocale.zh
      ? '凌晨 4 点前的活动计入前一天'
      : 'Activity before 4 AM counts toward the previous day';
  String lateNightModeActive(String date) => locale == AppLocale.zh
      ? '已开启 · 当前活动计入 $date'
      : 'On · current activity counts toward $date';
  String get exportAllData =>
      locale == AppLocale.zh ? '导出所有数据' : 'Export all data';
  String get exportAllDataSubtitle => locale == AppLocale.zh
      ? '保存带版本信息的完整 JSON 备份'
      : 'Save a complete, versioned JSON backup';
  String get exportAllDataSuccess =>
      locale == AppLocale.zh ? '数据已成功导出' : 'Data exported successfully';
  String get exportAllDataFailed => locale == AppLocale.zh
      ? '导出失败，请重新选择保存位置'
      : 'Export failed. Choose another save location.';
  String get connected => locale == AppLocale.zh ? '已连接' : 'Connected';
  String get notConnected => locale == AppLocale.zh ? '未连接' : 'Not Connected';
  String get connectedToSupabase =>
      locale == AppLocale.zh ? '已连接 Supabase' : 'Connected to Supabase';
  String get user => locale == AppLocale.zh ? '用户' : 'User';
  String get unknown => locale == AppLocale.zh ? '未知' : 'Unknown';
  String get logout => locale == AppLocale.zh ? '退出登录' : 'Sign Out';
  String get logoutConfirm => locale == AppLocale.zh
      ? '确定退出登录？本地数据会保留，重新登录后将从云端同步。'
      : 'Sign out? Local data is kept; re-login will sync from cloud.';
  String get signOut => locale == AppLocale.zh ? '退出' : 'Sign Out';
  String get signOutSubtitle => locale == AppLocale.zh
      ? '退出后数据保留在本地，重新登录可同步'
      : 'Local data is kept and can sync after signing in again';
  String get checkUpdate =>
      locale == AppLocale.zh ? '检查更新' : 'Check for Updates';
  String get checkUpdateSubtitle => locale == AppLocale.zh
      ? '检查是否有新版本可用'
      : 'Check whether a newer version is available';
  String get checkingUpdate =>
      locale == AppLocale.zh ? '正在检查更新...' : 'Checking for updates...';
  String get latestVersion =>
      locale == AppLocale.zh ? '当前已是最新版本' : 'Already up to date';
  String get aboutGoWorkBro =>
      locale == AppLocale.zh ? '关于 GoWorkBro' : 'About GoWorkBro';
  String get version => locale == AppLocale.zh ? '版本' : 'Version';
  String get openSourceLicenses =>
      locale == AppLocale.zh ? '开源许可' : 'Open Source Licenses';
  String get language => locale == AppLocale.zh ? '语言' : 'Language';
  String get chinese => locale == AppLocale.zh ? '中文' : 'Chinese';
  String get english => locale == AppLocale.zh ? '英文' : 'English';
  String get theme => locale == AppLocale.zh ? '主题' : 'Theme';
  String get lightMode => locale == AppLocale.zh ? '浅色' : 'Light';
  String get darkMode => locale == AppLocale.zh ? '深色' : 'Dark';
  String get systemMode => locale == AppLocale.zh ? '跟随系统' : 'System';
  String get font => locale == AppLocale.zh ? '字体' : 'Font';
  String get fontSystem => locale == AppLocale.zh ? '系统默认' : 'System';
  String get fontWenKai => locale == AppLocale.zh ? '霞鹜文楷' : 'WenKai';
  String get fontNoto => locale == AppLocale.zh ? '思源黑体' : 'Noto Sans SC';
  String get deleteData =>
      locale == AppLocale.zh ? '删除所有数据' : 'Delete All Data';
  String get deleteDataConfirm => locale == AppLocale.zh
      ? '确定删除所有本地数据？此操作不可撤销，包括待办、习惯、打卡记录等。'
      : 'Delete all local data? This is irreversible and includes todos, habits, check-in records, etc.';
  String get deleteDataSuccess =>
      locale == AppLocale.zh ? '所有数据已删除' : 'All data deleted';
  String get editName => locale == AppLocale.zh ? '编辑昵称' : 'Edit Name';
  String get nameHint => locale == AppLocale.zh ? '输入昵称' : 'Enter name';

  // ---- Countdown ----
  String get noCountdowns =>
      locale == AppLocale.zh ? '还没有倒计时' : 'No countdowns';
  String get addCountdownHint =>
      locale == AppLocale.zh ? '点击右下角 + 添加' : 'Tap + to add';
  String get countdownTitle =>
      locale == AppLocale.zh ? '倒计时标题' : 'Countdown title';
  String get editCountdown =>
      locale == AppLocale.zh ? '编辑倒计时' : 'Edit Countdown';
  String get newCountdown => locale == AppLocale.zh ? '新建倒计时' : 'New Countdown';
  String get title => locale == AppLocale.zh ? '标题' : 'Title';
  String get countdownTitleHint =>
      locale == AppLocale.zh ? '如：考试、截止日期' : 'e.g. Exam or deadline';
  String get color => locale == AppLocale.zh ? '颜色' : 'Color';
  String get targetDate => locale == AppLocale.zh ? '目标日期' : 'Target date';
  String get targetTime => locale == AppLocale.zh ? '目标时间' : 'Target time';
  String get days => locale == AppLocale.zh ? '天' : 'd';
  String get hours => locale == AppLocale.zh ? '时' : 'h';
  String get mins => locale == AppLocale.zh ? '分' : 'm';
  String get secs => locale == AppLocale.zh ? '秒' : 's';
  String get expired => locale == AppLocale.zh ? '已过期' : 'Expired';
  String get countdownFinished => locale == AppLocale.zh ? '已结束' : 'Finished';
  String get deleteCountdownTitle =>
      locale == AppLocale.zh ? '删除倒计时' : 'Delete Countdown';
  String get targetMustBeFuture =>
      locale == AppLocale.zh ? '目标时间必须在未来' : 'Target must be in the future';
  String get create => locale == AppLocale.zh ? '创建' : 'Create';
  String countdownTarget(String value) =>
      locale == AppLocale.zh ? '目标：$value' : 'Target: $value';
  String confirmDeleteCountdown(String name) =>
      locale == AppLocale.zh ? '确认删除「$name」？' : 'Delete "$name"?';
  String get noPomodoroRecords =>
      locale == AppLocale.zh ? '还没有番茄记录' : 'No focus-session records yet';
  String weekdayShort(int weekday) {
    const zh = ['一', '二', '三', '四', '五', '六', '日'];
    const en = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
    return (locale == AppLocale.zh ? zh : en)[weekday - 1];
  }

  // ---- Updates ----
  String downloadFailed(Object error) =>
      locale == AppLocale.zh ? '下载失败: $error' : 'Download failed: $error';
  String get newVersionAvailable =>
      locale == AppLocale.zh ? '发现新版本' : 'Update Available';
  String currentVersion(String value) =>
      locale == AppLocale.zh ? '当前版本: v$value' : 'Current version: v$value';
  String latestVersionValue(String value) =>
      locale == AppLocale.zh ? '最新版本: v$value' : 'Latest version: v$value';
  String get releaseNotes => locale == AppLocale.zh ? '更新内容:' : 'What’s new:';
  String get later => locale == AppLocale.zh ? '稍后再说' : 'Later';
  String get downloadAndInstall =>
      locale == AppLocale.zh ? '下载并安装' : 'Download & Install';
  String get goToDownload =>
      locale == AppLocale.zh ? '前往下载' : 'Open Download Page';
  String get downloadingUpdate =>
      locale == AppLocale.zh ? '正在下载更新...' : 'Downloading update...';

  // ---- Tray ----
  String get trayRunning => locale == AppLocale.zh
      ? 'GoWorkBro — 正在后台运行'
      : 'GoWorkBro — Running in background';
  String get showMainWindow =>
      locale == AppLocale.zh ? '显示主窗口' : 'Show Main Window';
  String get exitGoWorkBro =>
      locale == AppLocale.zh ? '退出 GoWorkBro' : 'Exit GoWorkBro';
}
