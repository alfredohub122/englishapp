import 'package:flutter/cupertino.dart' show CupertinoPageRoute;
import 'package:material_ui/material_ui.dart';

import '../services/news_service.dart';
import '../theme.dart';
import '../widgets/material_symbols.dart';
import 'news_article_screen.dart';

class NewsFeedSection extends StatefulWidget {
  const NewsFeedSection({super.key});

  @override
  State<NewsFeedSection> createState() => _NewsFeedSectionState();
}

class _NewsFeedSectionState extends State<NewsFeedSection> {
  List<NewsArticle> _articles = const <NewsArticle>[];
  bool _loading = true;
  bool _failed = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    if (mounted) {
      setState(() {
        _loading = true;
        _failed = false;
      });
    }
    final articles = await NewsService.fetchLatest();
    if (!mounted) return;
    setState(() {
      _articles = articles;
      _loading = false;
      _failed = articles.isEmpty;
    });
  }

  String _relativeDate(DateTime? date) {
    if (date == null) return 'Recentemente';
    final difference = DateTime.now().difference(date.toLocal());
    if (difference.isNegative || difference.inMinutes < 1) return 'Agora';
    if (difference.inMinutes < 60) return 'há ${difference.inMinutes} min';
    if (difference.inHours < 24) return 'há ${difference.inHours} h';
    if (difference.inDays < 7) return 'há ${difference.inDays} d';
    final value = date.toLocal();
    return '${value.day.toString().padLeft(2, '0')}/${value.month.toString().padLeft(2, '0')}';
  }

  void _open(NewsArticle article) {
    Navigator.of(context).push<void>(
      CupertinoPageRoute<void>(
        builder: (_) => NewsArticleScreen(article: article),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final text = mainText(context);
    final muted = mutedText(context);
    final surface = settingsCardColor(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Padding(
          padding: const EdgeInsets.fromLTRB(5, 0, 2, 10),
          child: Row(
            children: <Widget>[
              Expanded(
                child: Text(
                  'Notícias',
                  style: TextStyle(color: text, fontSize: 16, fontWeight: FontWeight.w700),
                ),
              ),
              Text('Angola e mundo', style: TextStyle(color: muted, fontSize: 11.5)),
              const SizedBox(width: 5),
              Tooltip(
                message: 'Atualizar notícias',
                child: Material(
                  color: Colors.transparent,
                  child: InkWell(
                    customBorder: const CircleBorder(),
                    onTap: _loading ? null : _load,
                    child: SizedBox(
                      width: 34,
                      height: 34,
                      child: Center(
                        child: _loading
                            ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator.adaptive(strokeWidth: 2))
                            : AppMaterialIcon(AppIcons.arrowClockwise, color: muted, size: 19),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
        if (_loading && _articles.isEmpty)
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(vertical: 27),
            decoration: BoxDecoration(color: surface, borderRadius: BorderRadius.circular(23)),
            child: const Center(child: CircularProgressIndicator.adaptive()),
          )
        else if (_failed)
          Container(
            width: double.infinity,
            padding: const EdgeInsets.fromLTRB(16, 18, 16, 16),
            decoration: BoxDecoration(color: surface, borderRadius: BorderRadius.circular(23)),
            child: Column(
              children: <Widget>[
                AppMaterialIcon(AppIcons.wifiSlash, size: 24, color: muted),
                const SizedBox(height: 8),
                Text('Não foi possível carregar as notícias.', style: TextStyle(color: text, fontSize: 13, fontWeight: FontWeight.w600)),
                const SizedBox(height: 4),
                Text('Verifica a ligação e tenta novamente.', style: TextStyle(color: muted, fontSize: 11.5)),
                const SizedBox(height: 9),
                TextButton(onPressed: _load, child: const Text('Tentar novamente')),
              ],
            ),
          )
        else
          Container(
            padding: const EdgeInsets.fromLTRB(12, 4, 12, 4),
            decoration: BoxDecoration(color: surface, borderRadius: BorderRadius.circular(25)),
            child: Column(
              children: <Widget>[
                for (var index = 0; index < _articles.length && index < 8; index++) ...<Widget>[
                  _NewsTile(
                    article: _articles[index],
                    relativeDate: _relativeDate(_articles[index].publishedAt),
                    onTap: () => _open(_articles[index]),
                  ),
                  if (index < _articles.length - 1 && index < 7)
                    Divider(height: 1, indent: 2, endIndent: 2, color: lineColor(context)),
                ],
              ],
            ),
          ),
      ],
    );
  }
}

class _NewsTile extends StatelessWidget {
  const _NewsTile({
    required this.article,
    required this.relativeDate,
    required this.onTap,
  });

  final NewsArticle article;
  final String relativeDate;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final text = mainText(context);
    final muted = mutedText(context);
    return Material(
      color: Colors.transparent,
      child: InkWell(
        borderRadius: BorderRadius.circular(17),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 11, horizontal: 2),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      article.publisher,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: muted, fontSize: 10.5, fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      article.title,
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: text, fontSize: 13.5, height: 1.3, fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 6),
                    Text(relativeDate, style: TextStyle(color: muted, fontSize: 10.5)),
                  ],
                ),
              ),
              if (article.imageUrl != null) ...<Widget>[
                const SizedBox(width: 12),
                ClipRRect(
                  borderRadius: BorderRadius.circular(13),
                  child: SizedBox(
                    width: 88,
                    height: 82,
                    child: Image.network(
                      article.imageUrl!,
                      fit: BoxFit.cover,
                      errorBuilder: (_, __, ___) => ColoredBox(
                        color: pillColor(context),
                        child: Center(child: AppMaterialIcon(AppIcons.globe, color: muted, size: 22)),
                      ),
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}
