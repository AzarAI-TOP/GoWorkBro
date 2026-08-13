import 'package:uuid/uuid.dart';

import 'package:goworkbro/models/timing_type.dart';

const _uuid = Uuid();

class Todo {
  final String id;
  final String title;
  final TimingType timingType;
  final int durationMinutes;
  final bool isCompleted;
  final int sortOrder;
  final bool keepTomorrow;
  final String createdDate;
  final String? completedDate;
  final int actualDurationSeconds;

  /// Last-write timestamp (UTC ISO-8601). Stamped by the repository on every
  /// local write and taken from the cloud row when a remote change is
  /// applied. Used for last-write-wins merge on pull.
  final String? updatedAt;

  Todo({
    required this.id,
    required this.title,
    required this.timingType,
    this.durationMinutes = 25,
    this.isCompleted = false,
    this.sortOrder = 0,
    this.keepTomorrow = true,
    required this.createdDate,
    this.completedDate,
    this.actualDurationSeconds = 0,
    this.updatedAt,
  });

  factory Todo.create({
    required String title,
    required TimingType timingType,
    int durationMinutes = 25,
    bool keepTomorrow = true,
    int sortOrder = 0,
  }) {
    return Todo(
      id: _uuid.v4(),
      title: title,
      timingType: timingType,
      durationMinutes: durationMinutes,
      keepTomorrow: keepTomorrow,
      sortOrder: sortOrder,
      createdDate: DateTime.now().toIso8601String(),
    );
  }

  Todo copyWith({
    String? title,
    TimingType? timingType,
    int? durationMinutes,
    bool? isCompleted,
    int? sortOrder,
    bool? keepTomorrow,
    String? completedDate,
    int? actualDurationSeconds,
    String? updatedAt,
  }) {
    return Todo(
      id: id,
      title: title ?? this.title,
      timingType: timingType ?? this.timingType,
      durationMinutes: durationMinutes ?? this.durationMinutes,
      isCompleted: isCompleted ?? this.isCompleted,
      sortOrder: sortOrder ?? this.sortOrder,
      keepTomorrow: keepTomorrow ?? this.keepTomorrow,
      createdDate: createdDate,
      completedDate: completedDate ?? this.completedDate,
      actualDurationSeconds: actualDurationSeconds ?? this.actualDurationSeconds,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  Map<String, dynamic> toMap() => {
    'id': id,
    'title': title,
    'timing_type': timingType.value,
    'duration_minutes': durationMinutes,
    'is_completed': isCompleted ? 1 : 0,
    'sort_order': sortOrder,
    'keep_tomorrow': keepTomorrow ? 1 : 0,
    'created_date': createdDate,
    'completed_date': completedDate,
    'actual_duration_seconds': actualDurationSeconds,
    'updated_at': updatedAt,
  };

  factory Todo.fromMap(Map<String, dynamic> m) => Todo(
    id: m['id'] as String,
    title: m['title'] as String,
    timingType: TimingTypeExtension.fromValue(m['timing_type'] as String),
    durationMinutes: m['duration_minutes'] as int,
    isCompleted: (m['is_completed'] as int) == 1,
    sortOrder: m['sort_order'] as int,
    keepTomorrow: (m['keep_tomorrow'] as int) == 1,
    createdDate: m['created_date'] as String,
    completedDate: m['completed_date'] as String?,
    actualDurationSeconds: m['actual_duration_seconds'] as int,
    updatedAt: m['updated_at'] as String?,
  );
}
