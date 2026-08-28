import 'package:flutter/material.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';
import 'package:provider/provider.dart';

import 'package:goworkbro/services/api_service.dart';
import 'package:goworkbro/core/l10n/app_locale.dart';

/// Right/news column of the Today screen (desktop layout).
class UstcNewsSection extends StatelessWidget {
  const UstcNewsSection({
    super.key,
    required this.news,
    required this.loading,
    required this.error,
    required this.onRetry,
  });

  final UstcNews? news;
  final bool loading;
  final String? error;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final s = S.of(context.watch<AppLocaleProvider>().locale);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(Icons.menu_book_outlined, color: colorScheme.primary, size: 20),
            const SizedBox(width: 8),
            Text(
              s.ustcNews,
              style: theme.textTheme.titleMedium?.copyWith(
                color: colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        Expanded(child: _body(context)),
      ],
    );
  }

  Widget _body(BuildContext context) {
    if (loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (news == null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.cloud_off_outlined,
              size: 48,
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
            const SizedBox(height: 12),
            Text(error ?? S.of(context.watch<AppLocaleProvider>().locale).loadFailed),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: Text(S.of(context.watch<AppLocaleProvider>().locale).retry),
            ),
          ],
        ),
      );
    }

    return NewsView(news: news!);
  }
}

/// Markdown news viewer showing date, title and body.
class NewsView extends StatelessWidget {
  const NewsView({super.key, required this.news});

  final UstcNews news;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (news.date.isNotEmpty)
          Text(
            news.date,
            style: theme.textTheme.labelLarge?.copyWith(
              color: colorScheme.primary,
              fontWeight: FontWeight.w600,
            ),
          ),
        if (news.title.isNotEmpty) ...[
          const SizedBox(height: 4),
          Text(
            news.title,
            style: theme.textTheme.titleLarge?.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
        ],
        const Divider(height: 24),
        Expanded(
          child: Markdown(
            data: news.markdown,
            selectable: true,
            padding: EdgeInsets.zero,
            styleSheet: MarkdownStyleSheet.fromTheme(theme).copyWith(
              p: theme.textTheme.bodyMedium?.copyWith(
                fontFamily: 'LXGWWenKai',
                height: 1.8,
              ),
              h1: theme.textTheme.headlineMedium?.copyWith(
                fontFamily: 'LXGWWenKai',
                fontWeight: FontWeight.w700,
              ),
              h2: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w700,
              ),
              h3: theme.textTheme.titleSmall?.copyWith(
                fontWeight: FontWeight.w700,
              ),
              a: theme.textTheme.bodyMedium?.copyWith(
                color: colorScheme.primary,
                decoration: TextDecoration.underline,
              ),
            ),
          ),
        ),
      ],
    );
  }
}

/// Full-screen news modal used on mobile/narrow layouts.
class NewsFullScreen extends StatelessWidget {
  const NewsFullScreen({
    super.key,
    required this.news,
    required this.loading,
    required this.error,
    required this.onRetry,
  });

  final UstcNews? news;
  final bool loading;
  final String? error;
  final Future<void> Function() onRetry;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context.watch<AppLocaleProvider>().locale);
    return Scaffold(
      appBar: AppBar(
        title: Text(s.viewNews),
        leading: IconButton(
          icon: const Icon(Icons.close),
          onPressed: () => Navigator.of(context).pop(),
        ),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: _body(context),
        ),
      ),
    );
  }

  Widget _body(BuildContext context) {
    if (loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (news == null) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(
              Icons.cloud_off_outlined,
              size: 48,
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
            const SizedBox(height: 12),
            Text(error ?? S.of(context.watch<AppLocaleProvider>().locale).loadFailed),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: onRetry,
              icon: const Icon(Icons.refresh),
              label: Text(S.of(context.watch<AppLocaleProvider>().locale).retry),
            ),
          ],
        ),
      );
    }
    return NewsView(news: news!);
  }
}
