# UserScripts no BrowseIt

## Motor do navegador

O BrowseIt continua a usar `android.webkit.WebView`, que é baseado no motor Chromium fornecido pelo Android System WebView do dispositivo. Não é uma distribuição autónoma do Chromium nem incorpora o runtime de extensões do Google Chrome.

Por esse motivo, ficheiros `.crx` e extensões completas da Chrome Web Store não são compatíveis. O suporte implementado é uma camada própria de UserScripts, semelhante ao modelo de scripts de conteúdo de gestores de userscripts.

## Funcionalidades suportadas

- Pesquisa e instalação de scripts a partir do catálogo público Greasy Fork.
- Importação de um UserScript a partir de um URL HTTPS.
- Lista de scripts instalados, ativação/desativação e remoção.
- Metadados `@name`, `@description`, `@match`, `@include`, `@exclude` e `@run-at` (`document-start`, `document-end` e `document-idle`).
- Padrões de endereço simples com `*` para `@match`, `@include` e `@exclude`.
- Injeção no início do documento quando o WebView suporta `DOCUMENT_START_SCRIPT`; caso contrário, a injeção é feita depois de a página terminar de carregar.
- Scripts desativados em navegação incógnita.

## Limitações

Não são implementados `chrome.*`, `browser.*`, `background service workers`, páginas de opções/popup da extensão, `native messaging`, `GM_*`, `@require`, content scripts com isolamento próprio ou regras avançadas de correspondência por expressão regular. Scripts que dependam destas APIs ou de dependências externas podem não funcionar.

Os UserScripts executam no contexto da página, sem um mundo isolado, e podem ler ou alterar o seu conteúdo. Alguns sites também podem restringir a execução dinâmica de código pelas suas políticas de segurança (CSP). Instala apenas scripts de fontes em que confias. O catálogo externo é comunitário; a presença no catálogo não constitui uma auditoria de segurança.

## Onde gerir

`Definições → Navegador → Extensões` permite procurar, instalar, ativar, desativar e remover UserScripts. A configuração é carregada quando a atividade do navegador é aberta; depois de instalar, ativar, desativar ou remover um script, reabre o separador para aplicar a alteração.
