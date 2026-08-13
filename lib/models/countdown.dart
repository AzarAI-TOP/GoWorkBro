import 'package:uuid/uuid.dart';

const _uuid = Uuid();

class Countdown {
  final String id;
  final String title;
  final DateTime targetDateTime;
  final String createdDate;
  final int colorIndex;

  /// Last-write timestamp (UTC ISO-8601), stamped by the repository on local
  /// writes and taken from cloud rows on remote applies. Used for LWW merge.
  final String? updatedAt;

  Countdown({
    required this.id,
    required this.title,
    required this.targetDateTime,
    required this.createdDate,
    this.colorIndex = 0,
    this.updatedAt,
  });

  factory Countdown.create({
    required String title,
    required DateTime targetDateTime,
    int colorIndex = 0,
  }) {
    return Countdown(
      id: _uuid.v4(),
      title: title,
      targetDateTime: targetDateTime,
      createdDate: DateTime.now().toIso8601String(),
      colorIndex: colorIndex,
    );
  }

  bool get isExpired => DateTime.now().isAfter(targetDateTime);

  Duration get remaining => targetDateTime.difference(DateTime.now());

  Map<String, dynamic> toMap() => {
    'id': id,
    'title': title,
    'target_datetime': targetDateTime.toIso8601String(),
    'created_date': createdDate,
    'color_index': colorIndex,
    'updated_at': updatedAt,
  };

  factory Countdown.fromMap(Map<String, dynamic> m) => Countdown(
    id: m['id'] as String,
    title: m['title'] as String,
    targetDateTime: DateTime.parse(m['target_datetime'] as String),
    createdDate: m['created_date'] as String,
    colorIndex: m['color_index'] as int,
    updatedAt: m['updated_at'] as String?,
  );
}
