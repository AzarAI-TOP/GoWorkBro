import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:supabase_flutter/supabase_flutter.dart';
import 'database_service.dart';
import 'supabase_config.dart';

/// API service for USTC news fetching and cloud data access.
/// Go backend has been removed — everything goes through Supabase.
class ApiService {
  // ============ USTC News ============

  /// Fetch USTC news with a 2-tier fallback chain:
  /// 1. Supabase (cloud — works on all platforms without PC)
  /// 2. Local Obsidian vault file (desktop only, fallback)
  static Future<UstcNews?> fetchUstcNews({String? date}) async {
    final dateStr = date ?? DateTime.now().toIso8601String().substring(0, 10);

    // Published daily editions are immutable. Prefer the persistent date-keyed
    // cache so reopening Today does not spend another network request.
    final cached = await DatabaseService.getCachedUstcNews(dateStr);
    if (cached != null) return _newsFromCache(cached);

    final cloud = await _fetchUstcNewsFromSupabase(dateStr);
    if (cloud != null) {
      await _cacheNews(cloud);
      return cloud;
    }

    final local = await _fetchUstcNewsFromLocalVault(dateStr);
    if (local != null) await _cacheNews(local);
    return local;
  }

  /// Fetch from Supabase ustc_news table (anon RLS allows SELECT)
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
          .timeout(const Duration(seconds: 8));

      if (data != null) {
        return UstcNews(
          date: data['date'] as String,
          title: data['title'] as String,
          markdown: data['content'] as String,
        );
      }
    } catch (e) {
      debugPrint('Supabase USTC news fetch failed: $e');
    }
    return null;
  }

  /// Fetch directly from local Obsidian vault file (desktop fallback).
  /// Vault path is read from DB settings; falls back to platform-specific default.
  static Future<UstcNews?> _fetchUstcNewsFromLocalVault(String? date) async {
    try {
      final dateStr = date ?? DateTime.now().toIso8601String().substring(0, 10);
      final vaultPath =
          await DatabaseService.getSetting('obsidian_vault_path') ??
          _defaultVaultPath();
      final file = File('$vaultPath/USTC 每日要闻/$dateStr.md');
      if (await file.exists()) {
        final content = await file.readAsString();
        String? title;
        final titleMatch = RegExp(
          r'^#\s+(.+)$',
          multiLine: true,
        ).firstMatch(content);
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
    } catch (e) {
      debugPrint('Local vault read failed: $e');
    }
    return null;
  }

  /// Fetch latest available USTC news (most recent cached or cloud edition)
  static Future<UstcNews?> fetchLatestUstcNews() async {
    final cached = await DatabaseService.getLatestCachedUstcNews();
    try {
      if (isSupabaseConfigured) {
        final client = Supabase.instance.client;
        final data = await client
            .from('ustc_news')
            .select('date, title, content')
            .order('date', ascending: false)
            .limit(1)
            .maybeSingle()
            .timeout(const Duration(seconds: 8));
        if (data != null) {
          final news = UstcNews(
            date: data['date'] as String,
            title: data['title'] as String,
            markdown: data['content'] as String,
          );
          await _cacheNews(news);
          return news;
        }
      }
    } catch (e) {
      debugPrint('Supabase latest news fetch failed: $e');
    }

    if (cached != null) return _newsFromCache(cached);
    final local = await _fetchUstcNewsFromLocalVault(null);
    if (local != null) await _cacheNews(local);
    return local;
  }

  static UstcNews _newsFromCache(Map<String, String> cached) => UstcNews(
    date: cached['date']!,
    title: cached['title']!,
    markdown: cached['markdown']!,
  );

  static Future<void> _cacheNews(UstcNews news) =>
      DatabaseService.cacheUstcNews(
        date: news.date,
        title: news.title,
        markdown: news.markdown,
      );

  /// List available USTC news dates from Supabase or local vault
  static Future<List<String>> listUstcNewsDates() async {
    try {
      if (isSupabaseConfigured) {
        final client = Supabase.instance.client;
        final data = await client
            .from('ustc_news')
            .select('date')
            .order('date', ascending: false)
            .timeout(const Duration(seconds: 8));
        if (data.isNotEmpty) {
          return data.map((row) => row['date'] as String).toList();
        }
      }
    } catch (e) {
      debugPrint('Supabase news dates fetch failed: $e');
    }

    try {
      final vaultPath =
          await DatabaseService.getSetting('obsidian_vault_path') ??
          _defaultVaultPath();
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
    } catch (e) {
      debugPrint('Local vault dates listing failed: $e');
      return [];
    }
  }
}

class UstcNews {
  final String date;
  final String title;
  final String markdown;

  UstcNews({required this.date, required this.title, required this.markdown});
}

/// Platform-aware default vault path. Only used as a last-resort fallback
/// when no setting is configured. On mobile this returns an empty string
/// (no local vault), so the fallback gracefully does nothing.
String _defaultVaultPath() {
  // Desktop: ~/Documents/Notes (works for any user)
  final home =
      Platform.environment['HOME'] ?? Platform.environment['USERPROFILE'] ?? '';
  if (home.isEmpty) return '';
  if (Platform.isWindows) {
    return '$home\\Documents\\Notes';
  }
  return '$home/Documents/Notes';
}
