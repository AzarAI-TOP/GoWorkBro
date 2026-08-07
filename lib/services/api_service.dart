import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:supabase_flutter/supabase_flutter.dart';
import '../models/models.dart';
import 'database_service.dart';
import 'supabase_config.dart';

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

  /// Fetch USTC news with a 3-tier fallback chain:
  /// 1. Supabase (cloud — works on mobile without PC)
  /// 2. Go backend (reads from Obsidian vault on PC)
  /// 3. Local Obsidian vault file (desktop only)
  static Future<UstcNews?> fetchUstcNews({String? date}) async {
    // Tier 1: Supabase cloud
    final cloud = await _fetchUstcNewsFromSupabase(date);
    if (cloud != null) return cloud;

    // Tier 2: Go backend
    final backend = await _fetchUstcNewsFromBackend(date);
    if (backend != null) return backend;

    // Tier 3: Local vault file (desktop only)
    return _fetchUstcNewsFromLocalVault(date);
  }

  /// Fetch from Supabase ustc_news table
  static Future<UstcNews?> _fetchUstcNewsFromSupabase(String? date) async {
    try {
      if (!isSupabaseConfigured) return null;
      final client = Supabase.instance.client;
      final dateStr = date ?? DateTime.now().toIso8601String().substring(0, 10);

      final data = await client
          .from('ustc_news')
          .select('date, title, content')
          .eq('date', dateStr)
          .maybeSingle()
          .timeout(const Duration(seconds: 5));

      if (data != null) {
        return UstcNews(
          date: data['date'] as String,
          title: data['title'] as String,
          markdown: data['content'] as String,
        );
      }
    } catch (_) {}
    return null;
  }

  /// Fetch from Go backend (reads Obsidian vault)
  static Future<UstcNews?> _fetchUstcNewsFromBackend(String? date) async {
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

  /// Fetch directly from local Obsidian vault file
  static Future<UstcNews?> _fetchUstcNewsFromLocalVault(String? date) async {
    try {
      final dateStr = date ??
          DateTime.now().toIso8601String().substring(0, 10);
      final vaultPath = await DatabaseService.getSetting('obsidian_vault_path') ??
          r'C:\Users\ASUS\Documents\Notes';
      final file = File('$vaultPath/USTC 每日要闻/$dateStr.md');
      if (await file.exists()) {
        final content = await file.readAsString();
        String? title;
        final titleMatch = RegExp(r'^#\s+(.+)$', multiLine: true).firstMatch(content);
        if (titleMatch != null) {
          title = titleMatch.group(1)!.trim();
        }
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

  /// Fetch latest available USTC news (most recent date in Supabase)
  static Future<UstcNews?> fetchLatestUstcNews() async {
    // Try Supabase first
    try {
      if (isSupabaseConfigured) {
        final client = Supabase.instance.client;
        final data = await client
            .from('ustc_news')
            .select('date, title, content')
            .order('date', ascending: false)
            .limit(1)
            .maybeSingle()
            .timeout(const Duration(seconds: 5));
        if (data != null) {
          return UstcNews(
            date: data['date'] as String,
            title: data['title'] as String,
            markdown: data['content'] as String,
          );
        }
      }
    } catch (_) {}

    // Fallback to backend
    return fetchUstcNews();
  }

  /// List available USTC news dates from Supabase (cloud) or local vault
  static Future<List<String>> listUstcNewsDates() async {
    // Try Supabase first
    try {
      if (isSupabaseConfigured) {
        final client = Supabase.instance.client;
        final data = await client
            .from('ustc_news')
            .select('date')
            .order('date', ascending: false)
            .timeout(const Duration(seconds: 5));
        if (data.isNotEmpty) {
          return data.map((row) => row['date'] as String).toList();
        }
      }
    } catch (_) {}

    // Fallback to local vault
    try {
      final vaultPath = await DatabaseService.getSetting('obsidian_vault_path') ??
          r'C:\Users\ASUS\Documents\Notes';
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
