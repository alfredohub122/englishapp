import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../services/news_service.dart';
import '../theme.dart';
import '../widgets/material_symbols.dart';
import '../shell/browser_navigation.dart';

class NewsArticleScreen extends StatefulWidget {
  const NewsArticleScreen({super.key, required this.article});

  final NewsArticle article;

  @override
  State<NewsArticleScreen> createState() => _NewsArticleScreenState();
}

class _NewsArticleScreenState extends State<NewsArticleScreen> {
  late final TextEditingController _commentController;
  final List<_LocalArticleComment> _comments = <_LocalArticleComment>[];
  bool _loadingComments = true;
  bool _savingComment = false;

  String get _storageKey => 'browseit_news_comments_${base64Url.encode(utf8.encode(widget.article.id))}';

  @override
  void initState() {
    super.initState();
    _commentController = TextEditingController();
    _loadComments();
  }

  @override
  void dispose() {
    _commentController.dispose();
    super.dispose();
  }

  Future<void> _loadComments() async {
    try {
      final preferences = await SharedPreferences.getInstance();
      final raw = preferences.getString(_storageKey);
      if (raw != null && raw.isNotEmpty) {
        final decoded = jsonDecode(raw);
        if (decoded is List) {
          _comments
            ..clear()
            ..addAll(decoded.whereType<Map>().map((entry) => _LocalArticleComment.fromMap(
                  Map<String, dynamic>.from(entry),
                )));
        }
      }
    } catch (_) {
      // A falha de leitura não impede abrir a notícia nem escrever comentários.
    }
    if (mounted) setState(() => _loadingComments = false);
  }

  Future<void> _postComment() async {
    final text = _commentController.text.trim();
    if (text.isEmpty || _savingComment) return;
    setState(() => _savingComment = true);
    final comment = _LocalArticleComment(text: text, createdAt: DateTime.now());
    _comments.insert(0, comment);
    _commentController.clear();
    try {
      final preferences = await SharedPreferences.getInstance();
      await preferences.setString(
        _storageKey,
        jsonEncode(_comments.map((entry) => entry.toMap()).toList(growable: false)),
      );
    } catch (_) {}
    if (mounted) setState(() => _savingComment = false);
  }

  Future<void> _copyLink() async {
    await Clipboard.setData(ClipboardData(text: widget.article.url));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Ligação copiada.')),
    );
  }

  String _dateLabel(DateTime? date) {
    if (date == null) return 'Data não indicada';
    final local = date.toLocal();
    final day = local.day.toString().padLeft(2, '0');
    final month = local.month.toString().padLeft(2, '0');
    return '$day/$month/${local.year} · ${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final article = widget.article;
    final text = mainText(context);
    final muted = mutedText(context);
    final bg = pageBg(context);
    final surface = settingsCardColor(context);
    final dark = Theme.of(context).brightness == Brightness.dark;

    return AnnotatedRegion<SystemUiOverlayStyle>(
      value: (dark ? SystemUiOverlayStyle.light : SystemUiOverlayStyle.dark).copyWith(
        statusBarColor: bg,
        statusBarIconBrightness: dark ? Brightness.light : Brightness.dark,
        systemNavigationBarColor: bg,
        systemNavigationBarIconBrightness: dark ? Brightness.light : Brightness.dark,
        systemNavigationBarDividerColor: bg,
        systemNavigationBarContrastEnforced: false,
      ),
      child: Scaffold(
        backgroundColor: bg,
        appBar: AppBar(
          toolbarHeight: 58,
          automaticallyImplyLeading: false,
          backgroundColor: bg,
          foregroundColor: text,
          surfaceTintColor: Colors.transparent,
          elevation: 0,
          scrolledUnderElevation: 0,
          leading: AppBackButton(
            tooltip: 'Voltar',
            color: text,
            onPressed: () => Navigator.of(context).maybePop(),
          ),
          title: Text(
            'Publicação',
            style: TextStyle(color: text, fontSize: 18, fontWeight: FontWeight.w600),
          ),
          actions: <Widget>[
            IconButton(
              tooltip: 'Copiar ligação',
              onPressed: _copyLink,
              icon: AppMaterialIcon(AppIcons.copy, color: text, size: 21),
            ),
            const SizedBox(width: 5),
          ],
        ),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
          children: <Widget>[
            if (article.imageUrl != null)
              ClipRRect(
                borderRadius: BorderRadius.circular(22),
                child: AspectRatio(
                  aspectRatio: 16 / 9,
                  child: Image.network(
                    article.imageUrl!,
                    fit: BoxFit.cover,
                    errorBuilder: (_, __, ___) => const SizedBox.shrink(),
                  ),
                ),
              ),
            if (article.imageUrl != null) const SizedBox(height: 18),
            Row(
              children: <Widget>[
                CircleAvatar(
                  radius: 18,
                  backgroundColor: surface,
                  child: Text(
                    article.publisher.isEmpty ? 'N' : article.publisher.substring(0, 1).toUpperCase(),
                    style: TextStyle(color: text, fontWeight: FontWeight.w700),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(article.publisher, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: text, fontSize: 13, fontWeight: FontWeight.w700)),
                      Text(_dateLabel(article.publishedAt), style: TextStyle(color: muted, fontSize: 11.5)),
                    ],
                  ),
                ),
                IconButton(
                  tooltip: 'Abrir publicação original',
                  onPressed: () => openOrbitTab(context, article.url, title: article.publisher),
                  icon: AppMaterialIcon(AppIcons.openInNew, color: muted, size: 21),
                ),
              ],
            ),
            const SizedBox(height: 15),
            Text(
              article.title,
              style: TextStyle(color: text, fontSize: 25, height: 1.16, fontWeight: FontWeight.w700, letterSpacing: -.45),
            ),
            const SizedBox(height: 13),
            if (article.summary.isNotEmpty)
              Text(
                article.summary,
                style: TextStyle(color: text.withOpacity(.88), fontSize: 16, height: 1.55),
              ),
            const SizedBox(height: 18),
            OutlinedButton.icon(
              onPressed: () => openOrbitTab(context, article.url, title: article.publisher),
              icon: const AppMaterialIcon(AppIcons.openInNew, size: 19),
              label: const Text('Ler artigo completo no editor'),
              style: OutlinedButton.styleFrom(
                foregroundColor: text,
                side: BorderSide(color: lineColor(context)),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 13),
              ),
            ),
            const SizedBox(height: 25),
            Divider(color: lineColor(context), height: 1),
            const SizedBox(height: 17),
            Row(
              children: <Widget>[
                Expanded(
                  child: Text('Comentários', style: TextStyle(color: text, fontSize: 18, fontWeight: FontWeight.w700)),
                ),
                Text('${_comments.length}', style: TextStyle(color: muted, fontSize: 12, fontWeight: FontWeight.w600)),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              'Os comentários ficam guardados apenas neste dispositivo.',
              style: TextStyle(color: muted, fontSize: 11.5),
            ),
            const SizedBox(height: 12),
            if (_loadingComments)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 18),
                child: Center(child: CircularProgressIndicator.adaptive()),
              )
            else if (_comments.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 24),
                child: Center(
                  child: Text('Ainda não há comentários. Começa a conversa.', style: TextStyle(color: muted, fontSize: 13)),
                ),
              )
            else
              ..._comments.map((comment) => _commentTile(comment, text, muted, surface)),
          ],
        ),
        bottomNavigationBar: SafeArea(
          top: false,
          child: Container(
            padding: const EdgeInsets.fromLTRB(12, 10, 12, 9),
            decoration: BoxDecoration(
              color: bg,
              border: Border(top: BorderSide(color: lineColor(context), width: .7)),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.end,
              children: <Widget>[
                CircleAvatar(
                  radius: 17,
                  backgroundColor: surface,
                  child: AppMaterialIcon(AppIcons.house, size: 18, color: muted),
                ),
                const SizedBox(width: 9),
                Expanded(
                  child: Container(
                    constraints: const BoxConstraints(minHeight: 44, maxHeight: 112),
                    decoration: BoxDecoration(color: surface, borderRadius: BorderRadius.circular(22)),
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
                    child: TextField(
                      controller: _commentController,
                      minLines: 1,
                      maxLines: 4,
                      textCapitalization: TextCapitalization.sentences,
                      textInputAction: TextInputAction.newline,
                      style: TextStyle(color: text, fontSize: 14),
                      decoration: InputDecoration(
                        hintText: 'Escreve um comentário',
                        hintStyle: TextStyle(color: muted, fontSize: 14),
                        border: InputBorder.none,
                        enabledBorder: InputBorder.none,
                        focusedBorder: InputBorder.none,
                        isDense: true,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Material(
                  color: accentColor(context),
                  shape: const CircleBorder(),
                  child: InkWell(
                    customBorder: const CircleBorder(),
                    onTap: _savingComment ? null : _postComment,
                    child: SizedBox(
                      width: 42,
                      height: 42,
                      child: Center(
                        child: _savingComment
                            ? const SizedBox(width: 17, height: 17, child: CircularProgressIndicator.adaptive(strokeWidth: 2))
                            : AppMaterialIcon(AppIcons.arrowUp, color: Colors.white, size: 20),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _commentTile(_LocalArticleComment comment, Color text, Color muted, Color surface) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 11),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          CircleAvatar(
            radius: 16,
            backgroundColor: surface,
            child: AppMaterialIcon(AppIcons.house, size: 17, color: muted),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(_dateLabel(comment.createdAt), style: TextStyle(color: muted, fontSize: 10.5)),
                const SizedBox(height: 4),
                Text(comment.text, style: TextStyle(color: text, fontSize: 14, height: 1.4)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _LocalArticleComment {
  const _LocalArticleComment({required this.text, required this.createdAt});

  final String text;
  final DateTime createdAt;

  Map<String, dynamic> toMap() => <String, dynamic>{
        'text': text,
        'createdAt': createdAt.toIso8601String(),
      };

  factory _LocalArticleComment.fromMap(Map<String, dynamic> map) {
    return _LocalArticleComment(
      text: map['text']?.toString() ?? '',
      createdAt: DateTime.tryParse(map['createdAt']?.toString() ?? '') ?? DateTime.now(),
    );
  }
}
