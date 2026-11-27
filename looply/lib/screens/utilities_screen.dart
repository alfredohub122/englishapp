import 'package:material_ui/material_ui.dart';

import '../shell/browser_navigation.dart';
import '../theme.dart';
import '../widgets/material_symbols.dart';

class UtilitiesScreen extends StatelessWidget {
  const UtilitiesScreen({super.key});

  void _open(BuildContext context, String url, String title) {
    openOrbitTab(context, url, title: title);
  }

  @override
  Widget build(BuildContext context) {
    final bg = pageBg(context);
    final text = mainText(context);
    final muted = mutedText(context);
    final items = <_UtilityItem>[
      _UtilityItem('Calculadora', 'Cálculos rápidos no navegador', AppIcons.statistics, 'https://www.google.com/search?q=calculator'),
      _UtilityItem('Tradutor', 'Traduzir texto entre idiomas', AppIcons.globe, 'https://translate.google.com/?sl=auto&tl=pt'),
      _UtilityItem('Conversor de unidades', 'Converter medidas e unidades', AppIcons.slidersHorizontal, 'https://www.google.com/search?q=unit+converter'),
      _UtilityItem('Teste de velocidade', 'Verificar a velocidade da ligação', AppIcons.gauge, 'https://speed.cloudflare.com/'),
    ];

    return Scaffold(
      backgroundColor: bg,
      appBar: AppBar(
        toolbarHeight: 64,
        automaticallyImplyLeading: false,
        backgroundColor: bg,
        foregroundColor: text,
        surfaceTintColor: Colors.transparent,
        elevation: 0,
        scrolledUnderElevation: 0,
        leading: AppBackButton(tooltip: 'Voltar', color: text),
        title: Text('Utilitários', style: TextStyle(color: text, fontSize: 21, fontWeight: FontWeight.w600)),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
        children: <Widget>[
          Text('Ferramentas rápidas para usar durante a navegação.', style: TextStyle(color: muted, fontSize: 13, height: 1.4)),
          const SizedBox(height: 17),
          for (final item in items)
            Container(
              margin: const EdgeInsets.only(bottom: 9),
              decoration: BoxDecoration(color: settingsCardColor(context), borderRadius: BorderRadius.circular(22)),
              child: Material(
                color: Colors.transparent,
                child: InkWell(
                  borderRadius: BorderRadius.circular(22),
                  onTap: () => _open(context, item.url, item.title),
                  child: Padding(
                    padding: const EdgeInsets.all(14),
                    child: Row(
                      children: <Widget>[
                        Container(
                          width: 44,
                          height: 44,
                          decoration: BoxDecoration(color: pillColor(context), shape: BoxShape.circle),
                          child: Center(child: AppMaterialIcon(item.icon, color: text, size: 22)),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: <Widget>[
                              Text(item.title, style: TextStyle(color: text, fontSize: 14, fontWeight: FontWeight.w600)),
                              const SizedBox(height: 3),
                              Text(item.subtitle, style: TextStyle(color: muted, fontSize: 11.5)),
                            ],
                          ),
                        ),
                        AppMaterialIcon(AppIcons.caretRight, color: muted, size: 20),
                      ],
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _UtilityItem {
  const _UtilityItem(this.title, this.subtitle, this.icon, this.url);
  final String title;
  final String subtitle;
  final IconData icon;
  final String url;
}
