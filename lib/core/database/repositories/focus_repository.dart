
import '../../../models/models.dart';
import '../app_database.dart';

/// Focus sessions data access.
abstract final class FocusRepository {
  static Future<String> insert(FocusSession session) async {
    final db = await AppDatabase.database;
    await db.insert('focus_sessions', session.toMap());
    return session.id;
  }

  static Future<void> deleteById(String id) async {
    final db = await AppDatabase.database;
    await db.delete('focus_sessions', where: 'id = ?', whereArgs: [id]);
  }

  static Future<List<FocusSession>> getByDate(String date) async {
    final db = await AppDatabase.database;
    final maps = await db.query(
      'focus_sessions',
      where: 'session_date = ?',
      whereArgs: [date],
    );
    return maps.map((m) => FocusSession.fromMap(m)).toList();
  }

  static Future<List<FocusSession>> getDateRange(
    String start,
    String end,
  ) async {
    final db = await AppDatabase.database;
    final maps = await db.query(
      'focus_sessions',
      where: 'session_date >= ? AND session_date <= ?',
      whereArgs: [start, end],
    );
    return maps.map((m) => FocusSession.fromMap(m)).toList();
  }

  static Future<List<FocusSession>> getAll() async {
    final db = await AppDatabase.database;
    final maps = await db.query('focus_sessions', orderBy: 'start_time ASC');
    return maps.map((m) => FocusSession.fromMap(m)).toList();
  }

  /// Insert a remote session only if it does not exist locally yet.
  /// Sessions are immutable once recorded, so an existing row wins.
  static Future<void> insertIfNotExists(Map<String, dynamic> row) async {
    final db = await AppDatabase.database;
    final existing = await db.query(
      'focus_sessions',
      where: 'id = ?',
      whereArgs: [row['id']],
    );
    if (existing.isEmpty) {
      await db.insert('focus_sessions', {
        'id': row['id'],
        'todo_id': row['todo_id'],
        'source_type': row['source_type'],
        'source_title': row['source_title'],
        'start_time': row['start_time'],
        'end_time': row['end_time'],
        'duration_seconds': row['duration_seconds'],
        'session_date': row['session_date'],
      });
    }
  }
}
