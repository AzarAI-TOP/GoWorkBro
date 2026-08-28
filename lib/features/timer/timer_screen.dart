import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:goworkbro/models/models.dart';
import 'package:goworkbro/providers/app_provider.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';

/// Full-screen pomodoro timer page.
/// Supports forward (count up), backward (count down), and none (no timing).
/// On finish, records a FocusSession AND marks the todo as completed.
class TimerScreen extends StatefulWidget {
  final Todo todo;

  const TimerScreen({super.key, required this.todo});

  @override
  State<TimerScreen> createState() => _TimerScreenState();
}

class _TimerScreenState extends State<TimerScreen> with WidgetsBindingObserver {
  late int _elapsedSeconds;
  late int _targetSeconds;
  Timer? _timer;
  bool _isRunning = false;
  bool _isFinished = false;
  bool _sessionRecorded = false;
  DateTime? _backgroundedAt;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _elapsedSeconds = 0;
    if (widget.todo.timingType == TimingType.backward) {
      _targetSeconds = widget.todo.durationMinutes * 60;
    } else {
      _targetSeconds = 0;
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused && _isRunning) {
      // Record when app went to background so we can recover elapsed time
      _backgroundedAt = DateTime.now();
      _timer?.cancel();
      setState(() => _isRunning = false);
    } else if (state == AppLifecycleState.resumed && _backgroundedAt != null) {
      // Recover elapsed time while in background
      final delta = DateTime.now().difference(_backgroundedAt!).inSeconds;
      _backgroundedAt = null;
      _elapsedSeconds += delta;
      if (widget.todo.timingType == TimingType.backward &&
          _elapsedSeconds >= _targetSeconds) {
        setState(() {
          _isFinished = true;
        });
        unawaited(_recordSessionAndComplete());
      } else {
        setState(() {});
        _start();
      }
    }
  }

  void _start() {
    if (_isFinished) return;
    setState(() => _isRunning = true);
    HapticFeedback.lightImpact();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      _elapsedSeconds++;
      if (widget.todo.timingType == TimingType.backward &&
          _elapsedSeconds >= _targetSeconds) {
        _timer?.cancel();
        setState(() {
          _isRunning = false;
          _isFinished = true;
        });
        unawaited(_recordSessionAndComplete());
        return;
      }
      setState(() {});
    });
  }

  void _pause() {
    _timer?.cancel();
    HapticFeedback.lightImpact();
    setState(() => _isRunning = false);
  }

  Future<void> _finish() async {
    if (_elapsedSeconds == 0) {
      // #9: Show feedback instead of silently popping
      unawaited(HapticFeedback.heavyImpact());
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            S.of(context.read<AppLocaleProvider>().locale).timerNotStarted,
          ),
          duration: const Duration(milliseconds: 1200),
        ),
      );
      return;
    }
    _timer?.cancel();
    unawaited(HapticFeedback.mediumImpact());
    setState(() {
      _isRunning = false;
      _isFinished = true;
    });
    await _recordSessionAndComplete();
  }

  /// Record focus session AND mark the todo as completed.
  /// #10: Read current todo title from provider to avoid stale snapshot.
  ///
  /// Both writes are awaited — a lost DB write must be visible to the user,
  /// not silently dropped with the data gone. On failure the finished state
  /// is reverted so 停止/记录 can be pressed again; the session half of the
  /// pair is remembered so a retry cannot double-record it.
  Future<void> _recordSessionAndComplete() async {
    final provider = context.read<AppProvider>();

    // #10: Use current title from provider if available
    final currentTodo = provider.todos
        .where((t) => t.id == widget.todo.id)
        .firstOrNull;
    final currentTitle = currentTodo?.title ?? widget.todo.title;

    try {
      if (!_sessionRecorded) {
        await provider.recordFocusSession(
          FocusSession.create(
            todoId: widget.todo.id,
            sourceType: 'todo',
            sourceTitle: currentTitle,
            durationSeconds: _elapsedSeconds,
          ),
        );
        _sessionRecorded = true;
      }
      await provider.completeTodoWithDuration(widget.todo.id, _elapsedSeconds);
    } catch (e) {
      debugPrint('Record focus session/todo completion failed: $e');
      if (!mounted) return;
      setState(() {
        _isRunning = false;
        _isFinished = false;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            S.of(context.read<AppLocaleProvider>().locale).recordSaveFailed,
          ),
        ),
      );
    }
  }

  String _formatTime(int seconds) {
    final h = seconds ~/ 3600;
    final m = (seconds % 3600) ~/ 60;
    final s = seconds % 60;
    if (h > 0) {
      return '${h.toString().padLeft(2, '0')}:${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
    }
    return '${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final cs = theme.colorScheme;
    final s = S.of(context.read<AppLocaleProvider>().locale);
    final isBackward = widget.todo.timingType == TimingType.backward;
    final isNoTiming = widget.todo.timingType == TimingType.none;

    double progress = 0;
    if (isBackward && _targetSeconds > 0) {
      progress = (_elapsedSeconds / _targetSeconds).clamp(0.0, 1.0);
    }

    final displaySeconds = isBackward
        ? (_targetSeconds - _elapsedSeconds).clamp(0, _targetSeconds)
        : _elapsedSeconds;

    return PopScope(
      canPop: !_isRunning,
      onPopInvokedWithResult: (didPop, result) {
        if (!didPop && _elapsedSeconds > 0) {
          _showExitConfirm();
        }
      },
      child: Scaffold(
        appBar: AppBar(
          leading: IconButton(
            icon: const Icon(Icons.close),
            onPressed: () {
              if (_isRunning) {
                _showExitConfirm();
              } else {
                Navigator.pop(context);
              }
            },
          ),
          title: Text(widget.todo.title, style: const TextStyle(fontSize: 16)),
          actions: [
            if (!_isFinished && _elapsedSeconds > 0)
              TextButton(
                onPressed: _finish,
                child: Text(
                  isNoTiming ? s.record : s.stop,
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
              ),
          ],
        ),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 32),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                SizedBox(
                  width: 260,
                  height: 260,
                  child: Stack(
                    children: [
                      if (isBackward || !isNoTiming)
                        SizedBox(
                          width: 260,
                          height: 260,
                          child: CircularProgressIndicator(
                            value: isBackward ? progress : null,
                            strokeWidth: 6,
                            backgroundColor: cs.primary.withValues(alpha: 0.12),
                            valueColor: AlwaysStoppedAnimation(cs.primary),
                          ),
                        ),
                      Center(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Text(
                              _formatTime(displaySeconds),
                              style: TextStyle(
                                fontSize: 56,
                                fontWeight: FontWeight.w700,
                                color: cs.onSurface,
                                fontFeatures: const [
                                  FontFeature.tabularFigures(),
                                ],
                              ),
                            ),
                            const SizedBox(height: 8),
                            if (isBackward)
                              Text(
                                _isFinished ? s.done : s.remaining,
                                style: TextStyle(
                                  fontSize: 14,
                                  color: cs.onSurfaceVariant,
                                ),
                              )
                            else if (!isNoTiming)
                              Text(
                                s.forwardTimer,
                                style: TextStyle(
                                  fontSize: 14,
                                  color: cs.onSurfaceVariant,
                                ),
                              )
                            else
                              Text(
                                s.noTimer,
                                style: TextStyle(
                                  fontSize: 14,
                                  color: cs.onSurfaceVariant,
                                ),
                              ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 48),
                if (_isFinished) ...[
                  Icon(Icons.check_circle, size: 64, color: cs.primary),
                  const SizedBox(height: 16),
                  Text(
                    s.focusTime(_formatTime(_elapsedSeconds)),
                    style: theme.textTheme.titleMedium,
                  ),
                  const SizedBox(height: 8),
                  Text(
                    s.todoDone,
                    style: theme.textTheme.bodyMedium?.copyWith(
                      color: cs.primary,
                    ),
                  ),
                  const SizedBox(height: 24),
                  FilledButton(
                    onPressed: () => Navigator.pop(context),
                    child: Padding(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 32,
                        vertical: 8,
                      ),
                      child: Text(s.back),
                    ),
                  ),
                ] else ...[
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      if (_isRunning)
                        _controlButton(
                          icon: Icons.pause,
                          label: s.pause,
                          color: cs.primary,
                          onTap: _pause,
                        )
                      else
                        _controlButton(
                          icon: Icons.play_arrow,
                          label: s.start,
                          color: cs.primary,
                          onTap: _start,
                        ),
                      const SizedBox(width: 24),
                      if (_elapsedSeconds > 0) ...[
                        _controlButton(
                          icon: Icons.stop,
                          label: s.stop,
                          color: cs.error,
                          onTap: _finish,
                        ),
                        const SizedBox(width: 24),
                      ],
                      _controlButton(
                        icon: Icons.close,
                        label: s.cancel,
                        color: cs.onSurfaceVariant,
                        onTap: () {
                          if (_elapsedSeconds > 0) {
                            _showExitConfirm();
                          } else {
                            Navigator.pop(context);
                          }
                        },
                      ),
                    ],
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _controlButton({
    required IconData icon,
    required String label,
    required Color color,
    required VoidCallback onTap,
  }) {
    return GestureDetector(
      onTap: onTap,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.12),
              shape: BoxShape.circle,
            ),
            child: Icon(icon, color: color, size: 28),
          ),
          const SizedBox(height: 6),
          Text(label, style: TextStyle(fontSize: 12, color: color)),
        ],
      ),
    );
  }

  void _showExitConfirm() {
    final s = S.of(context.read<AppLocaleProvider>().locale);
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(s.exitTimer),
        content: Text(
          _elapsedSeconds > 0
              ? s.recordPrompt(_formatTime(_elapsedSeconds))
              : s.confirmExitTimer,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text(s.keepTiming),
          ),
          if (_elapsedSeconds > 0)
            TextButton(
              // Stay on the timer screen when saving failed (reverted
              // _isFinished) so the user can retry instead of losing it.
              onPressed: () async {
                Navigator.pop(ctx);
                await _finish();
                if (mounted && _isFinished) Navigator.pop(context);
              },
              child: Text(s.recordAndExit),
            ),
          TextButton(
            onPressed: () {
              Navigator.pop(ctx);
              Navigator.pop(context);
            },
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: Text(s.abandon),
          ),
        ],
      ),
    );
  }
}
