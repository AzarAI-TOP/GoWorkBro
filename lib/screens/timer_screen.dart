import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../models/models.dart';
import '../providers/app_provider.dart';

/// Full-screen pomodoro timer page.
/// Supports forward (count up), backward (count down), and none (no timing).
/// On finish, records a FocusSession AND marks the todo as completed.
class TimerScreen extends StatefulWidget {
  final Todo todo;

  const TimerScreen({super.key, required this.todo});

  @override
  State<TimerScreen> createState() => _TimerScreenState();
}

class _TimerScreenState extends State<TimerScreen> {
  late int _elapsedSeconds;
  late int _targetSeconds;
  Timer? _timer;
  bool _isRunning = false;
  bool _isFinished = false;

  @override
  void initState() {
    super.initState();
    _elapsedSeconds = 0;
    if (widget.todo.timingType == TimingType.backward) {
      _targetSeconds = widget.todo.durationMinutes * 60;
    } else {
      _targetSeconds = 0; // forward or none
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _start() {
    if (_isFinished) return;
    setState(() => _isRunning = true);
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      setState(() {
        _elapsedSeconds++;
        if (widget.todo.timingType == TimingType.backward &&
            _elapsedSeconds >= _targetSeconds) {
          _timer?.cancel();
          _isRunning = false;
          _isFinished = true;
          _recordSessionAndComplete();
        }
      });
    });
  }

  void _pause() {
    _timer?.cancel();
    setState(() => _isRunning = false);
  }

  void _finish() {
    if (_elapsedSeconds == 0) {
      Navigator.pop(context);
      return;
    }
    _timer?.cancel();
    setState(() {
      _isRunning = false;
      _isFinished = true;
    });
    _recordSessionAndComplete();
  }

  /// Record focus session AND mark the todo as completed.
  void _recordSessionAndComplete() {
    final provider = context.read<AppProvider>();

    // Record focus session
    provider.recordFocusSession(FocusSession.create(
      todoId: widget.todo.id,
      sourceType: 'todo',
      sourceTitle: widget.todo.title,
      durationSeconds: _elapsedSeconds,
    ));

    // Read the CURRENT state of the todo from the provider (avoids stale
    // widget.todo snapshot if the todo was modified while timer was open).
    final currentTodo = provider.todos.where((t) => t.id == widget.todo.id).firstOrNull;
    final base = currentTodo ?? widget.todo;

    if (!base.isCompleted) {
      final completed = base.copyWith(
        isCompleted: true,
        completedDate: DateTime.now().toIso8601String(),
        actualDurationSeconds: base.actualDurationSeconds + _elapsedSeconds,
      );
      provider.updateTodo(completed);
    } else {
      final updated = base.copyWith(
        actualDurationSeconds: base.actualDurationSeconds + _elapsedSeconds,
      );
      provider.updateTodo(updated);
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
    final isBackward = widget.todo.timingType == TimingType.backward;
    final isNoTiming = widget.todo.timingType == TimingType.none;

    // Progress for ring
    double progress = 0;
    if (isBackward && _targetSeconds > 0) {
      progress = (_elapsedSeconds / _targetSeconds).clamp(0.0, 1.0);
    }

    // Remaining seconds for backward
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
                  isNoTiming ? '记录' : '完成',
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
                // Timer ring / display
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
                            backgroundColor:
                                cs.primary.withValues(alpha: 0.12),
                            valueColor:
                                AlwaysStoppedAnimation(cs.primary),
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
                                  FontFeature.tabularFigures()
                                ],
                              ),
                            ),
                            const SizedBox(height: 8),
                            if (isBackward)
                              Text(
                                _isFinished ? '已完成' : '剩余时间',
                                style: TextStyle(
                                  fontSize: 14,
                                  color: cs.onSurfaceVariant,
                                ),
                              )
                            else if (!isNoTiming)
                              Text(
                                '正向计时',
                                style: TextStyle(
                                    fontSize: 14, color: cs.onSurfaceVariant),
                              )
                            else
                              Text(
                                '不记时',
                                style: TextStyle(
                                    fontSize: 14, color: cs.onSurfaceVariant),
                              ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 48),
                // Control buttons
                if (_isFinished) ...[
                  Icon(Icons.check_circle,
                      size: 64, color: cs.primary),
                  const SizedBox(height: 16),
                  Text('专注 ${_formatTime(_elapsedSeconds)}',
                      style: theme.textTheme.titleMedium),
                  const SizedBox(height: 8),
                  Text('待办已完成 ✓', style: theme.textTheme.bodyMedium?.copyWith(color: cs.primary)),
                  const SizedBox(height: 24),
                  FilledButton(
                    onPressed: () => Navigator.pop(context),
                    child: const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 32, vertical: 8),
                      child: Text('返回'),
                    ),
                  ),
                ] else ...[
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      if (_isRunning)
                        _controlButton(
                          icon: Icons.pause,
                          label: '暂停',
                          color: cs.primary,
                          onTap: _pause,
                        )
                      else
                        _controlButton(
                          icon: Icons.play_arrow,
                          label: '开始',
                          color: cs.primary,
                          onTap: _start,
                        ),
                      const SizedBox(width: 24),
                      if (_elapsedSeconds > 0) ...[
                        _controlButton(
                          icon: Icons.stop,
                          label: '完成',
                          color: cs.error,
                          onTap: _finish,
                        ),
                        const SizedBox(width: 24),
                      ],
                      _controlButton(
                        icon: Icons.close,
                        label: '取消',
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
          Text(label,
              style: TextStyle(fontSize: 12, color: color)),
        ],
      ),
    );
  }

  void _showExitConfirm() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('退出计时'),
        content: Text(_elapsedSeconds > 0
            ? '已计时 ${_formatTime(_elapsedSeconds)}，是否记录本次专注？'
            : '确定退出计时？'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('继续计时'),
          ),
          if (_elapsedSeconds > 0)
            TextButton(
              onPressed: () {
                Navigator.pop(ctx);
                _finish();
                Navigator.pop(context);
              },
              child: const Text('记录并退出'),
            ),
          TextButton(
            onPressed: () {
              Navigator.pop(ctx);
              Navigator.pop(context);
            },
            style: TextButton.styleFrom(foregroundColor: Colors.red),
            child: const Text('放弃'),
          ),
        ],
      ),
    );
  }
}
