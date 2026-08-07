import 'package:uuid/uuid.dart';

const _uuid = Uuid();

class SleepRecord {
  final String id;
  final String recordDate;
  final String? wakeTime;
  final String? sleepTime;
  final String? workoutTime;
  final String? note;

  SleepRecord({
    required this.id,
    required this.recordDate,
    this.wakeTime,
    this.sleepTime,
    this.workoutTime,
    this.note,
  });

  factory SleepRecord.create({
    required String recordDate,
    String? wakeTime,
    String? sleepTime,
    String? workoutTime,
    String? note,
  }) {
    return SleepRecord(
      id: _uuid.v4(),
      recordDate: recordDate,
      wakeTime: wakeTime,
      sleepTime: sleepTime,
      workoutTime: workoutTime,
      note: note,
    );
  }

  SleepRecord copyWith({
    String? wakeTime,
    String? sleepTime,
    String? workoutTime,
    String? note,
  }) {
    return SleepRecord(
      id: id,
      recordDate: recordDate,
      wakeTime: wakeTime ?? this.wakeTime,
      sleepTime: sleepTime ?? this.sleepTime,
      workoutTime: workoutTime ?? this.workoutTime,
      note: note ?? this.note,
    );
  }

  Map<String, dynamic> toMap() => {
    'id': id,
    'record_date': recordDate,
    'wake_time': wakeTime,
    'sleep_time': sleepTime,
    'workout_time': workoutTime,
    'note': note,
  };

  factory SleepRecord.fromMap(Map<String, dynamic> m) => SleepRecord(
    id: m['id'] as String,
    recordDate: m['record_date'] as String,
    wakeTime: m['wake_time'] as String?,
    sleepTime: m['sleep_time'] as String?,
    workoutTime: m['workout_time'] as String?,
    note: m['note'] as String?,
  );
}
