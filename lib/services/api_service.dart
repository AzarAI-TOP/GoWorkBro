import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import '../models/models.dart';
import 'database_service.dart';

/// API service for backend sync (Go server) and USTC news fetching
class ApiService {
  static String? _baseUrl;
  static bool _isConnected = false;

  static Future<String> get baseUrl async {
    if (_baseUrl != null) return _baseUrl!;
    _baseUrl = await DatabaseService.getSetting('api_base_url') ??
        'http://localhost:8765';
    return _baseUrl!;
  }

  static Future<void> setBaseUrl(String url) async {
    _baseUrl = url;
    await DatabaseService.setSetting('api_base_url', url);
  }

  static bool get isConnected => _isConnected;

  /// Test connection to backend
  static Future<bool> testConnection() async {
    try {
      final url = await baseUrl;
      final res = await http.get(Uri.parse('$url/health')).timeout(
        const Duration(seconds: 3),
      );
      _isConnected = res.statusCode == 200;
      return _isConnected;
    } catch (_) {
      _isConnected = false;
      return false;
    }
  }

  // ============ TODO Sync ============

  static Future<void> syncTodos(List<Todo> todos) async {
    try {
      final url = await baseUrl;
      await http.post(
        Uri.parse('$url/api/todos/sync'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'todos': todos.map((t) => t.toMap()).toList(),
        }),
      ).timeout(const Duration(seconds: 5));
    } catch (_) {}
  }

  // ============ USTC News ============

  /// Fetch today's USTC news markdown from backend (which reads from Obsidian vault)
  static Future<UstcNews?> fetchUstcNews({String? date}) async {
    try {
      final url = await baseUrl;
      final endpoint = date != null
          ? '$url/api/ustc-news?date=$date'
          : '$url/api/ustc-news/today';
      final res = await http.get(Uri.parse(endpoint)).timeout(
        const Duration(seconds: 5),
      );
      if (res.statusCode == 200) {
        final data = jsonDecode(res.body);
        return UstcNews(
          date: data['date'] as String,
          title: data['title'] as String,
          markdown: data['markdown'] as String,
        );
      }
    } catch (_) {}
    return null;
  }

  /// Fetch today's USTC news directly from the Obsidian vault file
  /// (used as fallback when backend is not running)
  static Future<UstcNews?> fetchUstcNewsLocal(String vaultPath) async {
    try {
      final now = DateTime.now();
      final dateStr = '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
      final file = File('$vaultPath/USTC 每日要闻/$dateStr.md');
      if (await file.exists()) {
        final content = await file.readAsString();
        // Extract title from first H1
        String? title;
        final titleMatch = RegExp(r'^#\s+(.+)$', multiLine: true).firstMatch(content);
        if (titleMatch != null) {
          title = titleMatch.group(1)!.trim();
        }
        // Strip frontmatter
        String markdown = content;
        if (markdown.startsWith('---')) {
          final end = markdown.indexOf('---', 3);
          if (end > 0) {
            markdown = markdown.substring(end + 3).trim();
          }
        }
        return UstcNews(
          date: dateStr,
          title: title ?? 'USTC 每日要闻 — $dateStr',
          markdown: markdown,
        );
      }
    } catch (_) {}
    return null;
  }

  /// List available USTC news dates from the vault
  static Future<List<String>> listUstcNewsDates(String vaultPath) async {
    try {
      final dir = Directory('$vaultPath/USTC 每日要闻');
      if (!await dir.exists()) return [];
      final files = await dir.list().toList();
      final dates = <String>[];
      for (final f in files) {
        if (f is File && f.path.endsWith('.md')) {
          final name = f.uri.pathSegments.last.replaceAll('.md', '');
          if (RegExp(r'^\d{4}-\d{2}-\d{2}$').hasMatch(name)) {
            dates.add(name);
          }
        }
      }
      dates.sort((a, b) => b.compareTo(a));
      return dates;
    } catch (_) {
      return [];
    }
  }
}

class UstcNews {
  final String date;
  final String title;
  final String markdown;

  UstcNews({
    required this.date,
    required this.title,
    required this.markdown,
  });
}
