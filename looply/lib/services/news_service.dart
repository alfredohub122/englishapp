import 'dart:io';

import 'package:http/http.dart' as http;

class NewsArticle {
  const NewsArticle({
    required this.id,
    required this.title,
    required this.publisher,
    required this.summary,
    required this.url,
    this.imageUrl,
    this.publishedAt,
  });

  final String id;
  final String title;
  final String publisher;
  final String summary;
  final String url;
  final String? imageUrl;
  final DateTime? publishedAt;
}

/// Carrega manchetes da edição angolana do Google News em português.
/// O feed RSS é utilizado apenas como fonte de títulos, datas, excertos e links;
/// o artigo integral continua a pertencer ao respetivo editor.
class NewsService {
  NewsService._();

  static const Map<String, String> _headers = <String, String>{
    'User-Agent': 'BrowseIt/1.0 (Android; news reader)',
    'Accept': 'application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.5',
  };

  static Future<List<NewsArticle>> fetchLatest() async {
    final edition = <String, String>{
      'hl': 'pt-PT',
      'gl': 'AO',
      'ceid': 'AO:pt-PT',
    };
    final primary = await _fetch(Uri.https('news.google.com', '/rss', edition));
    if (primary.isNotEmpty) return primary;

    final fallback = <String, String>{
      ...edition,
      'q': 'Angola OR África',
    };
    return _fetch(Uri.https('news.google.com', '/rss/search', fallback));
  }

  static Future<List<NewsArticle>> _fetch(Uri uri) async {
    try {
      final response = await http.get(uri, headers: _headers).timeout(
            const Duration(seconds: 16),
          );
      if (response.statusCode != 200) return const <NewsArticle>[];
      return parseRss(response.body);
    } catch (_) {
      return const <NewsArticle>[];
    }
  }

  static List<NewsArticle> parseRss(String xml) {
    final items = RegExp(
      r'<item\b[^>]*>([\s\S]*?)</item>',
      caseSensitive: false,
    ).allMatches(xml);
    final result = <NewsArticle>[];
    final seen = <String>{};

    for (final item in items) {
      final block = item.group(1) ?? '';
      final rawTitle = _tag(block, 'title');
      final title = _plainText(rawTitle);
      final link = _plainText(_tag(block, 'link'));
      if (title.isEmpty || link.isEmpty) continue;
      final uri = Uri.tryParse(link);
      if (uri == null || (uri.scheme != 'https' && uri.scheme != 'http')) continue;
      if (!seen.add(uri.toString())) continue;

      final publisher = _plainText(_tag(block, 'source'));
      final summaryRaw = _tag(block, 'description');
      final summary = _plainText(summaryRaw);
      final dateText = _plainText(_tag(block, 'pubDate'));
      final image = _imageFrom(block, summaryRaw);
      final date = _parseDate(dateText);
      result.add(
        NewsArticle(
          id: uri.toString(),
          title: title,
          publisher: publisher.isNotEmpty ? publisher : _publisherFromTitle(title),
          summary: summary,
          url: uri.toString(),
          imageUrl: image,
          publishedAt: date,
        ),
      );
      if (result.length >= 18) break;
    }

    result.sort((a, b) {
      final aDate = a.publishedAt;
      final bDate = b.publishedAt;
      if (aDate == null && bDate == null) return 0;
      if (aDate == null) return 1;
      if (bDate == null) return -1;
      return bDate.compareTo(aDate);
    });
    return result;
  }

  static String _tag(String source, String name) {
    final escaped = RegExp.escape(name);
    final match = RegExp(
      '<$escaped(?:\\s[^>]*)?>([\\s\\S]*?)</$escaped>',
      caseSensitive: false,
    ).firstMatch(source);
    return match?.group(1)?.trim() ?? '';
  }

  static String _plainText(String value) {
    var text = value
        .replaceAll(RegExp(r'<!\[CDATA\['), '')
        .replaceAll(RegExp(r'\]\]>'), ' ');
    // Alguns RSS entregam HTML escapado dentro do próprio XML.
    text = _decodeEntities(_decodeEntities(text));
    text = text
        .replaceAll(RegExp(r'<script\b[^>]*>[\s\S]*?</script>', caseSensitive: false), ' ')
        .replaceAll(RegExp(r'<style\b[^>]*>[\s\S]*?</style>', caseSensitive: false), ' ')
        .replaceAll(RegExp(r'<[^>]+>'), ' ');
    return _decodeEntities(text).replaceAll(RegExp(r'\s+'), ' ').trim();
  }

  static String _decodeEntities(String value) {
    var result = value
        .replaceAll('&amp;', '&')
        .replaceAll('&lt;', '<')
        .replaceAll('&gt;', '>')
        .replaceAll('&quot;', '"')
        .replaceAll('&#39;', "'")
        .replaceAll('&apos;', "'")
        .replaceAll('&nbsp;', ' ');
    result = result.replaceAllMapped(
      RegExp(r'&#(x[0-9a-fA-F]+|[0-9]+);'),
      (match) {
        final raw = match.group(1) ?? '';
        final codePoint = raw.startsWith('x')
            ? int.tryParse(raw.substring(1), radix: 16)
            : int.tryParse(raw);
        return codePoint == null ? match.group(0)! : String.fromCharCode(codePoint);
      },
    );
    return result;
  }

  static String? _imageFrom(String block, String description) {
    final decodedDescription = _decodeEntities(description);
    final candidates = <String>[
      _attribute(block, 'media:thumbnail', 'url'),
      _attribute(block, 'media:content', 'url'),
      _attribute(block, 'enclosure', 'url'),
      _attribute(decodedDescription, 'img', 'src'),
    ];
    for (final candidate in candidates) {
      final uri = Uri.tryParse(candidate);
      if (uri != null && uri.scheme == 'https' && uri.host.isNotEmpty) {
        return uri.toString();
      }
    }
    return null;
  }

  static String _attribute(String source, String tag, String attribute) {
    final tagPattern = RegExp.escape(tag);
    final attrPattern = RegExp.escape(attribute);
    final prefix = '<$tagPattern\\b[^>]*\\b$attrPattern=';
    final doubleQuoted = RegExp('$prefix\"([^\"]+)\"', caseSensitive: false).firstMatch(source);
    if (doubleQuoted != null) return doubleQuoted.group(1) ?? '';
    final singleQuoted = RegExp("$prefix'([^']+)'", caseSensitive: false).firstMatch(source);
    return singleQuoted?.group(1) ?? '';
  }

  static DateTime? _parseDate(String value) {
    if (value.isEmpty) return null;
    final iso = DateTime.tryParse(value);
    if (iso != null) return iso.toLocal();
    try {
      return HttpDate.parse(value).toLocal();
    } catch (_) {
      return null;
    }
  }

  static String _publisherFromTitle(String title) {
    final separator = title.lastIndexOf(' - ');
    if (separator > 0 && separator < title.length - 3) {
      return title.substring(separator + 3).trim();
    }
    return 'Notícias';
  }
}
