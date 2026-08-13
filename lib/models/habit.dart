import 'package:uuid/uuid.dart';

const _uuid = Uuid();

class Habit {
  final String id;
  final String title;
  final int targetCount;
  final String unit;
  final int sortOrder;
  final String createdDate;
  final int currentCount;
  final String? lastResetDate;

  /// Last-write timestamp (UTC ISO-8601), stamped by the repository on local
  /// writes and taken from cloud rows on remote applies. Used for LWW merge.
  final String? updatedAt;

  Habit({
    required this.id,
    required this.title,
    this.targetCount = 1,
    this.unit = '次',
    this.sortOrder = 0,
    required this.createdDate,
    this.currentCount = 0,
    this.lastResetDate,
    this.updatedAt,
  });

  factory Habit.create({
    required String title,
    int targetCount = 1,
    String unit = '次',
    int sortOrder = 0,
  }) {
    return Habit(
      id: _uuid.v4(),
      title: title,
      targetCount: targetCount,
      unit: unit,
      sortOrder: sortOrder,
      createdDate: DateTime.now().toIso8601String(),
    );
  }

  bool get isCompleted => currentCount >= targetCount;

  double get progress {
    if (targetCount == 0) return 0;
    return (currentCount / targetCount).clamp(0.0, 1.0);
  }

  Habit copyWith({
    String? title,
    int? targetCount,
    String? unit,
    int? sortOrder,
    int? currentCount,
    String? lastResetDate,
    String? updatedAt,
  }) {
    return Habit(
      id: id,
      title: title ?? this.title,
      targetCount: targetCount ?? this.targetCount,
      unit: unit ?? this.unit,
      sortOrder: sortOrder ?? this.sortOrder,
      createdDate: createdDate,
      currentCount: currentCount ?? this.currentCount,
      lastResetDate: lastResetDate ?? this.lastResetDate,
      updatedAt: updatedAt ?? this.updatedAt,
    );
  }

  Map<String, dynamic> toMap() => {
    'id': id,
    'title': title,
    'target_count': targetCount,
    'unit': unit,
    'sort_order': sortOrder,
    'created_date': createdDate,
    'current_count': currentCount,
    'last_reset_date': lastResetDate,
    'updated_at': updatedAt,
  };

  factory Habit.fromMap(Map<String, dynamic> m) => Habit(
    id: m['id'] as String,
    title: m['title'] as String,
    targetCount: m['target_count'] as int,
    unit: m['unit'] as String,
    sortOrder: m['sort_order'] as int,
    createdDate: m['created_date'] as String,
    currentCount: m['current_count'] as int,
    lastResetDate: m['last_reset_date'] as String?,
    updatedAt: m['updated_at'] as String?,
  );
}
