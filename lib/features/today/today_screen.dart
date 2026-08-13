import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'package:goworkbro/providers/app_provider.dart';
import 'package:goworkbro/services/api_service.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';
import 'package:goworkbro/features/today/widgets/focus_data_section.dart';
import 'package:goworkbro/features/today/widgets/ustc_news_section.dart';

/// "Today" screen of GoWorkBro.
///
/// Shows today's focus statistics (total time, pie chart by source, 7-day bar
/// chart and a sessions list) alongside the USTC news digest.
///
/// Layout adapts to viewport width:
///   * width >= 900 -> two-column split (left: data, right: news)
///   * width  < 900 -> single column with a bottom button that opens the news
///     in a full-screen modal.
class TodayScreen extends StatefulWidget {
  const TodayScreen({super.key});

  @override
  State<TodayScreen> createState() => _TodayScreenState();
}

class _TodayScreenState extends State<TodayScreen> {
  UstcNews? _news;
  bool _loadingNews = true;
  String? _newsError;
  List<int> _weeklySeconds = [];
  int _lastSessionCount = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _fetchNews();
      _fetchWeeklyData();
    });
  }

  // #2: Only refresh weekly data on session count change, NOT news
  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final provider = context.read<AppProvider>();
    if (provider.todaySessionCount != _lastSessionCount) {
      _lastSessionCount = provider.todaySessionCount;
      _fetchWeeklyData();
    }
  }

  Future<void> _fetchWeeklyData() async {
    final provider = context.read<AppProvider>();
    final data = await provider.getWeeklyFocusSeconds();
    if (!mounted) return;
    setState(() => _weeklySeconds = data);
  }

  Future<void> _fetchNews() async {
    setState(() {
      _loadingNews = true;
      _newsError = null;
    });

    UstcNews? news;
    try {
      // fetchUstcNews now has a 3-tier fallback:
      // 1. Supabase (cloud) → 2. Go backend → 3. Local Obsidian vault
      news = await ApiService.fetchUstcNews();
    } catch (_) {
      news = null;
    }

    if (news == null) {
      // Try latest available news (not necessarily today's)
      try {
        news = await ApiService.fetchLatestUstcNews();
      } catch (_) {
        news = null;
      }
    }

    if (!mounted) return;

    setState(() {
      _news = news;
      _loadingNews = false;
      _newsError = news == null ? S.of(context.read<AppLocaleProvider>().locale).noNews : null;
    });
  }

  @override
  Widget build(BuildContext context) {
    final width = MediaQuery.of(context).size.width;
    final isDesktop = width >= 900;
    final s = S.of(context.read<AppLocaleProvider>().locale);

    final dataSection = FocusDataSection(weeklySeconds: _weeklySeconds);
    final newsSection = UstcNewsSection(
      news: _news,
      loading: _loadingNews,
      error: _newsError,
      onRetry: _fetchNews,
    );

    if (isDesktop) {
      return Scaffold(
        appBar: AppBar(
          title: Text(s.today),
          centerTitle: false,
        ),
        body: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              flex: 5,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(24, 16, 12, 24),
                child: dataSection,
              ),
            ),
            const VerticalDivider(width: 1),
            Expanded(
              flex: 4,
              child: Padding(
                padding: const EdgeInsets.fromLTRB(12, 16, 24, 24),
                child: newsSection,
              ),
            ),
          ],
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: Text(s.today),
        centerTitle: false,
      ),
      body: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(child: dataSection),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: _openNewsModal,
              icon: const Icon(Icons.article_outlined),
              label: Text(s.viewNews),
            ),
            const SizedBox(height: 8),
          ],
        ),
      ),
    );
  }

  void _openNewsModal() {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => NewsFullScreen(
          news: _news,
          loading: _loadingNews,
          error: _newsError,
          onRetry: _fetchNews,
        ),
        fullscreenDialog: true,
      ),
    );
  }
}
