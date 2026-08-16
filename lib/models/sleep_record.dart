import 'package:uuid/uuid.dart';

const _uuid = Uuid();

class SleepRecord {
  final String id;
  final String recordDate;
  final String? wakeTime;
  final String? sleepTime;

  /// Legacy clock-time field retained for lossless migration/export.
  final String? workoutTime;
  final int? workoutDurationMinutes;
  final String? note;
  final String? updatedAt;

  SleepRecord({
    required this.id,
    required this.recordDate,
    this.wakeTime,
    this.sleepTime,
    this.workoutTime,
    this.workoutDurationMinutes,
    this.note,
    this.updatedAt,
  });

  factory SleepRecord.create({
    required String recordDate,
    String? wakeTime,
    String? sleepTime,
    String? workoutTime,
    int? workoutDurationMinutes,
    String? note,
    String? updatedAt,
  }) {
    return SleepRecord(
      id: _uuid.v4(),
      recordDate: recordDate,
      wakeTime: wakeTime,
      sleepTime: sleepTime,
      workoutTime: workoutTime,
      workoutDurationMinutes: workoutDurationMinutes,
      note: note,
      updatedAt: updatedAt,
    );
  }

  SleepRecord copyWith({
    String? wakeTime,
    String? sleepTime,
    String? workoutTime,
    int? workoutDurationMinutes,
    String? note,
    String? updatedAt,
  }) {
    return SleepRecord(
      id: id,
      recordDate: recordDate,
      wakeTime: wakeTime ?? this.wakeTime,
      sleepTime: sleepTime ?? this.sleepTime,
      workoutTime: workoutTime ?? this.workoutTime,
      workoutDurationMinutes:
          workoutDurationMinutes ?? this.workoutDurationMinutes,
      note: note ?? this.note,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  Map<String, dynamic> toMap() => {
    'id': id,
    'record_date': recordDate,
    'wake_time': wakeTime,
    'sleep_time': sleepTime,
    'workout_time': workoutTime,
    'workout_duration_minutes': workoutDurationMinutes,
    'note': note,
    'updated_at': updatedAt,
  };

  factory SleepRecord.fromMap(Map<String, dynamic> m) => SleepRecord(
    id: m['id'] as String,
    recordDate: m['record_date'] as String,
    wakeTime: m['wake_time'] as String?,
    sleepTime: m['sleep_time'] as String?,
    workoutTime: m['workout_time'] as String?,
    workoutDurationMinutes: m['workout_duration_minutes'] as int?,
    note: m['note'] as String?,
    updatedAt: m['updated_at'] as String?,
  );
}
