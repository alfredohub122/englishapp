import 'dart:convert';

import 'package:flutter/services.dart';
import 'package:material_ui/material_ui.dart';
import 'package:http/http.dart' as http;

import '../localization.dart';
import '../theme.dart';
import '../widgets/material_symbols.dart';

class ExtensionsScreen extends StatefulWidget {
  const ExtensionsScreen({super.key});

  @override
  State<ExtensionsScreen> createState() => _ExtensionsScreenState();
}

class _ExtensionsScreenState extends State<ExtensionsScreen> {
  static const MethodChannel _native = MethodChannel('looply/native_extensions');
  final TextEditingController _searchController = TextEditingController();
  final List<_UserScriptListing> _results = <_UserScriptListing>[];
  List<Map<String, dynamic>> _installed = <Map<String, dynamic>>[];
  bool _searching = false;
  bool _loadingInstalled = true;
  String? _searchError;
  String? _workingId;

  @override
  void initState() {
    super.initState();
    _loadInstalled();
    _searchController.text = 'dark mode';
    _search(_searchController.text);
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadInstalled() async {
    try {
      final entries = await _native.invokeListMethod<dynamic>('list') ?? const <dynamic>[];
      if (!mounted) return;
      setState(() {
        _installed = entries
            .whereType<Map>()
            .map((entry) => Map<String, dynamic>.from(entry))
            .toList(growable: false);
        _loadingInstalled = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _loadingInstalled = false);
    }
  }

  Future<void> _search(String query) async {
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      _searching = true;
      _searchError = null;
      _results.clear();
    });

    try {
      final uri = Uri.https('greasyfork.org', '/scripts.json', <String, String>{
        if (query.trim().isNotEmpty) 'q': query.trim(),
        'page': '1',
      });
      final response = await http.get(uri, headers: const <String, String>{
        'Accept': 'application/json',
      }).timeout(const Duration(seconds: 18));
      if (response.statusCode != 200) {
        throw Exception('A loja respondeu com o código ${response.statusCode}.');
      }
      final decoded = jsonDecode(response.body);
      if (decoded is! List) throw const FormatException('Formato de catálogo inválido.');
      final listings = decoded.whereType<Map>().map((item) {
        final map = Map<String, dynamic>.from(item);
        final id = (map['id'] ?? '').toString();
        final rawCodeUrl = (map['code_url'] ?? '').toString();
        final codeUrl = rawCodeUrl.startsWith('https://')
            ? rawCodeUrl
            : 'https://greasyfork.org/scripts/$id/code/script.js';
        return _UserScriptListing(
          id: id,
          name: (map['name'] ?? 'Script sem nome').toString(),
          description: (map['description'] ?? '').toString(),
          codeUrl: codeUrl,
          totalInstalls: int.tryParse((map['total_installs'] ?? '0').toString()) ?? 0,
        );
      }).where((item) => item.id.isNotEmpty && item.codeUrl.startsWith('https://')).toList();

      if (!mounted) return;
      setState(() {
        _results
          ..clear()
          ..addAll(listings);
      });
    } catch (_) {
      if (!mounted) return;
      setState(() => _searchError = 'Não foi possível carregar a loja. Verifica a ligação e tenta novamente.');
    } finally {
      if (mounted) setState(() => _searching = false);
    }
  }

  bool _isInstalled(String id) => _installed.any((item) => item['id']?.toString() == id);

  Future<void> _installListing(_UserScriptListing listing) async {
    await _installFromUrl(
      id: 'greasyfork-${listing.id}',
      name: listing.name,
      description: listing.description,
      url: listing.codeUrl,
    );
  }

  Future<void> _installFromUrl({
    required String id,
    required String name,
    required String description,
    required String url,
  }) async {
    final uri = Uri.tryParse(url.trim());
    if (uri == null || uri.scheme != 'https' || uri.host.isEmpty) {
      _showMessage('Introduz um URL HTTPS válido.');
      return;
    }
    final confirmed = await _confirmInstall(
      name: name.trim().isEmpty ? 'UserScript personalizado' : name,
      host: uri.host,
    );
    if (!confirmed || !mounted) return;
    setState(() => _workingId = id);
    try {
      final response = await http.get(uri).timeout(const Duration(seconds: 22));
      if (response.statusCode != 200) {
        throw Exception('Não foi possível transferir o script (${response.statusCode}).');
      }
      final finalUri = response.request?.url ?? uri;
      if (finalUri.scheme != 'https') {
        throw Exception('O servidor redirecionou para uma ligação sem HTTPS.');
      }
      final source = response.body;
      if (utf8.encode(source).length > 2 * 1024 * 1024) {
        throw Exception('O script excede o limite de 2 MB.');
      }
      if (!source.contains('==UserScript==') || !source.contains('==/UserScript==')) {
        throw Exception('O endereço não contém um UserScript reconhecido.');
      }
      await _native.invokeMapMethod<String, dynamic>('install', <String, dynamic>{
        'id': id,
        'name': name,
        'description': description,
        'sourceUrl': finalUri.toString(),
        'sourceCode': source,
      });
      await _loadInstalled();
      _showMessage('Script instalado. Abre novamente a página para o ativar.');
    } on PlatformException catch (error) {
      _showMessage(error.message ?? 'O BrowseIt não conseguiu instalar este script.');
    } catch (error) {
      _showMessage(error.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _workingId = null);
    }
  }

  Future<void> _installBuiltin(_BuiltinExtension extension) async {
    if (_isInstalled(extension.id) || _workingId == extension.id) return;
    final confirmed = await _confirmInstall(
      name: extension.name,
      host: 'biblioteca incluída no BrowseIt',
    );
    if (!confirmed || !mounted) return;
    setState(() => _workingId = extension.id);
    try {
      await _native.invokeMapMethod<String, dynamic>('install', <String, dynamic>{
        'id': extension.id,
        'name': extension.name,
        'description': extension.description,
        'sourceUrl': 'https://browseit.local/extensions/${extension.id}.user.js',
        'sourceCode': extension.source,
      });
      await _loadInstalled();
      _showMessage('Extensão instalada. Abre novamente a página compatível para a ativar.');
    } on PlatformException catch (error) {
      _showMessage(error.message ?? 'Não foi possível instalar esta extensão.');
    } catch (_) {
      _showMessage('Não foi possível instalar esta extensão.');
    } finally {
      if (mounted) setState(() => _workingId = null);
    }
  }

  Widget _builtinCard(_BuiltinExtension extension) {
    final installed = _isInstalled(extension.id);
    final working = _workingId == extension.id;
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(color: settingsCardColor(context), borderRadius: BorderRadius.circular(20)),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(13, 12, 12, 12),
        child: Row(
          children: <Widget>[
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(color: pillColor(context), shape: BoxShape.circle),
              child: Center(child: AppMaterialIcon(extension.icon, color: mainText(context), size: 21)),
            ),
            const SizedBox(width: 11),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(extension.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: mainText(context), fontSize: 13.5, fontWeight: FontWeight.w600)),
                  const SizedBox(height: 3),
                  Text(extension.description, maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(color: mutedText(context), fontSize: 11.5, height: 1.25)),
                  const SizedBox(height: 4),
                  Text('Incluída no BrowseIt · ${extension.siteLabel}', style: TextStyle(color: mutedText(context), fontSize: 10)),
                ],
              ),
            ),
            const SizedBox(width: 6),
            TextButton.icon(
              onPressed: installed || working ? null : () => _installBuiltin(extension),
              icon: working
                  ? const SizedBox(width: 15, height: 15, child: CircularProgressIndicator.adaptive(strokeWidth: 2))
                  : AppMaterialIcon(installed ? AppIcons.check : AppIcons.downloadSimple, size: 17),
              label: Text(installed ? 'Instalada' : working ? 'A instalar' : 'Baixar'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _promptImportByUrl() async {
    final controller = TextEditingController();
    final url = await showDialog<String>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Instalar por URL'),
        content: TextField(
          controller: controller,
          keyboardType: TextInputType.url,
          textInputAction: TextInputAction.done,
          decoration: const InputDecoration(
            hintText: 'https://site.com/script.user.js',
            labelText: 'URL do UserScript',
          ),
          onSubmitted: (value) => Navigator.of(dialogContext).pop(value.trim()),
        ),
        actions: <Widget>[
          TextButton(onPressed: () => Navigator.of(dialogContext).pop(), child: const Text('Cancelar')),
          TextButton(onPressed: () => Navigator.of(dialogContext).pop(controller.text.trim()), child: const Text('Continuar')),
        ],
      ),
    );
    controller.dispose();
    if (url == null || url.isEmpty || !mounted) return;
    final uri = Uri.tryParse(url);
    final id = 'custom-${(uri?.host ?? 'script')}-${url.hashCode.abs()}';
    await _installFromUrl(id: id, name: '', description: '', url: url);
  }

  Future<bool> _confirmInstall({required String name, required String host}) async {
    return await showDialog<bool>(
          context: context,
          builder: (dialogContext) => AlertDialog(
            title: const Text('Instalar script?'),
            content: Text(
              '“$name” será transferido de $host. Um UserScript pode ler e alterar o conteúdo das páginas dos sites indicados no seu cabeçalho e pode enviar dados para servidores externos. Instala apenas código de fontes em que confias.',
            ),
            actions: <Widget>[
              TextButton(
                onPressed: () => Navigator.of(dialogContext).pop(false),
                child: const Text('Cancelar'),
              ),
              TextButton(
                onPressed: () => Navigator.of(dialogContext).pop(true),
                child: const Text('Instalar'),
              ),
            ],
          ),
        ) ??
        false;
  }

  Future<void> _toggleInstalled(Map<String, dynamic> item, bool enabled) async {
    final id = item['id']?.toString() ?? '';
    try {
      await _native.invokeMethod<bool>('setEnabled', <String, dynamic>{
        'id': id,
        'enabled': enabled,
      });
      await _loadInstalled();
      _showMessage('A alteração será aplicada na próxima página carregada.');
    } catch (_) {
      _showMessage('Não foi possível atualizar este script.');
    }
  }

  Future<void> _removeInstalled(Map<String, dynamic> item) async {
    final id = item['id']?.toString() ?? '';
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Remover script?'),
        content: Text('Queres remover “${item['name'] ?? 'Script'}” do BrowseIt?'),
        actions: <Widget>[
          TextButton(onPressed: () => Navigator.of(dialogContext).pop(false), child: const Text('Cancelar')),
          TextButton(onPressed: () => Navigator.of(dialogContext).pop(true), child: const Text('Remover')),
        ],
      ),
    );
    if (confirmed != true) return;
    try {
      await _native.invokeMethod<bool>('remove', <String, dynamic>{'id': id});
      await _loadInstalled();
      _showMessage('Script removido.');
    } catch (_) {
      _showMessage('Não foi possível remover o script.');
    }
  }

  void _showMessage(String message) {
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final bg = pageBg(context);
    final text = mainText(context);
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
          toolbarHeight: 64,
          automaticallyImplyLeading: false,
          backgroundColor: bg,
          foregroundColor: text,
          surfaceTintColor: Colors.transparent,
          elevation: 0,
          scrolledUnderElevation: 0,
          leading: AppBackButton(
            tooltip: appText('back'),
            color: text,
            onPressed: () => Navigator.of(context).maybePop(),
          ),
          title: const Text('Extensões', style: TextStyle(fontSize: 21, fontWeight: FontWeight.w600)),
          actions: <Widget>[
            IconButton(
              tooltip: 'Instalar por URL',
              onPressed: _promptImportByUrl,
              icon: const AppMaterialIcon(AppIcons.plus, size: 24),
            ),
            const SizedBox(width: 6),
          ],
        ),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 28),
          children: [
            Text(
              'Scripts úteis para personalizar páginas no motor Chromium.',
              style: TextStyle(color: mutedText(context), fontSize: 13, height: 1.4),
            ),
            const SizedBox(height: 12),
            _CompatibilityNotice(),
            const SizedBox(height: 22),
            Row(
              children: [
                Expanded(
                  child: Text('Instaladas', style: TextStyle(color: text, fontSize: 16, fontWeight: FontWeight.w700)),
                ),
                Text('${_installed.length}', style: TextStyle(color: mutedText(context), fontSize: 12)),
              ],
            ),
            const SizedBox(height: 9),
            if (_loadingInstalled)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 20),
                child: Center(child: CircularProgressIndicator.adaptive()),
              )
            else if (_installed.isEmpty)
              _EmptyExtensionsCard(onImport: _promptImportByUrl)
            else
              ..._installed.map(_installedCard),
            const SizedBox(height: 22),
            Text('Extensões recomendadas', style: TextStyle(color: text, fontSize: 16, fontWeight: FontWeight.w700)),
            const SizedBox(height: 4),
            Text('Pequenas ferramentas UserScript prontas a instalar, sem precisares de introduzir ligações.', style: TextStyle(color: mutedText(context), fontSize: 11.5, height: 1.35)),
            const SizedBox(height: 9),
            ..._builtinExtensions.map(_builtinCard),
            const SizedBox(height: 16),
            Text('Explorar loja online', style: TextStyle(color: text, fontSize: 16, fontWeight: FontWeight.w700)),
            const SizedBox(height: 9),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _searchController,
                    textInputAction: TextInputAction.search,
                    onSubmitted: _search,
                    decoration: InputDecoration(
                      hintText: 'Pesquisar scripts',
                      prefixIcon: const AppMaterialIcon(AppIcons.magnifyingGlass, size: 21),
                      filled: true,
                      fillColor: settingsCardColor(context),
                      isDense: true,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(999), borderSide: BorderSide.none),
                      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(999), borderSide: BorderSide.none),
                      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(999), borderSide: BorderSide(color: lineColor(context))),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Material(
                  color: settingsCardColor(context),
                  borderRadius: BorderRadius.circular(999),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(999),
                    onTap: _searching ? null : () => _search(_searchController.text),
                    child: SizedBox(
                      width: 48,
                      height: 48,
                      child: Center(
                        child: _searching
                            ? const SizedBox(width: 19, height: 19, child: CircularProgressIndicator.adaptive(strokeWidth: 2))
                            : AppMaterialIcon(AppIcons.magnifyingGlass, color: text, size: 22),
                      ),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            if (_searchError != null)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 18),
                child: Text(_searchError!, style: TextStyle(color: mutedText(context), fontSize: 13)),
              )
            else if (_searching && _results.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 24),
                child: Center(child: CircularProgressIndicator.adaptive()),
              )
            else if (_results.isEmpty)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 18),
                child: Text('Não foram encontrados scripts para esta pesquisa.', style: TextStyle(color: mutedText(context), fontSize: 13)),
              )
            else
              ..._results.map(_listingCard),
            const SizedBox(height: 12),
            Text(
              'Catálogo externo: Greasy Fork. Analisa a origem e o código antes de instalar. As alterações entram em vigor quando uma página é carregada novamente.',
              style: TextStyle(color: mutedText(context), fontSize: 11.5, height: 1.4),
            ),
          ],
        ),
      ),
    );
  }

  Widget _installedCard(Map<String, dynamic> item) {
    final name = item['name']?.toString() ?? 'Script';
    final description = item['description']?.toString() ?? '';
    final enabled = item['enabled'] == true;
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(color: settingsCardColor(context), borderRadius: BorderRadius.circular(20)),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(13, 12, 7, 12),
        child: Row(
          children: [
            Container(
              width: 38,
              height: 38,
              decoration: BoxDecoration(color: pillColor(context), shape: BoxShape.circle),
              child: Center(child: AppMaterialIcon(AppIcons.browser, color: mainText(context), size: 20)),
            ),
            const SizedBox(width: 11),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(name, maxLines: 1, overflow: TextOverflow.ellipsis, style: TextStyle(color: mainText(context), fontSize: 13.5, fontWeight: FontWeight.w600)),
                  if (description.isNotEmpty) ...[
                    const SizedBox(height: 3),
                    Text(description, maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(color: mutedText(context), fontSize: 11.5, height: 1.25)),
                  ],
                ],
              ),
            ),
            Switch.adaptive(value: enabled, onChanged: (value) => _toggleInstalled(item, value)),
            IconButton(
              tooltip: 'Remover',
              onPressed: () => _removeInstalled(item),
              icon: AppMaterialIcon(AppIcons.trash, color: mutedText(context), size: 21),
            ),
          ],
        ),
      ),
    );
  }

  Widget _listingCard(_UserScriptListing item) {
    final installed = _isInstalled('greasyfork-${item.id}');
    final working = _workingId == 'greasyfork-${item.id}';
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(color: settingsCardColor(context), borderRadius: BorderRadius.circular(20)),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(13, 12, 12, 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(item.name, maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(color: mainText(context), fontSize: 13.5, fontWeight: FontWeight.w600)),
                ),
                const SizedBox(width: 8),
                Text('${item.totalInstalls} instalações', style: TextStyle(color: mutedText(context), fontSize: 10.5)),
              ],
            ),
            if (item.description.isNotEmpty) ...[
              const SizedBox(height: 5),
              Text(item.description, maxLines: 3, overflow: TextOverflow.ellipsis, style: TextStyle(color: mutedText(context), fontSize: 11.5, height: 1.3)),
            ],
            const SizedBox(height: 9),
            Align(
              alignment: Alignment.centerRight,
              child: TextButton.icon(
                onPressed: installed || working ? null : () => _installListing(item),
                icon: working
                    ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator.adaptive(strokeWidth: 2))
                    : AppMaterialIcon(installed ? AppIcons.check : AppIcons.downloadSimple, size: 18),
                label: Text(installed ? 'Instalado' : working ? 'A transferir' : 'Instalar'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _UserScriptListing {
  const _UserScriptListing({
    required this.id,
    required this.name,
    required this.description,
    required this.codeUrl,
    required this.totalInstalls,
  });

  final String id;
  final String name;
  final String description;
  final String codeUrl;
  final int totalInstalls;
}

class _CompatibilityNotice extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final bg = settingsCardColor(context);
    final muted = mutedText(context);
    return Container(
      padding: const EdgeInsets.all(13),
      decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(18)),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AppMaterialIcon(AppIcons.info, size: 21, color: mainText(context)),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              'O WebView usa Chromium, mas não inclui o runtime completo de extensões do Chrome. Esta loja instala UserScripts com @match/@include (padrões simples com *). APIs chrome.*, processos em segundo plano, @require e GM_* não são suportados.',
              style: TextStyle(color: muted, fontSize: 11.5, height: 1.35),
            ),
          ),
        ],
      ),
    );
  }
}

class _EmptyExtensionsCard extends StatelessWidget {
  const _EmptyExtensionsCard({required this.onImport});

  final VoidCallback onImport;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 16),
      decoration: BoxDecoration(color: settingsCardColor(context), borderRadius: BorderRadius.circular(20)),
      child: Row(
        children: [
          AppMaterialIcon(AppIcons.browser, color: mutedText(context), size: 22),
          const SizedBox(width: 10),
          Expanded(child: Text('Ainda não instalaste scripts.', style: TextStyle(color: mutedText(context), fontSize: 12))),
          TextButton(onPressed: onImport, child: const Text('Adicionar')),
        ],
      ),
    );
  }
}


class _BuiltinExtension {
  const _BuiltinExtension({
    required this.id,
    required this.name,
    required this.description,
    required this.siteLabel,
    required this.icon,
    required this.source,
  });

  final String id;
  final String name;
  final String description;
  final String siteLabel;
  final IconData icon;
  final String source;
}

const List<_BuiltinExtension> _builtinExtensions = <_BuiltinExtension>[
  _BuiltinExtension(
    id: 'browseit-night-toggle',
    name: 'Modo escuro com botão',
    description: 'Adiciona um botão discreto para alternar uma aparência escura em páginas compatíveis.',
    siteLabel: 'Vários sites',
    icon: AppIcons.moon,
    source: r'''// ==UserScript==
// @name BrowseIt Night Toggle
// @namespace browseit.local
// @version 1.0.0
// @description Adiciona um botão local para alternar um modo escuro simples.
// @match *://*/*
// @run-at document-end
// @grant none
// ==/UserScript==
(function () {
  if (document.getElementById('browseit-night-toggle')) return;
  const style = document.createElement('style');
  style.id = 'browseit-night-style';
  style.textContent = `
    html.browseit-night, html.browseit-night body {
      background: #111315 !important; color: #e7e9ec !important;
      color-scheme: dark !important;
    }
    html.browseit-night body *:not(img):not(video):not(canvas):not(svg):not(#browseit-night-toggle) {
      background-color: transparent !important; color: inherit !important;
      border-color: #34383d !important;
    }
    html.browseit-night a { color: #8ab4f8 !important; }
    html.browseit-night input, html.browseit-night textarea, html.browseit-night select {
      background: #202327 !important; color: #f2f3f4 !important;
    }
  `;
  document.documentElement.appendChild(style);
  const button = document.createElement('button');
  button.id = 'browseit-night-toggle';
  button.type = 'button';
  button.textContent = '☾';
  button.setAttribute('aria-label', 'Alternar modo escuro');
  button.style.cssText = 'position:fixed!important;right:16px!important;bottom:24px!important;z-index:2147483647!important;width:44px!important;height:44px!important;border:0!important;border-radius:50%!important;background:#272a30!important;color:#fff!important;font:22px sans-serif!important;box-shadow:0 3px 14px #0006!important;';
  button.addEventListener('click', () => document.documentElement.classList.toggle('browseit-night'));
  document.body.appendChild(button);
  document.documentElement.classList.add('browseit-night');
})();''',
  ),
  _BuiltinExtension(
    id: 'browseit-youtube-shorts-cleaner',
    name: 'Limpar YouTube Shorts',
    description: 'Oculta prateleiras e blocos de Shorts para reduzir distrações no YouTube.',
    siteLabel: 'YouTube',
    icon: AppIcons.playCircle,
    source: r'''// ==UserScript==
// @name BrowseIt YouTube Shorts Cleaner
// @namespace browseit.local
// @version 1.0.0
// @description Oculta prateleiras e secções de YouTube Shorts.
// @match https://www.youtube.com/*
// @match https://m.youtube.com/*
// @run-at document-end
// @grant none
// ==/UserScript==
(function () {
  if (window.__browseitShortsCleaner) return;
  window.__browseitShortsCleaner = true;
  const css = document.createElement('style');
  css.textContent = `ytd-reel-shelf-renderer, ytd-reel-video-renderer, a[href^="/shorts/"] { display:none!important; }`;
  document.documentElement.appendChild(css);
  const clean = () => {
    document.querySelectorAll('ytd-rich-section-renderer').forEach(section => {
      if (section.querySelector('a[href^="/shorts/"]')) section.style.display = 'none';
    });
  };
  clean();
  new MutationObserver(clean).observe(document.documentElement, { childList:true, subtree:true });
})();''',
  ),
  _BuiltinExtension(
    id: 'browseit-youtube-speed-control',
    name: 'Controlo rápido de velocidade',
    description: 'Botão flutuante para alternar a velocidade do vídeo entre 1× e 2×.',
    siteLabel: 'YouTube',
    icon: AppIcons.gauge,
    source: r'''// ==UserScript==
// @name BrowseIt YouTube Speed Control
// @namespace browseit.local
// @version 1.0.0
// @description Botão para alternar a velocidade de reprodução dos vídeos.
// @match https://www.youtube.com/*
// @match https://m.youtube.com/*
// @run-at document-end
// @grant none
// ==/UserScript==
(function () {
  if (document.getElementById('browseit-speed-toggle')) return;
  const speeds = [1, 1.25, 1.5, 1.75, 2];
  let index = 0;
  const button = document.createElement('button');
  button.id = 'browseit-speed-toggle';
  button.type = 'button';
  button.textContent = '1×';
  button.style.cssText = 'position:fixed!important;right:16px!important;bottom:84px!important;z-index:2147483647!important;padding:10px 13px!important;border:0!important;border-radius:999px!important;background:#202124!important;color:#fff!important;font:600 13px sans-serif!important;box-shadow:0 3px 14px #0006!important;';
  button.addEventListener('click', () => {
    index = (index + 1) % speeds.length;
    const video = document.querySelector('video');
    if (video) video.playbackRate = speeds[index];
    button.textContent = speeds[index] + '×';
  });
  document.body.appendChild(button);
})();''',
  ),
  _BuiltinExtension(
    id: 'browseit-wikipedia-reading-layout',
    name: 'Leitura confortável',
    description: 'Alarga a coluna do artigo e reduz elementos laterais na Wikipédia.',
    siteLabel: 'Wikipedia',
    icon: AppIcons.bookmarksSimple,
    source: r'''// ==UserScript==
// @name BrowseIt Wikipedia Reading Layout
// @namespace browseit.local
// @version 1.0.0
// @description Ajusta a largura e a tipografia da área principal dos artigos da Wikipédia.
// @match https://*.wikipedia.org/wiki/*
// @run-at document-end
// @grant none
// ==/UserScript==
(function () {
  if (document.getElementById('browseit-wikipedia-reader')) return;
  const style = document.createElement('style');
  style.id = 'browseit-wikipedia-reader';
  style.textContent = `
    #content, #bodyContent, .mw-body { max-width: 980px!important; margin-left:auto!important; margin-right:auto!important; }
    #mw-panel, #right-navigation, #left-navigation, .vector-sidebar-container { display:none!important; }
    .mw-parser-output { font-size:1.08rem!important; line-height:1.75!important; }
    .mw-parser-output p { margin:0 0 1.05em!important; }
  `;
  document.documentElement.appendChild(style);
})();''',
  ),
];
