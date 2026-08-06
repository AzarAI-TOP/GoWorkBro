import 'package:uuid/uuid.dart';

const _uuid = Uuid();

class FocusSession {
  final String id;
  final String? todoId;
  final String sourceType; // 'todo' or 'countdown'
  final String sourceTitle;
  final String startTime;
  final String endTime;
  final int durationSeconds;
  final String sessionDate;

  FocusSession({
    required this.id,
    this.todoId,
    required this.sourceType,
    required this.sourceTitle,
    required this.startTime,
    required this.endTime,
    required this.durationSeconds,
    required this.sessionDate,
  });

  factory FocusSession.create({
    String? todoId,
    required String sourceType,
    required String sourceTitle,
    required int durationSeconds,
  }) {
    final now = DateTime.now();
    final start = now.subtract(Duration(seconds: durationSeconds));
    return FocusSession(
      id: _uuid.v4(),
      todoId: todoId,
      sourceType: sourceType,
      sourceTitle: sourceTitle,
      startTime: start.toIso8601String(),
      endTime: now.toIso8601String(),
      durationSeconds: durationSeconds,
      sessionDate: _dateOnly(now),
    );
  }

  static String _dateOnly(DateTime dt) =>
      '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')}';

  Map<String, dynamic> toMap() => {
    'id': id,
    'todo_id': todoId,
    'source_type': sourceType,
    'source_title': sourceTitle,
    'start_time': startTime,
    'end_time': endTime,
    'duration_seconds': durationSeconds,
    'session_date': sessionDate,
  };

  factory FocusSession.fromMap(Map<String, dynamic> m) => FocusSession(
    id: m['id'] as String,
    todoId: m['todo_id'] as String?,
    sourceType: m['source_type'] as String,
    sourceTitle: m['source_title'] as String,
    startTime: m['start_time'] as String,
    endTime: m['end_time'] as String,
    durationSeconds: m['duration_seconds'] as int,
    sessionDate: m['session_date'] as String,
  );
}
