import 'package:sqflite_common_ffi/sqflite_ffi.dart';

import '../app_database.dart';

/// Local cache of USTC daily news (fallback when the network is down).
abstract final class NewsCacheRepository {
  static Future<Map<String, String>?> getCached(String date) async {
    final db = await AppDatabase.database;
    final rows = await db.query(
      'ustc_news_cache',
      where: 'date = ?',
      whereArgs: [date],
      limit: 1,
    );
    if (rows.isEmpty) return null;
    final row = rows.first;
    return {
      'date': row['date'] as String,
      'title': row['title'] as String,
      'markdown': row['markdown'] as String,
    };
  }

  static Future<Map<String, String>?> getLatest() async {
    final db = await AppDatabase.database;
    final rows = await db.query(
      'ustc_news_cache',
      orderBy: 'date DESC',
      limit: 1,
    );
    if (rows.isEmpty) return null;
    final row = rows.first;
    return {
      'date': row['date'] as String,
      'title': row['title'] as String,
      'markdown': row['markdown'] as String,
    };
  }

  static Future<void> cache({
    required String date,
    required String title,
    required String markdown,
  }) async {
    final db = await AppDatabase.database;
    await db.insert('ustc_news_cache', {
      'date': date,
      'title': title,
      'markdown': markdown,
      'cached_at': DateTime.now().toIso8601String(),
    }, conflictAlgorithm: ConflictAlgorithm.replace);
  }
}
