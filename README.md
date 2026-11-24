# English Course — Android nativo

Aplicação de aprendizagem de inglês construída com **Kotlin e Jetpack Compose**. A interface e a lógica da aplicação são Kotlin nativo com Jetpack Compose; não usa Flutter, JavaScript nem WebView. O conteúdo do curso é carregado de ficheiros de dados locais e formatado por componentes Android. A configuração Gradle e os recursos Android XML/JSON são apenas os ficheiros de construção e dados necessários ao projeto Android.

## Conteúdo

- 93 aulas de inglês organizadas por níveis A1–C1.
- 16 leituras com tradução, glossário e perguntas de compreensão.
- Vocabulário geral, termos das aulas e expressões dos textos (índice local expandido a partir dos dados do curso).
- Questionários e acompanhamento local do progresso.
- Dicionário bilingue inglês → português e português → inglês, com pesquisa offline depois de instalado; inclui os termos das aulas/leituras e as formas de 126 verbos irregulares e 106 phrasal verbs do curso.
- Tema exclusivamente claro e interface nativa adaptada a telemóveis.
- Bandeira fornecida no pedido como ícone de lançamento Android (mipmap normal e adaptativo).
- Ícones Ionicons SVG descarregados automaticamente durante a compilação e desenhados pela interface Kotlin.

## Correção do workflow

A verificação de implementação nativa examina apenas os ficheiros de código e recursos textuais Kotlin/Gradle/XML. As bases SQLite do dicionário em `app/src/main/assets/dictionary/` são dados binários e ficam excluídas dessa pesquisa.

## Compilação

Abra a pasta no Android Studio com JDK 17 e SDK Android 35, ou execute `./gradlew :app:assembleRelease` (Windows: `gradlew.bat :app:assembleRelease`). A compilação `:app:assembleRelease` descarrega automaticamente os SVG Ionicons e as duas bases WikDict, caso ainda não estejam em `app/src/main/assets/`.

Também pode usar o workflow `.github/workflows/build.yml`: faça push para `main` ou `master`, ou execute `workflow_dispatch`. O workflow prepara Java 17 e Gradle 8.11.1, descarrega os recursos externos, compila `assembleRelease`, publica o APK na release `latest-build` e escreve o link direto para descarregar no resumo da execução.

O APK deste projeto é assinado com a chave debug do Android, adequada para testes e instalação direta. Antes de distribuir uma versão pública na Play Store, configure uma chave de assinatura release própria e guarde-a nos secrets do GitHub Actions.

## Dicionário e atribuições

As bases de dados são fornecidas pelo WikDict, no formato SQLite, em pares de idiomas independentes (`en-pt` e `pt-en`). O índice do curso complementa a pesquisa com termos, verbos, expressões e exemplos incluídos nas aulas e leituras. As entradas e traduções disponíveis dependem da cobertura da edição da base de dados; o dicionário não traduz frases livres completas.

- WikDict: https://www.wikdict.com/page/download — dados distribuídos sob Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0). A licença exige atribuição, indicação de alterações e partilha de adaptações sob os mesmos termos.
- Ionicons: https://github.com/ionic-team/ionicons — ícones SVG distribuídos sob MIT; a licença MIT está incluída em `app/src/main/assets/ionicons/LICENSE.txt`.
- AndroidSVG: https://bigbadaboom.github.io/androidsvg/ — renderizador SVG para Android sob Apache License 2.0; texto da licença incluído em `app/src/main/assets/ionicons/ANDROIDSVG-APACHE-2.0.txt`.

As versões utilizadas no build estão fixadas no Gradle: Ionicons `v8.1.0` e WikDict `2_2026-06`.


## Diagnóstico de falhas de arranque

A aplicação inclui uma tela de diagnóstico para falhas fatais. Se ocorrer uma exceção não tratada, o app guarda o relatório (tipo, mensagem, stack trace, versão Android e dispositivo) e mostra-o na próxima abertura. A tela também valida os três JSON do curso, tenta carregar o conteúdo, verifica o SVG principal do Ionicons e confirma os cabeçalhos das duas bases SQLite. O relatório pode ser copiado para a área de transferência.

Depois de atualizar o repositório e gerar um novo APK, instala-o por cima da versão anterior. Se voltar a fechar, abre novamente a aplicação e usa **Copiar relatório completo** para obter a causa técnica. A opção **Limpar aviso e tentar abrir novamente** apenas limpa o relatório guardado e reinicia a aplicação; não apaga o progresso do curso.
